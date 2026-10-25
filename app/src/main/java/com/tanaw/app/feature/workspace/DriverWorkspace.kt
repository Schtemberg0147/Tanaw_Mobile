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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalUriHandler
import com.tanaw.app.R
import com.tanaw.app.feature.driver.components.AllowanceRow
import com.tanaw.app.feature.driver.components.AddTripExpenseSheet
import kotlinx.serialization.json.*
import org.maplibre.android.geometry.LatLng
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Figma Driver's App flow. This theme and shell apply only to authenticated drivers. */
internal val DriverNavy=Color(0xFF002D54)
internal val DriverBackground=Color(0xFFF9F9FF)
internal val DriverMuted=Color(0xFF43474E)
internal val DriverLine=Color(0xFFE7EEFF)
@OptIn(ExperimentalTextApi::class)
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
            {delivery,request,category,amount,receipt,close->vm.addExpense(delivery,request,category,amount,receipt,close)})
        state.proof?.let {bitmap->Dialog(onDismissRequest=vm::closeProof) {
            Surface(shape=RoundedCornerShape(4.dp)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(state.proofTitle,style=MaterialTheme.typography.titleMedium)
                Image(bitmap.asImageBitmap(),"Recorded delivery proof",Modifier.fillMaxWidth().heightIn(max=480.dp))
                TextButton(onClick=vm::closeProof){Text("Close proof")}
            }}
        }}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DriverWorkspaceScreen(
    state:MobileState,data:JsonObject,onRefresh:()->Unit,onLogout:()->Unit,onRecovery:()->Unit,
    onRead:(String)->Unit,onDetails:(String)->Unit,
    tripActions:@Composable (JsonObject,JsonObject,()->Unit)->Unit,
    onProof:(String,String)->Unit,
    onExpense:(String,String,String,Double,ByteArray?,()->Unit)->Unit
) {
    var tab by rememberSaveable {mutableStateOf("Home")}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var info by remember {mutableStateOf<String?>(null)}
    var expense by rememberSaveable {mutableStateOf(false)}
    val request=rememberSaveable(expense){UUID.randomUUID().toString()}
    val shipments=data.rows("shipments")
    val active=shipments.filter {DriverUiRules.isActive(it.value("status"))}
    val shipment=shipments.firstOrNull {it.value("id")==selected} ?: if(tab=="Navigate")active.firstOrNull() else null
    val delivery=shipment?.let {s->DriverUiRules.delivery(data,s.value("id"))}
    LaunchedEffect(shipment?.value("id"),tab) {if(shipment!=null)onDetails(shipment.value("id"))}
    BackHandler(tab!="Home" || selected!=null) {if(selected!=null)selected=null else tab="Home"}
    Scaffold(containerColor=DriverBackground,topBar={
        if(tab!="Navigate" || shipment==null) Surface(color=Color.White,shadowElevation=1.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal=25.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                DriverAsset(R.drawable.figma_driver_fac2b,50)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={tab="Alerts";selected=null}) {DriverAsset(R.drawable.figma_driver_90918,24,28,"Notifications")}
                }
            }
        }
    },bottomBar={DriverBottomBar(tab,state.busy) {tab=it;selected=null}}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {DriverNotice(it,true)}
            state.message?.let {DriverNotice(it)}
            if(state.offline)DriverNotice("Offline · showing saved records. Reconnect and refresh.")
            data.value("driver_read_error").takeIf(String::isNotBlank)?.let {DriverNotice(it,true)}
            when {
                selected!=null && tab!="Navigate" && shipment!=null -> {
                    DriverPageHeading("Trip details") {selected=null}
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        if(!DriverUiRules.isActive(shipment.value("status"))) DriverComplete(shipment,delivery,data){selected=null;tab="Home"}
                        else {DriverAssignment(shipment,delivery?:buildJsonObject{}){tab="Navigate"};DriverButton("Open navigation",!state.busy){tab="Navigate"}}
                        DriverRecords(shipment,state,onDetails,onProof)
                    }
                }
                tab=="Home" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverIdentity(data)
                    if(active.isEmpty())DriverEmpty(data)
                    else {
                        if(active.any {s->DriverUiRules.delivery(data,s.value("id")).value("route_started_at").isBlank()})
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
                    DriverNavigation(shipment,delivery?:buildJsonObject{},state,data,{s,d->tripActions(s,d){selected=null;tab="Home"}}) {expense=true}
                    Column(Modifier.padding(16.dp)) {DriverRecords(shipment,state,onDetails,onProof)}
                }
                tab=="Navigate" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Navigate");DriverEmpty(data)
                }
                tab=="Trips" -> DriverTrips(data){selected=it}
                tab=="Profile" -> DriverProfile(data,onRecovery,onLogout) {info=it}
                tab=="Alerts" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Notifications") {tab="Home"}
                    if(data.rows("notifications").isEmpty())Text("No notifications yet.")
                    data.rows("notifications").forEach {n->DriverCard {
                        Text(n.value("title"),fontWeight=FontWeight.Bold);SupportingText(n.value("body"));SupportingText(MobilePresentation.time(n.value("created_at")))
                        if(n.value("read_at").isBlank())TextButton(onClick={onRead(n.value("id"))},enabled=!state.busy){Text("Mark read")}
                        if(shipments.any {it.value("id")==n.value("shipment_id")})TextButton(onClick={selected=n.value("shipment_id");tab="Trips"}){Text("Open trip")}
                    }}
                }
            }
        }
    }
    if(expense && delivery!=null && shipment!=null)AddTripExpenseSheet(delivery.value("id"),shipment.value("tracking_number"),state.busy,state.error,
        onDismiss={if(!state.busy)expense=false},onSubmit={category,amount,receipt->onExpense(delivery.value("id"),request,category,amount,receipt){expense=false}})
    info?.let {AlertDialog(onDismissRequest={info=null},title={Text("Tanaw")},text={Text(it)},confirmButton={TextButton(onClick={info=null}){Text("Close")}})}
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
    val status=driver.value("status")
    val available=status=="available"
    val color=if(available)Color(0xFF22C55E) else DriverNavy
    Surface(color=Color.White,shadowElevation=1.dp) {Column {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(data.obj("profile").value("full_name").ifBlank {"Driver"},fontSize=18.sp,color=DriverNavy,fontWeight=FontWeight.Bold)
                Text(vehicle?.value("model")?.ifBlank {vehicle.value("plate_number")}?.ifBlank {"No assigned vehicle"}?:"No assigned vehicle",fontSize=14.sp,color=DriverMuted)
            }
            DriverBadge(if(status.isBlank())"Not recorded" else MobilePresentation.status(status),if(available)Color(0xFF166534)else DriverNavy,if(available)Color(0xFFDCFCE7)else DriverLine)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(color))
    }}
}

@Composable private fun DriverEmpty(data:JsonObject) {
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

@Composable private fun DriverNavigation(s:JsonObject,d:JsonObject,state:MobileState,data:JsonObject,actions:@Composable (JsonObject,JsonObject)->Unit,expense:()->Unit) {
    val stage=DriverUiRules.stage(d)
    val heading=listOf("Ready to start route","Heading to Pick-up","Waiting for Customer","Waiting to Load Cargo","Heading to Destination","Delivery submitted")[stage]
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        val points=listOf("Pickup" to ("pickup_lat" to "pickup_lng"),"Destination" to ("dropoff_lat" to "dropoff_lng")).mapNotNull {(label,keys)->
            val lat=s.value(keys.first).toDoubleOrNull();val lng=s.value(keys.second).toDoubleOrNull()
            if(lat!=null && lng!=null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)label to LatLng(lat,lng)else null
        }.toMutableList()
        val position=state.details?.takeIf {it.value("shipment_id")==s.value("id")}?.obj("position")
        val lat=position?.value("latitude")?.toDoubleOrNull();val lng=position?.value("longitude")?.toDoubleOrNull()
        if(lat!=null && lng!=null)points.add("Last recorded vehicle position" to LatLng(lat,lng))
        Surface(Modifier.padding(16.dp).fillMaxWidth(),color=Color(0xFF11213F),shadowElevation=3.dp) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(heading,fontSize=18.sp,color=Color.White,fontWeight=FontWeight.Bold)
            Text(s.value(if(stage>=4)"dropoff_address" else "pickup_address"),fontSize=14.sp,color=Color(0xFFA9C7EF))
            HorizontalDivider(color=Color.White.copy(alpha=.15f))
            Text("Recorded route: ${MobilePresentation.number(s.value("distance_km"),"km")} · ${MobilePresentation.number(s.value("route_duration_minutes"),"min")}",color=Color.White,fontSize=12.sp)
        }}
        if(points.isNotEmpty())ShipmentMap(points,s["route_geometry"]?.toString())
        else DriverNotice("This assignment has no saved map pins or road route. Use address directions below; confirm the exact entrance with the office.")
        if(points.isNotEmpty() && (s["route_geometry"]==null || s["route_geometry"]==JsonNull))
            DriverNotice("No road route was saved for this assignment. The map shows recorded locations; use directions below for routing.")
        DriverDirectionsButtons(s)
    }
    Surface(color=Color.White,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(48.dp).height(4.dp).clip(CircleShape).background(Color(0xFFDFE0E6)))
            Text(s.value("tracking_number"),fontWeight=FontWeight.Bold,color=DriverNavy)
            val labels=listOf("Assigned","Route to Pick-up","Customer at Pick-up","Cargo Loaded","Delivered")
            val dates=listOf("assigned_at","route_started_at","customer_showed_at","departed_at","arrived_at")
            labels.forEachIndexed {index,label->
                val complete=d.value(dates[index]).isNotBlank()
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if(complete)Color(0xFF11213F)else DriverBackground).border(if(index==stage)3.dp else 1.dp,if(index<=stage)DriverNavy else Color(0xFFC3C6CF),CircleShape),contentAlignment=Alignment.Center) {
                        if(complete)DriverAsset(R.drawable.figma_driver_c7a53,10,7)
                    }
                    Column(Modifier.padding(vertical=4.dp)) {
                        Text(if(index==2 && stage==2)"Waiting for the customer at Pick-up" else label,fontSize=14.sp,fontWeight=if(index<=stage)FontWeight.SemiBold else FontWeight.Normal,color=if(index<=stage)Color(0xFF11213F)else Color(0xFF73777F))
                        if(complete)Text(MobilePresentation.time(d.value(dates[index])),fontSize=10.sp,color=DriverMuted)
                    }
                }
            }
            if(d.value("id").isBlank())Text("No delivery assignment is available. Refresh or contact the office.")else actions(s,d)
            if(d.value("status") in listOf("in_transit","delivered"))OutlinedButton(onClick=expense,enabled=!state.busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(4.dp)){Text("Add Trip Expense")}
        }
    }
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

@Composable private fun DriverTrips(data:JsonObject,open:(String)->Unit) {
    var filter by rememberSaveable {mutableStateOf("All")}
    var period by rememberSaveable {mutableStateOf("This month")}
    var showFilter by remember {mutableStateOf(false)}
    val records=data.rows("shipments").filter {DriverUiRules.inPeriod(it,period)}
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
            Text("${records.count {it.value("status")=="completed"}} Completed",fontSize=24.sp,fontWeight=FontWeight.Bold)
            HorizontalDivider(color=DriverLine);DriverCaption("TOTAL ALLOWANCE")
            Text(DriverUiRules.allowanceTotal(data,records),fontSize=24.sp,color=DriverNavy,fontWeight=FontWeight.Bold)
            HorizontalDivider(color=DriverLine);DriverCaption("ON-TIME RATE")
            Text(DriverUiRules.onTime(data,records),fontSize=24.sp,color=Color(0xFF15803D),fontWeight=FontWeight.Bold)
        }
        Text("${period} · latest 200 permitted shipment records",fontSize=11.sp,color=DriverMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            listOf("All","Completed","Ongoing","Cancelled").forEach {f->Column(Modifier.clickable{filter=f}.padding(vertical=8.dp)) {
                Text(f,color=if(filter==f)DriverNavy else DriverMuted,fontWeight=if(filter==f)FontWeight.Bold else FontWeight.Normal)
                if(filter==f)Box(Modifier.padding(top=10.dp).width(50.dp).height(2.dp).background(DriverNavy))
            }}
        }
        val visible=records.filter {DriverUiRules.matches(it.value("status"),filter)}
        if(visible.isEmpty())Text("No trips in this view.",color=DriverMuted)
        visible.forEach {s->Surface(onClick={open(s.value("id"))},color=Color.White,shape=RoundedCornerShape(4.dp)) {
            Column {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(s.value("tracking_number"),fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));DriverBadge(MobilePresentation.status(s.value("status")))
                    }
                    Text("○  ${s.value("pickup_address")}",fontSize=14.sp);Text("○  ${s.value("dropoff_address")}",fontSize=14.sp,color=DriverMuted)
                    HorizontalDivider(color=DriverLine)
                    Text(MobilePresentation.time(s.value("scheduled_departure")),fontSize=12.sp,color=DriverMuted)
                    Text(DriverUiRules.delivery(data,s.value("id")).value("driver_allowance").takeIf(String::isNotBlank)?.let(MobilePresentation::money)?:"Allowance not recorded",fontWeight=FontWeight.Bold,color=DriverNavy)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(if(s.value("status")=="completed")Color(0xFFCCF2D9)else DriverLine))
            }
        }}
    }
}

@Composable private fun DriverProfile(data:JsonObject,recover:()->Unit,logout:()->Unit,info:(String)->Unit) {
    val p=data.obj("profile");val d=data.obj("driver");val truck=DriverUiRules.truck(data)
    Column {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=DriverNavy,shape=RoundedCornerShape(8.dp)) {Text(p.value("full_name").split(' ').filter(String::isNotBlank).take(2).map{it.first()}.joinToString(""),Modifier.padding(16.dp),color=Color.White,fontSize=24.sp)}
            Text(p.value("full_name"),fontSize=24.sp,fontWeight=FontWeight.Bold,color=DriverNavy)
            Text(truck?.let {"${it.value("model")} · ${it.value("plate_number")}"}?:"No assigned vehicle",fontSize=14.sp,color=DriverMuted)
            d.value("date_hired").takeIf(String::isNotBlank)?.let {Text("Driver since $it",fontSize=12.sp,color=Color(0xFF73777F))}
        }
        DriverSectionTitle("Compliance Status",R.drawable.figma_driver_77550)
        val documents=data.rows("compliance")
        if(documents.isEmpty())Text("No compliance documents are available.",Modifier.fillMaxWidth().background(Color.White).padding(16.dp),color=DriverMuted)
        documents.forEach {doc->Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(MobilePresentation.status(doc.value("doc_type")),fontSize=14.sp)
                Text("Expiry: ${doc.value("expiry_date").ifBlank {"Not recorded"}}",fontSize=12.sp,color=DriverMuted)
            }
            val label=DriverUiRules.compliance(doc)
            DriverBadge(label,if(label=="VALID")Color(0xFF166534)else Color(0xFF7D3100),if(label=="VALID")Color(0xFFE5F5E9)else Color(0xFFFFDBCA))
        }}
        DriverNotice("Contact your administrator to update compliance documents.")
        DriverSectionTitle("Personal Information",R.drawable.figma_driver_a35c0)
        Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            listOf("EMAIL" to p.value("email"),"CONTACT" to d.value("contact_number").ifBlank{p.value("phone")},
                "ASSIGNED VEHICLE" to truck?.let {"${it.value("model")} · ${it.value("plate_number")}"}.orEmpty(),"LICENSE NO." to d.value("license_number")).forEach {(label,value)->
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {DriverCaption(label);Text(value.ifBlank {"Not recorded"},fontSize=14.sp)}
            }
        }
        DriverNotice("Contact your administrator to update personal information.")
        Spacer(Modifier.height(16.dp))
        DriverMenu("Change Password",R.drawable.figma_driver_69f3e,recover)
        DriverMenu("Help & Support",R.drawable.figma_driver_991e9) {info("Contact the office with your tracking reference for dispatch, account access, compliance or delivery support.")}
        DriverMenu("Terms of Service",R.drawable.figma_driver_8c779) {info("Contact the office for the current privacy and service terms. Trip location is shared while tracking is enabled; delivery proof is private and reviewed by the office.")}
        OutlinedButton(onClick=logout,modifier=Modifier.fillMaxWidth().padding(top=24.dp,bottom=16.dp).heightIn(min=48.dp),shape=RoundedCornerShape(0.dp),border=BorderStroke(1.dp,Color.Red)) {Text("LOG OUT",color=Color.Red,fontSize=12.sp)}
    }
}

@Composable private fun DriverMenu(label:String,icon:Int,click:()->Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).clickable(onClick=click).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        DriverAsset(icon,if(icon==R.drawable.figma_driver_991e9)20 else 16,if(icon==R.drawable.figma_driver_69f3e)21 else 20)
        Text(label,Modifier.weight(1f),fontSize=14.sp);DriverAsset(R.drawable.figma_driver_3fa0b,7,12)
    }
    HorizontalDivider(color=DriverLine)
}

@Composable private fun DriverComplete(s:JsonObject,d:JsonObject?,data:JsonObject,home:()->Unit) {
    Surface(color=DriverNavy,shape=RoundedCornerShape(4.dp)) {Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(color=Color(0xFF16A34A),shape=CircleShape,border=BorderStroke(4.dp,Color.White)) {Box(Modifier.size(80.dp),contentAlignment=Alignment.Center) {DriverAsset(R.drawable.figma_driver_966f6,33,24)}}
        Text(if(s.value("status")=="cancelled")"Trip Cancelled" else if(s.value("status")=="completed")"Delivery Complete!" else "Proof Submitted",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text(s.value("tracking_number"),color=Color.White)
    }}
    DriverCard {DriverCaption("TRIP SUMMARY");DriverRoute(s);HorizontalDivider(color=DriverLine);DriverMetrics(s)}
    DriverCard {
        AllowanceRow(d?.value("driver_allowance")?.takeIf(String::isNotBlank)?.let(MobilePresentation::money)?:"Not recorded")
        val expenses=data.rows("expenses").filter {it.value("delivery_id")==d?.value("id")}
        Text("Other expenses submitted: ${MobilePresentation.money(expenses.sumOf {it.value("amount").toDoubleOrNull()?:0.0}.toString())}")
        Text(if(s.value("status")=="completed")"Contact the office for allowance processing." else "Office review determines final completion.",fontSize=12.sp,color=DriverMuted)
    }
    DriverButton("BACK TO HOME",true,home)
}

@Composable private fun DriverRecords(s:JsonObject,state:MobileState,load:(String)->Unit,proof:(String,String)->Unit) {
    TextButton(onClick={load(s.value("id"))},enabled=!state.detailLoading){Text("Refresh timeline and proof")}
    if(state.detailLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
    state.details?.takeIf {it.value("shipment_id")==s.value("id")}?.let {details->
        details.rows("history").forEach {Text("${MobilePresentation.status(it.value("status"))} · ${MobilePresentation.time(it.value("created_at"))}",fontSize=12.sp)}
        details.rows("documents").forEach {doc->TextButton(onClick={proof(s.value("id"),doc.value("id"))}){Text("View ${MobilePresentation.status(doc.value("kind"))}")}}
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
@Composable private fun DriverSectionTitle(title:String,icon:Int) {
    Row(Modifier.fillMaxWidth().padding(16.dp,24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {DriverAsset(icon,16);Text(title,color=DriverNavy,fontWeight=FontWeight.Bold)}
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
    fun truck(data:JsonObject):JsonObject? {
        val deliveries=data.rows("driver_deliveries").ifEmpty {data.rows("deliveries")}
        val active=deliveries.filter {it.value("status") in listOf("assigned","loading","in_transit")}.maxByOrNull {it.value("assigned_at")}
        return data.rows("driver_trucks").firstOrNull {it.value("id")==active?.value("truck_id")}
            ?:data.rows("driver_trucks").firstOrNull {it.value("assigned_driver_id")==data.obj("driver").value("id")}
    }
    fun isActive(status:String)=status !in listOf("completed","cancelled")
    fun delivery(data:JsonObject,id:String):JsonObject=(data.rows("driver_deliveries").ifEmpty{data.rows("deliveries")})
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
        if(doc.value("review_status")!="approved")return doc.value("review_status").ifBlank {"Not reviewed"}.replace('_',' ').uppercase()
        val expiry=runCatching{LocalDate.parse(doc.value("expiry_date"))}.getOrNull()?:return "EXPIRY UNKNOWN"
        return when {expiry.isBefore(today)->"EXPIRED";!expiry.isAfter(today.plusDays(60))->"EXPIRING SOON";else->"VALID"}
    }
}
