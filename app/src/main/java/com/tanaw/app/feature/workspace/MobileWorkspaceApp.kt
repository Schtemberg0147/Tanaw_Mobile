package com.tanaw.app.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.tanaw.app.feature.auth.LoginScreen
import com.tanaw.app.feature.auth.CreateAccountScreen
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.platform.LocalContext

@Composable
fun MobileWorkspaceApp(vm: MobileWorkspaceViewModel) {
    val state by vm.state.collectAsState()
    val data=state.data
    val activityContext = LocalContext.current
    if (state.page=="login") {
        var legal by rememberSaveable { mutableStateOf<String?>(null) }
        LoginScreen(
            onLoginClick = vm::login,
            onForgotPassword = { vm.page("recover") },
            onCreateAccount = { vm.page("signup") },
            onTermsClick = { legal = "Terms of Service" },
            onPrivacyClick = { legal = "Privacy Policy" },
            successMessage = state.message,
            isLoading = state.busy,
            errorMessage = state.error,
            allowPhone = true,
            onGoogleSignIn = if (com.tanaw.app.BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) ({ vm.googleSignIn(activityContext) }) else null,
        )
        legal?.let { title ->
            InfoDialog(title,"The office has not published an approved $title in this app. Ask your administrator for the current document. No policy acceptance is recorded by opening this panel.") { legal=null }
        }
        return
    }
    if (state.page=="signup") {
        BackHandler(!state.busy) { vm.page("login") }
        var terms by remember { mutableStateOf(value = false) }
        CreateAccountScreen(onVerifyAndContinue=vm::signup,onSignIn={vm.page("login")},onTermsClick={terms=true},isLoading=state.busy,errorMessage=state.error,phoneSignup=true)
        if(terms) InfoDialog("Before creating an account","Use accurate contact details. Requests require office review and a confirmed quotation before dispatch. Contact the office for privacy and service terms; the full policy has not been published in this app.") { terms=false }
        return
    }
    if (state.page == "recover") {
        BackHandler(!state.busy) { vm.page("login") }
        ForgotPasswordCardScreen(state = state, vm = vm)
        return
    }
    if (state.page == "recover_code") {
        BackHandler(!state.busy) { vm.page("login") }
        VerifyRecoveryCodeScreen(state = state, vm = vm)
        return
    }
    if ((state.page != "workspace") || (data == null)) {
        BackHandler(state.page in listOf("verify","verify_phone")) {
            if(!state.busy) vm.page("login")
        }
        FormPage("Tanaw") {
            Notices(state)
            if(state.offline) SupportingText("Offline or reconnecting · showing the last loaded records. Tap Refresh to retry.")
            when(state.page) {
                "loading" -> { Text("Checking your session…"); if(!state.busy) Action("Retry",true,vm::refresh) }
                "verify" -> {
                    Text("Verify your email",style=MaterialTheme.typography.headlineSmall)
                    Text("Check ${state.email}. Enter the email verification code or paste its original confirmation link. Phone verification is not required.")
                    var code by remember(state.recoveryCode) { mutableStateOf(state.recoveryCode.orEmpty()) }
                    val canVerify = vm.canVerify(code,"signup")
                    val isCodeError = code.isNotBlank() && !canVerify
                    Field(
                        label = "Email code or confirmation link",
                        value = code,
                        change = { code = it.trim() },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!state.busy && canVerify) vm.verifySignup(code) }),
                        isError = isCodeError,
                        supportingText = if (isCodeError) { { Text("Enter a valid verification code or link.") } } else null
                    )
                    Action("Verify email",!state.busy && canVerify) { vm.verifySignup(code) }
                    Action(if(state.resendSeconds>0) "Resend in ${state.resendSeconds}s" else "Resend email",!state.busy && state.resendSeconds==0,vm::resend)
                }
                "verify_phone" -> {
                    Text("Verify your phone",style=MaterialTheme.typography.headlineSmall)
                    Text("Enter the code sent by SMS to the number you registered. Codes expire; request a new one if needed.")
                    var code by remember {mutableStateOf("")}
                    val codeValid = code.matches(Regex("[0-9]{6,8}"))
                    val isCodeError = code.isNotBlank() && !codeValid
                    Field(
                        label = "SMS verification code",
                        value = code,
                        change = { code = it.trim() },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!state.busy && codeValid) vm.verifyPhoneSignup(code) }),
                        isError = isCodeError,
                        supportingText = if (isCodeError) { { Text("Enter a 6-digit verification code.") } } else null
                    )
                    Action("Verify phone",!state.busy && codeValid) {vm.verifyPhoneSignup(code)}
                    Action(if(state.resendSeconds>0) "Resend in ${state.resendSeconds}s" else "Resend SMS",!state.busy && state.resendSeconds==0,vm::resendPhoneSignup)
                }
                else -> { Text("Account access needs attention.");Action("Retry",!state.busy,vm::refresh);Action("Sign out",!state.busy,vm::logout) }
            }
            if(state.page in listOf("verify","verify_phone")) Action("Back to sign in",!state.busy) { vm.page("login") }
        }
        return
    }
    key(data.obj("profile").value("id")) {
        if (data.obj("profile").value("role")=="customer" &&
            MobileValidation.phone(data.obj("profile").value("phone"))==null) {
            FormPage("Complete your profile") {
                Notices(state)
                Text("Add your contact number before continuing to shipments.")
                ProfileForm(vm,state,data)
                Action("Sign out",!state.busy,vm::logout)
            }
        } else if (data.obj("profile").value("role")=="driver") DriverWorkspace(vm,state,data)
        else Workspace(vm,state,data)
    }
    if(state.passwordChangeOwner!=null) PasswordChangeDialog(state,vm::cancelPasswordChange,
        {vm.resetEmail(state.email)},{vm.canVerify(it,"recovery")},vm::recover)
    if(state.phoneChangeTarget!=null) PhoneChangeDialog(state,vm::verifyPhoneChange,
        vm::resendPhoneChange,vm::cancelPhoneChange)
}

@Composable private fun Workspace(vm: MobileWorkspaceViewModel,state: MobileState,data: JsonObject) {
    var tab by rememberSaveable { mutableStateOf(if(state.passwordChangeOwner!=null)"Profile" else "Home") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable(tab) { mutableStateOf("") }
    var historyFilter by rememberSaveable { mutableStateOf("All") }
    var fullScreenBookingStep by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val bookingDraft=rememberSaveableStateHolder()
    val profile=data.obj("profile")
    val role=profile.value("role")
    val isCustomerHome = role == "customer" && tab == "Home" && detail == null
    val isCustomerProfile = role == "customer" && tab == "Profile" && detail == null
    BackHandler(detail!=null) { detail=null }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        if (!(isCustomerHome && fullScreenBookingStep)) {
            NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                val icons=listOf(Icons.Outlined.Home,Icons.Outlined.LocalShipping,Icons.Outlined.History,Icons.Outlined.Notifications,Icons.Outlined.Person)
                listOf("Home","Active","History","Alerts","Profile").forEachIndexed { index,label ->
                    val unreadAlerts = if (label == "Alerts") data.rows("notifications").count { it.value("read_at").isBlank() } else 0
                    NavigationBarItem(
                        selected=tab==label,
                        onClick={tab=label;detail=null},
                        enabled=!state.busy,
                        icon={
                            if (unreadAlerts > 0) {
                                BadgedBox(badge = { Badge { Text("$unreadAlerts") } }) {
                                    Icon(icons[index], contentDescription = label)
                                }
                            } else {
                                Icon(icons[index], contentDescription = label)
                            }
                        },
                        label={Text(label,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)},
                        alwaysShowLabel = true
                    )
                }
            }
        }
    }) { padding ->
        if (isCustomerHome) {
            Box(modifier = Modifier.fillMaxSize().padding(if (fullScreenBookingStep) PaddingValues(0.dp) else padding)) {
                bookingDraft.SaveableStateProvider("booking") {
                    CustomerBookingFlow(
                        vm = vm,
                        state = state,
                        data = data,
                        onOpenAlerts = { tab = "Alerts"; detail = null },
                        onTrackShipment = { id ->
                            tab = "Active"
                            detail = id.takeIf { it.isNotBlank() }
                        },
                        onFullScreenStepChanged = { fullScreenBookingStep = it }
                    )
                }
            }
        } else if (isCustomerProfile) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CustomerProfile(
                    vm = vm,
                    state = state,
                    data = data,
                    onOpenAlerts = { tab = "Alerts"; detail = null },
                    onOpenBookings = { tab = "Active"; detail = null },
                    onLogout = vm::logout
                )
            }
        } else {
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    TanawBrandLogo(modifier=Modifier.size(36.dp))
                    Column { Text("TANAW",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);SupportingText("${role.replaceFirstChar(Char::uppercase)} workspace") }
                }
                TextButton(onClick=vm::refresh,enabled=!state.busy) { Text("Refresh") }
            }
            Notices(state)
            if(detail!=null) {
                TextButton(onClick={detail=null}) { Text("← Back") }
                val allShipments = state.customerShipments.orEmpty() + data.rows("shipments")
                val shipment=allShipments.firstOrNull{it.value("id")==detail}
                if(shipment==null) Text("Shipment not found in the current list. Refresh or contact the office.")
                else WorkspaceCard { ShipmentDetails(shipment,data,role,state,vm) }
            } else when(tab) {
                "Home" -> {
                    Text("Hello, ${profile.value("full_name").ifBlank{"there"}}",style=MaterialTheme.typography.headlineSmall)
                    SupportingText("Your deliveries and office updates, in one place.")
                    WorkspaceCard {
                        Text("Delivery overview",style=MaterialTheme.typography.titleMedium)
                        val shipments=data.rows("shipments")
                        Text("${shipments.count{it.value("status") !in listOf("completed","cancelled")}} active  ·  ${shipments.count{it.value("status")=="completed"}} completed",style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold)
                    }
                    Text(if(role=="driver") "Your assigned trips" else "Shipment overview")
                    SupportingText(if(role=="driver") "Open your assigned trip to start location sharing, record pickup milestones and submit delivery proof." else "Dispatch, quotation and completion review are available on the office website.")
                    ShipmentList(data.rows("shipments").filter{it.value("status") !in listOf("completed","cancelled")}) {detail=it}
                }
                "Active","History" -> {
                    LaunchedEffect(tab, historyFilter, query) {
                        if (query.isNotBlank()) {
                            kotlinx.coroutines.delay(350)
                        }
                        vm.loadCustomerShipments(tab, historyFilter, query, loadMore = false)
                    }
                    Text(if(tab=="History") "Shipment history" else "Active shipments",style=MaterialTheme.typography.headlineSmall)
                    SupportingText("Search all your shipments by tracking reference or address.")
                    Field(
                        label = "Search reference or address",
                        value = query,
                        change = { query = it },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                    if(tab=="History") Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("All","Completed","Cancelled").forEach { f -> FilterChip(selected=historyFilter==f,onClick={historyFilter=f},label={Text(f)}) }
                    }
                    val isCurrentTabLoaded = state.customerShipmentsTab == tab && (tab != "History" || state.customerShipmentsFilter == historyFilter)
                    val displayedShipments = if (isCurrentTabLoaded) {
                        state.customerShipments ?: CustomerPaginationRules.filterOfflineShipments(
                            data.rows("shipments"),
                            tab,
                            historyFilter,
                            query
                        )
                    } else {
                        CustomerPaginationRules.filterOfflineShipments(
                            data.rows("shipments"),
                            tab,
                            historyFilter,
                            query
                        )
                    }
                    if(state.customerShipmentsLoading && (!isCurrentTabLoaded || state.customerShipments == null)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if(state.customerShipmentsError != null) {
                        Text(state.customerShipmentsError!!, color = MaterialTheme.colorScheme.error)
                    }
                    ShipmentList(displayedShipments) {detail=it}
                    if(isCurrentTabLoaded && state.customerShipmentsHasMore) {
                        OutlinedButton(
                            onClick = { vm.loadCustomerShipments(tab, historyFilter, query, loadMore = true) },
                            enabled = !state.customerShipmentsLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if(state.customerShipmentsLoading) "Loading more shipments..." else "Load more shipments")
                        }
                    }
                }
                "Alerts" -> {
                    LaunchedEffect(Unit) {
                        if (state.notificationsPage == null) vm.loadNotifications(loadMore = false)
                    }
                    Text("Notifications",style=MaterialTheme.typography.headlineSmall)
                    val displayedNotifications = state.notificationsPage ?: data.rows("notifications")
                    if(state.notificationsLoading && state.notificationsPage == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if(state.notificationsError != null) {
                        Text(state.notificationsError!!, color = MaterialTheme.colorScheme.error)
                    }
                    if(displayedNotifications.isEmpty() && !state.notificationsLoading) Text("No notifications.")
                    val allShipments = state.customerShipments.orEmpty() + data.rows("shipments")
                    displayedNotifications.forEach { n ->
                        WorkspaceCard {
                            if(n.value("read_at").isBlank()) StatusBadge("unread")
                            Text(n.value("title"),fontWeight=FontWeight.SemiBold);SupportingText(n.value("body"));SupportingText(MobilePresentation.time(n.value("created_at")))
                            if(n.value("read_at").isBlank()) TextButton(onClick={vm.markRead(n.value("id"))},enabled=!state.busy) {Text("Mark read")}
                            if(allShipments.any{it.value("id")==n.value("shipment_id")}) TextButton(onClick={detail=n.value("shipment_id")}) {Text("Open shipment")}
                        }
                    }
                    if(state.notificationsHasMore) {
                        OutlinedButton(
                            onClick = { vm.loadNotifications(loadMore = true) },
                            enabled = !state.notificationsLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if(state.notificationsLoading) "Loading more notifications..." else "Load more notifications")
                        }
                    }
                }
                "Profile" -> {
                    WorkspaceCard { ProfileForm(vm,state,data) }
                    Action("Saved address",!state.busy) { info=data.obj("customer").value("address").ifBlank{"No saved address. Add your address above and save your profile."} }
                    Action("Help and service information",true) {info="Contact the office with your tracking reference for quotations, dispatch, account activation or changes. Drivers share location during active trips and submit private proof for office review. Chat, payments and profile photos are not available in this release."}
                    Action("Password recovery",!state.busy) {vm.page("recover")}
                    Text("Login email changes require a verified account-change flow. Contact the office; editing profile details does not change your sign-in email.")
                    Action("Sign out",!state.busy,vm::logout)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        }
    }
    if(info!=null) InfoDialog("Tanaw",info!!) {info=null}
    state.proof?.let { bitmap ->
        Dialog(onDismissRequest=vm::closeProof) {
            Surface(shape=MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(state.proofTitle,style=MaterialTheme.typography.titleMedium)
                    Image(bitmap.asImageBitmap(),contentDescription="Recorded proof of delivery",modifier=Modifier.fillMaxWidth().heightIn(max=480.dp))
                    Text("Private document. Close this viewer when finished.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick=vm::closeProof) {Text("Close proof")}
                }
            }
        }
    }
}

@Composable private fun ShipmentList(rows: List<JsonObject>,open:(String)->Unit) {
    if(rows.isEmpty()) Text("No shipments in this section.")
    rows.forEach { s ->
        OutlinedCard(onClick={open(s.value("id"))},modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.value("tracking_number"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(s.value("status"))
                }
                Text(
                    text = "${s.value("pickup_address")} → ${s.value("dropoff_address")}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                SupportingText(MobilePresentation.time(s.value("created_at")))
            }
        }
    }
}

@Composable private fun ShipmentDetails(s:JsonObject,data:JsonObject,role:String,state:MobileState,vm:MobileWorkspaceViewModel) {
    val busy=state.busy
    var now by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(15.seconds)
            now = java.time.Instant.now()
        }
    }
    LaunchedEffect(s.value("id")) { vm.loadDetails(s.value("id")) }
    Text(s.value("tracking_number"),style=MaterialTheme.typography.headlineSmall)
    listOf(
        "status" to "Status",
        "pickup_address" to "Pickup",
        "dropoff_address" to "Destination",
        "cargo_description" to "Cargo",
        "weight_kg" to "Weight (kg)",
        "scheduled_departure" to "Scheduled departure",
        "estimated_arrival" to "Scheduled arrival (office plan)",
        "driver_notes" to "Notes",
        "cancelled_reason" to "Cancellation reason"
    ).forEach { (key,label) ->
        if(s.value(key).isNotBlank()) Text("$label: ${when(key){"scheduled_departure","estimated_arrival"->MobilePresentation.time(s.value(key));"weight_kg"->MobilePresentation.number(s.value(key),"kg");"status"->MobilePresentation.status(s.value(key));else->s.value(key)}}")
    }
    StatusBadge(s.value("status"))
    Text("Quoted amount: ${MobilePresentation.money(s.value("quoted_amount"))}")
    Text("Final amount: ${MobilePresentation.money(s.value("final_amount"))}")
    SupportingText("Amounts in PHP · Philippine time")
    val deliveries=data.rows("deliveries").filter{it.value("shipment_id")==s.value("id")}
    if(deliveries.isEmpty()) Text("No delivery assignment has been recorded.")
    deliveries.forEach { d ->
        Text("Delivery: ${d.value("status").replace('_',' ')}",fontWeight=FontWeight.Bold)
        listOf("assigned_at","route_started_at","pickup_arrived_at","customer_showed_at","departed_at","arrived_at","recipient_name").forEach {key-> if(d.value(key).isNotBlank())Text("${MobilePresentation.status(key)}: ${if(key.endsWith("_at"))MobilePresentation.time(d.value(key)) else d.value(key)}")}
        if(role=="driver") DriverTripActions(d,state,data,vm)
    }
    if(role=="customer") {
        CustomerCancellationSection(
            shipment = s,
            deliveries = deliveries,
            state = state,
            onCancelShipment = { shipmentId, reason, onDone ->
                vm.cancelShipment(shipmentId, reason, onDone)
            },
            onClearError = { vm.clearCancellationError() }
        )
    }
    TextButton(onClick={vm.loadDetails(s.value("id"))},enabled=!state.detailLoading) {Text("Refresh timeline and proof")}
    val detail=state.details?.takeIf { it.value("shipment_id")==s.value("id") }
    if(state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(detail!=null) {
        val position=detail.obj("position")
        val activeDelivery=deliveries.firstOrNull { it.value("status") in listOf("assigned","loading","in_transit") } ?: deliveries.firstOrNull()
        val routing=detail.obj("routing").takeIf { it.isNotEmpty() }
        val freshness=TrackingFreshnessRules.evaluatePositionFreshness(position.value("recorded_at"),position.value("accuracy_m"),now)
        val eta=TrackingFreshnessRules.customerEtaSummary(s,activeDelivery,routing,freshness,state.offline,now)

        Text("Arrival & Tracking Freshness",style=MaterialTheme.typography.titleMedium)
        WorkspaceCard {
            Text("Scheduled arrival (office plan): ${eta.scheduledArrival}",fontWeight=FontWeight.SemiBold)
            if(s.value("scheduled_departure").isNotBlank()) {
                SupportingText("Scheduled departure: ${eta.scheduledDeparture}")
            }
            HorizontalDivider()
            Text("Live ETA: ${eta.liveEta}",fontWeight=FontWeight.Bold,color=if(eta.isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            SupportingText(eta.liveEtaDetail)
            HorizontalDivider()
            Row(
                modifier=Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically
            ) {
                Text("Vehicle GPS",fontWeight=FontWeight.SemiBold)
                GpsFreshnessBadge(freshness)
            }
            if(position.value("recorded_at").isNotBlank()) {
                Text("Position: ${position.value("latitude")}, ${position.value("longitude")}\n${MobilePresentation.time(position.value("recorded_at"))} · ${freshness.accuracyText}")
            } else {
                SupportingText("No GPS location has been recorded for this delivery.")
            }
        }

        val points=mutableListOf<Pair<String,org.maplibre.android.geometry.LatLng>>()
        fun point(label:String,lat:String,lng:String) {val a=lat.toDoubleOrNull();val b=lng.toDoubleOrNull();if(a!=null && b!=null)points.add(label to org.maplibre.android.geometry.LatLng(a,b))}
        point("Pickup",s.value("pickup_lat"),s.value("pickup_lng"))
        point("Destination",s.value("dropoff_lat"),s.value("dropoff_lng"))
        point("Last recorded vehicle position",position.value("latitude"),position.value("longitude"))
        val verifiedRouting = routing?.let {
            DriverRouteStatus.forAssignment(it, s, activeDelivery ?: buildJsonObject {})
        }
        if(points.isNotEmpty()) {
            ShipmentMap(points, DriverRouteStatus.resolvedGeometry(s, verifiedRouting))
            SupportingText("Red: pickup · Blue: destination · Green: last recorded vehicle position")
        }

        Text("Recorded timeline",style=MaterialTheme.typography.titleMedium)
        val timeline=(detail.rows("events").map {it.value("occurred_at") to it.value("event_type").replace('_',' ')}+
            detail.rows("history").map {it.value("created_at") to (it.value("to_status").replace('_',' ')+it.value("note").takeIf(String::isNotBlank)?.let {note->" · $note"}.orEmpty())}).sortedBy {it.first}
        if(timeline.isEmpty()) Text("No status events have been recorded.")
        timeline.forEach { (time,label)->Text("${MobilePresentation.time(time)}\n${MobilePresentation.status(label)}") }

        Text("Proof of delivery",style=MaterialTheme.typography.titleMedium)
        val documents=detail.rows("documents")
        if(documents.isEmpty()) Text("No proof documents are available for this shipment.")
        documents.forEach { d->
            Text("${d.value("kind").replace('_',' ')} · ${d.value("file_name").ifBlank{"Document recorded"}}\nRecipient: ${d.value("recipient_name").ifBlank{"Not recorded"}}\n${MobilePresentation.time(d.value("created_at"))}")
            Action("View recorded proof",!busy) {vm.viewProof(s.value("id"),d.value("id"))}
        }
    }
}

@Composable private fun ProfileForm(vm:MobileWorkspaceViewModel,state:MobileState,data:JsonObject) {
    val p=data.obj("profile");val c=data.obj("customer")
    val initialAddr = remember(c) {
        CustomerAddressRules.parseFromSaved(
            rawAddress = c.value("address"),
            rawCity = c.value("city"),
            explicitBarangay = c.value("barangay"),
            explicitProvince = c.value("province")
        )
    }
    var name by rememberSaveable(p.value("full_name")){mutableStateOf(p.value("full_name"))}
    var phone by rememberSaveable(p.value("phone")){mutableStateOf(p.value("phone"))}
    var street by rememberSaveable(initialAddr.streetAddress){mutableStateOf(initialAddr.streetAddress)}
    var barangay by rememberSaveable(initialAddr.barangay){mutableStateOf(initialAddr.barangay)}
    var city by rememberSaveable(initialAddr.city){mutableStateOf(initialAddr.city)}
    var province by rememberSaveable(initialAddr.province){mutableStateOf(initialAddr.province.ifBlank { CustomerAddressRules.DEFAULT_PROVINCE_SUGGESTION })}
    Text("Your profile",style=MaterialTheme.typography.headlineSmall)
    Text(if(p.value("email").isNotBlank()) "Sign-in email: ${p.value("email")}" else "Phone-number account")
    Field("Full name",name,{name=it})
    Field("Phone",phone,{phone=it})
    if(p.value("role")=="customer") {
        Field("House / Street address",street,{street=it})
        Field("Barangay",barangay,{barangay=it})
        Field("City / Municipality",city,{city=it})
        Field("Province",province,{province=it})
    }
    val addressValid = p.value("role") != "customer" || CustomerAddressRules.isValidAddress(street, barangay, city, province)
    Action("Save profile",!state.busy && name.trim().length in 2..200 && MobileValidation.phone(phone)!=null && addressValid){
        val (formattedAddress, formattedCity) = if (p.value("role") == "customer") {
            CustomerAddressRules.formatForRpc(street, barangay, city, province)
        } else {
            Pair("", "")
        }
        vm.profile(name,phone,formattedAddress,formattedCity)
    }
    if(state.offline)Text("Saved SMS status may be out of date. Reconnect and refresh.")
    Text("Shipment SMS: ${RecoveryPolicy.sms(data, "shipment_status")}")
    Text("Delay SMS: ${RecoveryPolicy.sms(data, "delay")}")
    Text("SMS updates are optional and go only to your verified signup number. Keep that number as your contact number.")
    Action("Enable shipment SMS",!state.busy){vm.smsPreference(true)}
    Action("Disable shipment SMS",!state.busy){vm.smsPreference(false)}
}

@Composable internal fun Notices(state:MobileState) {
    if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(10.dp)) { Text(it,Modifier.padding(14.dp),color=MaterialTheme.colorScheme.onErrorContainer) } }
    state.message?.let { Surface(color=MaterialTheme.colorScheme.primary.copy(alpha=.08f),shape=RoundedCornerShape(10.dp)) { Text(it,Modifier.padding(14.dp),color=MaterialTheme.colorScheme.primary) } }
}
@Composable private fun Field(
    label: String,
    value: String,
    change: (String) -> Unit,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        isError = isError,
        supportingText = supportingText
    )
}
@Composable private fun Action(label:String,enabled:Boolean,onClick:()->Unit) { Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label)} }
@Composable private fun FormPage(title:String,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxSize()) { Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Text(title,style=MaterialTheme.typography.headlineMedium);content()} }
}
@Composable private fun InfoDialog(title:String,text:String,close:()->Unit) {AlertDialog(onDismissRequest=close,title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=close){Text("Close")}})}
