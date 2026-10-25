package com.tanaw.app.feature.workspace

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tanaw.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import java.net.URL
import java.net.URLEncoder

data class SelectedAddress(val label:String,val latitude:Double,val longitude:Double)
object MobileGeocoding {
 suspend fun search(query:String):List<SelectedAddress> = withContext(Dispatchers.IO) {
  require(query.trim().length>=3 && BuildConfig.MAPTILER_KEY.isNotBlank())
  val url=URL("https://api.maptiler.com/geocoding/${URLEncoder.encode(query.trim(),"UTF-8")}.json?country=ph&limit=5&key=${URLEncoder.encode(BuildConfig.MAPTILER_KEY,"UTF-8")}")
  val connection=url.openConnection().apply { connectTimeout=10000;readTimeout=10000 }
  val response=connection.getInputStream().bufferedReader().use { it.readText() }
  Json.parseToJsonElement(response).jsonObject["features"]?.jsonArray?.mapNotNull { feature ->
   val record=feature.jsonObject;val center=record["center"]?.jsonArray ?: return@mapNotNull null
   SelectedAddress(record["place_name"]?.jsonPrimitive?.content ?: query,center[1].jsonPrimitive.double,center[0].jsonPrimitive.double)
  }.orEmpty()
 }
 @Suppress("DEPRECATION")
 suspend fun reverse(context:android.content.Context,point:LatLng):String?=withContext(Dispatchers.IO) {
  val provider=runCatching {
   require(BuildConfig.MAPTILER_KEY.isNotBlank())
   val url=URL("https://api.maptiler.com/geocoding/${point.longitude},${point.latitude}.json?language=en&limit=1&key=${URLEncoder.encode(BuildConfig.MAPTILER_KEY,"UTF-8")}")
   val connection=url.openConnection().apply {connectTimeout=6000;readTimeout=6000}
   val data=Json.parseToJsonElement(connection.getInputStream().bufferedReader().use {it.readText()}).jsonObject
   data["features"]?.jsonArray?.firstOrNull()?.jsonObject?.get("place_name")?.jsonPrimitive?.contentOrNull
  }.getOrNull()
  provider?.takeIf {it.isNotBlank()} ?: runCatching {
   if(!android.location.Geocoder.isPresent()) null else android.location.Geocoder(context,java.util.Locale.ENGLISH)
    .getFromLocation(point.latitude,point.longitude,1)?.firstOrNull()?.getAddressLine(0)
  }.getOrNull()
 }

}

private const val STREET_MAP = "https://tiles.openfreemap.org/styles/liberty"

private fun pinIcon(context:android.content.Context,label:String):org.maplibre.android.annotations.Icon {
 val scale=context.resources.displayMetrics.density
 val bitmap=android.graphics.Bitmap.createBitmap((32*scale).toInt(),(42*scale).toInt(),android.graphics.Bitmap.Config.ARGB_8888)
 val canvas=android.graphics.Canvas(bitmap);canvas.scale(scale,scale)
 val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
 paint.color=if(label=="Destination")android.graphics.Color.rgb(37,99,235) else android.graphics.Color.rgb(220,38,38)
 val path=android.graphics.Path().apply {moveTo(16f,41f);lineTo(3f,22f);cubicTo(-9f,-6f,41f,-6f,29f,22f);close()}
 canvas.drawPath(path,paint)
 paint.color=android.graphics.Color.WHITE;canvas.drawCircle(16f,14f,7f,paint)
 return org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(bitmap)
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
@Composable fun ShipmentMap(points:List<Pair<String,LatLng>>,geometry:String?=null,onSelect:((LatLng)->Unit)?=null) {
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 val callback by rememberUpdatedState(onSelect)
 val pinIcons=remember(context) {mapOf("Pickup" to pinIcon(context,"Pickup"),"Destination" to pinIcon(context,"Destination"))}
 var previousGeometry by remember {mutableStateOf<String?>(null)}
 var fallback by remember {mutableStateOf(BuildConfig.MAPTILER_KEY.isBlank())}
 var ready by remember {mutableStateOf(false)}
 var failed by remember {mutableStateOf(false)}
 var requested by remember {mutableStateOf(false)}
 var previous by remember {mutableStateOf<List<Triple<String,Double,Double>>?>(null)}
 val view=remember {MapLibre.getInstance(context);MapView(context).apply {onCreate(null)}}
 DisposableEffect(view,lifecycle) {
  view.getMapAsync {map ->
   map.cameraPosition=CameraPosition.Builder().target(LatLng(14.5995,120.9842)).zoom(11.0).build()
   map.addOnMapClickListener {point -> if(ready && !failed) callback?.invoke(point);callback!=null}
  }
  view.addOnDidFinishLoadingMapListener {ready=true;failed=false}
  view.addOnDidFailLoadingMapListener { _ ->
   ready=false
   if(!fallback) {fallback=true;view.getMapAsync {it.setStyle(STREET_MAP)}} else failed=true
  }
  // The map owns gestures inside its bounds; the page scrolls outside the map.
  view.setOnTouchListener {_,event ->
   view.parent?.requestDisallowInterceptTouchEvent(event.actionMasked!=android.view.MotionEvent.ACTION_UP && event.actionMasked!=android.view.MotionEvent.ACTION_CANCEL)
   false
  }
  val observer=LifecycleEventObserver { _,event -> when(event) {
   Lifecycle.Event.ON_START->view.onStart();Lifecycle.Event.ON_RESUME->view.onResume();Lifecycle.Event.ON_PAUSE->view.onPause();Lifecycle.Event.ON_STOP->view.onStop();else->Unit
  }}
  lifecycle.addObserver(observer)
  if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
  if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
  onDispose {lifecycle.removeObserver(observer);view.onPause();view.onStop();view.onDestroy()}
 }
 AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(300.dp).testTag("Shipment map"),update={mapView ->
  mapView.getMapAsync {map ->
   if(!requested) {requested=true;map.setStyle(if(fallback) STREET_MAP else "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_KEY}")}
   val signature=points.map {(label,point)->Triple(label,point.latitude,point.longitude)}
   if(ready && previous!=signature) {
    val old=previous
    map.clear()
    points.forEach {(label,point)->val marker=MarkerOptions().position(point).title(label);pinIcons[label]?.let {marker.icon(it)};map.addMarker(marker)}
    val moved=signature.lastOrNull {it !in old.orEmpty()}
    if(onSelect==null && points.map {it.second}.distinct().size>=2) {
     val bounds=org.maplibre.android.geometry.LatLngBounds.Builder().includes(points.map {it.second}).build()
     map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds,50))
    } else if(moved!=null) map.cameraPosition=CameraPosition.Builder(map.cameraPosition).target(LatLng(moved.second,moved.third)).zoom(map.cameraPosition.zoom.coerceAtLeast(12.0)).build()
    previous=signature
   }
   map.getStyle {style ->
    if(geometry.isNullOrBlank() || geometry=="null") {
     style.removeLayer("shipment-route-line");style.removeSource("shipment-route");previousGeometry=null
    } else {
     try {style.getSourceAs<GeoJsonSource>("shipment-route")?.setGeoJson(geometry) ?: run {
      style.addSource(GeoJsonSource("shipment-route",geometry))
      style.addLayer(LineLayer("shipment-route-line","shipment-route").withProperties(lineColor("#2563eb"),lineWidth(4f)))
     }
     if(previousGeometry!=geometry && points.size>=2) {
      val bounds=org.maplibre.android.geometry.LatLngBounds.Builder().includes(points.map {it.second}).build()
      map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds,50))
      previousGeometry=geometry
     }} catch(_:Exception) { /* Keep location markers if recorded route geometry is incompatible. */ }
    }
   }
  }
 })
 if(failed) Text("Map unavailable. Check your connection and reopen this step.",color=MaterialTheme.colorScheme.error)
 else if(!ready) SupportingText("Loading street map…")
 else if(onSelect!=null) SupportingText("Map ready · drag to move, pinch to zoom, tap to place a pin.")
 if(fallback) SupportingText("© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors")
}

/** Inline route editor: tapping places a pin immediately; coordinates stay internal. */
@Composable fun BookingRouteMap(pickup:String,destination:String,pickupPoint:LatLng?,destinationPoint:LatLng?,enabled:Boolean=true,
 onPickupText:(String)->Unit,onDestinationText:(String)->Unit,onPickup:(SelectedAddress)->Unit,onDestination:(SelectedAddress)->Unit,
 routeLoader:(suspend (LatLng,LatLng)->RoutePreview)?=null,addressLoader:(suspend (LatLng)->String?)?=null) {
 val context=LocalContext.current
 val pickupCallback by rememberUpdatedState(onPickup);val destinationCallback by rememberUpdatedState(onDestination)
 val currentPickup by rememberUpdatedState(pickup);val currentDestination by rememberUpdatedState(destination)
 var lookupMessage by remember {mutableStateOf<String?>(null)}
 var route by remember {mutableStateOf<RoutePreview?>(null)}
 var routing by remember {mutableStateOf(false)}
 var routeError by remember {mutableStateOf<String?>(null)}
 var retry by remember {mutableIntStateOf(0)}
 LaunchedEffect(pickupPoint?.latitude,pickupPoint?.longitude) {
  val point=pickupPoint ?: return@LaunchedEffect
  val original=currentPickup
  if(original!="Pickup map pin") return@LaunchedEffect
  val name=addressLoader?.invoke(point) ?: MobileGeocoding.reverse(context,point)
  if(currentPickup==original) {
   if(!name.isNullOrBlank())pickupCallback(SelectedAddress(name,point.latitude,point.longitude))
   else lookupMessage="Address lookup unavailable. The pin is saved; enter a street or landmark."
  }
 }
 LaunchedEffect(destinationPoint?.latitude,destinationPoint?.longitude) {
  val point=destinationPoint ?: return@LaunchedEffect
  val original=currentDestination
  if(original!="Destination map pin") return@LaunchedEffect
  val name=addressLoader?.invoke(point) ?: MobileGeocoding.reverse(context,point)
  if(currentDestination==original) {
   if(!name.isNullOrBlank())destinationCallback(SelectedAddress(name,point.latitude,point.longitude))
   else lookupMessage="Address lookup unavailable. The pin is saved; enter a street or landmark."
  }
 }
 LaunchedEffect(pickupPoint?.latitude,pickupPoint?.longitude,destinationPoint?.latitude,destinationPoint?.longitude,retry) {
  route=null;routeError=null;routing=false
  if(pickupPoint!=null && destinationPoint!=null && routeLoader!=null) {
   routing=true
   try {kotlinx.coroutines.delay(450);route=routeLoader(pickupPoint,destinationPoint)}
   catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
   catch(_:Exception) {routeError="Road route unavailable. Retry or adjust the pins; the office will verify the route."}
   finally {routing=false}
  }
 }
 var mode by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("pickup")}
 var searchRequest by remember {mutableStateOf<Pair<String,String>?>(null)}
 var searchAttempt by remember {mutableIntStateOf(0)}
 var results by remember {mutableStateOf(emptyList<SelectedAddress>())}
 var error by remember {mutableStateOf<String?>(null)}
 var busy by remember {mutableStateOf(false)}
 val query=if(mode=="pickup") pickup else destination
 LaunchedEffect(mode,query,searchRequest,searchAttempt) {
  results=emptyList();error=null;busy=false
  val request=searchRequest
  if(request!=null && request.first==mode && request.second==query) {
   busy=true
   try {results=MobileGeocoding.search(query);if(results.isEmpty())error="No matches. Try another address or place the pin on the map."}
   catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
   catch(_:Exception) {error="Address search is unavailable. You can still pan, zoom and tap the map."}
   finally {busy=false}
  }
 }
 OutlinedTextField(pickup,{searchRequest=null;onPickupText(it)},label={Text("Pickup address")},supportingText={Text(if(pickupPoint==null)"Place a pickup pin on the map." else "Pickup pin placed · add a street or landmark if needed.")},enabled=enabled,modifier=Modifier.fillMaxWidth())
 OutlinedTextField(destination,{searchRequest=null;onDestinationText(it)},label={Text("Destination address")},supportingText={Text(if(destinationPoint==null)"Place a destination pin on the map." else "Destination pin placed · add a street or landmark if needed.")},enabled=enabled,modifier=Modifier.fillMaxWidth())
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  FilterChip(selected=mode=="pickup",enabled=enabled,onClick={mode="pickup";searchRequest=null},label={Text("Pin pickup")})
  FilterChip(selected=mode=="destination",enabled=enabled,onClick={mode="destination";searchRequest=null},label={Text("Pin destination")})
 }
 TextButton(enabled=enabled && !busy && query.trim().length>=3,onClick={searchRequest=mode to query;searchAttempt++}) {Text(if(busy)"Searching…" else if(mode=="pickup")"Search pickup address" else "Search destination address")}
 results.forEach {address -> TextButton(enabled=enabled,onClick={
  if(mode=="pickup") onPickup(address) else onDestination(address)
  searchRequest=null
 }) {Text(address.label)} }
 error?.let {SupportingText(it)}
 Text(if(mode=="pickup")"Tap the map to place or move pickup" else "Tap the map to place or move destination",style=MaterialTheme.typography.titleSmall)
 val points=buildList {pickupPoint?.let {add("Pickup" to it)};destinationPoint?.let {add("Destination" to it)}}
 ShipmentMap(points,geometry=route?.geometry,onSelect=if(!enabled)null else {point ->
  lookupMessage=null
  val label=if(mode=="pickup")"Pickup map pin" else "Destination map pin"
  val address=SelectedAddress(label,point.latitude,point.longitude)
  if(mode=="pickup") onPickup(address) else onDestination(address)
  searchRequest=null
 })
 if(routing) {LinearProgressIndicator(Modifier.fillMaxWidth());SupportingText("Calculating road route…")}
 route?.let {SupportingText("Road route · ${it.distanceKm} km · about ${it.minutes} min. Office confirmation required.")}
 routeError?.let {SupportingText(it);TextButton(onClick={retry++},enabled=enabled){Text("Retry route")}}
 lookupMessage?.let {SupportingText(it)}
 SupportingText("Red: pickup · Blue: destination")
 SupportingText("Pickup: ${if(pickupPoint!=null)"pin placed" else "not placed"} · Destination: ${if(destinationPoint!=null)"pin placed" else "not placed"}")
}
