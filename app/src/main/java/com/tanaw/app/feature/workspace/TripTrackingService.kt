package com.tanaw.app.feature.workspace

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tanaw.app.BuildConfig
import com.tanaw.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.time.Instant
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class TrackingStatus(val delivery:String?=null,val message:String="Tracking is stopped",val buffered:Int=0)
object TripTrackingStatus { val state=MutableStateFlow(TrackingStatus()) }

/** Private, project/account/delivery-scoped durable queue; retries reuse immutable sample IDs. */
class TrackingQueue(context:Context,databaseName:String="tracking_${URI(BuildConfig.SUPABASE_URL).host}.db"):SQLiteOpenHelper(context,databaseName,null,1) {
 override fun onCreate(db:SQLiteDatabase) { db.execSQL("CREATE TABLE samples(id TEXT PRIMARY KEY, owner TEXT NOT NULL, delivery TEXT NOT NULL, captured INTEGER NOT NULL, payload TEXT NOT NULL)") }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) = Unit
 fun add(owner:String,delivery:String,location:Location) {
  val recorded=Instant.ofEpochMilli(location.time)
  val payload=buildJsonObject {put("lat",location.latitude);put("lng",location.longitude);put("accuracy",location.accuracy);put("recorded_at",recorded.toString());put("buffered",true)}
  writableDatabase.insertOrThrow("samples",null,ContentValues().apply {put("id",UUID.randomUUID().toString());put("owner",owner);put("delivery",delivery);put("captured",location.time);put("payload",payload.toString())})
 }
 fun clearOwner(owner:String) {writableDatabase.delete("samples","owner=?",arrayOf(owner))}
 fun retain(owner:String,delivery:String) {
  writableDatabase.delete("samples","owner<>? OR delivery<>? OR captured<?",arrayOf(owner,delivery,(System.currentTimeMillis()-86400000).toString()))
 }
 fun pending(owner:String,delivery:String):List<Triple<String,String,Long>> = readableDatabase.query("samples",arrayOf("id","payload","captured"),"owner=? AND delivery=?",arrayOf(owner,delivery),null,null,"captured ASC","100").use { cursor->buildList {while(cursor.moveToNext())add(Triple(cursor.getString(0),cursor.getString(1),cursor.getLong(2)))} }
 fun remove(id:String) {writableDatabase.delete("samples","id=?",arrayOf(id))}
}

@AndroidEntryPoint
class TripTrackingService:Service(),LocationListener {
 @Inject lateinit var client:SupabaseClient
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private lateinit var locations:LocationManager
 private lateinit var queue:TrackingQueue
 private var owner:String?=null;private var delivery:String?=null;private var loop:Job?=null
 private var lastFix:Long?=null
 private var stoppedReason:String?=null
 private fun fail(message:String) {stoppedReason=message;TripTrackingStatus.state.value=TrackingStatus(message=message);stopSelf()}
 override fun onBind(intent:Intent?) = null
 override fun onCreate() {super.onCreate();locations=getSystemService(LocationManager::class.java);queue=TrackingQueue(this)}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="stop") {stopSelf();return START_NOT_STICKY}
  val uid=intent?.getStringExtra("owner");val id=intent?.getStringExtra("delivery")
  if(uid==null || id==null || client.auth.currentUserOrNull()?.id!=uid || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {stopSelf();return START_NOT_STICKY}
  owner=uid;delivery=id;lastFix=null;stoppedReason=null
  val manager=getSystemService(NotificationManager::class.java)
  if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel("active_trip","Active trip location",NotificationManager.IMPORTANCE_LOW))
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop=PendingIntent.getService(this,1,Intent(this,TripTrackingService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification=NotificationCompat.Builder(this,"active_trip").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Tanaw trip tracking")
   .setContentText("Trip location service · open app for current GPS status").setContentIntent(open).setOngoing(true).addAction(0,"Stop tracking",stop).build()
  try {
   ServiceCompat.startForeground(this,731,notification,if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
   locations.removeUpdates(this)
   val provider=TrackingSafety.provider(locations::isProviderEnabled)
   locations.requestLocationUpdates(provider,15000,0f,this,Looper.getMainLooper())
  } catch(_:SecurityException) {fail("Precise location permission is required. Allow it, then Resume location sharing.");return START_NOT_STICKY}
  catch(_:RuntimeException) {fail("Location services could not start. Enable location, then Resume location sharing.");return START_NOT_STICKY}
  loop?.cancel()
  loop=scope.launch {
   queue.retain(uid,id)
   while(isActive) {
    if(client.auth.currentUserOrNull()?.id!=uid) {queue.clearOwner(uid);stopSelf();break}
    try {
     // Reauthorize even when no location fix arrives; a remote cancellation must stop the service.
     val data=client.postgrest.rpc("mobile_workspace").decodeAs<JsonObject>()
     val current=data.rows("deliveries").firstOrNull {it.value("id")==id}
     if(data.obj("profile").value("role")!="driver" || current==null || current.value("status") !in listOf("assigned","loading","in_transit")) {
      // Existing server post-delivery window controls final buffered samples.
      flush(uid,id);queue.clearOwner(uid);stopSelf();break
     }
     flush(uid,id)
     TripTrackingStatus.state.value=TrackingStatus(id,if(lastFix?.let {System.currentTimeMillis()-it in 0..60000}==true)"Sharing trip location" else "Waiting for fresh accurate GPS · try outdoors",queue.pending(uid,id).size)
    } catch(cancelled:CancellationException) {throw cancelled}
    catch(error:Exception) {
     val detail=error.message.orEmpty()
     if(detail.contains("ACTIVE_") || detail.contains("DELIVERY_CLOSED")) {queue.clearOwner(uid);stopSelf();break}
     TripTrackingStatus.state.value=TrackingStatus(id,"Offline or reconnecting · samples queued",queue.pending(uid,id).size)
    }
    delay(15.seconds)
   }
  }
  TripTrackingStatus.state.value=TrackingStatus(id,"Waiting for a location fix")
  return START_NOT_STICKY
 }
 private suspend fun flush(uid:String,id:String) {
  for((sample,payload,_) in queue.pending(uid,id)) {
   if(client.auth.currentUserOrNull()?.id!=uid)return
   try {client.postgrest.rpc("driver_record_position",buildJsonObject {put("p_delivery",id);put("p_sample",sample);put("p_data",Json.parseToJsonElement(payload))});queue.remove(sample)}
   catch(error:Exception) {if(error is CancellationException)throw error;if(error.message.orEmpty().contains("INVALID_POSITION")){queue.remove(sample)}else throw error}
  }
 }
 override fun onLocationChanged(location:Location) {
  val uid=owner?:return;val id=delivery?:return
  if(client.auth.currentUserOrNull()?.id!=uid || !location.hasAccuracy() || !TrackingSafety.valid(location.latitude,location.longitude,location.accuracy,System.currentTimeMillis()-location.time))return
  lastFix=location.time
  queue.add(uid,id,location)
 }
 override fun onProviderDisabled(provider:String) {fail("Location provider disabled. Enable location, then Resume location sharing.")}
 override fun onDestroy() {
  if(::locations.isInitialized) runCatching {locations.removeUpdates(this)}
  scope.cancel();queue.close();TripTrackingStatus.state.value=TrackingStatus(message=stoppedReason?:"Location sharing stopped. Resume it while this trip is active.")
  ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE);super.onDestroy()
 }
 companion object {
  fun start(context:Context,owner:String,delivery:String) {ContextCompat.startForegroundService(context,Intent(context,TripTrackingService::class.java).putExtra("owner",owner).putExtra("delivery",delivery))}
  fun stop(context:Context) {context.stopService(Intent(context,TripTrackingService::class.java))}
 }
}
