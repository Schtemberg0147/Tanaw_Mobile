package com.tanaw.app.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.tanaw.app.BuildConfig
import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import io.ktor.http.ContentType
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation

data class MobileState(val page: String = "loading", val busy: Boolean = false, val error: String? = null,
    val message: String? = null, val data: JsonObject? = null, val email: String = "", val resendSeconds: Int = 0,
    val details: JsonObject? = null, val detailLoading: Boolean = false,
    val proof: android.graphics.Bitmap? = null, val proofTitle: String = "", val offline:Boolean=false, val lastSync:String="")
fun JsonObject.value(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: buildJsonObject {}

@HiltViewModel
class MobileWorkspaceViewModel @Inject constructor(private val client: SupabaseClient,@ApplicationContext private val context:Context) : ViewModel() {
    private val mutable = MutableStateFlow(MobileState())
    val state = mutable.asStateFlow()
    private var timer: Job? = null
    private var generation = 0
    private var loadedUser: String? = null
    private var detailJob: Job? = null
    private var detailVersion=0
    private var liveJob:Job?=null
    private var pollJob:Job?=null
    private fun stopLive() {liveJob?.cancel();pollJob?.cancel();TripTrackingService.stop(context)}
    private fun startLive(uid:String) {
        liveJob?.cancel();pollJob?.cancel()
        pollJob=viewModelScope.launch {while(true){delay(30000);backgroundRefresh()}}
        liveJob=viewModelScope.launch {
            while(true) {
                val channel=client.channel("mobile-$uid")
                try {
                    coroutineScope {
                        listOf("shipments","deliveries","notifications","tracking_logs","shipment_status_history").forEach { table ->
                            val changes=channel.postgresChangeFlow<PostgresAction>(schema="public") {this.table=table}
                            launch {changes.collect {delay(300);backgroundRefresh()}}
                        }
                        channel.subscribe()
                        awaitCancellation()
                    }
                } catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){delay(10000)}
                finally {withContext(kotlinx.coroutines.NonCancellable){try {client.realtime.removeChannel(channel)}catch(_:Exception){}}}
            }
        }
    }
    private suspend fun backgroundRefresh() {
        if(mutable.value.busy || mutable.value.page!="workspace")return
        try {load();mutable.value.details?.value("shipment_id")?.takeIf(String::isNotBlank)?.let { id ->
            val version=generation
            val detail=client.postgrest.rpc("mobile_shipment_details",buildJsonObject {put("p_shipment",id)}).decodeAs<JsonObject>()
            if(version==generation && mutable.value.details?.value("shipment_id")==id)mutable.update {it.copy(details=detail)}
        }}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutable.update {it.copy(offline=true)}}
    }
    init {
        viewModelScope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val id = client.auth.currentUserOrNull()?.id
                        if (id != loadedUser) { stopLive();generation++; mutable.update { it.copy(data=null,details=null,proof=null) }; loadedUser = id;if(id!=null)startLive(id) }
                        if (!mutable.value.page.startsWith("recover")) refresh()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        stopLive();loadedUser?.let {TrackingQueue(context).use {queue->queue.clearOwner(it)}}
                        generation++; loadedUser = null
                        mutable.update { it.copy(data=null,details=null,proof=null,page=if(it.page in listOf("loading","workspace")) "login" else it.page) }
                    }
                    is SessionStatus.RefreshFailure -> {
                        stopLive()
                        generation++; loadedUser = null
                        mutable.update { MobileState(page="login",error="Your session could not be refreshed. Please sign in again.") }
                    }
                    else -> Unit
                }
            }
        }
    }
    fun page(page: String) { if (!mutable.value.busy) mutable.update { it.copy(page=page,error=null,message=null) } }
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy=true,error=null,message=null) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(error=friendly(error)) } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    private fun friendly(error: Exception): String {
        val detail=error.message.orEmpty()
        return when {
            detail.contains("ACTIVE_") -> "This account is inactive or does not have access. Contact the office."
            detail.contains("Invalid login",true)||detail.contains("invalid_credentials",true) -> "Email or password is incorrect."
            detail.contains("email_not_confirmed",true) -> "Verify your email before signing in."
            detail.contains("CAPACITY_EXCEEDED") -> "Cargo exceeds registered vehicle capacity. Choose another type or contact the office."
            detail.contains("INVALID_DEPARTURE") -> "Choose a future departure within the next year."
            detail.contains("REQUEST_ALREADY_SAVED_REFRESH") -> "This request was already saved. Refresh Active shipments before making another request."
            detail.contains("CUSTOMER_PROFILE_REQUIRED") -> "Your customer profile needs office attention before booking."
            detail.contains("rate",true)||detail.contains("429") -> "Too many requests. Please wait before trying again."
            detail.contains("otp",true)||detail.contains("token",true) -> "The code is invalid or expired. Request another code."
            detail.contains("OUTSIDE_OPERATING_HOURS") -> "This action is only available during operating hours."
            detail.contains("ARRIVAL_TOO_FAR") -> "Move within the pickup arrival radius and use a fresh accurate location."
            detail.contains("WAIT_PERIOD_NOT_FINISHED") -> "The pickup waiting period has not finished. The office decides any cancellation."
            detail.contains("DELIVERY_CLOSED") -> "This delivery has closed. Refresh its current status."
            detail.contains("INVALID_")||error is IllegalArgumentException -> "Check the entered details and try again."
            else -> "Unable to complete the request. Check your connection, refresh and try again."
        }
    }
    private suspend fun load() {
        val version=generation
        val id=client.auth.currentUserOrNull()?.id ?: return
        val data=try { client.postgrest.rpc("mobile_workspace").decodeAs<JsonObject>() }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            if (version==generation && error.message.orEmpty().contains("ACTIVE_")) {
                mutable.update { it.copy(data=null,details=null,proof=null,page="access") }
            }
            throw error
        }
        if (version==generation && client.auth.currentUserOrNull()?.id==id) {
            mutable.update { it.copy(page="workspace",data=data,error=null,offline=false,lastSync=java.time.Instant.now().toString()) }
            if(data.obj("profile").value("role")!="driver" || data.rows("deliveries").none {it.value("id")==TripTrackingStatus.state.value.delivery && it.value("status") in listOf("assigned","loading","in_transit")}) TripTrackingService.stop(context)
        }
    }
    fun refresh() = operation { load() }
    fun login(email: String,password: String) = operation {
        require(MobileValidation.email(email) && password.isNotEmpty())
        client.auth.signInWith(Email) { this.email=email.trim(); this.password=password }
        loadedUser=client.auth.currentUserOrNull()?.id
        loadedUser?.let(::startLive)
        load()
    }
    fun signup(first: String,last: String,phone: String,email: String,password: String) = operation {
        val contact=MobileValidation.phone(phone) ?: error("INVALID_CONTACT")
        require(first.isNotBlank() && last.isNotBlank() && MobileValidation.email(email) && password.length>=8)
        mutable.update { it.copy(email=email.trim()) }
        client.auth.signUpWith(Email) {
            this.email=email.trim(); this.password=password
            data=buildJsonObject { put("full_name","${first.trim()} ${last.trim()}");put("phone",contact) }
        }
        if (client.auth.currentUserOrNull()!=null) load() else { mutable.update { it.copy(page="verify") };countdown() }
    }
    fun verifySignup(code: String) = operation {
        verifyEmail(code,"signup",OtpType.Email.SIGNUP)
        load()
    }
    fun canVerify(raw: String,type: String): Boolean = raw.matches(Regex("[0-9]{6,8}")) || MobileValidation.linkToken(raw,java.net.URI(BuildConfig.SUPABASE_URL).host,type)!=null
    private suspend fun verifyEmail(raw: String,type: String,otpType: OtpType.Email) {
        val hash=MobileValidation.linkToken(raw,java.net.URI(BuildConfig.SUPABASE_URL).host,type)
        if(hash!=null) client.auth.verifyEmailOtp(type=otpType,tokenHash=hash)
        else { require(raw.matches(Regex("[0-9]{6,8}")));client.auth.verifyEmailOtp(otpType,mutable.value.email,raw) }
        require(client.auth.currentUserOrNull()?.email.equals(mutable.value.email,true))
    }
    private fun countdown() {
        timer?.cancel()
        timer=viewModelScope.launch {
            mutable.update { it.copy(resendSeconds=60) }
            while (mutable.value.resendSeconds>0) { delay(1000);mutable.update { it.copy(resendSeconds=(it.resendSeconds-1).coerceAtLeast(0)) } }
        }
    }
    fun resend() {
        if (mutable.value.resendSeconds>0) return
        operation { client.auth.resendEmail(OtpType.Email.SIGNUP,mutable.value.email);countdown();mutable.update { it.copy(message="Verification email requested. Check your inbox and spam folder.") } }
    }
    fun resetEmail(email: String) = operation {
        require(MobileValidation.email(email))
        client.auth.resetPasswordForEmail(email.trim())
        mutable.update { it.copy(page="recover_code",email=email.trim(),message="If the account exists, a recovery email has been requested. Enter its code or paste the original recovery link below. Do not share that link with anyone.") }
    }
    fun recover(code: String,password: String,confirm: String) = operation {
        require(MobileValidation.password(password,confirm))
        verifyEmail(code,"recovery",OtpType.Email.RECOVERY)
        client.auth.updateUser { this.password=password }
        client.auth.signOut()
        mutable.update { MobileState(page="login",message="Password updated. Sign in with your new password.") }
    }
    fun logout() = operation {
        stopLive();loadedUser?.let {owner->
            TrackingQueue(context).use {queue->queue.clearOwner(owner)}
            // Only this account's private, temporary camera files; uploaded proof stays on the server.
            java.io.File(context.cacheDir,"proof/$owner").deleteRecursively()
        }
        client.auth.signOut();generation++;loadedUser=null
        mutable.update { MobileState(page="login") }
    }
    fun book(requestId: String,data: JsonObject,onSuccess: (String) -> Unit) = operation {
        val result=client.postgrest.rpc("mobile_create_request",buildJsonObject { put("p_request_id",requestId);put("p_data",data) }).decodeAs<JsonObject>()
        require(result.value("id").isNotBlank() && result.value("tracking_number").isNotBlank())
        mutable.update { it.copy(message="Request ${result.value("tracking_number")} saved. Office review and quotation are pending.") }
        onSuccess(result.value("id"))
        try { load() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { mutable.update { it.copy(error="Request saved. Refresh to reload shipment details.") } }
    }
    fun profile(name: String,phone: String,address: String,city: String) = operation {
        val normalized=MobileValidation.phone(phone) ?: error("INVALID_CONTACT")
        client.postgrest.rpc("mobile_save_profile",buildJsonObject { put("p_name",name.trim());put("p_phone",normalized);put("p_address",address.trim());put("p_city",city.trim()) })
        load();mutable.update { it.copy(message="Profile saved.") }
    }
    fun driver(id: String,action: String) = operation {
        client.postgrest.rpc("mobile_driver_action",buildJsonObject { put("p_delivery",id);put("p_action",action) })
        load();mutable.update { it.copy(message="Assignment updated.") }
    }
    fun arrive(id:String,lat:Double,lng:Double)=operation {
        client.postgrest.rpc("driver_arrive_at_pickup",buildJsonObject {put("p_delivery_id",id);put("p_lat",lat);put("p_lng",lng)})
        load();mutable.update {it.copy(message="Pickup arrival recorded.")}
    }
    fun startTrip(id:String,onStarted:()->Unit)=operation {
        client.postgrest.rpc("mobile_driver_action",buildJsonObject {put("p_delivery",id);put("p_action","start")})
        load();onStarted();mutable.update {it.copy(message="Route started. Trip location sharing is active.")}
    }
    fun reportIncident(id:String,request:String,category:String,description:String,minutes:Int,onSaved:()->Unit={})=operation {
        client.postgrest.rpc("driver_report_incident",buildJsonObject {put("p_delivery",id);put("p_request",request);put("p_category",category);put("p_description",description.trim());put("p_delay",minutes)})
        load();onSaved();mutable.update {it.copy(message="Incident reported to the office.")}
    }
    fun submitProof(id:String,recipient:String,photo:ByteArray,signature:ByteArray,attempt:String,onSaved:()->Unit)=operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_DRIVER_REQUIRED");val version=generation
        require(recipient.trim().length in 2..100 && photo.size in 1..(5*1024*1024) && signature.size in 1..(5*1024*1024))
        val bucket=client.storage.from("delivery-documents")
        val photoPath="$id/pod-photo-$attempt.jpg";val signaturePath="$id/signature-$attempt.png"
        suspend fun uploadOnce(path:String,bytes:ByteArray,type:ContentType) {
            try {bucket.upload(path,bytes) {upsert=false;contentType=type}}
            catch(error:Exception) {if(error is CancellationException)throw error
                // Retrying an interrupted attempt may find the immutable object already uploaded.
                if(!error.message.orEmpty().contains("Duplicate",true) && !error.message.orEmpty().contains("already exists",true))throw error
                require(bucket.downloadAuthenticated(path).contentEquals(bytes))
            }
        }
        try {
            uploadOnce(photoPath,photo,ContentType.Image.JPEG);uploadOnce(signaturePath,signature,ContentType.Image.PNG)
            check(version==generation && client.auth.currentUserOrNull()?.id==uid)
            client.postgrest.rpc("driver_submit_pod",buildJsonObject {put("p_delivery_id",id);put("p_recipient_name",recipient.trim());put("p_photo_path",photoPath);put("p_signature_path",signaturePath)})
        } catch(error:Exception) {
            // A committed proof is protected from deletion even if its response was lost.
            withContext(kotlinx.coroutines.NonCancellable) {try {bucket.delete(listOf(photoPath,signaturePath))}catch(_:Exception){}}
            throw error
        }
        TripTrackingService.stop(context);onSaved();mutable.update {it.copy(message="Proof submitted. Office review is pending.")}
        try {load()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutable.update{it.copy(offline=true,error="Proof submitted. Refresh to reload the office review status.")}}
    }
    override fun onCleared() {liveJob?.cancel();pollJob?.cancel()}
    fun markRead(id: String) = operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_ACCOUNT_REQUIRED")
        client.from("notifications").update(buildJsonObject { put("read_at",java.time.Instant.now().toString()) }) { filter { eq("id",id);eq("user_id",uid) } }
        load()
    }
    fun loadDetails(id: String) {
        detailJob?.cancel()
        val requestVersion=++detailVersion
        val version=generation
        val uid=client.auth.currentUserOrNull()?.id ?: return
        mutable.update { it.copy(details=null,proof=null,detailLoading=true,error=null) }
        detailJob=viewModelScope.launch {
            try {
                val details=client.postgrest.rpc("mobile_shipment_details",buildJsonObject { put("p_shipment",id) }).decodeAs<JsonObject>()
                if(version==generation && requestVersion==detailVersion && uid==client.auth.currentUserOrNull()?.id) mutable.update { it.copy(details=details) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { if(version==generation && requestVersion==detailVersion) mutable.update { it.copy(error=friendly(error)) } }
            finally { if(version==generation && requestVersion==detailVersion) mutable.update { it.copy(detailLoading=false) } }
        }
    }
    fun viewProof(shipment: String,document: String) = operation {
        val version=generation
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_ACCOUNT_REQUIRED")
        // Reauthorize exact document immediately before fetching; no stale or caller-supplied paths.
        val detail=client.postgrest.rpc("mobile_shipment_details",buildJsonObject { put("p_shipment",shipment) }).decodeAs<JsonObject>()
        val record=detail.rows("documents").firstOrNull { it.value("id")==document } ?: error("SHIPMENT_NOT_FOUND")
        val path=record.value("storage_path")
        require(path.isNotBlank() && !path.startsWith('/') && path.split('/').none { it==".." })
        val bytes=client.storage.from("delivery-documents").downloadAuthenticated(path)
        val bitmap=withContext(Dispatchers.Default) { ProofImage.decode(bytes) }
        if(version==generation && uid==client.auth.currentUserOrNull()?.id) {
            mutable.update { it.copy(proof=bitmap,proofTitle=record.value("file_name").ifBlank { record.value("kind").replace('_',' ') }) }
        }
    }
    fun closeProof() { mutable.update { it.copy(proof=null,proofTitle="") } }
    fun departure(raw:String):String?=MobileBookingRules.departure(raw)
    suspend fun previewRoute(pickup:org.maplibre.android.geometry.LatLng,destination:org.maplibre.android.geometry.LatLng):RoutePreview {
        val token=client.auth.currentSessionOrNull()?.accessToken ?: error("Sign in to preview a route.")
        return withContext(Dispatchers.IO) {
            val connection=java.net.URL("${BuildConfig.SUPABASE_URL}/functions/v1/calculate-route").openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=25000;connection.doOutput=true
                connection.setRequestProperty("Authorization","Bearer $token")
                connection.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Content-Type","application/json")
                val payload=buildJsonObject {put("preview",true);put("pickup",buildJsonObject {put("lat",pickup.latitude);put("lng",pickup.longitude)});put("destination",buildJsonObject {put("lat",destination.latitude);put("lng",destination.longitude)})}
                connection.outputStream.use {it.write(payload.toString().toByteArray())}
                require(connection.responseCode==200) {"Route preview unavailable. Retry when connected; the office will verify your route."}
                RoutePreview.parse(Json.parseToJsonElement(connection.inputStream.bufferedReader().use {it.readText()}).jsonObject)
            } finally {connection.disconnect()}
        }
    }
}
