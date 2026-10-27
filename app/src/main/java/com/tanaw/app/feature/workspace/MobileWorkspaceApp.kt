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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
        LoginScreen(onLoginClick=vm::login,onForgotPassword={vm.page("recover")},onCreateAccount={vm.page("signup")},
            successMessage=state.message,isLoading=state.busy,errorMessage=state.error,allowPhone=true,
            onGoogleSignIn=if(com.tanaw.app.BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) ({vm.googleSignIn(activityContext)}) else null)
        return
    }
    if (state.page=="signup") {
        BackHandler(!state.busy) { vm.page("login") }
        var terms by remember { mutableStateOf(false) }
        CreateAccountScreen(onVerifyAndContinue=vm::signup,onSignIn={vm.page("login")},onTermsClick={terms=true},isLoading=state.busy,errorMessage=state.error,phoneSignup=true)
        if(terms) InfoDialog("Before creating an account","Use accurate contact details. Requests require office review and a confirmed quotation before dispatch. Contact the office for privacy and service terms; the full policy has not been published in this app.") { terms=false }
        return
    }
    if (state.page!="workspace" || data==null) {
        FormPage("Tanaw") {
            Notices(state)
            if(state.offline) SupportingText("Offline or reconnecting · showing the last loaded records. Tap Refresh to retry.")
            when(state.page) {
                "loading" -> { Text("Checking your session…"); if(!state.busy) Action("Retry",true,vm::refresh) }
                "verify" -> {
                    Text("Verify your email",style=MaterialTheme.typography.headlineSmall)
                    Text("Check ${state.email}. Enter the email verification code or paste its original confirmation link. Phone verification is not required.")
                    var code by remember { mutableStateOf("") }
                    Field("Email code or confirmation link",code,{code=it.trim()})
                    Action("Verify email",!state.busy && vm.canVerify(code,"signup")) { vm.verifySignup(code) }
                    Action(if(state.resendSeconds>0) "Resend in ${state.resendSeconds}s" else "Resend email",!state.busy && state.resendSeconds==0,vm::resend)
                }
                "verify_phone" -> {
                    Text("Verify your phone",style=MaterialTheme.typography.headlineSmall)
                    Text("Enter the code sent by SMS to the number you registered. Codes expire; request a new one if needed.")
                    var code by remember {mutableStateOf("")}
                    Field("SMS verification code",code,{code=it.trim()})
                    Action("Verify phone",!state.busy && code.matches(Regex("[0-9]{6,8}"))) {vm.verifyPhoneSignup(code)}
                    Action(if(state.resendSeconds>0) "Resend in ${state.resendSeconds}s" else "Resend SMS",!state.busy && state.resendSeconds==0,vm::resendPhoneSignup)
                }
                "recover","recover_code" -> {
                    Text("Reset password",style=MaterialTheme.typography.headlineSmall)
                    var email by remember { mutableStateOf(state.email) }
                    Field("Account phone number or email",email,{email=it})
                    Action("Request recovery code",!state.busy && state.resendSeconds==0 && (MobileValidation.email(email) || MobileValidation.phone(email)!=null)) { vm.resetEmail(email) }
                    if(state.resendSeconds>0)Text("Request another code in ${state.resendSeconds}s")
                    if(state.page=="recover_code") {
                        Text("Recovery requested for ${state.email}")
                        var code by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        var confirm by remember { mutableStateOf("") }
                        Field("Recovery code (or original email link)",code,{code=it.trim()})
                        Field("New password (8+ characters)",password,{password=it},true)
                        Field("Confirm new password",confirm,{confirm=it},true)
                        Action("Update password",!state.busy && vm.canVerify(code,"recovery") && MobileValidation.password(password,confirm)) { vm.recover(code,password,confirm) }
                    }
                }
                else -> { Text("Account access needs attention.");Action("Retry",!state.busy,vm::refresh);Action("Sign out",!state.busy,vm::logout) }
            }
            if(state.page in listOf("verify","verify_phone","recover","recover_code")) Action("Back to sign in",!state.busy) { vm.page("login") }
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
}

@Composable private fun Workspace(vm: MobileWorkspaceViewModel,state: MobileState,data: JsonObject) {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable(tab) { mutableStateOf("") }
    var historyFilter by rememberSaveable { mutableStateOf("All") }
    val bookingDraft=rememberSaveableStateHolder()
    val profile=data.obj("profile")
    val role=profile.value("role")
    BackHandler(detail!=null) { detail=null }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
            val icons=listOf(Icons.Outlined.Home,Icons.Outlined.LocalShipping,Icons.Outlined.History,Icons.Outlined.Notifications,Icons.Outlined.Person)
            listOf("Home","Active","History","Alerts","Profile").forEachIndexed { index,label ->
                NavigationBarItem(selected=tab==label,onClick={tab=label;detail=null},enabled=!state.busy,
                    icon={Icon(icons[index],null)},label={Text(label,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)})
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("TANAW",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);SupportingText("${role.replaceFirstChar(Char::uppercase)} workspace") }
                TextButton(onClick=vm::refresh,enabled=!state.busy) { Text("Refresh") }
            }
            Notices(state)
            if(detail!=null) {
                TextButton(onClick={detail=null}) { Text("← Back") }
                val shipment=data.rows("shipments").firstOrNull{it.value("id")==detail}
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
                    if(role=="customer") WorkspaceCard { bookingDraft.SaveableStateProvider("booking") { BookingForm(vm,state,data) { detail=it;tab="Active" } } }
                    else {
                        Text(if(role=="driver") "Your assigned trips" else "Shipment overview")
                        SupportingText(if(role=="driver") "Open your assigned trip to start location sharing, record pickup milestones and submit delivery proof." else "Dispatch, quotation and completion review are available on the office website.")
                        ShipmentList(data.rows("shipments").filter{it.value("status") !in listOf("completed","cancelled")}) {detail=it}
                    }
                }
                "Active","History" -> {
                    Text(if(tab=="History") "Shipment history" else "Active shipments",style=MaterialTheme.typography.headlineSmall)
                    SupportingText("Your latest 200 permitted shipments. Updates appear automatically while connected.")
                    Field("Search reference or address",query,{query=it})
                    if(tab=="History") Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("All","Completed","Cancelled").forEach { f -> FilterChip(selected=historyFilter==f,onClick={historyFilter=f},label={Text(f)}) }
                    }
                    ShipmentList(data.rows("shipments").filter{
                        (it.value("status") in listOf("completed","cancelled")) == (tab=="History") &&
                        (tab!="History" || historyFilter=="All" || it.value("status").equals(historyFilter,true)) &&
                        listOf("tracking_number","pickup_address","dropoff_address").any {key->it.value(key).contains(query,true)}
                    }) {detail=it}
                }
                "Alerts" -> {
                    Text("Notifications",style=MaterialTheme.typography.headlineSmall)
                    if(data.rows("notifications").isEmpty()) Text("No notifications.")
                    data.rows("notifications").forEach { n ->
                        WorkspaceCard {
                            if(n.value("read_at").isBlank()) StatusBadge("unread")
                            Text(n.value("title"),fontWeight=FontWeight.SemiBold);SupportingText(n.value("body"));SupportingText(MobilePresentation.time(n.value("created_at")))
                            if(n.value("read_at").isBlank()) TextButton(onClick={vm.markRead(n.value("id"))},enabled=!state.busy) {Text("Mark read")}
                            if(data.rows("shipments").any{it.value("id")==n.value("shipment_id")}) TextButton(onClick={detail=n.value("shipment_id")}) {Text("Open shipment")}
                        }
                    }
                }
                "Profile" -> {
                    WorkspaceCard { ProfileForm(vm,state,data) }
                    Action("Saved address",!state.busy) { info=data.obj("customer").value("address").ifBlank{"No saved address. Add your address above and save your profile."} }
                    Action("Help and service information",true) {info="Contact the office with your tracking reference for quotations, dispatch, account activation or changes. Drivers share location during active trips and submit private proof for office review. Chat, payments, profile photos and customer cancellation are not available in this release."}
                    Action("Password recovery",!state.busy) {vm.page("recover")}
                    Text("Login email changes require a verified account-change flow. Contact the office; editing profile details does not change your sign-in email.")
                    Action("Sign out",!state.busy,vm::logout)
                }
            }
            Spacer(Modifier.height(12.dp))
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

@Composable private fun BookingForm(vm: MobileWorkspaceViewModel,state: MobileState,data: JsonObject,onSaved:(String)->Unit) {
    var requestId by rememberSaveable {mutableStateOf(UUID.randomUUID().toString())}
    var pickup by rememberSaveable {mutableStateOf("")}
    var dropoff by rememberSaveable {mutableStateOf("")}
    var cargo by rememberSaveable {mutableStateOf("")}
    var weight by rememberSaveable {mutableStateOf("")}
    var contact by rememberSaveable {mutableStateOf(data.obj("profile").value("phone"))}
    var departure by rememberSaveable {mutableStateOf("")}
    var notes by rememberSaveable {mutableStateOf("")}
    var vehicleId by rememberSaveable {mutableStateOf("")}
    var review by remember {mutableStateOf(false)}
    var started by rememberSaveable {mutableStateOf(false)}
    var step by rememberSaveable {mutableIntStateOf(1)}
    val stepHeading=remember {BringIntoViewRequester()}
    LaunchedEffect(step,started) {if(started) stepHeading.bringIntoView()}
    var bookingClock by remember {mutableStateOf(java.time.Instant.now())}
    LaunchedEffect(Unit) {while(true) {bookingClock=java.time.Instant.now();kotlinx.coroutines.delay(1.seconds)}}
    var pickupLat by rememberSaveable {mutableStateOf<Double?>(null)}
    var pickupLng by rememberSaveable {mutableStateOf<Double?>(null)}
    var dropoffLat by rememberSaveable {mutableStateOf<Double?>(null)}
    var dropoffLng by rememberSaveable {mutableStateOf<Double?>(null)}
    var handling by rememberSaveable {mutableStateOf("None")}
    val vehicle=data.rows("vehicles").firstOrNull{it.value("id")==vehicleId}
    val parsedWeight=MobileValidation.weight(weight)
    val date=MobileBookingRules.departure(departure,bookingClock)
    val vehicleFits=vehicle?.let {MobileBookingRules.fits(weight,it.value("default_capacity_kg"))}==true
    val valid=pickup.trim().length in 5..500 && dropoff.trim().length in 5..500 && !pickup.trim().equals(dropoff.trim(),true) && cargo.trim().length in 3..1000 && parsedWeight!=null && MobileValidation.phone(contact)!=null && date!=null && vehicle!=null && vehicleFits && notes.length<=2000 && pickupLat!=null && pickupLng!=null && dropoffLat!=null && dropoffLng!=null
    if(!started) {
        Icon(Icons.Outlined.LocalShipping,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))
        Text("Plan your next delivery",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        SupportingText("Set your route and schedule, add your cargo, then review your request.")
        Action("New shipment",!state.busy) {started=true}
        SupportingText("The office will prepare your quotation and confirm availability.")
        return
    }
    val routeValid=pickup.trim().length in 5..500 && dropoff.trim().length in 5..500 && !pickup.trim().equals(dropoff.trim(),true) && pickupLat!=null && pickupLng!=null && dropoffLat!=null && dropoffLng!=null && date!=null
    val cargoValid=cargo.trim().length in 3..1000 && parsedWeight!=null && vehicle!=null && vehicleFits && MobileValidation.phone(contact)!=null && notes.length<=2000
    BackHandler(!state.busy && !review) {if(step>1) step-- else started=false}
    Text("New shipment",modifier=Modifier.bringIntoViewRequester(stepHeading),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    SupportingText("Step $step of 3 · ${listOf("Route & schedule","Cargo & vehicle","Review request")[step-1]}")
    LinearProgressIndicator(progress={step/3f},modifier=Modifier.fillMaxWidth())
    if(step==1) {
    Text("Route & schedule",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
    SupportingText("Place pickup and destination pins on the map, then choose your departure in Philippine time.")
    BookingRouteMap(pickup,dropoff,
        pickupLat?.let {lat->pickupLng?.let {lng->org.maplibre.android.geometry.LatLng(lat,lng)}},
        dropoffLat?.let {lat->dropoffLng?.let {lng->org.maplibre.android.geometry.LatLng(lat,lng)}},
        enabled=!state.busy,
        onPickupText={pickup=it},onDestinationText={dropoff=it},
        onPickup={pickup=it.label;pickupLat=it.latitude;pickupLng=it.longitude},
        onDestination={dropoff=it.label;dropoffLat=it.latitude;dropoffLng=it.longitude},routeLoader=vm::previewRoute)
    BookingSchedule(departure,!state.busy) {departure=it}
    if(!routeValid) SupportingText("Place both map pins and choose a future departure to continue.")
    Action("Continue to cargo",routeValid && !state.busy) {step=2}
    }
    if(step==2) {
    Text("Cargo & vehicle",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
    SupportingText("Describe your cargo and select a vehicle. Capacity is checked before dispatch.")
    Field("Cargo description and handling requirements",cargo,{cargo=it})
    SupportingText("Special handling")
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("None","Fragile","Keep Dry","Temperature Sensitive","Perishable","Cold Chain (Refrigerated)").forEach { h->FilterChip(selected=handling==h,onClick={handling=h},label={Text(h)}) }
    }
    Field("Weight in kg",weight,{weight=it;if(vehicle!=null && !MobileBookingRules.fits(it,vehicle.value("default_capacity_kg")))vehicleId=""})
    Text("Vehicle type",fontWeight=FontWeight.Bold)
    SupportingText(if(parsedWeight==null)"Enter cargo weight first to see suitable vehicle types." else "Showing types with capacity from $weight to ${parsedWeight+2000} kg, matching the website.")
    if(data.rows("vehicles").isEmpty()) Text("No vehicle types are available. Contact the office.")
    data.rows("vehicles").forEach {v ->
        val fits=MobileBookingRules.fits(weight,v.value("default_capacity_kg"))
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=vehicleId==v.value("id"),enabled=!state.busy && fits,onClick={vehicleId=v.value("id")})) {
            RadioButton(selected=vehicleId==v.value("id"),onClick=null,enabled=!state.busy && fits)
            Column(Modifier.padding(top=10.dp)) {
                Text(v.value("name"),color=if(fits)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                SupportingText("Capacity: ${v.value("default_capacity_kg")} kg${if(parsedWeight!=null && !fits)" · outside this weight range" else ""}")
            }
        }
    }
    if(parsedWeight!=null && data.rows("vehicles").none {MobileBookingRules.fits(weight,it.value("default_capacity_kg"))}) SupportingText("No vehicle type matches this weight range. Contact the office for help.")

    Field("Contact number (09… or +639…)",contact,{contact=it})
    Field("Notes (optional)",notes,{notes=it})
    if(!cargoValid) SupportingText("Add your cargo description, valid weight, vehicle type and Philippine mobile number to continue.")
    Action("Review request",routeValid && cargoValid && !state.busy) {step=3}
    TextButton(onClick={step=1},enabled=!state.busy) {Text("Back to route")}
    }
    if(step==3) {
        Text("Review request",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        SupportingText("Check your details before sending them to the office.")
        Text("ROUTE & SCHEDULE",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text("Pickup",fontWeight=FontWeight.SemiBold);Text(pickup)
        Text("Destination",fontWeight=FontWeight.SemiBold);Text(dropoff)
        Text("Departure: $departure · Philippine time")
        TextButton(onClick={step=1},enabled=!state.busy) {Text("Edit route & schedule")}
        HorizontalDivider()
        Text("CARGO & VEHICLE",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text(cargo);Text("${vehicle?.value("name")} · $weight kg")
        Text("Special handling: $handling")
        Text("Contact: ${MobileValidation.phone(contact)}")
        if(notes.isNotBlank()) Text("Notes: $notes")
        TextButton(onClick={step=2},enabled=!state.busy) {Text("Edit cargo & vehicle")}
        HorizontalDivider()
        Text("Awaiting office quotation",fontWeight=FontWeight.SemiBold)
        SupportingText("Submitting creates a pending request. The office confirms the road route, price and dispatch availability.")
        if(!valid) SupportingText("Check your route, future departure and cargo details before submitting.")
        Action("Send request",valid && !state.busy && !state.offline) {review=true}
        if(state.offline) SupportingText("Reconnect before submitting. Your current details are kept here.")
        TextButton(onClick={step=2},enabled=!state.busy) {Text("Back to cargo")}
    }
    if(review) AlertDialog(onDismissRequest={if(!state.busy)review=false},title={Text("Submit delivery request?")},text={
        Text("$pickup → $dropoff\n${vehicle?.value("name")} · $weight kg\nDeparture: $departure Philippine time\nContact: ${MobileValidation.phone(contact)}\n\nOffice review and quotation are required.")
    },confirmButton={TextButton(enabled=valid && !state.busy,onClick={
        val payload=buildJsonObject {put("pickup",pickup.trim());put("dropoff",dropoff.trim());put("cargo",cargo.trim());put("weight",parsedWeight);put("vehicle_id",vehicleId);put("contact",MobileValidation.phone(contact));put("departure",date);put("notes",notes.trim());put("pickup_lat",pickupLat);put("pickup_lng",pickupLng);put("dropoff_lat",dropoffLat);put("dropoff_lng",dropoffLng);put("handling",handling)}
        vm.book(requestId,payload) { id -> review=false;started=false;step=1;vehicleId="";handling="None";requestId=UUID.randomUUID().toString();pickup="";dropoff="";cargo="";weight="";notes="";departure="";pickupLat=null;pickupLng=null;dropoffLat=null;dropoffLng=null;onSaved(id) }
    }) {Text(if(state.busy) "Submitting…" else "Submit request")}},dismissButton={TextButton(enabled=!state.busy,onClick={review=false}){Text("Back")}})
}

@Composable private fun ShipmentList(rows: List<JsonObject>,open:(String)->Unit) {
    if(rows.isEmpty()) Text("No shipments in this section.")
    rows.forEach { s ->
        OutlinedCard(onClick={open(s.value("id"))},modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(s.value("tracking_number"),fontWeight=FontWeight.Bold)
                StatusBadge(s.value("status"))
                Text("${s.value("pickup_address")} → ${s.value("dropoff_address")}")
                SupportingText(MobilePresentation.time(s.value("created_at")))
            }
        }
    }
}

@Composable private fun ShipmentDetails(s:JsonObject,data:JsonObject,role:String,state:MobileState,vm:MobileWorkspaceViewModel) {
    val busy=state.busy
    LaunchedEffect(s.value("id")) { vm.loadDetails(s.value("id")) }
    Text(s.value("tracking_number"),style=MaterialTheme.typography.headlineSmall)
    listOf("status" to "Status","pickup_address" to "Pickup","dropoff_address" to "Destination","cargo_description" to "Cargo","weight_kg" to "Weight (kg)","scheduled_departure" to "Scheduled departure","estimated_arrival" to "Estimated arrival","driver_notes" to "Notes","cancelled_reason" to "Cancellation reason").forEach { (key,label) ->
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
    TextButton(onClick={vm.loadDetails(s.value("id"))},enabled=!state.detailLoading) {Text("Refresh timeline and proof")}
    val detail=state.details?.takeIf { it.value("shipment_id")==s.value("id") }
    if(state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(detail!=null) {
        Text("Recorded timeline",style=MaterialTheme.typography.titleMedium)
        val timeline=(detail.rows("events").map {it.value("occurred_at") to it.value("event_type").replace('_',' ')}+
            detail.rows("history").map {it.value("created_at") to (it.value("to_status").replace('_',' ')+it.value("note").takeIf(String::isNotBlank)?.let {note->" · $note"}.orEmpty())}).sortedBy {it.first}
        if(timeline.isEmpty()) Text("No status events have been recorded.")
        timeline.forEach { (time,label)->Text("${MobilePresentation.time(time)}\n${MobilePresentation.status(label)}") }
        val position=detail.obj("position")
        if(position.value("recorded_at").isNotBlank()) Text("Last recorded position: ${position.value("latitude")}, ${position.value("longitude")}\n${MobilePresentation.time(position.value("recorded_at"))} · accuracy ${position.value("accuracy_m").ifBlank{"unknown"}} m")
        val points=mutableListOf<Pair<String,org.maplibre.android.geometry.LatLng>>()
        fun point(label:String,lat:String,lng:String) {val a=lat.toDoubleOrNull();val b=lng.toDoubleOrNull();if(a!=null && b!=null)points.add(label to org.maplibre.android.geometry.LatLng(a,b))}
        point("Pickup",s.value("pickup_lat"),s.value("pickup_lng"));point("Destination",s.value("dropoff_lat"),s.value("dropoff_lng"));point("Last recorded vehicle position",position.value("latitude"),position.value("longitude"))
        if(points.isNotEmpty()) ShipmentMap(points,s["route_geometry"]?.toString())
        if(position.value("recorded_at").isNotBlank()) SupportingText(if(runCatching {java.time.Instant.parse(position.value("recorded_at")).isBefore(java.time.Instant.now().minusSeconds(300))}.getOrDefault(true))"Stale location · more than five minutes old" else "Recent GPS · check the recorded time")
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
    var name by rememberSaveable(p.value("full_name")){mutableStateOf(p.value("full_name"))}
    var phone by rememberSaveable(p.value("phone")){mutableStateOf(p.value("phone"))}
    var address by rememberSaveable(c.value("address")){mutableStateOf(c.value("address"))}
    var city by rememberSaveable(c.value("city")){mutableStateOf(c.value("city"))}
    Text("Your profile",style=MaterialTheme.typography.headlineSmall)
    Text(if(p.value("email").isNotBlank()) "Sign-in email: ${p.value("email")}" else "Phone-number account")
    Field("Full name",name,{name=it})
    Field("Phone",phone,{phone=it})
    if(p.value("role")=="customer") { Field("Address",address,{address=it});Field("City",city,{city=it}) }
    Action("Save profile",!state.busy && name.trim().length in 2..200 && MobileValidation.phone(phone)!=null && address.length<=500 && city.length<=100){vm.profile(name,phone,address,city)}
    if(state.offline)Text("Saved SMS status may be out of date. Reconnect and refresh.")
    Text("Shipment SMS: ${RecoveryPolicy.sms(data, "shipment_status")}")
    Text("Delay SMS: ${RecoveryPolicy.sms(data, "delay")}")
    Text("SMS updates are optional and go only to your verified signup number. Keep that number as your contact number.")
    Action("Enable shipment SMS",!state.busy){vm.smsPreference(true)}
    Action("Disable shipment SMS",!state.busy){vm.smsPreference(false)}
}

@Composable private fun Notices(state:MobileState) {
    if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(10.dp)) { Text(it,Modifier.padding(14.dp),color=MaterialTheme.colorScheme.onErrorContainer) } }
    state.message?.let { Surface(color=MaterialTheme.colorScheme.primary.copy(alpha=.08f),shape=RoundedCornerShape(10.dp)) { Text(it,Modifier.padding(14.dp),color=MaterialTheme.colorScheme.primary) } }
}
@Composable private fun Field(label:String,value:String,change:(String)->Unit,secret:Boolean=false) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),singleLine=true,shape=RoundedCornerShape(8.dp),
        visualTransformation=if(secret)PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
@Composable private fun Action(label:String,enabled:Boolean,onClick:()->Unit) { Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label)} }
@Composable private fun FormPage(title:String,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxSize()) { Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Text(title,style=MaterialTheme.typography.headlineMedium);content()} }
}
@Composable private fun InfoDialog(title:String,text:String,close:()->Unit) {AlertDialog(onDismissRequest=close,title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=close){Text("Close")}})}
