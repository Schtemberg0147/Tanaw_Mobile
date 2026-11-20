package com.tanaw.app.feature.workspace.driver.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.workspace.driver.components.*
import com.tanaw.app.feature.workspace.driver.dialogs.*
import com.tanaw.app.feature.workspace.driver.rules.*
import com.tanaw.app.feature.workspace.driver.store.*
import com.tanaw.app.feature.workspace.model.RoutePreview
import com.tanaw.app.feature.workspace.rules.*
import com.tanaw.app.feature.workspace.state.*
import com.tanaw.app.feature.workspace.tracking.*
import com.tanaw.app.feature.workspace.viewmodel.*
import com.tanaw.app.ui.components.*
import com.tanaw.app.ui.theme.*
import kotlinx.serialization.json.*
import java.time.*
import java.util.UUID

/** Unified Driver tokens aligned with DESIGN.md and TanawTheme. */
internal val DriverNavy=NavyPrimary

internal val DriverLightColorScheme = LightColorScheme

internal val DriverDarkColorScheme = DarkColorScheme

@Composable internal fun DriverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DriverDarkColorScheme else DriverLightColorScheme
    val extendedColors = if (darkTheme) ExtendedDarkColors else ExtendedLightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable internal fun DriverAsset(id:Int,width:Int,height:Int=width,description:String?=null,tint:Color?=null) {
    Image(painterResource(id),description,Modifier.size(width.dp,height.dp),colorFilter=tint?.let {ColorFilter.tint(it)})
}

@Composable fun DriverWorkspace(vm:MobileWorkspaceViewModel,state:MobileState,data:JsonObject) {
    DriverTheme {
        DriverWorkspaceScreen(state,data,vm::refresh,vm::logout,vm::openPasswordChange,vm::markRead,vm::loadDetails,
            {s,d,home->DriverTripActions(d,state,data,vm,reference=s.value("tracking_number"),onBackHome=home,shipment=s)},
            {shipment,document->vm.viewProof(shipment,document)},
            {delivery,request,category,amount,receipt,close->vm.addExpense(delivery,request,category,amount,receipt,close)},
            {period,more->vm.loadHistory(period,more)},
            {delivery,request,entry,close->vm.logFuel(delivery,request,entry,close)},vm::clearOfflineWorkspace,vm::viewEvidence,
            {d->key(data.obj("profile").value("id"),d.value("id"),d.value("driver_id")) {
                DriverConversation(d.value("id"),state.offline,if(state.offline)null else state.conversationUnread?.let{ConversationRules.unread(it.filterIsInstance<JsonObject>(),d.value("id"))},
                    {before->vm.conversation(d.value("id"),before)},
                    {request,body->vm.sendConversation(d.value("id"),request,body,d.value("driver_id"))},
                    {ids->vm.readConversation(d.value("id"),ids)},vm::refreshConversationUnread,d.value("driver_id"),d.value("inbox_open")=="true",
                    d.value("tracking_number").ifBlank {DriverRecordPresentation.shipments(data,state.history).firstOrNull {it.value("id")==d.value("shipment_id")}?.value("tracking_number").orEmpty()})
            }},vm::previewDriverRoute,
            onLoadNotifications = { more -> vm.loadNotifications(more) })
        state.proof?.let {bitmap->Dialog(onDismissRequest=vm::closeProof) {
            Surface(shape=RoundedCornerShape(16.dp), color=MaterialTheme.colorScheme.surface) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(state.proofTitle,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
                Image(bitmap.asImageBitmap(),"Recorded delivery proof",Modifier.fillMaxWidth().heightIn(max=480.dp))
                TextButton(onClick=vm::closeProof,modifier=Modifier.heightIn(min=48.dp)){Text("Close proof")}
            }}
        }}
    }
}

private fun autoSelectNavigateTripId(active: List<JsonObject>, data: JsonObject): String? {
    if (active.isEmpty()) return null
    val inProgress = active.filter { s ->
        val d = DriverUiRules.delivery(data, s.value("id"))
        d.value("route_started_at").isNotBlank() ||
            d.value("status") in listOf("loading", "in_transit") ||
            DriverUiRules.isCorrectionRequested(d)
    }
    return when {
        inProgress.size == 1 -> inProgress.first().value("id")
        active.size == 1 -> active.first().value("id")
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    routePreview:(suspend (String,org.maplibre.android.geometry.LatLng,org.maplibre.android.geometry.LatLng)->RoutePreview)?=null,
    onLoadNotifications:((Boolean)->Unit)?=null
) {
    var tab by rememberSaveable {mutableStateOf(if(state.passwordChangeOwner!=null)"Profile" else "Home")}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var chooseTripRequested by rememberSaveable {mutableStateOf(false)}
    var navigateChatOpen by rememberSaveable(selected) {mutableStateOf(false)}
    var messageThread by rememberSaveable {mutableStateOf<String?>(null)}
    var profileSubPanelOpen by rememberSaveable {mutableStateOf(false)}
    val shipments=DriverRecordPresentation.shipments(data,state.history)
    val tripData=JsonObject(data+mapOf("driver_deliveries" to JsonArray(DriverRecordPresentation.deliveries(data,state.history))))
    val active=data.rows("shipments").filter {s->DriverUiRules.isActiveAssignment(s,DriverUiRules.delivery(data,s.value("id")))}
    val owner=data.obj("profile").value("id")
    val shipment=shipments.firstOrNull {it.value("id")==selected}
    val delivery=shipment?.let {s->DriverUiRules.delivery(tripData,s.value("id"))}
    var expense by rememberSaveable(delivery?.value("id")){mutableStateOf(false)}
    var fuel by rememberSaveable(delivery?.value("id")){mutableStateOf(false)}
    val request=rememberSaveable(delivery?.value("id"),expense){UUID.randomUUID().toString()}
    LaunchedEffect(tab) {
        if(tab!="Profile") profileSubPanelOpen=false
        if(tab!="Navigate") {
            chooseTripRequested=false
            navigateChatOpen=false
        }
    }
    LaunchedEffect(tab, selected, chooseTripRequested, active) {
        if (tab == "Navigate" && selected == null && !chooseTripRequested) {
            autoSelectNavigateTripId(active, data)?.let { selected = it }
        }
    }
    LaunchedEffect(shipment?.value("id"),tab) {if(shipment!=null)onDetails(shipment.value("id"))}
    BackHandler(tab!="Home" || selected!=null) {
        when {
            tab=="Messages" && messageThread!=null -> messageThread=null
            tab=="Navigate" && selected!=null -> {
                if (active.size > 1) {
                    chooseTripRequested = true
                    selected = null
                } else {
                    selected = null
                    tab = "Home"
                }
            }
            selected!=null -> selected=null
            else -> tab="Home"
        }
    }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
        val unreadCount = if(state.offline || state.conversationUnread==null) null else state.conversationUnread.filterIsInstance<JsonObject>().sumOf{it.value("unread").toIntOrNull()?:0}
        val unreadAlerts = data.rows("notifications").count { it.value("read_at").isBlank() }
        DriverTopAppBar(
            trackingNumber = if(tab=="Navigate") shipment?.value("tracking_number") else null,
            showMessages = conversation!=null,
            unreadMessagesCount = unreadCount,
            unreadAlertsCount = unreadAlerts,
            isBusy = state.busy,
            onSwitchTrip = {
                if (tab == "Navigate" && active.size > 1) {
                    chooseTripRequested = true
                    selected = null
                } else if (tab == "Navigate") {
                    selected = null
                    tab = "Home"
                } else {
                    selected = null
                }
            },
            onOpenMessages = {tab="Messages";selected=null;messageThread=null},
            onOpenAlerts = {tab="Alerts";selected=null},
            onRefresh = onRefresh
        )
    },bottomBar={
        if(!(tab=="Profile" && profileSubPanelOpen)) {
            DriverBottomBar(tab,state.busy) { nextTab ->
                tab = nextTab
                selected = null
                chooseTripRequested = false
            }
        }
    }) {padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().then(if(tab=="Messages" && messageThread!=null) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
            state.error?.let {DriverNotice(it,true)}
            state.message?.let {DriverNotice(it)}
            if(state.offline) {
                DriverNotice("Offline · saved ${state.lastSync.ifBlank {"at an unknown time"}}. Records may have changed. Reconnect and refresh before making changes.")
                TextButton(onClick=onRefresh,enabled=!state.busy){Text("Retry connection")}
            }
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
                    val showComplete=!DriverUiRules.isActive(shipment.value("status")) || delivery?.value("status")=="delivered"
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        if(showComplete) DriverComplete(shipment,delivery,state.details){selected=null;tab="Home"}
                        else {DriverAssignment(shipment,delivery?:buildJsonObject{}){tab="Navigate"};DriverButton("Open navigation",!state.busy){tab="Navigate"}}
                        if(showComplete && delivery?.value("status")=="delivered")tripActions(shipment,delivery){selected=null;tab="Home"}
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
                            Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                                    DriverAsset(R.drawable.figma_driver_e588e,17,12,tint=MaterialTheme.colorScheme.onPrimary);Spacer(Modifier.width(8.dp));Text("New Trip Assigned!",color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.ExtraBold)
                                }
                            }
                        active.forEach {s->DriverAssignment(s,DriverUiRules.delivery(data,s.value("id"))){chooseTripRequested=false;selected=s.value("id");tab="Navigate"}}
                    }
                }
                tab=="Navigate" && shipment!=null -> {
                    val hasConversation = conversation != null && !delivery?.value("id").isNullOrBlank()
                    DriverNavigation(
                        s = shipment,
                        d = delivery ?: buildJsonObject {},
                        state = state,
                        actions = { s, d -> tripActions(s, d) { selected = null; tab = "Home" } },
                        expense = { expense = true },
                        fuel = if(onFuel!=null && delivery?.value("status") in listOf("assigned","loading","in_transit","delivered"))({fuel=true})else null,
                        routePreview = routePreview,
                        onMessageOffice = if (hasConversation) ({ navigateChatOpen = true }) else null,
                        tripLogsContent = { DriverRecords(shipment, state, onDetails, onProof, onEvidence) }
                    )
                }
                tab=="Navigate" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Choose a trip to navigate")
                    DriverHomeReadNotices(data,state.busy,onRefresh)
                    if(active.isEmpty())DriverEmpty(data)
                    else active.forEach {s->DriverAssignment(s,DriverUiRules.delivery(data,s.value("id"))){chooseTripRequested=false;selected=s.value("id")}}
                }
                tab=="Messages" -> Column(Modifier.then(if(messageThread!=null) Modifier.weight(1f) else Modifier).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    val threads=(state.conversationUnread?.filterIsInstance<JsonObject>().orEmpty().map {JsonObject(it+mapOf("id" to JsonPrimitive(it.value("delivery_id"))))}+
                        DriverRecordPresentation.deliveries(data,state.history)).distinctBy{it.value("id")}
                    val opened=threads.firstOrNull {it.value("id")==messageThread}
                    DriverPageHeading(if(messageThread==null) "Messages" else "Office conversation") {if(messageThread!=null)messageThread=null else tab="Home"}
                    if(messageThread!=null) {
                        if(opened==null) {
                            DriverNotice("This conversation is no longer available. Refresh to check your delivery access.",true)
                            TextButton(onClick=onRefresh,enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp)){Text("Retry message inbox")}
                        } else key(opened.value("id"),opened.value("driver_id")) {
                            conversation?.invoke(JsonObject(opened+mapOf("inbox_open" to JsonPrimitive(true))))
                        }
                    } else {
                        Text("Your delivery conversations",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(state.offline || state.conversationUnread==null) {
                            DriverNotice(if(state.offline) "Reconnect to view current unread counts." else "Unread messages unavailable. Refresh to retry.")
                            if (!state.offline) {
                                TextButton(onClick=onRefresh,enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp)){Text("Retry unread messages")}
                            }
                        }
                        if(threads.isEmpty())Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("No conversations yet",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                                Text("Your office conversations will appear here when a delivery is assigned.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        threads.forEach {d->key(d.value("id"),d.value("driver_id")) {
                            val reference=d.value("tracking_number").ifBlank {shipments.firstOrNull {it.value("id")==d.value("shipment_id")}?.value("tracking_number").orEmpty()}.ifBlank {"Assigned delivery"}
                            val count=if(state.offline || state.conversationUnread==null)null else ConversationRules.unread(state.conversationUnread.filterIsInstance<JsonObject>(),d.value("id"))
                            Surface(onClick={messageThread=d.value("id")},color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=CircleShape) {Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){Text("O",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}}
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text("Office",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                                        Text("Delivery $reference",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if(count==null) "Unread unavailable" else if(count>0) "$count unread" else "Open conversation",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                                    }
                                    Text("›",fontSize=26.sp,color=MaterialTheme.colorScheme.primary)
                                }
                            }
                        }}
                    }
                }
                tab=="Trips" -> DriverTrips(data,state,onHistory){selected=it}
                tab=="Profile" -> {
                    DriverProfile(
                        data = data,
                        busy = state.busy,
                        offline = state.offline,
                        refresh = onRefresh,
                        recover = onRecovery,
                        logout = onLogout,
                        messages = {tab="Messages";messageThread=null;selected=null},
                        onClearOffline = onClearOffline,
                        onSubPanelChanged = {profileSubPanelOpen=it}
                    )
                }
                tab=="Alerts" -> Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverPageHeading("Notifications") {tab="Home"}
                    LaunchedEffect(Unit) {
                        if (state.notificationsPage == null && onLoadNotifications != null) {
                            onLoadNotifications(false)
                        }
                    }
                    val displayedNotifications = state.notificationsPage ?: data.rows("notifications")
                    if(state.notificationsLoading && state.notificationsPage == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.notificationsError?.let {
                        DriverNotice(it, true)
                    }
                    if(DriverRecordPresentation.section(data,"notifications") in listOf("error","unavailable") && displayedNotifications.isEmpty()) {
                        DriverNotice("Notifications unavailable. Refresh to retry.",true)
                        TextButton(onClick=onRefresh,enabled=!state.busy){Text("Retry notifications")}
                    } else if(displayedNotifications.isEmpty() && !state.notificationsLoading)Text("No notifications yet.")
                    displayedNotifications.forEach {n->DriverCard {
                        Text(n.value("title"),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface);SupportingText(n.value("body"));SupportingText(MobilePresentation.time(n.value("created_at")))
                        if(n.value("read_at").isBlank())TextButton(onClick={onRead(n.value("id"))},enabled=!state.busy){Text("Mark read")}
                        if(shipments.any {it.value("id")==n.value("shipment_id")})TextButton(onClick={selected=n.value("shipment_id");tab="Trips"}){Text("Open trip")}
                    }}
                    if(state.notificationsHasMore && onLoadNotifications != null) {
                        OutlinedButton(
                            onClick = { onLoadNotifications(true) },
                            enabled = !state.notificationsLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if(state.notificationsLoading) "Loading more notifications..." else "Load more notifications")
                        }
                    }
                }
            }
        }
    }
    if(expense && delivery!=null)AddTripExpenseSheet(delivery.value("id"),shipment.value("tracking_number"),state.busy,state.error,
        onDismiss={if(!state.busy)expense=false},onSubmit={category,amount,receipt->onExpense(delivery.value("id"),request,category,amount,receipt){expense=false}},offline=state.offline,owner=owner)
    if(fuel && delivery!=null && onFuel!=null)DriverFuelForm(delivery.value("id"),state,{fuel=false}) {fuelRequest,entry,close->onFuel(delivery.value("id"),fuelRequest,entry,close)}
    if(navigateChatOpen && tab=="Navigate" && shipment!=null && delivery!=null && !delivery.value("id").isNullOrBlank() && conversation!=null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { navigateChatOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                key(delivery.value("id"), delivery.value("driver_id")) {
                    conversation(JsonObject(delivery + mapOf("inbox_open" to JsonPrimitive(true))))
                }
            }
        }
    }
}

@Composable private fun DriverBottomBar(tab:String,busy:Boolean,onTab:(String)->Unit) {
    Surface(color=MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min=68.dp).padding(horizontal=8.dp,vertical=6.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf(Triple("Home",R.drawable.figma_driver_0699e,18),Triple("Navigate",R.drawable.figma_driver_10a8b,19),
                    Triple("Trips",R.drawable.figma_driver_8b002,18),Triple("Profile",R.drawable.figma_driver_dce13,16)).forEach {(name,icon,height)->
                    val selectedTab = tab==name
                    val color=if(selectedTab)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled=!busy){onTab(name)}.padding(vertical=6.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier=Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if(selectedTab)MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(horizontal=16.dp,vertical=4.dp),
                            contentAlignment=Alignment.Center
                        ) {
                            DriverAsset(icon,if(name=="Trips")18 else 16,height,tint=color)
                        }
                        Text(name.uppercase(),color=color,fontSize=10.sp,fontWeight=if(selectedTab)FontWeight.ExtraBold else FontWeight.SemiBold,maxLines=1,softWrap=false)
                    }
                }
            }
        }
    }
}

@Composable private fun DriverIdentity(data:JsonObject) {
    val driver=data.obj("driver");val vehicle=DriverUiRules.truck(data)
    val status=if(DriverProfileRules.rosterState(data)=="ready")driver.value("status") else ""
    val available=status=="available"
    val extendedColors = TanawTheme.extendedColors
    val barColor=if(available) extendedColors.onSuccessBannerText else MaterialTheme.colorScheme.primary
    val badgeTextColor=if(available) extendedColors.onSuccessBannerText else MaterialTheme.colorScheme.primary
    val badgeBgColor=if(available) extendedColors.successBannerBackground else MaterialTheme.colorScheme.surfaceVariant
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {Column {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(data.obj("profile").value("full_name").ifBlank {"Driver"},style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface)
                Text(if(DriverRecordPresentation.section(data,"driver_deliveries") in listOf("error","unavailable") || DriverRecordPresentation.section(data,"driver_trucks") in listOf("error","unavailable"))"Assigned vehicle unavailable"
                    else if(vehicle==null && DriverUiRules.deliveryRows(data).any {it.value("status") in listOf("assigned","loading","in_transit")})"Trip vehicle unavailable"
                    else DriverRecordPresentation.vehicleLabel(data,vehicle),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DriverBadge(if(DriverProfileRules.rosterState(data)!="ready")"Unavailable" else if(status.isBlank())"Not recorded" else MobilePresentation.status(status),badgeTextColor,badgeBgColor)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(barColor))
    }}
}

@Composable private fun DriverEmpty(data:JsonObject) {
    if(DriverProfileRules.rosterState(data)!="ready" || DriverRecordPresentation.section(data,"shipments") in listOf("error","unavailable") || DriverRecordPresentation.section(data,"driver_deliveries") in listOf("error","unavailable")) {
        return
    }
    val extendedColors = TanawTheme.extendedColors
    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            DriverAsset(R.drawable.figma_driver_e4aa7,120)
            Spacer(Modifier.height(24.dp));Text("No trips assigned",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp));Text("You'll be notified when a new trip is ready.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            if(data.obj("driver").value("status")=="available")DriverBadge("●  You are available for assignments",extendedColors.onSuccessBannerText,extendedColors.successBannerBackground,uppercase=false)
        }
    }
}

@Composable private fun DriverHomeReadNotices(data:JsonObject,busy:Boolean,refresh:()->Unit) {
    val roster=DriverProfileRules.rosterState(data)
    if(roster!="ready") {
        DriverNotice(if(roster=="empty")"No driver roster link was returned for your account. Ask the office to check your driver account." else "Driver roster unavailable. Refresh to retry.",true)
        TextButton(onClick=refresh,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Retry driver roster")}
    }
    if(DriverRecordPresentation.section(data,"driver_trucks") in listOf("error","unavailable")) {
        DriverNotice("Assigned vehicle records unavailable. Other loaded records remain visible.",true)
        TextButton(onClick=refresh,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Retry assigned vehicle")}
    }
    if(DriverRecordPresentation.section(data,"shipments") in listOf("error","unavailable") || DriverRecordPresentation.section(data,"driver_deliveries") in listOf("error","unavailable")) {
        DriverNotice("Trip details unavailable. Refresh to confirm current assignments.",true)
        TextButton(onClick=refresh,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Retry assignments")}
    }
}

@Composable private fun DriverAssignment(s:JsonObject,d:JsonObject,open:()->Unit) {
    Surface(onClick=open,color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {Column {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Trip Assignment",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f,fill=false),maxLines=1,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            DriverBadge(s.value("tracking_number"),MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.surfaceVariant)
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    DriverCaption("CARGO",R.drawable.figma_driver_99491,10,10)
                    Text(s.value("cargo_description").ifBlank {"Not recorded"},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis)
                    SupportingText(MobilePresentation.number(s.value("weight_kg"),"kg"))
                }
                s.value("special_handling").takeIf {it.isNotBlank() && it!="false"}?.let {Column(Modifier.weight(1f)) {
                    DriverCaption("SPECIAL",R.drawable.figma_driver_02651,10,8)
                    DriverBadge(if(it=="true")"Special handling" else it,MaterialTheme.colorScheme.onSecondaryContainer,MaterialTheme.colorScheme.secondaryContainer)
                }}
            }
            DriverRoute(s)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            DriverMetrics(s)
            AllowanceRow(d.value("driver_allowance").takeIf {it.isNotBlank()}?.let(MobilePresentation::money)?:"Not recorded")
            s.value("driver_notes").takeIf(String::isNotBlank)?.let {notes->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    DriverAsset(R.drawable.figma_driver_d2476,14,12,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(notes,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.primary))
    }}
}

@Composable private fun DriverRoute(s:JsonObject) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf("PICK-UP" to "pickup_address","DESTINATION" to "dropoff_address").forEachIndexed {index,(label,key)->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(top=5.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if(index==0)MapPinPickup else MapPinDestination))
                    if(index==0)Box(Modifier.width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Column(Modifier.weight(1f, fill=false)) {
                    Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.value(key).ifBlank {"Not recorded"},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(index==0)Text(MobilePresentation.time(s.value("scheduled_departure")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun DriverMetrics(s:JsonObject) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {DriverAsset(R.drawable.figma_driver_2af21,13,8,tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(MobilePresentation.number(s.value("distance_km"),"km"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)}
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {DriverAsset(R.drawable.figma_driver_48ff7,13,tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(MobilePresentation.number(s.value("route_duration_minutes"),"min"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)}
    }
}

@Composable private fun DriverNavigation(
    s: JsonObject,
    d: JsonObject,
    state: MobileState,
    actions: @Composable (JsonObject, JsonObject) -> Unit,
    expense: () -> Unit,
    fuel: (() -> Unit)? = null,
    routePreview: (suspend (String, org.maplibre.android.geometry.LatLng, org.maplibre.android.geometry.LatLng) -> RoutePreview)? = null,
    onMessageOffice: (() -> Unit)? = null,
    tripLogsContent: (@Composable () -> Unit)? = null
) {
    DriverNavigateOverview(s, d, state, actions, expense, fuel, routePreview, onMessageOffice, tripLogsContent)
}

@Composable internal fun DriverDirectionsButtons(shipment: JsonObject, stage: Int = 0, confirmedGeoJson: String? = null) {
    val handler = LocalUriHandler.current
    var error by remember(shipment.value("id")) { mutableStateOf<String?>(null) }
    var showMoreOptions by rememberSaveable(shipment.value("id")) { mutableStateOf(false) }
    val effectiveGeoJson = confirmedGeoJson ?: shipment["route_geometry"]?.takeUnless { it == kotlinx.serialization.json.JsonNull }?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.contentOrNull else it.toString() }
    val hasWaypoints = remember(effectiveGeoJson) {
        !DriverDirections.corridorWaypointsParam(effectiveGeoJson).isNullOrBlank()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val pickupUrl = DriverDirections.url(shipment, false, effectiveGeoJson)
        val destUrl = DriverDirections.url(shipment, true, effectiveGeoJson)
        val tripUrl = DriverDirections.tripUrl(shipment, effectiveGeoJson)

        val isPickupPrimary = stage < 4
        val primaryLabel = if (isPickupPrimary) "Directions to pickup" else "Directions to destination"
        val primaryUrl = if (isPickupPrimary) pickupUrl else destUrl
        val secondaryLabel = if (isPickupPrimary) "Directions to destination" else "Directions to pickup"
        val secondaryUrl = if (isPickupPrimary) destUrl else pickupUrl

        FilledTonalButton(
            onClick = {
                error = null
                try { handler.openUri(requireNotNull(primaryUrl)) } catch (_: Exception) { error = "Could not open maps. Check that a browser or maps app is available." }
            },
            enabled = primaryUrl != null,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(primaryLabel) }

        TextButton(
            onClick = { showMoreOptions = !showMoreOptions },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(if (showMoreOptions) "Hide route options" else "More route options")
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (showMoreOptions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }

        if (showMoreOptions) {
            OutlinedButton(
                onClick = {
                    error = null
                    try { handler.openUri(requireNotNull(secondaryUrl)) } catch (_: Exception) { error = "Could not open maps. Check that a browser or maps app is available." }
                },
                enabled = secondaryUrl != null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(secondaryLabel) }

            OutlinedButton(
                onClick = {
                    error = null
                    try { handler.openUri(requireNotNull(tripUrl)) } catch (_: Exception) { error = "Could not open maps. Check that a browser or maps app is available." }
                },
                enabled = tripUrl != null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("View pickup-to-destination route") }
        }

        Text("Opens external driving directions. Confirm truck restrictions with the office.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hasWaypoints) {
            Text("Includes office-confirmed HGV corridor waypoints.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stage >= 4) {
                Text("If resuming navigation midway, remove past waypoints in Google Maps to avoid backtracking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun DriverTrips(data:JsonObject,state:MobileState,onHistory:((String,Boolean)->Unit)?,open:(String)->Unit) {
    var filter by rememberSaveable {mutableStateOf("All")}
    var period by rememberSaveable {mutableStateOf("This month")}
    var showFilter by remember {mutableStateOf(false)}
    val extendedColors = TanawTheme.extendedColors
    LaunchedEffect(period) {onHistory?.invoke(period,false)}
    val history=DriverHistory.reconcileWithLive(data,state.history,period)
    val recordData=JsonObject((history?:data)+mapOf("driver_deliveries" to JsonArray(DriverRecordPresentation.deliveries(data,history?:state.history))))
    val records=if(onHistory==null)data.rows("shipments").filter {DriverUiRules.inPeriod(it,period)} else history?.rows("shipments").orEmpty()
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("My Trips",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.onSurface)
            IconButton(onClick={showFilter=!showFilter},modifier=Modifier.size(48.dp)) {
                DriverAsset(R.drawable.figma_driver_4b41f,18,12,"Filter trips",tint=MaterialTheme.colorScheme.onSurface)
            }
        }
        if(showFilter)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("This month","Last month","All records").forEach {p->
                FilterChip(
                    selected=period==p,
                    onClick={period=p},
                    label={Text(p)},
                    shape=RoundedCornerShape(10.dp)
                )
            }
        }
        DriverCard(accent=MaterialTheme.colorScheme.primary) {
            DriverCaption(if(period=="This month")"MONTHLY TRIPS" else period.uppercase())
            Text("${history?.value("completed")?:if(onHistory==null)records.count {DriverUiRules.isDriverCompleted(it,DriverUiRules.delivery(recordData,it.value("id")))}.toString() else "—"} Completed",fontSize=24.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant);DriverCaption("TOTAL ALLOWANCE")
            Text(history?.let(DriverHistory::allowance)?:if(onHistory==null)DriverUiRules.allowanceTotal(recordData,records)else "Not loaded",fontSize=24.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant);DriverCaption("ON-TIME RATE")
            Text(history?.let(DriverHistory::onTime)?:if(onHistory==null)DriverUiRules.onTime(recordData,records)else "Not loaded",fontSize=24.sp,color=extendedColors.onSuccessBannerText,fontWeight=FontWeight.Bold)
        }
        Text(if(onHistory==null)"$period · latest 200 permitted shipment records" else "$period · ${records.size} of ${history?.value("total")?:"—"} trips · totals include the full period",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(onHistory!=null) {
            Text("Period uses scheduled departure, or creation date when absent, in Philippine time.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            history?.value("as_of")?.let {Text("Updated ${MobilePresentation.time(it)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(state.historyLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.historyError?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
            if(state.historyError != null || (history == null && !state.historyLoading)) {
                TextButton(onClick={onHistory(period,false)},enabled=!state.historyLoading,modifier=Modifier.heightIn(min=48.dp)){Text("Retry trip history")}
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("All","Completed","Ongoing","Cancelled").forEach {f->
                FilterChip(
                    selected=filter==f,
                    onClick={filter=f},
                    label={Text(f)},
                    shape=RoundedCornerShape(10.dp)
                )
            }
        }
        val visible=records.filter {s->DriverUiRules.matches(s,DriverUiRules.delivery(recordData,s.value("id")),filter)}
        if(visible.isEmpty() && !state.historyLoading && state.historyError==null)Text(if(history!=null && records.size<(history.value("total").toIntOrNull()?:0))"No matching trips loaded. Load more to search further." else "No trips in this view.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        visible.forEach {s->
            val d=DriverUiRules.delivery(recordData,s.value("id"))
            Surface(onClick={open(s.value("id"))},color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(s.value("tracking_number"),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f,fill=false),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        DriverBadge(DriverUiRules.tripStatusLabel(s,d))
                    }
                    Text("○  ${s.value("pickup_address")}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text("○  ${s.value("dropoff_address")}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Text(MobilePresentation.time(s.value("scheduled_departure")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.value("driver_allowance").takeIf(String::isNotBlank)?.let(MobilePresentation::money)?:"Allowance not recorded",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(if(DriverUiRules.isDriverCompleted(s,d)) extendedColors.onSuccessBannerText else MaterialTheme.colorScheme.outlineVariant))
            }
        }}
        if(onHistory!=null && history!=null && records.size<(history.value("total").toIntOrNull()?:0))
            OutlinedButton(onClick={onHistory(period,true)},enabled=!state.historyLoading,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Load more trips")}
    }
}

@Composable private fun DriverComplete(s:JsonObject,d:JsonObject?,details:JsonObject?,home:()->Unit) {
    val isDark = isSystemInDarkTheme()
    val heroTextColor = if(isDark) MaterialTheme.colorScheme.onSurface else Color.White
    Surface(
        color=if(isDark) MaterialTheme.colorScheme.surfaceContainerHigh else DriverNavy,
        shape=RoundedCornerShape(16.dp),
        border=if(isDark) BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant) else null
    ) {Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(color=MapPinVehicle,shape=CircleShape,border=BorderStroke(4.dp,Color.White)) {Box(Modifier.size(80.dp),contentAlignment=Alignment.Center) {DriverAsset(R.drawable.figma_driver_966f6,33,24,tint=Color.White)}}
        Text(if(s.value("status")=="cancelled")"Trip Cancelled" else if(s.value("status")=="completed")"Delivery Complete!" else "Proof Submitted",color=heroTextColor,style=MaterialTheme.typography.headlineMedium)
        Text(s.value("tracking_number"),color=heroTextColor,style=MaterialTheme.typography.titleMedium)
    }}
    DriverCard {DriverCaption("TRIP SUMMARY");DriverRoute(s);HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant);DriverMetrics(s)}
    DriverCard {
        AllowanceRow(d?.value("driver_allowance")?.takeIf(String::isNotBlank)?.let(MobilePresentation::money)?:"Not recorded")
        Text("Other expenses submitted: ${DriverRecordPresentation.expenseTotal(details,s.value("id"))}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
        Text(if(s.value("status")=="completed")"Contact the office for allowance processing." else "Office review determines final completion.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    DriverButton("BACK TO HOME",true,home)
}

@Composable private fun DriverRecords(s:JsonObject,state:MobileState,load:(String)->Unit,proof:(String,String)->Unit,evidence:((String,String,String)->Unit)?) {
    DriverTripSection("Saved trip records","Fuel purchases, expenses and recorded updates for this trip.") {
    if(state.detailLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
    val matchingDetails = state.details?.takeIf {it.value("shipment_id")==s.value("id")}
    matchingDetails?.let {details->
        DriverEvidenceRecords(s.value("id"),details,state.busy || state.offline,evidence)
        details.value("fuel_error").takeIf(String::isNotBlank)?.let {
            Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={load(s.value("id"))},enabled=!state.detailLoading,modifier=Modifier.heightIn(min=48.dp)){Text("Retry trip records")}
        }
        if(details.containsKey("fuel_logs")) {
            DriverFuelRecords(details.rows("fuel_logs"))
        }
        DriverRecordedTimeline(details)
        details.rows("documents").forEach {doc->TextButton(onClick={proof(s.value("id"),doc.value("id"))},modifier=Modifier.heightIn(min=48.dp)){Text("View ${MobilePresentation.status(doc.value("kind"))}")}}
    }
    if(matchingDetails == null && !state.detailLoading) {
        Text("Trip records not yet loaded.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={load(s.value("id"))},enabled=!state.detailLoading,modifier=Modifier.heightIn(min=48.dp)){Text("Retry trip records")}
    }
    }
}

@Composable internal fun DriverFuelRecords(records:List<JsonObject>) {
    Text("Fuel records",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
    if(records.isEmpty())Text("No fuel records for this trip.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    records.forEach {record->
        Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(record.value("total_cost").toBigDecimalOrNull()?.let{MobilePresentation.money(it.toPlainString())}?:"Amount unavailable",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                Text(record.value("station").ifBlank{"Station not recorded"},style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurface)
                Text("Purchased: ${MobilePresentation.number(record.value("liters"),"L")} · Unit price: ${record.value("price_per_liter").toBigDecimalOrNull()?.let{MobilePresentation.money(it.toPlainString())+"/L"}?:"Unavailable"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)
                Text("Odometer: ${MobilePresentation.number(record.value("odometer_km"),"km")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)
                Text("Recorded: ${MobilePresentation.time(record.value("logged_at"))}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable internal fun DriverButton(label:String,enabled:Boolean=true,click:()->Unit) {
    Button(
        onClick=click,
        enabled=enabled,
        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
        shape=RoundedCornerShape(10.dp),
        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary)
    ) {
        Text(label,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center)
        if(label=="COMPLETE DELIVERY") {Spacer(Modifier.width(8.dp));DriverAsset(R.drawable.figma_driver_dafed,17,tint=MaterialTheme.colorScheme.onPrimary)}
        if(label=="BACK TO HOME") {Spacer(Modifier.width(8.dp));DriverAsset(R.drawable.figma_driver_43c84,16,18,tint=MaterialTheme.colorScheme.onPrimary)}
    }
}
@Composable internal fun DriverPageHeading(title:String,back:(()->Unit)?=null) {
    Column {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).heightIn(min=64.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(back!=null)IconButton(onClick=back,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){DriverAsset(R.drawable.figma_driver_e6917,16,description="Back",tint=MaterialTheme.colorScheme.onSurface)}
            Text(title,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f,fill=false),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
@Composable private fun DriverCaption(text:String,icon:Int?=null,width:Int=10,height:Int=10) {
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {if(icon!=null)DriverAsset(icon,width,height,tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(text,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable private fun DriverBadge(label:String,color:Color=MaterialTheme.colorScheme.primary,background:Color=MaterialTheme.colorScheme.surfaceVariant,uppercase:Boolean=true) {
    Surface(color=background,shape=RoundedCornerShape(6.dp)) {Text(if(uppercase)label.uppercase()else label,Modifier.padding(horizontal=10.dp,vertical=4.dp),color=color,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
}
@Composable private fun DriverCard(accent:Color?=null,content:@Composable ColumnScope.()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if(accent!=null)Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}
@Composable private fun DriverNotice(text:String,error:Boolean=false) {
    val bg = if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if(error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
        color=bg,
        shape=RoundedCornerShape(10.dp),
        border=BorderStroke(1.dp,if(error) MaterialTheme.colorScheme.error.copy(alpha=0.35f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(text,Modifier.fillMaxWidth().padding(14.dp),style=MaterialTheme.typography.bodySmall,color=textColor)
    }
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
    fun isCorrectionRequested(delivery:JsonObject?):Boolean =
        delivery?.value("status")=="delivered" && delivery.value("proof_review_status")=="correction_requested"
    fun isDriverCompleted(shipment:JsonObject,delivery:JsonObject?=null):Boolean {
        if(isCorrectionRequested(delivery))return false
        val shipmentStatus=shipment.value("status")
        val deliveryStatus=delivery?.value("status").orEmpty()
        return shipmentStatus in listOf("completed","review","delivered") ||
            deliveryStatus in listOf("delivered","completed")
    }
    fun isActiveAssignment(shipment:JsonObject,delivery:JsonObject?=null):Boolean {
        val shipmentStatus=shipment.value("status")
        if(!isActive(shipmentStatus))return false
        if(isCorrectionRequested(delivery))return true
        return !isDriverCompleted(shipment,delivery)
    }
    fun tripStatusLabel(shipment:JsonObject,delivery:JsonObject?=null):String {
        val shipmentStatus=shipment.value("status")
        if(shipmentStatus in listOf("completed","cancelled"))return MobilePresentation.status(shipmentStatus)
        if(isCorrectionRequested(delivery))return "Correction requested"
        if(isDriverCompleted(shipment,delivery))return "Delivered"
        return MobilePresentation.status(shipmentStatus)
    }
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
    fun matches(shipment:JsonObject,delivery:JsonObject?,filter:String)=when(filter) {
        "Completed"->isDriverCompleted(shipment,delivery)
        "Cancelled"->shipment.value("status")=="cancelled" || delivery?.value("status")=="cancelled"
        "Ongoing","Active"->isActiveAssignment(shipment,delivery)
        else->true
    }
    fun inPeriod(s:JsonObject,period:String,today:LocalDate=LocalDate.now(ZoneId.of("Asia/Manila"))):Boolean {
        if(period=="All records")return true
        val date=runCatching {Instant.parse(s.value("scheduled_departure").ifBlank{s.value("created_at")}).atZone(ZoneId.of("Asia/Manila")).toLocalDate()}.getOrNull()?:return false
        return YearMonth.from(date)==YearMonth.from(if(period=="Last month")today.minusMonths(1)else today)
    }
    fun allowanceTotal(data:JsonObject,records:List<JsonObject>):String {
        val completed=records.filter {isDriverCompleted(it,delivery(data,it.value("id")))}
        val amounts=completed.map {
            val d=delivery(data,it.value("id"))
            (d.value("driver_allowance").ifBlank{it.value("driver_allowance_php")}).toBigDecimalOrNull()
        }
        if(amounts.any {it==null})return "Not fully recorded"
        return MobilePresentation.money(amounts.filterNotNull().fold(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).toString())
    }
    fun onTime(data:JsonObject,records:List<JsonObject>):String {
        val outcomes=records.filter{isDriverCompleted(it,delivery(data,it.value("id")))}.mapNotNull {s->runCatching {
            val d=delivery(data,s.value("id"))
            val arrived=d.value("arrived_at").ifBlank{s.value("actual_delivery_at")}
            val scheduled=s.value("scheduled_arrival").ifBlank{s.value("estimated_arrival")}
            !Instant.parse(arrived).isAfter(Instant.parse(scheduled))
        }.getOrNull()}
        return if(outcomes.isEmpty())"Not recorded" else "${outcomes.count {it}*100/outcomes.size}% (${outcomes.size} recorded)"
    }
}

@Composable
internal fun DriverTopAppBar(
    trackingNumber: String?,
    showMessages: Boolean,
    unreadMessagesCount: Int?,
    unreadAlertsCount: Int,
    isBusy: Boolean,
    onSwitchTrip: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(
                    start = if (trackingNumber != null) 4.dp else 16.dp,
                    end = 16.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (trackingNumber != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSwitchTrip,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Switch trip",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = trackingNumber.ifBlank { "Navigate" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    DriverAsset(
                        id = if (isSystemInDarkTheme()) R.drawable.ic_tanaw_logo_dark else R.drawable.figma_driver_fac2b,
                        width = 46,
                        description = "TANAW logo"
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showMessages) {
                        TextButton(
                            onClick = onOpenMessages,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (unreadMessagesCount != null && unreadMessagesCount > 0) "Messages ($unreadMessagesCount)" else "Messages",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !isBusy,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(id = R.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenAlerts,
                        modifier = Modifier.size(48.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadAlertsCount > 0) {
                                    Badge { Text("$unreadAlertsCount") }
                                }
                            }
                        ) {
                            DriverAsset(R.drawable.figma_driver_90918, 24, 28, "Notifications", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (isBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            }
        }
    }
}

@Preview(showBackground = true, name = "Driver Top Bar Light")
@Composable
private fun DriverTopAppBarLightPreview() {
    DriverTheme(darkTheme = false) {
        DriverTopAppBar(
            trackingNumber = null,
            showMessages = true,
            unreadMessagesCount = 2,
            unreadAlertsCount = 1,
            isBusy = false,
            onSwitchTrip = {},
            onOpenMessages = {},
            onOpenAlerts = {},
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true, name = "Driver Top Bar Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DriverTopAppBarDarkPreview() {
    DriverTheme(darkTheme = true) {
        DriverTopAppBar(
            trackingNumber = null,
            showMessages = true,
            unreadMessagesCount = 2,
            unreadAlertsCount = 1,
            isBusy = false,
            onSwitchTrip = {},
            onOpenMessages = {},
            onOpenAlerts = {},
            onRefresh = {}
        )
    }
}

private fun previewDriverWorkspaceData() = buildJsonObject {
    put("profile", buildJsonObject {
        put("id", "driver-1")
        put("role", "driver")
        put("full_name", "Mateo Reyes")
    })
    put("driver", buildJsonObject {
        put("id", "driver-row-1")
        put("status", "available")
    })
    put("driver_trucks", buildJsonArray {
        add(buildJsonObject {
            put("id", "truck-1")
            put("model", "Isuzu Giga Wing Van")
            put("plate_number", "NCV 4821")
            put("assigned_driver_id", "driver-row-1")
        })
    })
    put("shipments", buildJsonArray {
        add(buildJsonObject {
            put("id", "ship-1")
            put("tracking_number", "TNW-2026-0412")
            put("status", "assigned")
            put("cargo_description", "Palletized Beverage Crates")
            put("weight_kg", "6200")
            put("pickup_address", "Cabanatuan City Warehouse Hub")
            put("dropoff_address", "Valenzuela Distribution Center")
            put("scheduled_departure", "2026-10-29T00:00:00Z")
            put("created_at", "2026-10-28T09:00:00Z")
            put("distance_km", "94")
            put("route_duration_minutes", "135")
        })
    })
    put("driver_deliveries", buildJsonArray {
        add(buildJsonObject {
            put("id", "del-1")
            put("shipment_id", "ship-1")
            put("truck_id", "truck-1")
            put("status", "assigned")
            put("assigned_at", "2026-10-28T09:30:00Z")
            put("driver_allowance", "1250")
        })
    })
}

@Preview(showBackground = true, name = "Driver Home Light")
@Composable
private fun DriverHomeLightPreview() {
    val data = previewDriverWorkspaceData()
    DriverTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DriverIdentity(data)
                DriverAssignment(
                    data.rows("shipments").first(),
                    DriverUiRules.delivery(data, "ship-1")
                ) {}
            }
        }
    }
}

@Preview(showBackground = true, name = "Driver Trips Light")
@Composable
private fun DriverTripsLightPreview() {
    val data = previewDriverWorkspaceData()
    DriverTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DriverTrips(
                data = data,
                state = MobileState(),
                onHistory = null,
                open = {}
            )
        }
    }
}
