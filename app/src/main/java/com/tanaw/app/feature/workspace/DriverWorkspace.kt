package com.tanaw.app.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalUriHandler
import com.tanaw.app.R
import com.tanaw.app.feature.driver.components.AllowanceRow
import com.tanaw.app.feature.driver.components.AddTripExpenseSheet
import kotlinx.serialization.json.*
import java.time.*
import java.util.UUID

/** Figma driver interface. This theme and navigation apply only to authenticated drivers. */
internal val DriverNavy=Color(0xFF002D54)
internal val DriverBackground=Color(0xFFF9F9FF)
internal val DriverMuted=Color(0xFF43474E)
internal val DriverLine=Color(0xFFE7EEFF)
private val DriverFont=FontFamily(listOf(400,500,600,700,800).map {weight->
    Font(R.font.manrope,weight=FontWeight(weight),variationSettings=FontVariation.Settings(FontVariation.weight(weight)))
})

@Composable internal fun DriverTheme(content:@Composable ()->Unit) {
    val base=MaterialTheme.typography
    MaterialTheme(colorScheme=lightColorScheme(primary=DriverNavy,onPrimary=Color.White,
        background=DriverBackground,surface=Color.White,onSurface=Color(0xFF111C2D),onSurfaceVariant=DriverMuted,
        outline=Color(0xFFC3C6CF),outlineVariant=DriverLine,error=Color(0xFFBA1A1A)),
        typography=base.copy(
            bodyLarge=base.bodyLarge.copy(fontFamily=DriverFont,letterSpacing=0.sp),
            bodyMedium=base.bodyMedium.copy(fontFamily=DriverFont,letterSpacing=0.sp),
            bodySmall=base.bodySmall.copy(fontFamily=DriverFont,letterSpacing=0.sp),
            titleLarge=base.titleLarge.copy(fontFamily=DriverFont,fontWeight=FontWeight.Bold),
            titleMedium=base.titleMedium.copy(fontFamily=DriverFont,fontWeight=FontWeight.Bold),
            headlineSmall=base.headlineSmall.copy(fontFamily=DriverFont,fontWeight=FontWeight.Bold),
            headlineMedium=base.headlineMedium.copy(fontFamily=DriverFont,fontWeight=FontWeight.Bold),
            labelLarge=base.labelLarge.copy(fontFamily=DriverFont),labelMedium=base.labelMedium.copy(fontFamily=DriverFont),
            labelSmall=base.labelSmall.copy(fontFamily=DriverFont)),content=content)
}

@Composable internal fun DriverAsset(id:Int,width:Int,height:Int=width,description:String?=null,tint:Color?=null) {
    Image(painterResource(id),description,Modifier.size(width.dp,height.dp),colorFilter=tint?.let {ColorFilter.tint(it)})
}

@Composable fun DriverWorkspace(vm:MobileWorkspaceViewModel,state:MobileState,data:JsonObject) {
    DriverTheme {
        DriverWorkspaceScreen(state,data,vm::refresh,vm::logout,{vm.page("recover")},vm::markRead,vm::loadDetails,
            {s,d,home->DriverTripActions(d,state,data,vm,reference=s.value("tracking_number"),onBackHome=home)},
            {shipment,document->vm.viewProof(shipment,document)},
            {delivery,request,category,amount,receipt,close->vm.addExpense(delivery,request,category,amount,receipt,close)},
            {period,more->vm.loadHistory(period,more)},
            {delivery,request,entry,close->vm.logFuel(delivery,request,entry,close)},vm::clearOfflineWorkspace,vm::viewEvidence,
            {d->key(data.obj("profile").value("id"),d.value("id"),d.value("driver_id")) {
                DriverConversation(d.value("id"),state.offline,if(state.offline)null else state.conversationUnread?.let{ConversationRules.unread(it.filterIsInstance<JsonObject>(),d.value("id"))},
                    {before->vm.conversation(d.value("id"),before)},
                    {request,body->vm.sendConversation(d.value("id"),request,body,d.value("driver_id"))},
                    {ids->vm.readConversation(d.value("id"),ids)},vm::refresh,d.value("driver_id"),d.value("inbox_open")=="true",
                    d.value("tracking_number").ifBlank {DriverRecordPresentation.shipments(data,state.history).firstOrNull {it.value("id")==d.value("shipment_id")}?.value("tracking_number").orEmpty()})
            }},vm::previewDriverRoute)
        state.proof?.let {bitmap->Dialog(onDismissRequest=vm::closeProof) {
            Surface(shape=RoundedCornerShape(4.dp)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(state.proofTitle,style=MaterialTheme.typography.titleMedium)
                Image(bitmap.asImageBitmap(),"Recorded delivery proof",Modifier.fillMaxWidth().heightIn(max=480.dp))
                TextButton(onClick=vm::closeProof){Text("Close proof")}
            }}
        }}
    }
}

@Composable internal fun DriverWorkspaceScreen(
    state:MobileState,data:JsonObject,onRefresh:()->Unit,onLogout:()->Unit,onRecovery:()->Unit,
    onRead:(String)->Unit,onDetails:(String)->Unit,
    tripActions:@Composable (JsonObject,JsonObject,()->Unit)->Unit,
    onProof:(String,String)->Unit,
    onExpense:(String,String,String,Double,ByteArray?,()->Unit)->Unit,
    onHistory:((String,Boolean)->Unit)?=null,
    onFuel:((String,String,FuelEntry,()->Unit)->Unit)?=null,
    onClearOffline:(()->Unit)?=null,
    onEvidence:((String,String,String)->Unit)?=null,
    conversation:(@Composable (JsonObject)->Unit)?=null,
    routePreview:(suspend (String,org.maplibre.android.geometry.LatLng,org.maplibre.android.geometry.LatLng)->RoutePreview)?=null
) {
    var tab by rememberSaveable {mutableStateOf("Home")}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var messageThread by rememberSaveable {mutableStateOf<String?>(null)}
    val shipments=DriverRecordPresentation.shipments(data,state.history)
    val active=data.rows("shipments").filter {DriverUiRules.isActive(it.value("status"))}
    val tripData=JsonObject(data+mapOf("driver_deliveries" to JsonArray(DriverRecordPresentation.deliveries(data,state.history))))
    val shipment=shipments.firstOrNull {it.value("id")==selected}
    val delivery=shipment?.let {s->DriverUiRules.delivery(tripData,s.value("id"))}
    var expense by rememberSaveable(delivery?.value("id")){mutableStateOf(false)}
    var fuel by rememberSaveable(delivery?.value("id")){mutableStateOf(false)}
    val request=rememberSaveable(delivery?.value("id"),expense){UUID.randomUUID().toString()}
    LaunchedEffect(shipment?.value("id"),tab) {if(shipment!=null)onDetails(shipment.value("id"))}
    BackHandler(tab!="Home" || selected!=null) {if(tab=="Messages" && messageThread!=null)messageThread=null else if(selected!=null)selected=null else tab="Home"}
    Scaffold(containerColor=DriverBackground,topBar={
        if(tab!="Navigate" || shipment==null) Surface(color=Color.White,shadowElevation=1.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal=25.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                DriverAsset(R.drawable.figma_driver_fac2b,50)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(conversation!=null)TextButton(onClick={tab="Messages";selected=null;messageThread=null}) {Text("Messages (${if(state.offline || state.conversationUnread==null) "?" else state.conversationUnread.filterIsInstance<JsonObject>().sumOf{it.value("unread").toIntOrNull()?:0}.toString()})")}
                    IconButton(onClick={tab="Alerts";selected=null}) {DriverAsset(R.drawable.figma_driver_90918,24,28,"Notifications")}
                }
            }
        }
    },bottomBar={DriverBottomBar(tab,state.busy) {tab=it;selected=null}}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().then(if(tab=="Messages" && messageThread!=null) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {DriverNotice(it,true)}
            state.message?.let {DriverNotice(it)}
            if(state.offline) {
                DriverNotice("Offline · saved ${state.lastSync.ifBlank {"at an unknown time"}}. Records may have changed. Reconnect and refresh before making changes.")
                TextButton(onClick=onRefresh,enabled=!state.busy){Text("Retry connection")}
            }
            if(onClearOffline!=null && tab=="Profile")TextButton(onClick=onClearOffline,enabled=!state.busy){Text("Clear saved offline workspace")}
            data.value("driver_read_error").takeIf(String::isNotBlank)?.let {DriverNotice(it,true)}
            when {
                selected!=null && shipment==null -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Selected trip unavailable")
                    Text("This trip is no longer in your available records. Its assignment or access may have changed. No other trip has been opened.")
                    TextButton(onClick=onRefresh,enabled=!state.busy){Text("Refresh selected trip")}
                    DriverButton("Choose another trip",!state.busy){selected=null;tab="Home"}
                }
                selected!=null && shipment!=null && DriverUiRules.isActive(shipment.value("status")) && delivery?.value("id").isNullOrBlank() -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Selected trip details unavailable")
                    Text("${shipment.value("tracking_number")}: current delivery access could not be confirmed. Refresh before opening navigation or trip actions. No other trip has been opened.")
                    TextButton(onClick=onRefresh,enabled=!state.busy){Text("Refresh selected trip")}
                    DriverButton("Choose another trip",!state.busy){selected=null;tab="Home"}
                }
                selected!=null && tab!="Navigate" && shipment!=null -> {
                    DriverPageHeading("Trip details") {selected=null}
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        if(!DriverUiRules.isActive(shipment.value("status"))) DriverComplete(shipment,delivery,state.details){selected=null;tab="Home"}
                        else {DriverAssignment(shipment,delivery?:buildJsonObject{}){tab="Navigate"};DriverButton("Open navigation",!state.busy){tab="Navigate"}}
                        DriverRecords(shipment,state,onDetails,onProof,onEvidence)
                        delivery?.takeIf{it.value("id").isNotBlank()}?.let {conversation?.invoke(it)}
                    }
                }
                tab=="Home" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverIdentity(data)
                    DriverHomeReadNotices(data,state.busy,onRefresh)
                    if(active.isEmpty())DriverEmpty(data)
                    else {
                        if(active.any {s->val d=DriverUiRules.delivery(data,s.value("id"));d.value("status")=="assigned" && d.value("route_started_at").isBlank()})
                            Surface(color=DriverNavy,shape=RoundedCornerShape(4.dp)) {
                                Row(Modifier.fillMaxWidth().padding(17.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                                    DriverAsset(R.drawable.figma_driver_e588e,17,12);Spacer(Modifier.width(8.dp));Text("New Trip Assigned!",color=Color.White,fontWeight=FontWeight.ExtraBold)
                                }
                            }
                        active.forEach {s->DriverAssignment(s,DriverUiRules.delivery(data,s.value("id"))){selected=s.value("id");tab="Navigate"}}
                    }
                    TextButton(onClick=onRefresh,enabled=!state.busy,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Refresh")}
                }
                tab=="Navigate" && shipment!=null -> {
                    DriverNavigation(shipment,delivery?:buildJsonObject{},state,{s,d->tripActions(s,d){selected=null;tab="Home"}},expense={expense=true},
                        fuel=if(onFuel!=null && delivery?.value("status") in listOf("assigned","loading","in_transit","delivered"))({fuel=true})else null,routePreview=routePreview)
                    Column(Modifier.padding(16.dp)) {DriverRecords(shipment,state,onDetails,onProof,onEvidence)}
                    delivery?.takeIf{it.value("id").isNotBlank()}?.let {Column(Modifier.padding(16.dp)){conversation?.invoke(it)}}
                }
                tab=="Navigate" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Choose a trip to navigate")
                    DriverHomeReadNotices(data,state.busy,onRefresh)
                    if(active.isEmpty())DriverEmpty(data)
                    else active.forEach {s->DriverAssignment(s,DriverUiRules.delivery(data,s.value("id"))){selected=s.value("id")}}
                }
                tab=="Messages" -> Column(Modifier.then(if(messageThread!=null) Modifier.weight(1f) else Modifier).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    val threads=(state.conversationUnread?.filterIsInstance<JsonObject>().orEmpty().map {JsonObject(it+mapOf("id" to JsonPrimitive(it.value("delivery_id"))))}+
                        DriverRecordPresentation.deliveries(data,state.history)).distinctBy{it.value("id")}
                    val opened=threads.firstOrNull {it.value("id")==messageThread}
                    DriverPageHeading(if(messageThread==null) "Messages" else "Office conversation") {if(messageThread!=null)messageThread=null else tab="Home"}
                    if(messageThread!=null) {
                        if(opened==null) {
                            DriverNotice("This conversation is no longer available. Refresh to check your delivery access.",true)
                            TextButton(onClick=onRefresh,enabled=!state.busy){Text("Refresh message inbox")}
                        } else key(opened.value("id"),opened.value("driver_id")) {
                            conversation?.invoke(JsonObject(opened+mapOf("inbox_open" to JsonPrimitive(true))))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Your delivery conversations",style=MaterialTheme.typography.bodyMedium,color=DriverMuted,modifier=Modifier.weight(1f))
                            TextButton(onClick=onRefresh,enabled=!state.busy){Text("Refresh message inbox")}
                        }
                        if(state.offline || state.conversationUnread==null)DriverNotice(if(state.offline) "Reconnect to view current unread counts." else "Unread messages unavailable. Refresh to retry.")
                        if(threads.isEmpty())Surface(color=Color.White,shape=RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("No conversations yet",fontWeight=FontWeight.Bold,color=DriverNavy)
                                Text("Your office conversations will appear here when a delivery is assigned.",color=DriverMuted)
                            }
                        }
                        threads.forEach {d->key(d.value("id"),d.value("driver_id")) {
                            val reference=d.value("tracking_number").ifBlank {shipments.firstOrNull {it.value("id")==d.value("shipment_id")}?.value("tracking_number").orEmpty()}.ifBlank {"Assigned delivery"}
                            val count=if(state.offline || state.conversationUnread==null)null else ConversationRules.unread(state.conversationUnread.filterIsInstance<JsonObject>(),d.value("id"))
                            Surface(onClick={messageThread=d.value("id")},color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,DriverLine),modifier=Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Surface(color=DriverLine,shape=CircleShape) {Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){Text("O",color=DriverNavy,fontWeight=FontWeight.Bold)}}
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text("Office",fontWeight=FontWeight.Bold,color=DriverNavy)
                                        Text("Delivery $reference",style=MaterialTheme.typography.bodySmall,color=DriverMuted)
                                        Text(if(count==null) "Unread unavailable" else if(count>0) "$count unread" else "Open conversation",style=MaterialTheme.typography.labelMedium,color=DriverNavy)
                                    }
                                    Text("›",fontSize=26.sp,color=DriverNavy)
                                }
                            }
                        }}
                    }
                }
                tab=="Trips" -> DriverTrips(data,state,onHistory){selected=it}
                tab=="Profile" -> {
                    TextButton(onClick=onRefresh,enabled=!state.busy){Text("Refresh driver profile")}
                    DriverProfile(data,state.busy,state.offline,onRefresh,onRecovery,onLogout) {tab="Messages";messageThread=null;selected=null}
                }
                tab=="Alerts" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Notifications") {tab="Home"}
                    if(DriverRecordPresentation.section(data,"notifications") in listOf("error","unavailable")) {
                        DriverNotice("Notifications unavailable. Refresh to retry.",true)
                        TextButton(onClick=onRefresh,enabled=!state.busy){Text("Retry notifications")}
                    } else if(data.rows("notifications").isEmpty())Text("No notifications yet.")
                    data.rows("notifications").forEach {n->DriverCard {
                        Text(n.value("title"),fontWeight=FontWeight.Bold);SupportingText(n.value("body"));SupportingText(MobilePresentation.time(n.value("created_at")))
                        if(n.value("read_at").isBlank())TextButton(onClick={onRead(n.value("id"))},enabled=!state.busy){Text("Mark read")}
                        if(shipments.any {it.value("id")==n.value("shipment_id")})TextButton(onClick={selected=n.value("shipment_id");tab="Trips"}){Text("Open trip")}
                    }}
                }
            }
        }
    }
    if(expense && delivery!=null)AddTripExpenseSheet(delivery.value("id"),shipment.value("tracking_number"),state.busy,state.error,
        onDismiss={if(!state.busy)expense=false},onSubmit={category,amount,receipt->onExpense(delivery.value("id"),request,category,amount,receipt){expense=false}},offline=state.offline)
    if(fuel && delivery!=null && onFuel!=null)DriverFuelForm(delivery.value("id"),state,{fuel=false}) {fuelRequest,entry,close->onFuel(delivery.value("id"),fuelRequest,entry,close)}
}

@Composable private fun DriverBottomBar(tab:String,busy:Boolean,onTab:(String)->Unit) {
    Surface(color=Color.White,shadowElevation=1.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min=68.dp).padding(top=8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            listOf(Triple("Home",R.drawable.figma_driver_0699e,18),Triple("Navigate",R.drawable.figma_driver_10a8b,19),
                Triple("Trips",R.drawable.figma_driver_8b002,18),Triple("Profile",R.drawable.figma_driver_dce13,16)).forEach {(name,icon,height)->
                val color=if(tab==name)DriverNavy else DriverMuted
                Column(Modifier.weight(1f).clickable(enabled=!busy){onTab(name)}.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    DriverAsset(icon,if(name=="Trips")18 else 16,height,tint=color)
                    Text(name.uppercase(),color=color,fontSize=10.sp,fontWeight=if(tab==name)FontWeight.ExtraBold else FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun DriverIdentity(data:JsonObject) {
    val driver=data.obj("driver");val vehicle=DriverUiRules.truck(data)
    val status=if(DriverProfileRules.rosterState(data)=="ready")driver.value("status") else ""
    val available=status=="available"
    val color=if(available)Color(0xFF22C55E) else DriverNavy
    Surface(color=Color.White,shadowElevation=1.dp) {Column {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(data.obj("profile").value("full_name").ifBlank {"Driver"},fontSize=18.sp,color=DriverNavy,fontWeight=FontWeight.Bold)
                Text(if(DriverRecordPresentation.section(data,"driver_deliveries") in listOf("error","unavailable") || DriverRecordPresentation.section(data,"driver_trucks") in listOf("error","unavailable"))"Assigned vehicle unavailable"
                    else if(vehicle==null && DriverUiRules.deliveryRows(data).any {it.value("status") in listOf("assigned","loading","in_transit")})"Trip vehicle unavailable"
                    else DriverRecordPresentation.vehicleLabel(data,vehicle),fontSize=14.sp,color=DriverMuted)
            }
            DriverBadge(if(DriverProfileRules.rosterState(data)!="ready")"Unavailable" else if(status.isBlank())"Not recorded" else MobilePresentation.status(status),if(available)Color(0xFF166534)else DriverNavy,if(available)Color(0xFFDCFCE7)else DriverLine)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(color))
    }}
}

@Composable private fun DriverEmpty(data:JsonObject) {
    if(DriverProfileRules.rosterState(data)!="ready" || DriverRecordPresentation.section(data,"shipments") in listOf("error","unavailable") || DriverRecordPresentation.section(data,"driver_deliveries") in listOf("error","unavailable")) {
        return
    }
    Surface(color=Color(0xFFF0F3FF),shape=RoundedCornerShape(4.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min=480.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            DriverAsset(R.drawable.figma_driver_e4aa7,128)
            Spacer(Modifier.height(56.dp));Text("No trips assigned",fontSize=18.sp,fontWeight=FontWeight.ExtraBold,color=DriverNavy)
            Spacer(Modifier.height(8.dp));Text("You'll be notified when a new trip is ready.",color=DriverMuted,fontSize=14.sp,textAlign=TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            if(data.obj("driver").value("status")=="available")DriverBadge("●  You are available for assignments",Color(0xFF15803D),Color(0xFFF0FDF4),uppercase=false)
        }
    }
}

@Composable private fun DriverHomeReadNotices(data:JsonObject,busy:Boolean,refresh:()->Unit) {
    val roster=DriverProfileRules.rosterState(data)
    if(roster!="ready") {
        DriverNotice(if(roster=="empty")"No driver roster link was returned for your account. Ask the office to check your driver account." else "Driver roster unavailable. Refresh to retry.",true)
        TextButton(onClick=refresh,enabled=!busy){Text("Retry driver roster")}
    }
    if(DriverRecordPresentation.section(data,"driver_trucks") in listOf("error","unavailable")) {
        DriverNotice("Assigned vehicle records unavailable. Other loaded records remain visible.",true)
        TextButton(onClick=refresh,enabled=!busy){Text("Retry assigned vehicle")}
    }
    if(DriverRecordPresentation.section(data,"shipments") in listOf("error","unavailable") || DriverRecordPresentation.section(data,"driver_deliveries") in listOf("error","unavailable")) {
        DriverNotice("Trip details unavailable. Refresh to confirm current assignments.",true)
        TextButton(onClick=refresh,enabled=!busy){Text("Retry assignments")}
    }
}

@Composable private fun DriverAssignment(s:JsonObject,d:JsonObject,open:()->Unit) {
    Surface(onClick=open,color=Color.White,shape=RoundedCornerShape(4.dp),border=BorderStroke(1.dp,DriverLine)) {Column {
        Row(Modifier.fillMaxWidth().background(Color(0xFFF7F8FF)).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Trip Assignment",fontSize=18.sp,color=DriverNavy,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            DriverBadge(s.value("tracking_number"),DriverNavy,DriverLine)
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {DriverCaption("CARGO",R.drawable.figma_driver_99491,10,10);Text(s.value("cargo_description").ifBlank {"Not recorded"},fontSize=14.sp);SupportingText(MobilePresentation.number(s.value("weight_kg"),"kg"))}
                s.value("special_handling").takeIf {it.isNotBlank() && it!="false"}?.let {Column(Modifier.weight(1f)) {
                    DriverCaption("SPECIAL",R.drawable.figma_driver_02651,10,8);DriverBadge(if(it=="true")"Special handling" else it,Color(0xFF7D3100),Color(0xFFFFDBCA))
                }}
            }
            DriverRoute(s)
            HorizontalDivider(color=DriverLine)
            DriverMetrics(s)
            AllowanceRow(d.value("driver_allowance").takeIf {it.isNotBlank()}?.let(MobilePresentation::money)?:"Not recorded")
            s.value("driver_notes").takeIf(String::isNotBlank)?.let {notes->
                Row(Modifier.fillMaxWidth().background(DriverBackground).border(1.dp,Color(0xFFDADDE5)).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    DriverAsset(R.drawable.figma_driver_d2476,14,12);Text(notes,fontSize=12.sp,color=DriverMuted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(DriverNavy))
    }}
}

@Composable private fun DriverRoute(s:JsonObject) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        listOf("PICK-UP" to "pickup_address","DESTINATION" to "dropoff_address").forEachIndexed {index,(label,key)->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top=5.dp).size(9.dp).clip(CircleShape).background(if(index==0)DriverNavy else Color(0xFFBA1A1A)))
                Column {
                    Text(label,color=Color(0xFF73777F),fontSize=10.sp,letterSpacing=.5.sp)
                    Text(s.value(key).ifBlank {"Not recorded"},fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    if(index==0)Text(MobilePresentation.time(s.value("scheduled_departure")),fontSize=12.sp,color=DriverMuted)
                }
            }
        }
    }
}

@Composable private fun DriverMetrics(s:JsonObject) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {DriverAsset(R.drawable.figma_driver_2af21,13,8);Text(MobilePresentation.number(s.value("distance_km"),"km"),fontSize=12.sp)}
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {DriverAsset(R.drawable.figma_driver_48ff7,13);Text(MobilePresentation.number(s.value("route_duration_minutes"),"min"),fontSize=12.sp)}
    }
}

@Composable private fun DriverNavigation(s:JsonObject,d:JsonObject,state:MobileState,actions:@Composable (JsonObject,JsonObject)->Unit,expense:()->Unit,fuel:(()->Unit)?=null,routePreview:(suspend (String,org.maplibre.android.geometry.LatLng,org.maplibre.android.geometry.LatLng)->RoutePreview)?=null) {
    DriverNavigateOverview(s,d,state,actions,expense,fuel,routePreview)
}

@Composable internal fun DriverDirectionsButtons(shipment:JsonObject) {
    val handler=LocalUriHandler.current
    var error by remember(shipment.value("id")) {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val trip=DriverDirections.tripUrl(shipment)
        Button(onClick={
            error=null
            try {handler.openUri(requireNotNull(trip))}catch(_:Exception){error="Could not open maps. Check that a browser or maps app is available."}
        },enabled=trip!=null,modifier=Modifier.fillMaxWidth()) {Text("View pickup-to-destination route")}
        listOf(false to "Directions to pickup",true to "Directions to destination").forEach {(destination,label)->
            val url=DriverDirections.url(shipment,destination)
            OutlinedButton(onClick={
                error=null
                try {handler.openUri(requireNotNull(url))}catch(_:Exception){error="Could not open maps. Check that a browser or maps app is available."}
            },enabled=url!=null,modifier=Modifier.fillMaxWidth()) {Text(label)}
        }
        Text("Opens external driving directions. Confirm truck restrictions with the office.",fontSize=11.sp,color=DriverMuted)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}

@Composable private fun DriverTrips(data:JsonObject,state:MobileState,onHistory:((String,Boolean)->Unit)?,open:(String)->Unit) {
    var filter by rememberSaveable {mutableStateOf("All")}
    var period by rememberSaveable {mutableStateOf("This month")}
    var showFilter by remember {mutableStateOf(false)}
    LaunchedEffect(period) {onHistory?.invoke(period,false)}
    val history=state.history?.takeIf {it.value("period")==period}
    val records=if(onHistory==null)data.rows("shipments").filter {DriverUiRules.inPeriod(it,period)} else history?.rows("shipments").orEmpty()
    val recordData=history?:data
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("My Trips",style=MaterialTheme.typography.headlineMedium)
            TextButton(onClick={showFilter=!showFilter}) {DriverAsset(R.drawable.figma_driver_4b41f,18,12,"Filter trips")}
        }
        if(showFilter)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("This month","Last month","All records").forEach {p->FilterChip(selected=period==p,onClick={period=p},label={Text(p)})}
        }
        DriverCard(accent=DriverNavy) {
            DriverCaption(if(period=="This month")"MONTHLY TRIPS" else period.uppercase())
            Text("${history?.value("completed")?:if(onHistory==null)records.count {it.value("status")=="completed"}.toString() else "—"} Completed",fontSize=24.sp,fontWeight=FontWeight.Bold)
            HorizontalDivider(color=DriverLine);DriverCaption("TOTAL ALLOWANCE")
            Text(history?.let(DriverHistory::allowance)?:if(onHistory==null)DriverUiRules.allowanceTotal(data,records)else "Not loaded",fontSize=24.sp,color=DriverNavy,fontWeight=FontWeight.Bold)
            HorizontalDivider(color=DriverLine);DriverCaption("ON-TIME RATE")
            Text(history?.let(DriverHistory::onTime)?:if(onHistory==null)DriverUiRules.onTime(data,records)else "Not loaded",fontSize=24.sp,color=Color(0xFF15803D),fontWeight=FontWeight.Bold)
        }
        Text(if(onHistory==null)"$period · latest 200 permitted shipment records" else "$period · ${records.size} of ${history?.value("total")?:"—"} trips · totals include the full period",fontSize=11.sp,color=DriverMuted)
        if(onHistory!=null) {
            Text("Period uses scheduled departure, or creation date when absent, in Philippine time.",fontSize=11.sp,color=DriverMuted)
            history?.value("as_of")?.let {Text("Updated ${MobilePresentation.time(it)}",fontSize=11.sp,color=DriverMuted)}
            if(state.historyLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.historyError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            TextButton(onClick={onHistory(period,false)},enabled=!state.historyLoading){Text("Refresh trip history")}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            listOf("All","Completed","Ongoing","Cancelled").forEach {f->Column(Modifier.clickable{filter=f}.padding(vertical=8.dp)) {
                Text(f,color=if(filter==f)DriverNavy else DriverMuted,fontWeight=if(filter==f)FontWeight.Bold else FontWeight.Normal)
                if(filter==f)Box(Modifier.padding(top=10.dp).width(50.dp).height(2.dp).background(DriverNavy))
            }}
        }
        val visible=records.filter {DriverUiRules.matches(it.value("status"),filter)}
        if(visible.isEmpty() && !state.historyLoading && state.historyError==null)Text(if(history!=null && records.size<(history.value("total").toIntOrNull()?:0))"No matching trips loaded. Load more to search further." else "No trips in this view.",color=DriverMuted)
        visible.forEach {s->Surface(onClick={open(s.value("id"))},color=Color.White,shape=RoundedCornerShape(4.dp)) {
            Column {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(s.value("tracking_number"),fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));DriverBadge(MobilePresentation.status(s.value("status")))
                    }
                    Text("○  ${s.value("pickup_address")}",fontSize=14.sp);Text("○  ${s.value("dropoff_address")}",fontSize=14.sp,color=DriverMuted)
                    HorizontalDivider(color=DriverLine)
                    Text(MobilePresentation.time(s.value("scheduled_departure")),fontSize=12.sp,color=DriverMuted)
                    Text(DriverUiRules.delivery(recordData,s.value("id")).value("driver_allowance").takeIf(String::isNotBlank)?.let(MobilePresentation::money)?:"Allowance not recorded",fontWeight=FontWeight.Bold,color=DriverNavy)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(if(s.value("status")=="completed")Color(0xFFCCF2D9)else DriverLine))
            }
        }}
        if(onHistory!=null && history!=null && records.size<(history.value("total").toIntOrNull()?:0))
            OutlinedButton(onClick={onHistory(period,true)},enabled=!state.historyLoading,modifier=Modifier.fillMaxWidth()){Text("Load more trips")}
    }
}

@Composable private fun DriverComplete(s:JsonObject,d:JsonObject?,details:JsonObject?,home:()->Unit) {
    Surface(color=DriverNavy,shape=RoundedCornerShape(4.dp)) {Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(color=Color(0xFF16A34A),shape=CircleShape,border=BorderStroke(4.dp,Color.White)) {Box(Modifier.size(80.dp),contentAlignment=Alignment.Center) {DriverAsset(R.drawable.figma_driver_966f6,33,24)}}
        Text(if(s.value("status")=="cancelled")"Trip Cancelled" else if(s.value("status")=="completed")"Delivery Complete!" else "Proof Submitted",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text(s.value("tracking_number"),color=Color.White)
    }}
    DriverCard {DriverCaption("TRIP SUMMARY");DriverRoute(s);HorizontalDivider(color=DriverLine);DriverMetrics(s)}
    DriverCard {
        AllowanceRow(d?.value("driver_allowance")?.takeIf(String::isNotBlank)?.let(MobilePresentation::money)?:"Not recorded")
        Text("Other expenses submitted: ${DriverRecordPresentation.expenseTotal(details,s.value("id"))}")
        Text(if(s.value("status")=="completed")"Contact the office for allowance processing." else "Office review determines final completion.",fontSize=12.sp,color=DriverMuted)
    }
    DriverButton("BACK TO HOME",true,home)
}

@Composable private fun DriverRecords(s:JsonObject,state:MobileState,load:(String)->Unit,proof:(String,String)->Unit,evidence:((String,String,String)->Unit)?) {
    DriverTripSection("Saved trip records","Fuel purchases, expenses and recorded updates for this trip.") {
    TextButton(onClick={load(s.value("id"))},enabled=!state.detailLoading){Text("Refresh trip records")}
    if(state.detailLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
    state.details?.takeIf {it.value("shipment_id")==s.value("id")}?.let {details->
        DriverEvidenceRecords(s.value("id"),details,state.busy || state.offline,evidence)
        details.value("fuel_error").takeIf(String::isNotBlank)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(details.containsKey("fuel_logs")) {
            DriverFuelRecords(details.rows("fuel_logs"))
        }
        DriverRecordedTimeline(details)
        details.rows("documents").forEach {doc->TextButton(onClick={proof(s.value("id"),doc.value("id"))}){Text("View ${MobilePresentation.status(doc.value("kind"))}")}}
    }
    if(state.details?.value("shipment_id")!=s.value("id") && !state.detailLoading)Text("Refresh to view this trip's saved records.",fontSize=12.sp,color=DriverMuted)
    }
}

@Composable internal fun DriverFuelRecords(records:List<JsonObject>) {
    Text("Fuel records",fontSize=16.sp,fontWeight=FontWeight.Bold,color=DriverNavy)
    if(records.isEmpty())Text("No fuel records for this trip.",fontSize=13.sp,color=DriverMuted)
    records.forEach {record->
        Surface(color=Color(0xFFEFF6FF),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,DriverLine)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(record.value("total_cost").toBigDecimalOrNull()?.let{MobilePresentation.money(it.toPlainString())}?:"Amount unavailable",fontSize=22.sp,fontWeight=FontWeight.Bold,color=DriverNavy)
                Text(record.value("station").ifBlank{"Station not recorded"},fontWeight=FontWeight.SemiBold)
                Text("Purchased: ${MobilePresentation.number(record.value("liters"),"L")} · Unit price: ${record.value("price_per_liter").toBigDecimalOrNull()?.let{MobilePresentation.money(it.toPlainString())+"/L"}?:"Unavailable"}",fontSize=13.sp)
                Text("Odometer: ${MobilePresentation.number(record.value("odometer_km"),"km")}",fontSize=13.sp)
                Text("Recorded: ${MobilePresentation.time(record.value("logged_at"))}",fontSize=12.sp,color=DriverMuted)
            }
        }
    }
}

@Composable internal fun DriverButton(label:String,enabled:Boolean=true,click:()->Unit) {
    Button(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(4.dp)) {
        Text(label,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        if(label=="COMPLETE DELIVERY") {Spacer(Modifier.width(8.dp));DriverAsset(R.drawable.figma_driver_dafed,17)}
        if(label=="BACK TO HOME") {Spacer(Modifier.width(8.dp));DriverAsset(R.drawable.figma_driver_43c84,16,18)}
    }
}
@Composable internal fun DriverPageHeading(title:String,back:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().background(Color.White).heightIn(min=64.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(back!=null)IconButton(onClick=back,modifier=Modifier.size(32.dp)){DriverAsset(R.drawable.figma_driver_e6917,16,description="Back")}
        Text(title,fontSize=18.sp,color=DriverNavy,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun DriverCaption(text:String,icon:Int?=null,width:Int=10,height:Int=10) {
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {if(icon!=null)DriverAsset(icon,width,height);Text(text,fontSize=10.sp,color=Color(0xFF536581),letterSpacing=.5.sp)}
}
@Composable private fun DriverBadge(label:String,color:Color=DriverNavy,background:Color=DriverLine,uppercase:Boolean=true) {
    Surface(color=background,shape=RoundedCornerShape(2.dp)) {Text(if(uppercase)label.uppercase()else label,Modifier.padding(8.dp,4.dp),color=color,fontSize=10.sp,fontWeight=FontWeight.SemiBold)}
}
@Composable private fun DriverCard(accent:Color?=null,content:@Composable ColumnScope.()->Unit) {
    Surface(color=Color.White,shape=RoundedCornerShape(4.dp),border=BorderStroke(1.dp,DriverLine)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if(accent!=null)Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}
@Composable private fun DriverNotice(text:String,error:Boolean=false) {
    Text(text,Modifier.fillMaxWidth().background(if(error)Color(0xFFFFEDEA)else DriverLine).padding(12.dp),fontSize=12.sp,color=if(error)Color(0xFFBA1A1A)else DriverMuted)
}

internal object DriverUiRules {
    fun deliveryRows(data:JsonObject):List<JsonObject> = when {
        data.obj("driver_sections").value("driver_deliveries") in listOf("error","unavailable") -> emptyList()
        data["driver_deliveries"] is JsonArray -> data.rows("driver_deliveries")
        data.obj("driver_sections").value("driver_deliveries").isNotBlank() -> emptyList()
        else -> data.rows("deliveries")
    }
    fun truck(data:JsonObject):JsonObject? {
        val deliveries=deliveryRows(data)
        val active=deliveries.filter {it.value("status") in listOf("assigned","loading","in_transit")}.maxByOrNull {it.value("assigned_at")}
        if(active!=null)return data.rows("driver_trucks").firstOrNull {it.value("id")==active.value("truck_id")}
        return data.rows("driver_trucks").firstOrNull {it.value("assigned_driver_id")==data.obj("driver").value("id")}
    }
    fun isActive(status:String)=status !in listOf("completed","cancelled")
    fun delivery(data:JsonObject,id:String):JsonObject=deliveryRows(data)
        .filter {it.value("shipment_id")==id}.maxByOrNull {it.value("assigned_at")}?:buildJsonObject{}
    fun stage(d:JsonObject)=when {
        d.value("status") in listOf("delivered","completed")->5
        d.value("status")=="in_transit"->4
        d.value("status")=="loading"->3
        d.value("pickup_arrived_at").isNotBlank()->2
        d.value("route_started_at").isNotBlank()->1
        else->0
    }
    fun matches(status:String,filter:String)=when(filter){"Completed"->status=="completed";"Cancelled"->status=="cancelled";"Ongoing"->isActive(status)||status=="delivered";else->true}
    fun inPeriod(s:JsonObject,period:String,today:LocalDate=LocalDate.now(ZoneId.of("Asia/Manila"))):Boolean {
        if(period=="All records")return true
        val date=runCatching {Instant.parse(s.value("scheduled_departure").ifBlank{s.value("created_at")}).atZone(ZoneId.of("Asia/Manila")).toLocalDate()}.getOrNull()?:return false
        return YearMonth.from(date)==YearMonth.from(if(period=="Last month")today.minusMonths(1)else today)
    }
    fun allowanceTotal(data:JsonObject,records:List<JsonObject>):String {
        val completed=records.filter {it.value("status")=="completed"}
        val amounts=completed.map {delivery(data,it.value("id")).value("driver_allowance").toBigDecimalOrNull()}
        if(amounts.any {it==null})return "Not fully recorded"
        return MobilePresentation.money(amounts.filterNotNull().fold(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).toString())
    }
    fun onTime(data:JsonObject,records:List<JsonObject>):String {
        val outcomes=records.filter{it.value("status")=="completed"}.mapNotNull {s->runCatching {
            !Instant.parse(delivery(data,s.value("id")).value("arrived_at")).isAfter(Instant.parse(s.value("estimated_arrival")))
        }.getOrNull()}
        return if(outcomes.isEmpty())"Not recorded" else "${outcomes.count {it}*100/outcomes.size}% (${outcomes.size} recorded)"
    }
    fun compliance(doc:JsonObject,today:LocalDate=LocalDate.now(ZoneId.of("Asia/Manila"))):String {
        return DriverProfileRules.compliance(doc,JsonArray(emptyList()),today)
    }
}
