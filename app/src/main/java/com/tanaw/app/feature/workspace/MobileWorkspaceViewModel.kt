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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

data class RestoredBookingConfirmation(
    val id: String,
    val trackingNumber: String,
    val pickup: String,
    val dropoff: String,
    val vehicleId: String,
    val isPickupNow: Boolean,
    val scheduledDeparture: String
)

data class MobileState(val page: String = "loading", val busy: Boolean = false, val error: String? = null,
    val message: String? = null, val data: JsonObject? = null, val email: String = "", val resendSeconds: Int = 0,
    val details: JsonObject? = null, val detailLoading: Boolean = false,
    val proof: Bitmap? = null, val proofTitle: String = "", val offline:Boolean=false, val lastSync:String="",
    val history:JsonObject?=null,val historyLoading:Boolean=false,val historyError:String?=null,
    val conversationUnread:JsonArray?=null,val passwordChangeOwner:String?=null,val passwordChangeCodeSent:Boolean=false,
    val restoredBookingConfirmation: RestoredBookingConfirmation? = null,
    val recoveryCode: String? = null,
    val customerShipments: List<JsonObject>? = null,
    val customerShipmentsLoading: Boolean = false,
    val customerShipmentsHasMore: Boolean = false,
    val customerShipmentsError: String? = null,
    val customerShipmentsTab: String? = null,
    val customerShipmentsFilter: String? = null,
    val notificationsPage: List<JsonObject>? = null,
    val notificationsLoading: Boolean = false,
    val notificationsHasMore: Boolean = false,
    val notificationsError: String? = null,
    val phoneChangeTarget: String? = null,
    val phoneChangePendingName: String? = null,
    val phoneChangePendingAddress: String? = null,
    val phoneChangePendingCity: String? = null)
fun JsonObject.value(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: buildJsonObject {}

@HiltViewModel
class MobileWorkspaceViewModel @Inject constructor(
    private val client: SupabaseClient,
    @ApplicationContext private val context: Context,
    private val networkMonitor: WorkspaceNetworkMonitor = WorkspaceNetworkMonitor(context)
) : ViewModel() {
    private val recoveryStore=WorkspaceRecoveryStore(context,BuildConfig.SUPABASE_URL)
    private val proofDraftStore=ProofDraftStore(context)
    private val incidentDraftStore=IncidentDraftStore(context)
    private val expenseDraftStore=ExpenseDraftStore(context)
    internal val bookingDraftStore=BookingDraftStore(context)
    fun hasProofDraft(owner:String,delivery:String)=proofDraftStore.exists(owner,delivery)
    fun hasIncidentDraft(owner:String,delivery:String)=incidentDraftStore.exists(owner,delivery)
    fun hasExpenseDraft(owner:String,delivery:String)=expenseDraftStore.exists(owner,delivery)
    fun clearRestoredBookingConfirmation() { mutable.update { it.copy(restoredBookingConfirmation = null) } }
    private var signupPhone: String = ""
    private var recoveryPhone: String? = null
    private var pendingPasswordChange:JsonObject?=null
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
    private var customerShipmentsJob: Job? = null
    private var customerShipmentsVersion = 0
    private var notificationsJob: Job? = null
    private var notificationsVersion = 0
    private var pendingBackgroundRefresh = false
    private fun stopLive() {liveJob?.cancel();pollJob?.cancel();customerShipmentsJob?.cancel();notificationsJob?.cancel();TripTrackingService.stop(context)}
    private fun startLive(uid:String) {
        liveJob?.cancel();pollJob?.cancel()
        pollJob=viewModelScope.launch {while(true){delay(30.seconds);backgroundRefresh()}}
        liveJob=viewModelScope.launch {
            while(true) {
                val channel=client.channel("mobile-$uid")
                try {
                    coroutineScope {
                        listOf("shipments","deliveries","notifications","tracking_logs","shipment_status_history","drivers","trucks","compliance_documents","system_settings","delivery_expenses","delivery_incidents","incident_attachments","delivery_messages").forEach { table ->
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
        if(WorkspaceSyncRules.shouldQueueBackgroundRefresh(mutable.value.busy)) {
            pendingBackgroundRefresh = true
            return
        }
        if(mutable.value.page!="workspace")return
        val version = generation
        try {
            if (client.auth.currentUserOrNull() == null && loadedUser != null) {
                client.auth.loadFromStorage()
                withTimeout(15.seconds) {
                    client.auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
                }
                if (client.auth.currentUserOrNull() == null) return
            }
            load()
            mutable.value.details?.value("shipment_id")?.takeIf(String::isNotBlank)?.let { id ->
                val detailRequest=detailVersion
                val detail=readDetails(id)
                if(version==generation && detailRequest==detailVersion && mutable.value.details?.value("shipment_id")==id)mutable.update {it.copy(details=detail)}
            }
        } catch(cancelled:CancellationException){
            if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                mutable.update { it.copy(offline = true) }
            } else {
                throw cancelled
            }
        }
        catch(error:Exception){
            when (val outcome = WorkspaceAutoRefreshRules.evaluateRefreshError(error)) {
                is BackgroundRefreshOutcome.MarkOffline -> {
                    mutable.update { it.copy(offline = true) }
                }
                is BackgroundRefreshOutcome.AccessRevoked -> {
                    if (version == generation) {
                        loadedUser?.let { recoveryStore.clear(it) }
                        clearPasswordChallenge()
                        mutable.update { it.copy(data=null,details=null,proof=null,page="access",passwordChangeOwner=null,passwordChangeCodeSent=false,error=outcome.message) }
                    }
                }
                is BackgroundRefreshOutcome.SessionExpired -> {
                    if (mutable.value.error != outcome.message) {
                        mutable.update { it.copy(error = outcome.message) }
                    }
                }
                is BackgroundRefreshOutcome.ServerUnavailable -> {
                    if (mutable.value.error != outcome.message) {
                        mutable.update { it.copy(error = outcome.message) }
                    }
                }
                is BackgroundRefreshOutcome.Ignore -> Unit
            }
        }
    }
    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collectLatest { online ->
                if (online && mutable.value.page == "workspace" && mutable.value.offline) {
                    delay(1.seconds)
                    var attempts = 0
                    while (mutable.value.busy && attempts < 5) {
                        delay(500.milliseconds)
                        attempts++
                    }
                    if (WorkspaceAutoRefreshRules.shouldAutoRefresh(
                            isOnline = true,
                            page = mutable.value.page,
                            isOffline = mutable.value.offline,
                            isBusy = mutable.value.busy
                        )
                    ) {
                        backgroundRefresh()
                    }
                }
            }
        }
        recoveryStore.read("challenge",RecoveryPolicy.CHALLENGE_AGE)?.takeIf {RecoveryPolicy.valid(it,System.currentTimeMillis())}?.let {
            if(it.value("return_owner").isNotBlank()) {pendingPasswordChange=it;return@let}
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
                        if(mutable.value.passwordChangeOwner?.let{it!=id}==true) {
                            clearPasswordChallenge();mutable.update{MobileState(page="loading")}
                        }
                        pendingPasswordChange?.takeIf{it.value("return_owner")!=id}?.let{clearPasswordChallenge()}
                        if (id != loadedUser) { loadedUser?.let {recoveryStore.clear(it)};stopLive();generation++; mutable.update { it.copy(data=null,details=null,proof=null,history=null,historyLoading=false,historyError=null,conversationUnread=null) }; loadedUser = id;if(id!=null){restoreWorkspace(id);startLive(id)} }
                        if (!mutable.value.page.startsWith("recover")) refresh()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        val changing=mutable.value.passwordChangeOwner!=null || pendingPasswordChange!=null
                        if(changing)clearPasswordChallenge()
                        stopLive();loadedUser?.let {recoveryStore.clear(it);TrackingQueue(context).use {queue->queue.clearOwner(it)}}
                        generation++; loadedUser = null
                        mutable.update { if(changing)MobileState(page="login",message="Sign in again to manage your password.") else
                            it.copy(data=null,details=null,proof=null,history=null,historyLoading=false,historyError=null,conversationUnread=null,passwordChangeOwner=null,passwordChangeCodeSent=false,page=if(it.page in listOf("loading","workspace")) "login" else it.page) }
                    }
                    is SessionStatus.RefreshFailure -> {
                        stopLive()
                        generation++
                        if(mutable.value.passwordChangeOwner!=null || pendingPasswordChange!=null) {
                            clearPasswordChallenge();mutable.update{MobileState(page="login",error="Your session could not be verified. Sign in again to change your password.")};return@collect
                        }
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
    fun page(page: String) { if (!mutable.value.busy) {if(page=="login"){clearPasswordChallenge();cancelPhoneChange();signupPhone=""};mutable.update { it.copy(page=page,error=null,message=null,resendSeconds=if(page=="login")0 else it.resendSeconds,email=if(page=="login")"" else it.email,
        passwordChangeOwner=if(page=="login")null else it.passwordChangeOwner,passwordChangeCodeSent=page!="login" && it.passwordChangeCodeSent) }} }
    private fun clearPasswordChallenge(){recoveryStore.clear("challenge");recoveryPhone=null;pendingPasswordChange=null;timer?.cancel()}
    fun openPasswordChange() {
        if(mutable.value.busy)return
        val user=client.auth.currentUserOrNull()?:run {clearPasswordChallenge();mutable.update{MobileState(page="login",error="Sign in again to change your password.")};return}
        try {
            val next=PasswordChangePolicy.begin(mutable.value,user.id,user.phone,user.email)
            clearPasswordChallenge();mutable.value=next
        }catch(_:IllegalArgumentException){mutable.update{it.copy(error="Reconnect and verify your account phone or email before changing your password.")}}
    }
    fun cancelPasswordChange() {
        if(mutable.value.busy)return
        val next=PasswordChangePolicy.cancel(mutable.value,client.auth.currentUserOrNull()?.id)
        clearPasswordChallenge();mutable.value=next
    }
    private fun saveChallenge(page:String,target:String)=recoveryStore.write("challenge",RecoveryPolicy.challenge(page,target,System.currentTimeMillis(),
        mutable.value.passwordChangeOwner.takeIf{page=="recover_code"}))
    private fun restoreWorkspace(owner:String):Boolean {
        if(pendingPasswordChange!=null)return false // Verify the session before restoring its Profile overlay.
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
            finally {
                mutable.update { it.copy(busy=false) }
                if (WorkspaceSyncRules.shouldDrainPendingRefresh(pendingBackgroundRefresh, isBusy = false)) {
                    pendingBackgroundRefresh = false
                    viewModelScope.launch {
                        try { backgroundRefresh() } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    private fun friendly(error: Exception): String = MobileErrorClassifier.friendlyMessage(error)
    private suspend fun load() {
        val version=generation
        val id=client.auth.currentUserOrNull()?.id ?: return
        var data=try { client.postgrest.rpc("mobile_workspace").decodeAs<JsonObject>() }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            if (version==generation && error.message.orEmpty().contains("ACTIVE_")) {
                recoveryStore.clear(id)
                clearPasswordChallenge()
                mutable.update { it.copy(data=null,details=null,proof=null,page="access",passwordChangeOwner=null,passwordChangeCodeSent=false) }
            }
            throw error
        }
        if(data.obj("profile").value("role")=="driver") {
            // Driver-only reads retain the caller's RLS; customer loading is unchanged.
            val (extra, unread) = coroutineScope {
                val extraDeferred = async {
                    DriverDatabaseData.load(readSettings = {
                        client.from("system_settings").select {
                            filter { isIn("key", listOf("compliance_thresholds", "compliance_threshold_days", "driver_app_content")) }
                        }.decodeAs<JsonArray>()
                    }) { table, driverId ->
                        client.from(table).select {
                            when (table) {
                                "drivers" -> filter { eq("user_id", id) }
                                // Truck RLS includes both permanent and delivery assignments.
                                "trucks" -> Unit
                                else -> filter { eq("driver_id", requireNotNull(driverId)) }
                            }
                            if (table == "deliveries") { order("assigned_at", Order.DESCENDING); limit(200) }
                            if (table == "delivery_expenses") { order("created_at", Order.DESCENDING); limit(200) }
                        }.decodeAs<JsonArray>()
                    }
                }
                val unreadDeferred = async {
                    try { client.postgrest.rpc("delivery_conversation_unread").decodeAs<JsonArray>() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
                extraDeferred.await() to unreadDeferred.await()
            }
            data = JsonObject(data + extra)
            if (version == generation && client.auth.currentUserOrNull()?.id == id) mutable.update { it.copy(conversationUnread = unread) }
        }
        if(data.obj("profile").value("role")!="driver") {
            data=JsonObject(data+readSmsPreferences(id))
            reconcileUncertainBooking(data,id)
        }
        if (version==generation && client.auth.currentUserOrNull()?.id==id) {
            val savedAt=java.time.Instant.now().toString()
            val snapshot=RecoveryPolicy.workspace(data,id)
            val cached=snapshot==null || recoveryStore.writeAsync(id,buildJsonObject {put("created",System.currentTimeMillis());put("saved_at",savedAt);put("data",snapshot)})
            val pending=pendingPasswordChange
            val user=client.auth.currentUserOrNull()
            val restoreChange=pending!=null && user!=null && PasswordChangePolicy.isAllowedRole(data.obj("profile").value("role")) && PasswordChangePolicy.canRestore(pending,id,user.phone,user.email)
            if(pending!=null) {
                pendingPasswordChange=null
                if(restoreChange)recoveryPhone=MobileValidation.phone(pending.value("target")) else clearPasswordChallenge()
            }
            val phoneChallenge = recoveryStore.readAsync("phone_change_challenge", PhoneChangePolicy.CHALLENGE_AGE)
            val restorePhoneChange = phoneChallenge != null && PhoneChangePolicy.canRestore(phoneChallenge, id, System.currentTimeMillis())
            if (phoneChallenge != null && !restorePhoneChange) {
                recoveryStore.clear("phone_change_challenge")
            }
            mutable.update { state ->
                var s = state.copy(page="workspace",data=data,error=null,offline=false,lastSync=savedAt,message=if(cached)state.message else "Offline copy could not be saved on this device.",
                    passwordChangeOwner=if(restoreChange)id else state.passwordChangeOwner,passwordChangeCodeSent=restoreChange || state.passwordChangeCodeSent,
                    email=if(restoreChange)pending!!.value("target") else state.email)
                if (restorePhoneChange && phoneChallenge != null) {
                    s = PhoneChangePolicy.restore(s, phoneChallenge)
                }
                s
            }
            if(restoreChange)countdown(((pending!!.value("created").toLong()+60000-System.currentTimeMillis())/1000).toInt().coerceIn(0,60))
            else if (restorePhoneChange && phoneChallenge != null) {
                val remainingSeconds = ((phoneChallenge.value("created").toLong() + 60000 - System.currentTimeMillis()) / 1000).toInt().coerceIn(0, 60)
                if (remainingSeconds > 0) countdown(remainingSeconds)
            }
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
        }catch(e:Exception){
            if(e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException)throw e
            if(mutable.value.data!=null && MobileErrorClassifier.isNetworkError(e)) {
                mutable.update {it.copy(offline=true)}
            }
            throw e
        }
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
    fun loadCustomerShipments(tab: String, statusFilter: String, query: String, loadMore: Boolean = false) {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val version = ++customerShipmentsVersion
        val accountVersion = generation
        customerShipmentsJob?.cancel()

        val previousList = if (loadMore) mutable.value.customerShipments.orEmpty() else emptyList()
        val offset = previousList.size

        mutable.update {
            it.copy(
                customerShipments = if (loadMore) previousList else null,
                customerShipmentsLoading = true,
                customerShipmentsError = null,
                customerShipmentsTab = tab,
                customerShipmentsFilter = statusFilter
            )
        }

        if (mutable.value.offline) {
            val cached = mutable.value.data?.rows("shipments").orEmpty()
            val filtered = CustomerPaginationRules.filterOfflineShipments(cached, tab, statusFilter, query)
            mutable.update {
                it.copy(
                    customerShipments = filtered,
                    customerShipmentsLoading = false,
                    customerShipmentsHasMore = false,
                    customerShipmentsError = null,
                    customerShipmentsTab = tab,
                    customerShipmentsFilter = statusFilter
                )
            }
            return
        }

        customerShipmentsJob = viewModelScope.launch {
            try {
                val sanitizedQuery = CustomerPaginationRules.sanitizeSearchQuery(query)
                val pageSize = CustomerPaginationRules.SHIPMENTS_PAGE_SIZE

                val rows = client.from("shipments").select(
                    columns = io.github.jan.supabase.postgrest.query.Columns.raw(CustomerPaginationRules.SHIPMENT_COLUMNS)
                ) {
                    filter {
                        if (tab == "Active") {
                            notIn("status", listOf("completed", "cancelled"))
                        } else if (tab == "History") {
                            if (statusFilter == "All") {
                                isIn("status", listOf("completed", "cancelled"))
                            } else if (statusFilter.equals("Completed", ignoreCase = true) || statusFilter.equals("Cancelled", ignoreCase = true)) {
                                eq("status", statusFilter.lowercase())
                            } else {
                                isIn("status", listOf("completed", "cancelled"))
                            }
                        }
                        if (sanitizedQuery.isNotBlank()) {
                            or {
                                ilike("tracking_number", "%$sanitizedQuery%")
                                ilike("pickup_address", "%$sanitizedQuery%")
                                ilike("dropoff_address", "%$sanitizedQuery%")
                            }
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    range(offset.toLong(), (offset + pageSize - 1).toLong())
                }.decodeAs<JsonArray>().filterIsInstance<JsonObject>()

                if (version == customerShipmentsVersion && accountVersion == generation && client.auth.currentUserOrNull()?.id == uid) {
                    val merged = CustomerPaginationRules.mergePage(previousList, rows, reset = !loadMore)
                    val hasMore = CustomerPaginationRules.hasMore(rows.size, pageSize)
                    mutable.update {
                        it.copy(
                            customerShipments = merged,
                            customerShipmentsLoading = false,
                            customerShipmentsHasMore = hasMore,
                            customerShipmentsError = null,
                            customerShipmentsTab = tab,
                            customerShipmentsFilter = statusFilter
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == customerShipmentsVersion && accountVersion == generation) {
                    val cached = mutable.value.data?.rows("shipments").orEmpty()
                    val filtered = CustomerPaginationRules.filterOfflineShipments(cached, tab, statusFilter, query)
                    mutable.update {
                        it.copy(
                            customerShipments = if (loadMore) previousList else filtered,
                            customerShipmentsLoading = false,
                            customerShipmentsHasMore = false,
                            customerShipmentsError = if (loadMore) "Could not load more shipments. Check your connection and retry." else null,
                            customerShipmentsTab = tab,
                            customerShipmentsFilter = statusFilter
                        )
                    }
                }
            }
        }
    }
    fun loadNotifications(loadMore: Boolean = false) {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val version = ++notificationsVersion
        val accountVersion = generation
        notificationsJob?.cancel()

        val previousList = if (loadMore) mutable.value.notificationsPage.orEmpty() else emptyList()
        val offset = previousList.size

        mutable.update {
            it.copy(
                notificationsPage = if (loadMore) previousList else null,
                notificationsLoading = true,
                notificationsError = null
            )
        }

        if (mutable.value.offline) {
            val cached = mutable.value.data?.rows("notifications").orEmpty()
            val filtered = CustomerPaginationRules.filterOfflineNotifications(cached)
            mutable.update {
                it.copy(
                    notificationsPage = filtered,
                    notificationsLoading = false,
                    notificationsHasMore = false,
                    notificationsError = null
                )
            }
            return
        }

        notificationsJob = viewModelScope.launch {
            try {
                val pageSize = CustomerPaginationRules.NOTIFICATIONS_PAGE_SIZE
                val rows = client.from("notifications").select(
                    columns = io.github.jan.supabase.postgrest.query.Columns.raw(CustomerPaginationRules.NOTIFICATION_COLUMNS)
                ) {
                    filter {
                        eq("user_id", uid)
                    }
                    order("created_at", Order.DESCENDING)
                    range(offset.toLong(), (offset + pageSize - 1).toLong())
                }.decodeAs<JsonArray>().filterIsInstance<JsonObject>()

                if (version == notificationsVersion && accountVersion == generation && client.auth.currentUserOrNull()?.id == uid) {
                    val merged = CustomerPaginationRules.mergePage(previousList, rows, reset = !loadMore)
                    val hasMore = CustomerPaginationRules.hasMore(rows.size, pageSize)
                    mutable.update {
                        it.copy(
                            notificationsPage = merged,
                            notificationsLoading = false,
                            notificationsHasMore = hasMore,
                            notificationsError = null
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == notificationsVersion && accountVersion == generation) {
                    val cached = mutable.value.data?.rows("notifications").orEmpty()
                    val filtered = CustomerPaginationRules.filterOfflineNotifications(cached)
                    mutable.update {
                        it.copy(
                            notificationsPage = if (loadMore) previousList else filtered,
                            notificationsLoading = false,
                            notificationsHasMore = false,
                            notificationsError = if (loadMore) "Could not load more notifications. Check your connection and retry." else null
                        )
                    }
                }
            }
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
                if (!error.message.orEmpty().contains("invalid_credentials", ignoreCase = true) && !error.message.orEmpty().contains("Invalid login", ignoreCase = true)) throw error
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
    fun canVerify(raw: String,type: String): Boolean = raw == "session" || raw.matches(Regex("[0-9]{6,8}")) || raw.matches(Regex("[A-Za-z0-9_-]{20,500}")) || MobileValidation.linkToken(raw,java.net.URI(BuildConfig.SUPABASE_URL).host,type)!=null
    private suspend fun verifyEmail(raw: String,type: String,otpType: OtpType.Email) {
        val hash = MobileValidation.linkToken(raw, java.net.URI(BuildConfig.SUPABASE_URL).host, type)
            ?: raw.takeIf { it.matches(Regex("[A-Za-z0-9_-]{20,500}")) }
        if (hash != null) client.auth.verifyEmailOtp(type = otpType, tokenHash = hash)
        else {
            require(raw.matches(Regex("[0-9]{6,8}")))
            client.auth.verifyEmailOtp(otpType, mutable.value.email, raw)
        }
        val currentUserEmail = client.auth.currentUserOrNull()?.email.orEmpty()
        if (mutable.value.email.isBlank() && currentUserEmail.isNotBlank()) {
            mutable.update { it.copy(email = currentUserEmail) }
        }
        if (mutable.value.email.isNotBlank()) {
            require(currentUserEmail.equals(mutable.value.email, true))
        }
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
        val changeOwner=mutable.value.passwordChangeOwner
        if(changeOwner!=null) {
            val user=client.auth.currentUserOrNull()
            require(PasswordChangePolicy.current(mutable.value,user?.id) && email.equals(PasswordChangePolicy.target(user?.phone,user?.email),true))
        }
        val phone=MobileValidation.phone(email)
        require(MobileValidation.email(email) || phone!=null)
        if(phone!=null) client.auth.signInWith(OTP) {this.phone=phone;createUser=false}
        else client.auth.resetPasswordForEmail(email.trim())
        if(changeOwner!=null)check(PasswordChangePolicy.current(mutable.value,client.auth.currentUserOrNull()?.id))
        recoveryPhone=phone
        val saved=saveChallenge("recover_code",phone?:email.trim())
        countdown()
        val requested=if(phone!=null)"If the account exists, a recovery SMS has been requested." else "If the account exists, a recovery email has been requested."
        mutable.update { it.copy(page=if(changeOwner!=null)"workspace" else "recover_code",passwordChangeCodeSent=changeOwner!=null,email=email.trim(),message=requested+if(saved)"" else " Keep this screen open; recovery context could not be saved.") }
    }
    fun recover(code: String,password: String,confirm: String) = operation {
        require(MobileValidation.password(password,confirm))
        val changeOwner=mutable.value.passwordChangeOwner
        if(changeOwner!=null)check(mutable.value.passwordChangeCodeSent && PasswordChangePolicy.current(mutable.value,client.auth.currentUserOrNull()?.id))
        val phone=recoveryPhone
        if(phone!=null) {require(code.matches(Regex("[0-9]{6,8}")));client.auth.verifyPhoneOtp(OtpType.Phone.SMS,phone,code)}
        else if(code!="session") {
            val host = runCatching { java.net.URI(BuildConfig.SUPABASE_URL).host }.getOrNull().orEmpty()
            when (val parsed = DeepLinkParser.parse(code, host)) {
                is DeepLinkResult.Session -> {
                    if (parsed.isRecovery) {
                        client.auth.importAuthToken(parsed.accessToken, parsed.refreshToken, retrieveUser = true)
                    } else {
                        verifyEmail(code, "recovery", OtpType.Email.RECOVERY)
                    }
                }
                else -> verifyEmail(code, "recovery", OtpType.Email.RECOVERY)
            }
        }
        if(changeOwner!=null)check(PasswordChangePolicy.current(mutable.value,client.auth.currentUserOrNull()?.id))
        client.auth.updateUser { this.password=password }
        clearPasswordChallenge()
        client.auth.signOut()
        mutable.update { MobileState(page="login",message="Password updated. Sign in with your new password.") }
    }
    fun handleDeepLink(rawUri: String) {
        val host = runCatching { java.net.URI(BuildConfig.SUPABASE_URL).host }.getOrNull().orEmpty()
        when (val result = DeepLinkParser.parse(rawUri, host)) {
            is DeepLinkResult.Otp -> {
                if (result.type.equals("recovery", ignoreCase = true)) {
                    val currentUid = client.auth.currentUserOrNull()?.id
                    val changeOwner = mutable.value.passwordChangeOwner
                    if (currentUid != null && changeOwner == null) {
                        mutable.update { it.copy(message = "Recovery link received. Log out to reset password, or use Profile to change password.") }
                        return
                    }
                    val isOwnerChanging = changeOwner != null && PasswordChangePolicy.current(mutable.value, currentUid)
                    recoveryPhone = null
                    val email = result.email ?: mutable.value.email
                    saveChallenge("recover_code", email)
                    mutable.update {
                        it.copy(
                            page = if (isOwnerChanging) "workspace" else "recover_code",
                            passwordChangeCodeSent = isOwnerChanging,
                            email = if (email.isNotBlank()) email else it.email,
                            recoveryCode = result.tokenHash,
                            message = "Recovery link opened. Enter and confirm your new password."
                        )
                    }
                } else if (result.type.equals("signup", ignoreCase = true)) {
                    if (!result.email.isNullOrBlank()) {
                        mutable.update { it.copy(email = result.email) }
                    }
                    verifySignup(rawUri)
                }
            }
            is DeepLinkResult.Session -> {
                if (result.type.equals("recovery", ignoreCase = true)) {
                    val currentUid = client.auth.currentUserOrNull()?.id
                    val changeOwner = mutable.value.passwordChangeOwner
                    if (currentUid != null && changeOwner == null) {
                        mutable.update { it.copy(message = "Recovery link received. Log out to reset password, or use Profile to change password.") }
                        return
                    }
                    val isOwnerChanging = changeOwner != null && PasswordChangePolicy.current(mutable.value, currentUid)
                    recoveryPhone = null
                    viewModelScope.launch {
                        try {
                            client.auth.importAuthToken(result.accessToken, result.refreshToken, retrieveUser = true)
                            val email = client.auth.currentUserOrNull()?.email.orEmpty()
                            mutable.update {
                                it.copy(
                                    page = if (isOwnerChanging) "workspace" else "recover_code",
                                    passwordChangeCodeSent = isOwnerChanging,
                                    email = if (email.isNotBlank()) email else it.email,
                                    recoveryCode = "session",
                                    message = "Recovery link opened. Enter and confirm your new password."
                                )
                            }
                        } catch (e: Exception) {
                            mutable.update { it.copy(error = MobileErrorClassifier.friendlyMessage(e)) }
                        }
                    }
                } else if (result.type.equals("signup", ignoreCase = true)) {
                    viewModelScope.launch {
                        try {
                            client.auth.importAuthToken(result.accessToken, result.refreshToken, retrieveUser = true)
                            load()
                        } catch (e: Exception) {
                            mutable.update { it.copy(error = MobileErrorClassifier.friendlyMessage(e)) }
                        }
                    }
                }
            }
            is DeepLinkResult.Error -> {
                mutable.update { it.copy(error = result.description ?: result.error) }
            }
            DeepLinkResult.Invalid -> {}
        }
    }
    fun logout() = operation(allowOffline=true) {
        recoveryStore.clear("challenge")
        recoveryStore.clear("phone_change_challenge")
        stopLive();loadedUser?.let {owner->
            recoveryStore.clear(owner)
            TrackingQueue(context).use {queue->queue.clearOwner(owner)}
            // Only this account's private, temporary camera files; uploaded proof stays on the server.
            java.io.File(context.cacheDir,"proof/$owner").deleteRecursively()
            proofDraftStore.clearOwner(owner)
            incidentDraftStore.clearOwner(owner)
            expenseDraftStore.clearOwner(owner)
            val customerId = mutable.value.data?.obj("customer")?.value("id").orEmpty()
            if (customerId.isNotBlank()) bookingDraftStore.clear(customerId)
            bookingDraftStore.clearOwner(owner)
        }
        client.auth.signOut();generation++;loadedUser=null
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (_: ClearCredentialException) { /* Supabase sign-out already succeeded. */ }
        mutable.update { MobileState(page="login") }
    }
    fun book(requestId: String,data: JsonObject,onSuccess: (String) -> Unit) = book(requestId,data) { id,_ -> onSuccess(id) }
    fun book(requestId: String,data: JsonObject,onCreated: (String,String) -> Unit) = operation {
        val customerId = mutable.value.data?.obj("customer")?.value("id")?.ifBlank { client.auth.currentUserOrNull()?.id }.orEmpty()
        if (customerId.isNotBlank()) {
            bookingDraftStore.markSubmitted(customerId, requestId)
        }
        val result=client.postgrest.rpc("mobile_create_request",buildJsonObject { put("p_request_id",requestId);put("p_data",data) }).decodeAs<JsonObject>()
        require(result.value("id").isNotBlank() && result.value("tracking_number").isNotBlank())
        if (customerId.isNotBlank()) {
            bookingDraftStore.clear(customerId)
        }
        mutable.update { it.copy(message="Request ${result.value("tracking_number")} saved. Office review and quotation are pending.") }
        onCreated(result.value("id"),result.value("tracking_number"))
        try { load() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { mutable.update { it.copy(error="Request saved. Refresh to reload shipment details.") } }
    }
    private suspend fun reconcileUncertainBooking(data: JsonObject, userId: String) {
        val customerId = data.obj("customer").value("id").ifBlank { userId }
        val draft = bookingDraftStore.read(customerId) ?: return
        if (!draft.submitted) return

        val matching = try {
            client.from("shipments").select {
                filter { eq("client_request_id", draft.requestId) }
            }.decodeAs<JsonArray>().firstOrNull()?.jsonObject
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }

        when (val result = BookingRecoveryRules.reconcile(draft, matching)) {
            is BookingReconciliationResult.Confirmed -> {
                bookingDraftStore.clear(customerId)
                mutable.update { state ->
                    state.copy(
                        message = "Request ${result.trackingNumber} confirmed. Office review and quotation are pending.",
                        restoredBookingConfirmation = RestoredBookingConfirmation(
                            id = result.shipmentId,
                            trackingNumber = result.trackingNumber,
                            pickup = draft.pickup,
                            dropoff = draft.dropoff,
                            vehicleId = draft.selectedVehicleId,
                            isPickupNow = draft.isPickupNow,
                            scheduledDeparture = draft.scheduledDeparture
                        )
                    )
                }
            }
            is BookingReconciliationResult.RestoredForRetry -> {
                bookingDraftStore.save(customerId, result.draft)
                mutable.update { state ->
                    state.copy(
                        message = "Previous booking attempt was not received by the server. Your draft has been restored for retry."
                    )
                }
            }
            else -> Unit
        }
    }
    fun profile(name: String,phone: String,address: String,city: String) = operation {
        val normalized=MobileValidation.phone(phone) ?: error("INVALID_CONTACT")
        val user=client.auth.currentUserOrNull() ?: error("ACTIVE_ACCOUNT_REQUIRED")
        val uid=user.id
        val currentProfilePhone = mutable.value.data?.obj("profile")?.value("phone")
        if (PhoneChangePolicy.isPhoneChanged(user.phone, currentProfilePhone, normalized)) {
            requestPhoneChange(name, normalized, address, city)
            return@operation
        }
        executeSaveProfile(name, normalized, address, city, uid)
    }

    private suspend fun executeSaveProfile(name: String, normalized: String, address: String, city: String, uid: String) {
        val version = generation
        CustomerProfileRules.saveProfile(
            mutate = {
                client.postgrest.rpc("mobile_save_profile",buildJsonObject { put("p_name",name.trim());put("p_phone",normalized);put("p_address",address.trim());put("p_city",city.trim()) })
            },
            current = { version==generation && client.auth.currentUserOrNull()?.id==uid },
            onOptimisticUpdate = {
                mutable.update { state ->
                    val currentData = state.data
                    val updated = CustomerProfileRules.optimisticUpdate(currentData, name, normalized, address, city)
                    state.copy(data = updated ?: currentData, message = CustomerProfileRules.MSG_PROFILE_SAVED, error = null)
                }
                val currentData = mutable.value.data
                if (currentData != null) {
                    val snapshot = RecoveryPolicy.workspace(currentData, uid)
                    if (snapshot != null) {
                        recoveryStore.write(uid, buildJsonObject {
                            put("created", System.currentTimeMillis())
                            put("saved_at", java.time.Instant.now().toString())
                            put("data", snapshot)
                        })
                    }
                }
            },
            refresh = {
                load()
                if (version == generation && client.auth.currentUserOrNull()?.id == uid) {
                    mutable.update { state ->
                        state.copy(
                            message = CustomerProfileRules.resolveRefreshMessage(state.message),
                            error = null
                        )
                    }
                }
            },
            onRefreshFailed = {
                mutable.update { it.copy(message = CustomerProfileRules.MSG_PROFILE_SAVED_REFRESH_FAILED, error = null, offline = true) }
            }
        )
    }

    private suspend fun requestPhoneChange(name: String, normalizedPhone: String, address: String, city: String) {
        val user = client.auth.currentUserOrNull() ?: error("ACTIVE_ACCOUNT_REQUIRED")
        val uid = user.id
        val pending = PendingProfileEdit(name = name.trim(), phone = normalizedPhone, address = address.trim(), city = city.trim())
        client.auth.updateUser { this.phone = normalizedPhone }
        val now = System.currentTimeMillis()
        val challenge = PhoneChangePolicy.challenge(uid, normalizedPhone, pending, now)
        recoveryStore.write("phone_change_challenge", challenge)
        countdown()
        mutable.update { state ->
            PhoneChangePolicy.begin(state, uid, normalizedPhone, pending).copy(
                message = "Verification code sent to $normalizedPhone via SMS."
            )
        }
    }

    fun verifyPhoneChange(code: String) = operation {
        val targetPhone = mutable.value.phoneChangeTarget ?: error("NO_PENDING_PHONE_CHANGE")
        require(code.matches(Regex("[0-9]{6,8}"))) { "INVALID_VERIFICATION_CODE" }
        val user = client.auth.currentUserOrNull() ?: error("ACTIVE_ACCOUNT_REQUIRED")
        val uid = user.id
        client.auth.verifyPhoneOtp(type = OtpType.Phone.PHONE_CHANGE, phone = targetPhone, token = code.trim())
        val pendingName = mutable.value.phoneChangePendingName ?: user.userMetadata?.get("full_name")?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val pendingAddress = mutable.value.phoneChangePendingAddress.orEmpty()
        val pendingCity = mutable.value.phoneChangePendingCity.orEmpty()

        executeSaveProfile(pendingName, targetPhone, pendingAddress, pendingCity, uid)
        recoveryStore.clear("phone_change_challenge")
        mutable.update { state ->
            PhoneChangePolicy.cancel(state).copy(
                message = "Phone number verified and profile updated. Use this number for sign-in and SMS alerts."
            )
        }
    }

    fun resendPhoneChange() = operation {
        if (mutable.value.resendSeconds > 0) return@operation
        val targetPhone = mutable.value.phoneChangeTarget ?: error("NO_PENDING_PHONE_CHANGE")
        val user = client.auth.currentUserOrNull() ?: error("ACTIVE_ACCOUNT_REQUIRED")
        client.auth.resendPhone(type = OtpType.Phone.PHONE_CHANGE, phone = targetPhone)
        val pending = PendingProfileEdit(
            name = mutable.value.phoneChangePendingName.orEmpty(),
            phone = targetPhone,
            address = mutable.value.phoneChangePendingAddress.orEmpty(),
            city = mutable.value.phoneChangePendingCity.orEmpty()
        )
        val now = System.currentTimeMillis()
        val challenge = PhoneChangePolicy.challenge(user.id, targetPhone, pending, now)
        recoveryStore.write("phone_change_challenge", challenge)
        countdown()
        mutable.update { it.copy(message = "A new verification code was sent to $targetPhone via SMS.", error = null) }
    }

    fun cancelPhoneChange() {
        if (mutable.value.busy) return
        recoveryStore.clear("phone_change_challenge")
        mutable.update { PhoneChangePolicy.cancel(it) }
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
    private suspend fun reloadWorkspaceAndDetails(targetDeliveryId: String? = null) {
        load()
        val shipmentId = WorkspaceSyncRules.resolveShipmentToRefresh(
            currentDetailsShipmentId = mutable.value.details?.value("shipment_id"),
            deliveryId = targetDeliveryId,
            data = mutable.value.data
        )
        if (shipmentId != null && WorkspaceSyncRules.shouldRefreshDetails(shipmentId, mutable.value.details?.value("shipment_id"))) {
            val detailRequest = detailVersion
            val version = generation
            try {
                val detail = readDetails(shipmentId)
                if (version == generation && detailRequest == detailVersion && WorkspaceSyncRules.shouldRefreshDetails(shipmentId, mutable.value.details?.value("shipment_id"))) {
                    mutable.update { it.copy(details = detail) }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                // Partial failure reading details does not invalidate confirmed workspace data
            }
        }
    }
    private suspend fun confirmedDriverAction(message:String,onConfirmed:()->Unit={},deliveryId:String?=null,mutate:suspend ()->Unit) {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_DRIVER_REQUIRED")
        val version=generation
        ConfirmedMutation.run(mutate,{version==generation && client.auth.currentUserOrNull()?.id==uid},{
            mutable.update {it.copy(message=message,error=null)}
            try {onConfirmed()}catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){mutable.update {it.copy(message="$message Location sharing could not start. Tap Resume location sharing.")}}
        },{reloadWorkspaceAndDetails(deliveryId)},{mutable.update {it.copy(message="${it.message?:message} Saved successfully. Refresh to reload current trip status before another action.",error=null,offline=true)}})
    }
    fun driver(id: String,action: String) = operation {
        confirmedDriverAction(if(action=="no_show")"No-show reported for office review." else "Assignment updated.", deliveryId=id) {
            client.postgrest.rpc("mobile_driver_action",buildJsonObject {put("p_delivery",id);put("p_action",action)})
        }
    }
    fun arrive(id:String,lat:Double,lng:Double)=operation {
        confirmedDriverAction("Pickup arrival recorded.", deliveryId=id) {
            client.postgrest.rpc("driver_arrive_at_pickup",buildJsonObject {put("p_delivery_id",id);put("p_lat",lat);put("p_lng",lng)})
        }
    }
    fun startTrip(id:String,onStarted:()->Unit)=operation {
        confirmedDriverAction("Route started. Check location sharing status.",onStarted, deliveryId=id) {
            client.postgrest.rpc("mobile_driver_action",buildJsonObject {put("p_delivery",id);put("p_action","start")})
        }
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
        IncidentRequest.validate(request,category,description,minutes,photos)
        val paths=photos.mapIndexed {index,bytes->
            require(bytes.size in 1..DriverEvidence.MAX_BYTES)
            val path="$id/incident-$request-${index+1}.jpg"
            uploadEvidence(path,bytes);path
        }
        check(version==generation && client.auth.currentUserOrNull()?.id==uid)
        ConfirmedMutation.run({
            client.postgrest.rpc("driver_report_incident_evidence",buildJsonObject {put("p_delivery",id);put("p_request",request);put("p_category",category);put("p_description",description.trim());put("p_delay",minutes);put("p_paths",JsonArray(paths.map(::JsonPrimitive)))})
        },{version==generation && client.auth.currentUserOrNull()?.id==uid},{
            incidentDraftStore.clear(uid,id)
            mutable.update {it.copy(message="Incident reported to the office.",error=null)}
            try {onSaved()}catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){mutable.update {it.copy(message="Incident saved. Close the form to return to your trip.")}}
        },{reloadWorkspaceAndDetails(id)},{mutable.update {it.copy(message="Incident saved. Refresh to reload your records before another action.",error=null,offline=true)}})
    }
    fun addExpense(delivery:String,request:String,category:String,amount:Double,receipt:ByteArray?,onSaved:()->Unit)=operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_DRIVER_REQUIRED");val version=generation
        require(category.trim().length in 2..200 && amount.isFinite() && amount>0 && amount<=1000000)
        val driver=mutable.value.data?.obj("driver")?.value("id").orEmpty()
        require(driver.isNotBlank())
        java.util.UUID.fromString(request)
        val jpeg=receipt?.let {DriverEvidence.jpegAsync(it)}
        val path=jpeg?.let {"$delivery/receipt-$request.jpg"}
        fun current()=version==generation && client.auth.currentUserOrNull()?.id==uid
        val bucket=client.storage.from("delivery-documents")
        ConfirmedMutation.run({
            ExpenseSubmission.run({
                check(current())
                client.from("delivery_expenses").select {filter {eq("id",request)}}.decodeAs<JsonArray>().firstOrNull() as? JsonObject
            },{saved->
                require(ExpenseSubmission.matches(saved,delivery,driver,category,amount.toBigDecimal(),path)){"Expense request changed. Reopen the form for a new expense."}
                if(jpeg!=null)check(bucket.downloadAuthenticated(requireNotNull(path)).contentEquals(jpeg)){"The saved receipt differs. Reopen the form for a new expense."}
            },{
                if(jpeg!=null)ExpenseSubmission.upload(jpeg,{
                    check(current());bucket.upload(requireNotNull(path),jpeg){upsert=false;contentType=ContentType.Image.JPEG}
                },{check(current());bucket.downloadAuthenticated(requireNotNull(path))})
            },{
                check(current())
                client.from("delivery_expenses").insert(buildJsonObject {
                    put("id",request);put("delivery_id",delivery);put("driver_id",driver);put("category",category.trim().lowercase().takeIf{it in listOf("fuel","toll","parking","repair","other")}?:"other");put("description",category.trim());put("amount",amount);put("status","pending")
                    path?.let {put("receipt_path",it)}
                })
            })
        },::current,{
            expenseDraftStore.clear(uid,delivery)
            mutable.update {it.copy(message="Expense saved. See trip records for its current review status.",error=null)}
            try {onSaved()}catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){mutable.update {it.copy(message="Expense saved. Close the form to return to your trip.")}}
        },{reloadWorkspaceAndDetails(delivery)},{mutable.update {it.copy(message="Expense saved. Refresh to reload reviewed expense history before another action.",error=null,offline=true)}})
    }
    fun submitProof(id:String,recipient:String,photo:ByteArray,signature:ByteArray,attempt:String,onSaved:()->Unit)=operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_DRIVER_REQUIRED");val version=generation
        ProofSubmission.validate(id,attempt,recipient,photo,signature)
        withContext(Dispatchers.Default){ProofImage.decode(photo).recycle();ProofImage.decode(signature).recycle()}
        val bucket=client.storage.from("delivery-documents")
        val photoPath="$id/pod-photo-$attempt.jpg";val signaturePath="$id/signature-$attempt.png"
        fun current()=version==generation && client.auth.currentUserOrNull()?.id==uid
        suspend fun uploadOnce(path:String,bytes:ByteArray,type:ContentType) {
            ExpenseSubmission.upload(bytes,{check(current());bucket.upload(path,bytes) {upsert=false;contentType=type}},
                {check(current());bucket.downloadAuthenticated(path)})
        }
        ConfirmedMutation.run({ProofSubmission.run({
            check(current())
            val docs=client.from("delivery_documents").select {filter {eq("delivery_id",id)}}.decodeAs<JsonArray>().map{it.jsonObject}
            ProofSubmission.recorded(docs,id,uid,recipient,photoPath,signaturePath)
        },{
            check(current());check(bucket.downloadAuthenticated(photoPath).contentEquals(photo) && bucket.downloadAuthenticated(signaturePath).contentEquals(signature)){"The recorded proof files differ. Contact the office."}
        },{
            uploadOnce(photoPath,photo,ContentType.Image.JPEG);uploadOnce(signaturePath,signature,ContentType.Image.PNG)
        },{
            check(current());client.postgrest.rpc("driver_submit_pod",buildJsonObject {put("p_delivery_id",id);put("p_recipient_name",recipient.trim());put("p_photo_path",photoPath);put("p_signature_path",signaturePath)})
        })},::current,{
            if(TripTrackingStatus.state.value.delivery==id)TripTrackingService.stop(context)
            proofDraftStore.clear(uid,id)
            mutable.update {it.copy(message="Proof saved. Refresh for the current office review status.",error=null)}
            try {onSaved()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){ /* Confirmed proof remains saved. */ }
        },{reloadWorkspaceAndDetails(id)},{mutable.update{it.copy(offline=true,error=null,message="Proof saved. Refresh to reload office review status before another action.")}})
    }
    override fun onCleared() {liveJob?.cancel();pollJob?.cancel()}
    fun markRead(id: String) = operation {
        val uid=client.auth.currentUserOrNull()?.id ?: error("ACTIVE_ACCOUNT_REQUIRED")
        client.from("notifications").update(buildJsonObject { put("read_at",java.time.Instant.now().toString()) }) { filter { eq("id",id);eq("user_id",uid) } }
        mutable.update { state ->
            val updatedList = state.notificationsPage?.map { n ->
                if (n.value("id") == id) JsonObject(n + ("read_at" to JsonPrimitive(java.time.Instant.now().toString())))
                else n
            }
            state.copy(notificationsPage = updatedList)
        }
        load()
    }
    private suspend fun readDetails(id:String):JsonObject = coroutineScope {
        val detailsDeferred = async {
            client.postgrest.rpc("mobile_shipment_details", buildJsonObject { put("p_shipment", id) }).decodeAs<JsonObject>()
        }
        if (mutable.value.data?.obj("profile")?.value("role") != "driver") {
            val details = detailsDeferred.await()
            val activeDelivery = mutable.value.data?.rows("deliveries")?.firstOrNull {
                it.value("shipment_id") == id && it.value("status") in listOf("assigned", "loading", "in_transit")
            }
            val routing = if (activeDelivery != null) {
                try {
                    val deliveryId = activeDelivery.value("id")
                    if (deliveryId.isNotBlank()) readRouteStatus(deliveryId)
                    else buildJsonObject { put("reason", "Assigned delivery unavailable") }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    buildJsonObject { put("reason", "Live route calculation unavailable") }
                }
            } else null
            return@coroutineScope if (routing != null) JsonObject(details + mapOf("routing" to routing)) else details
        }
        val extraDeferred = async {
            try {
                buildJsonObject { put("fuel_logs", client.postgrest.rpc("mobile_driver_fuel_history", buildJsonObject { put("p_shipment", id) }).decodeAs<JsonArray>()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { buildJsonObject { put("fuel_error", "Could not load fuel records. Refresh to retry.") } }
        }
        val evidenceDeferred = async {
            try {
                client.postgrest.rpc("mobile_driver_evidence", buildJsonObject { put("p_shipment", id) }).decodeAs<JsonObject>()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { buildJsonObject { put("evidence_error", "Could not load incident photos or expense reviews. Refresh to retry.") } }
        }
        val routingDeferred = async {
            try {
                val delivery = DriverUiRules.delivery(mutable.value.data ?: buildJsonObject {}, id).value("id")
                if (delivery.isBlank()) buildJsonObject { put("reason", "Assigned delivery unavailable") }
                else readRouteStatus(delivery)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { buildJsonObject { put("reason", "Truck routing provider unavailable. Refresh to retry.") } }
        }
        val details = detailsDeferred.await()
        val extra = extraDeferred.await()
        val evidence = evidenceDeferred.await()
        val routing = routingDeferred.await()
        JsonObject(details + extra + evidence + mapOf("routing" to routing))
    }
    fun loadDetails(id: String) {
        detailJob?.cancel()
        val requestVersion=++detailVersion
        val version=generation
        val uid=client.auth.currentUserOrNull()?.id ?: return
        mutable.update { state ->
            state.copy(
                details = state.details?.takeIf { it.value("shipment_id") == id },
                proof = null,
                detailLoading = true,
                error = null
            )
        }
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
