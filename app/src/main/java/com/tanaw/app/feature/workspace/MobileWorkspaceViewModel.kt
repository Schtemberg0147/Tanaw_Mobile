package com.tanaw.app.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.Phone
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google
import com.tanaw.app.feature.auth.GoogleSignIn
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.ClearCredentialException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import com.tanaw.app.BuildConfig
import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation

data class MobileState(val page: String = "loading", val busy: Boolean = false, val error: String? = null,
    val message: String? = null, val data: JsonObject? = null, val email: String = "", val resendSeconds: Int = 0,
    val details: JsonObject? = null, val detailLoading: Boolean = false,
    val proof: Bitmap? = null, val proofTitle: String = "", val offline:Boolean=false, val lastSync:String="",
    val history:JsonObject?=null,val historyLoading:Boolean=false,val historyError:String?=null,
    val conversationUnread:JsonArray?=null)
fun JsonObject.value(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: buildJsonObject {}

@HiltViewModel
class MobileWorkspaceViewModel @Inject constructor(private val client: SupabaseClient,@ApplicationContext private val context:Context) : ViewModel() {
    private val recoveryStore=WorkspaceRecoveryStore(context,BuildConfig.SUPABASE_URL)
    private var signupPhone: String = ""
    private var recoveryPhone: String? = null
    private val mutable = MutableStateFlow(MobileState())
    val state = mutable.asStateFlow()
    private var timer: Job? = null
    private var generation = 0
    private var loadedUser: String? = null
    private var detailJob: Job? = null
    private var detailVersion=0
    private var liveJob:Job?=null
    private var pollJob:Job?=null
    private var historyVersion=0
    private fun stopLive() {liveJob?.cancel();pollJob?.cancel();TripTrackingService.stop(context)}
    private fun startLive(uid:String) {
        liveJob?.cancel();pollJob?.cancel()
        pollJob=viewModelScope.launch {while(true){delay(30.seconds);backgroundRefresh()}}
        liveJob=viewModelScope.launch {
            while(true) {
                val channel=client.channel("mobile-$uid")
                try {
                    coroutineScope {
                        listOf("shipments","deliveries","notifications","tracking_logs","shipment_status_history","drivers","trucks","compliance_documents","delivery_expenses","delivery_incidents","incident_attachments","delivery_messages").forEach { table ->
                            val changes=channel.postgresChangeFlow<PostgresAction>(schema="public") {this.table=table}
                            launch {changes.collect {delay(300.milliseconds);backgroundRefresh()}}
                        }
                        channel.subscribe()
                        awaitCancellation()
                    }
                } catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){delay(10.seconds)}
                finally {withContext(kotlinx.coroutines.NonCancellable){try {client.realtime.removeChannel(channel)}catch(_:Exception){}}}
            }
        }
    }
    private suspend fun backgroundRefresh() {
        if(mutable.value.busy || mutable.value.page!="workspace")return
        try {load();mutable.value.details?.value("shipment_id")?.takeIf(String::isNotBlank)?.let { id ->
            val version=generation
            val detailRequest=detailVersion
            val detail=readDetails(id)
            if(version==generation && detailRequest==detailVersion && mutable.value.details?.value("shipment_id")==id)mutable.update {it.copy(details=detail)}
        }}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutable.update {it.copy(offline=true)}}
    }
    init {
        recoveryStore.read("challenge",RecoveryPolicy.CHALLENGE_AGE)?.takeIf {RecoveryPolicy.valid(it,System.currentTimeMillis())}?.let {
            val target=it.value("target")
            if(it.value("page")=="verify_phone")signupPhone=target else recoveryPhone=MobileValidation.phone(target)
            mutable.value=MobileState(page=it.value("page"),email=target,message="Resume verification or request a fresh code. Re-enter your code and password.")
            val remaining=((it.value("created").toLong()+60000-System.currentTimeMillis())/1000).toInt().coerceIn(0,60)
            if(remaining>0)countdown(remaining)
        }
        viewModelScope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val id = client.auth.currentUserOrNull()?.id
                        if (id != loadedUser) { loadedUser?.let {recoveryStore.clear(it)};stopLive();generation++; mutable.update { it.copy(data=null,details=null,proof=null,history=null,historyLoading=false,historyError=null,conversationUnread=null) }; loadedUser = id;if(id!=null){restoreWorkspace(id);startLive(id)} }
                        if (!mutable.value.page.startsWith("recover")) refresh()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        stopLive();loadedUser?.let {recoveryStore.clear(it);TrackingQueue(context).use {queue->queue.clearOwner(it)}}
                        generation++; loadedUser = null
                        mutable.update { it.copy(data=null,details=null,proof=null,history=null,historyLoading=false,historyError=null,conversationUnread=null,page=if(it.page in listOf("loading","workspace")) "login" else it.page) }
                    }
                    is SessionStatus.RefreshFailure -> {
                        stopLive()
                        generation++
                        if(mutable.value.page.startsWith("recover") || mutable.value.page=="verify_phone")return@collect
                        // Expired sessions expose no currentUser in Auth 3.8. Read identity only;
                        // never import an expired token to authorize cached actions.
                        val owner=client.auth.currentUserOrNull()?.id ?: runCatching {client.auth.sessionManager.loadSession().user?.id}.getOrNull()
                        if(owner!=null && (mutable.value.data?.obj("profile")?.value("id")==owner || restoreWorkspace(owner)))
                            {loadedUser=owner;mutable.update {it.copy(page="workspace",offline=true,error="Session refresh unavailable. Saved records are read-only; reconnect to verify access.")}}
                        else {loadedUser=null;mutable.update { MobileState(page="login",error="Your session could not be refreshed. Please sign in again.") }}
                    }
                    else -> Unit
                }
            }
        }
    }
    fun page(page: String) { if (!mutable.value.busy) {if(page=="login"){recoveryStore.clear("challenge");recoveryPhone=null;signupPhone="";timer?.cancel()};mutable.update { it.copy(page=page,error=null,message=null) }} }
    private fun saveChallenge(page:String,target:String)=recoveryStore.write("challenge",RecoveryPolicy.challenge(page,target,System.currentTimeMillis()))
    private fun restoreWorkspace(owner:String):Boolean {
        if(mutable.value.page.startsWith("recover") || mutable.value.page=="verify_phone")return false
        val saved=recoveryStore.read(owner,RecoveryPolicy.CACHE_AGE)?:return false
        val data=RecoveryPolicy.workspace(saved.obj("data"),owner)?:return false
        mutable.update {it.copy(page="workspace",data=data,offline=true,lastSync=saved.value("saved_at"))};return true
    }
    fun clearOfflineWorkspace() {
        (client.auth.currentUserOrNull()?.id?:loadedUser)?.let {recoveryStore.clear(it)}
        mutable.update {it.copy(data=if(it.offline)null else it.data,details=null,history=null,page=if(it.offline)"access" else it.page,message="Saved offline workspace cleared.")}
    }
    private fun operation(allowOffline:Boolean=false,block: suspend () -> Unit) {
        if (mutable.value.busy) return
        if(!allowOffline && mutable.value.page=="workspace" && mutable.value.offline){mutable.update {it.copy(error="Reconnect and refresh before changing saved records.")};return}
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
        MobileAuthErrors.message(detail)?.let { return it }
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
        var data=try { client.postgrest.rpc("mobile_workspace").decodeAs<JsonObject>() }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            if (version==generation && error.message.orEmpty().contains("ACTIVE_")) {
                recoveryStore.clear(id)
                mutable.update { it.copy(data=null,details=null,proof=null,page="access") }
            }
            throw error
        }
        if(data.obj("profile").value("role")=="driver") {
            // Driver-only reads retain the caller's RLS; customer loading is unchanged.
            val extra=DriverDatabaseData.load {table,driverId->
                client.from(table).select {
                    when(table) {
                        "drivers"->filter {eq("user_id",id)}
                        // Truck RLS includes both permanent and delivery assignments.
                        "trucks"->Unit
                        else->filter {eq("driver_id",requireNotNull(driverId))}
                    }
                    if(table=="deliveries") {order("assigned_at",Order.DESCENDING);limit(200)}
                    if(table=="delivery_expenses") {order("created_at",Order.DESCENDING);limit(200)}
                }.decodeAs<JsonArray>()
            }
            data=JsonObject(data+extra)
            val unread=try {client.postgrest.rpc("delivery_conversation_unread").decodeAs<JsonArray>()}
            catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
            if(version==generation && client.auth.currentUserOrNull()?.id==id)mutable.update {it.copy(conversationUnread=unread)}
        }
        if(data.obj("profile").value("role")!="driver")data=JsonObject(data+readSmsPreferences(id))
        if (version==generation && client.auth.currentUserOrNull()?.id==id) {
            val savedAt=java.time.Instant.now().toString()
            val snapshot=RecoveryPolicy.workspace(data,id)
            val cached=snapshot==null || recoveryStore.write(id,buildJsonObject {put("created",System.currentTimeMillis());put("saved_at",savedAt);put("data",snapshot)})
            mutable.update { it.copy(page="workspace",data=data,error=null,offline=false,lastSync=savedAt,message=if(cached)it.message else "Offline copy could not be saved on this device.") }
            if(data.obj("profile").value("role")!="driver" || data.rows("deliveries").none {it.value("id")==TripTrackingStatus.state.value.delivery && it.value("status") in listOf("assigned","loading","in_transit")}) TripTrackingService.stop(context)
        }
    }
    fun refresh() = operation(allowOffline=true) {
        try {
            if(client.auth.currentUserOrNull()==null && loadedUser!=null) {
                client.auth.loadFromStorage()
                withTimeout(20.seconds) {client.auth.sessionStatus.first {it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated}}
                require(client.auth.currentUserOrNull()!=null){"ACTIVE_ACCOUNT_REQUIRED"}
            }
            load();loadedUser?.let(::startLive)
        }catch(e:Exception){if(e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException)throw e;if(mutable.value.data!=null)mutable.update {it.copy(offline=true)};throw e}
    }
    fun loadHistory(period:String,more:Boolean=false) {
        val uid=client.auth.currentUserOrNull()?.id?:return
        val version=++historyVersion;val accountVersion=generation
        val previous=mutable.value.history?.takeIf {it.value("period")==period}
        val offset=if(more)previous?.rows("shipments")?.size?:0 else 0
        mutable.update {it.copy(historyLoading=true,historyError=null,history=previous)}
        viewModelScope.launch {
            try {
                suspend fun read(offset:Int)=client.postgrest.rpc("mobile_driver_history",buildJsonObject {put("p_period",period);put("p_offset",offset);put("p_limit",50)}).decodeAs<JsonObject>()
                var page=read(offset)
                val changed=more && !DriverHistory.sameRevision(previous,page)
                if(changed)page=read(0)
                if(version==historyVersion && accountVersion==generation && client.auth.currentUserOrNull()?.id==uid)
                    mutable.update {it.copy(history=DriverHistory.merge(if(more && !changed)previous else null,page),historyLoading=false,
                        historyError=if(changed)"Trip records changed. History restarted with current totals; load more to continue." else null)}
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(version==historyVersion && accountVersion==generation)mutable.update {it.copy(historyLoading=false,historyError="Could not load complete trip history. Reconnect and retry.")}}
        }
    }
    internal fun logFuel(delivery:String,request:String,entry:FuelEntry,onSaved:()->Unit)=operation {
        val uid=client.auth.currentUserOrNull()?.id?:error("ACTIVE_DRIVER_REQUIRED");val version=generation
        client.postgrest.rpc("mobile_driver_log_fuel",buildJsonObject {
            put("p_request",request);put("p_delivery",delivery);put("p_liters",JsonPrimitive(entry.liters));put("p_price",JsonPrimitive(entry.price))
            put("p_odometer",JsonPrimitive(entry.odometer));put("p_station",entry.station)
        })
        if(version!=generation || client.auth.currentUserOrNull()?.id!=uid)return@operation
        onSaved();mutable.update {it.copy(message="Fuel record saved.")}
        mutable.value.details?.value("shipment_id")?.takeIf(String::isNotBlank)?.let(::loadDetails)
        try {load()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutable.update {it.copy(message="Fuel record saved. Refresh to reload your records.",offline=true)}}
    }
    fun googleSignIn(activityContext: Context) = operation {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            mutable.update { it.copy(error="Google sign-in setup is not complete yet. Please use email sign-in for now.") }
            return@operation
        }
        val nonce = GoogleSignIn.nonce()
        val token = try { GoogleSignIn.token(activityContext, BuildConfig.GOOGLE_WEB_CLIENT_ID, nonce) }
        catch (_: GetCredentialCancellationException) { return@operation }
        catch (_: GetCredentialException) {
            mutable.update { it.copy(error="Google sign-in could not start. Check your connection and Google account, then try again.") }
            return@operation
        }
        client.auth.signInWith(IDToken) {
            idToken = token
            provider = Google
            this.nonce = nonce
        }
        load()
    }
    fun login(email: String,password: String) = operation {
        require((MobileValidation.email(email) || MobileValidation.phone(email)!=null) && password.isNotEmpty())
        val phone=MobileValidation.phone(email)
        if(phone!=null) client.auth.signInWith(Phone) {this.phone=phone;this.password=password}
        else {
            try { client.auth.signInWith(Email) { this.email=email.trim(); this.password=password } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (!error.message.orEmpty().contains("invalid_credentials",true) && !error.message.orEmpty().contains("Invalid login",true)) throw error
                loginWithContactEmail(email.trim(),password)
            }
        }
        loadedUser=client.auth.currentUserOrNull()?.id
        loadedUser?.let(::startLive)
        load()
    }
    private suspend fun loginWithContactEmail(email:String,password:String) {
        val tokens=withContext(Dispatchers.IO) {
            val connection=java.net.URL("${BuildConfig.SUPABASE_URL}/functions/v1/mobile-email-login").openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=20000;connection.doOutput=true
                connection.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Content-Type","application/json")
                val payload=buildJsonObject {put("email",email);put("password",password)}
                connection.outputStream.use {it.write(payload.toString().toByteArray(Charsets.UTF_8))}
                when(connection.responseCode) {
                    200 -> Json.parseToJsonElement(connection.inputStream.bufferedReader().use {it.readText()}).jsonObject
                    401 -> error("invalid_credentials")
                    429 -> error("LOGIN_RATE_LIMIT")
                    else -> error("LOGIN_UNAVAILABLE")
                }
            } finally {connection.disconnect()}
        }
        require(tokens.value("access_token").isNotBlank() && tokens.value("refresh_token").isNotBlank())
        client.auth.importAuthToken(tokens.value("access_token"),tokens.value("refresh_token"),retrieveUser=true)
    }
    fun signup(first: String,last: String,phone: String,email: String,password: String,address: String) = operation {
        val contact=MobileValidation.phone(phone) ?: error("INVALID_CONTACT")
        require(first.isNotBlank() && last.isNotBlank() && password.length>=8 && MobileValidation.email(email) && email.trim().length<=254 && address.trim().length in 1..500)
        signupPhone=contact
        client.auth.signUpWith(Phone) {
            this.phone=contact; this.password=password
            data=buildJsonObject { put("full_name","${first.trim()} ${last.trim()}");put("phone",contact);put("contact_email",email.trim());put("address",address.trim()) }
        }
        if (client.auth.currentUserOrNull()!=null) load() else { val saved=saveChallenge("verify_phone",contact);mutable.update { it.copy(page="verify_phone",message=if(saved)null else "Verification requested. Keep this screen open; recovery context could not be saved.") };countdown() }
    }
    fun verifyPhoneSignup(code: String) = operation {
        require(code.matches(Regex("[0-9]{6,8}")) && signupPhone.isNotEmpty())
        client.auth.verifyPhoneOtp(OtpType.Phone.SMS,signupPhone,code)
        recoveryStore.clear("challenge")
        load()
    }
    fun resendPhoneSignup() {
        if(mutable.value.resendSeconds>0)return
        operation { client.auth.resendPhone(OtpType.Phone.SMS,signupPhone);saveChallenge("verify_phone",signupPhone);countdown() }
    }
    private suspend fun readSmsPreferences(uid:String):JsonObject = try {
        val rows=client.from("notification_preferences").select {filter {eq("user_id",uid);eq("channel","sms")}}.decodeAs<JsonArray>()
        buildJsonObject {put("sms_preferences",rows)}
    }catch(e:Exception){if(e is CancellationException)throw e;buildJsonObject {put("sms_error","Saved SMS preferences unavailable")}}
    fun smsPreference(enabled:Boolean)=operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_ACCOUNT_REQUIRED")
        val version=generation
        try {
            listOf("shipment_status","delay").forEach { type ->
                client.from("notification_preferences").upsert(buildJsonObject {put("user_id",uid);put("type",type);put("channel","sms");put("enabled",enabled)})
            }
        } finally {
            val saved=readSmsPreferences(uid)
            if(version==generation && client.auth.currentUserOrNull()?.id==uid)mutable.update {it.copy(data=it.data?.let {d->JsonObject(d.filterKeys {key->key !in listOf("sms_preferences","sms_error")}+saved)})}
        }
        if(version==generation && client.auth.currentUserOrNull()?.id==uid)mutable.update {it.copy(message="SMS preferences reloaded. See the saved status below.")}
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
    private fun countdown(seconds:Int=60) {
        timer?.cancel()
        timer=viewModelScope.launch {
            mutable.update { it.copy(resendSeconds=seconds) }
            while (mutable.value.resendSeconds>0) { delay(1.seconds);mutable.update { it.copy(resendSeconds=(it.resendSeconds-1).coerceAtLeast(0)) } }
        }
    }
    fun resend() {
        if (mutable.value.resendSeconds>0) return
        operation { client.auth.resendEmail(OtpType.Email.SIGNUP,mutable.value.email);countdown();mutable.update { it.copy(message="Verification email requested. Check your inbox and spam folder.") } }
    }
    fun resetEmail(email: String) = operation {
        if(mutable.value.resendSeconds>0)return@operation
        val phone=MobileValidation.phone(email)
        require(MobileValidation.email(email) || phone!=null)
        if(phone!=null) client.auth.signInWith(OTP) {this.phone=phone;createUser=false}
        else client.auth.resetPasswordForEmail(email.trim())
        recoveryPhone=phone
        val saved=saveChallenge("recover_code",phone?:email.trim())
        countdown()
        val requested=if(phone!=null)"If the account exists, a recovery SMS has been requested." else "If the account exists, a recovery email has been requested."
        mutable.update { it.copy(page="recover_code",email=email.trim(),message=requested+if(saved)"" else " Keep this screen open; recovery context could not be saved.") }
    }
    fun recover(code: String,password: String,confirm: String) = operation {
        require(MobileValidation.password(password,confirm))
        val phone=recoveryPhone
        if(phone!=null) {require(code.matches(Regex("[0-9]{6,8}")));client.auth.verifyPhoneOtp(OtpType.Phone.SMS,phone,code)}
        else verifyEmail(code,"recovery",OtpType.Email.RECOVERY)
        client.auth.updateUser { this.password=password }
        recoveryStore.clear("challenge");recoveryPhone=null
        client.auth.signOut()
        mutable.update { MobileState(page="login",message="Password updated. Sign in with your new password.") }
    }
    fun logout() = operation(allowOffline=true) {
        recoveryStore.clear("challenge")
        stopLive();loadedUser?.let {owner->
            recoveryStore.clear(owner)
            TrackingQueue(context).use {queue->queue.clearOwner(owner)}
            // Only this account's private, temporary camera files; uploaded proof stays on the server.
            java.io.File(context.cacheDir,"proof/$owner").deleteRecursively()
        }
        client.auth.signOut();generation++;loadedUser=null
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (_: ClearCredentialException) { /* Supabase sign-out already succeeded. */ }
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
    suspend fun conversation(delivery:String,before:String?):JsonObject = client.postgrest.rpc("delivery_conversation",buildJsonObject {
        put("p_delivery",delivery);put("p_before",before?.let(::JsonPrimitive)?:JsonNull)
    }).decodeAs<JsonObject>()
    suspend fun sendConversation(delivery:String,request:String,body:String,driver:String) {
        require(ConversationRules.valid(body))
        client.postgrest.rpc("send_delivery_message",buildJsonObject {put("p_delivery",delivery);put("p_request",request);put("p_body",body.trim());put("p_driver",driver)})
    }
    suspend fun readConversation(delivery:String,ids:List<String>) {
        client.postgrest.rpc("read_delivery_messages",buildJsonObject {put("p_delivery",delivery);put("p_ids",JsonArray(ids.map(::JsonPrimitive)))})
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
    private suspend fun uploadEvidence(path:String,bytes:ByteArray) {
        val bucket=client.storage.from("delivery-documents")
        try {bucket.upload(path,bytes){upsert=false;contentType=ContentType.Image.JPEG}}
        catch(failure:Exception) {
            if(failure is CancellationException)throw failure
            if(!failure.message.orEmpty().contains("Duplicate",true) && !failure.message.orEmpty().contains("already exists",true))throw failure
            check(bucket.downloadAuthenticated(path).contentEquals(bytes)){"The saved upload differs. Reopen the form before submitting a changed photo."}
        }
    }
    fun reportIncident(id:String,request:String,category:String,description:String,minutes:Int,photos:List<ByteArray> = emptyList(),onSaved:()->Unit={})=operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_DRIVER_REQUIRED");val version=generation
        require(photos.size<=3)
        val paths=photos.mapIndexed {index,bytes->
            require(bytes.size in 1..DriverEvidence.MAX_BYTES)
            val path="$id/incident-$request-${index+1}.jpg"
            uploadEvidence(path,bytes);path
        }
        check(version==generation && client.auth.currentUserOrNull()?.id==uid)
        client.postgrest.rpc("driver_report_incident_evidence",buildJsonObject {put("p_delivery",id);put("p_request",request);put("p_category",category);put("p_description",description.trim());put("p_delay",minutes);put("p_paths",JsonArray(paths.map(::JsonPrimitive)))})
        check(version==generation && client.auth.currentUserOrNull()?.id==uid)
        onSaved();mutable.update {it.copy(message="Incident reported to the office.")}
        try {load()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutable.update {it.copy(message="Incident saved. Refresh to reload your records.")}}
    }
    fun addExpense(delivery:String,request:String,category:String,amount:Double,receipt:ByteArray?,onSaved:()->Unit)=operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_DRIVER_REQUIRED");val version=generation
        require(category.trim().length in 2..200 && amount.isFinite() && amount>0 && amount<=1000000)
        val driver=mutable.value.data?.obj("driver")?.value("id").orEmpty()
        require(driver.isNotBlank())
        val existing=client.from("delivery_expenses").select {filter {eq("id",request)}}.decodeAs<JsonArray>()
        existing.firstOrNull()?.let {record->
            val saved=record as JsonObject
            require(saved.value("delivery_id")==delivery && saved.value("description").ifBlank{saved.value("category")}.equals(category.trim(),true) && saved.value("amount").toBigDecimal().compareTo(amount.toBigDecimal())==0 && saved.value("receipt_path").isNotBlank()==(receipt!=null)){"Expense request changed. Reopen the form for a new expense."}
            if(receipt!=null)check(client.storage.from("delivery-documents").downloadAuthenticated(saved.value("receipt_path")).contentEquals(DriverEvidence.jpeg(receipt))){"The saved receipt differs. Reopen the form for a new expense."}
        }
        if(existing.isEmpty()) {
            var path:String?=null
            if(receipt!=null) {
                require(receipt.size in 1..(5*1024*1024))
                val jpeg=DriverEvidence.jpeg(receipt)
                path="$delivery/receipt-$request.jpg"
                uploadEvidence(path,jpeg)
            }
            check(version==generation && client.auth.currentUserOrNull()?.id==uid)
            client.from("delivery_expenses").insert(buildJsonObject {
                put("id",request);put("delivery_id",delivery);put("driver_id",driver);put("category",category.trim().lowercase().takeIf{it in listOf("fuel","toll","parking","repair","other")}?:"other");put("description",category.trim());put("amount",amount);put("status","pending")
                path?.let {put("receipt_path",it)}
            })
        }
        check(version==generation && client.auth.currentUserOrNull()?.id==uid)
        onSaved()
        mutable.update {it.copy(message="Expense submitted for office review.")}
        try {load()} catch(error:Exception) {if(error is CancellationException)throw error;mutable.update {it.copy(message="Expense saved. Refresh to reload your records.")}}
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
    private suspend fun readDetails(id:String):JsonObject {
        val details=client.postgrest.rpc("mobile_shipment_details",buildJsonObject {put("p_shipment",id)}).decodeAs<JsonObject>()
        if(mutable.value.data?.obj("profile")?.value("role")!="driver")return details
        val extra=try {
            buildJsonObject {put("fuel_logs",client.postgrest.rpc("mobile_driver_fuel_history",buildJsonObject {put("p_shipment",id)}).decodeAs<JsonArray>())}
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){buildJsonObject {put("fuel_error","Could not load fuel records. Refresh to retry.")}}
        val evidence=try {client.postgrest.rpc("mobile_driver_evidence",buildJsonObject {put("p_shipment",id)}).decodeAs<JsonObject>()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){buildJsonObject {put("evidence_error","Could not load incident photos or expense reviews. Refresh to retry.")}}
        val routing=try {
            val delivery=DriverUiRules.delivery(mutable.value.data?:buildJsonObject{},id).value("id")
            if(delivery.isBlank())buildJsonObject {put("reason","Assigned delivery unavailable")}
            else readRouteStatus(delivery)
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){buildJsonObject {put("reason","Truck routing provider unavailable. Refresh to retry.")}}
        return JsonObject(details+extra+evidence+mapOf("routing" to routing))
    }
    fun loadDetails(id: String) {
        detailJob?.cancel()
        val requestVersion=++detailVersion
        val version=generation
        val uid=client.auth.currentUserOrNull()?.id ?: return
        mutable.update { it.copy(details=null,proof=null,detailLoading=true,error=null) }
        detailJob=viewModelScope.launch {
            try {
                val details=readDetails(id)
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
    fun viewEvidence(shipment:String,kind:String,record:String)=operation {
        val version=generation;val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_ACCOUNT_REQUIRED")
        val path=client.postgrest.rpc("mobile_delivery_evidence_file",buildJsonObject {put("p_shipment",shipment);put("p_kind",kind);put("p_record",record)}).decodeAs<String>()
        val bitmap=withContext(Dispatchers.Default){ProofImage.decode(client.storage.from("delivery-documents").downloadAuthenticated(path))}
        if(version==generation && uid==client.auth.currentUserOrNull()?.id)mutable.update {it.copy(proof=bitmap,proofTitle=if(kind=="expense")"Expense receipt" else "Incident photo")}
        else bitmap.recycle()
    }
    fun departure(raw:String):String?=MobileBookingRules.departure(raw)
    private suspend fun readRouteStatus(delivery:String):JsonObject=withContext(Dispatchers.IO) {
        val token=client.auth.currentSessionOrNull()?.accessToken ?: error("ACTIVE_ACCOUNT_REQUIRED")
        val connection=java.net.URL("${BuildConfig.SUPABASE_URL}/functions/v1/delivery-routing").openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod="POST";connection.connectTimeout=10000;connection.readTimeout=20000;connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer $token");connection.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);connection.setRequestProperty("Content-Type","application/json")
            connection.outputStream.use{it.write(buildJsonObject{put("mode","eta");put("delivery_id",delivery)}.toString().toByteArray())}
            require(connection.responseCode==200)
            Json.parseToJsonElement(connection.inputStream.bufferedReader().use{it.readText()}).jsonObject
        }finally{connection.disconnect()}
    }
    suspend fun previewRoute(pickup:org.maplibre.android.geometry.LatLng,destination:org.maplibre.android.geometry.LatLng):RoutePreview = requestRoutePreview(pickup,destination)
    suspend fun previewDriverRoute(delivery:String,pickup:org.maplibre.android.geometry.LatLng,destination:org.maplibre.android.geometry.LatLng):RoutePreview = requestRoutePreview(pickup,destination,delivery)
    private suspend fun requestRoutePreview(pickup:org.maplibre.android.geometry.LatLng,destination:org.maplibre.android.geometry.LatLng,delivery:String?=null):RoutePreview {
        val token=client.auth.currentSessionOrNull()?.accessToken ?: error("Sign in to preview a route.")
        return withContext(Dispatchers.IO) {
            val connection=java.net.URL("${BuildConfig.SUPABASE_URL}/functions/v1/calculate-route").openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=25000;connection.doOutput=true
                connection.setRequestProperty("Authorization","Bearer $token")
                connection.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Content-Type","application/json")
                val payload=buildJsonObject {put("preview",true);delivery?.let{put("delivery_id",it)};put("pickup",buildJsonObject {put("lat",pickup.latitude);put("lng",pickup.longitude)});put("destination",buildJsonObject {put("lat",destination.latitude);put("lng",destination.longitude)})}
                connection.outputStream.use {it.write(payload.toString().toByteArray())}
                require(connection.responseCode==200) {"Route preview unavailable. Retry when connected; the office will verify your route."}
                RoutePreview.parse(Json.parseToJsonElement(connection.inputStream.bufferedReader().use {it.readText()}).jsonObject)
            } finally {connection.disconnect()}
        }
    }
}
