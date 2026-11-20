package com.tanaw.app.feature.workspace.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tanaw.app.BuildConfig
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.auth.dialogs.PasswordChangeDialog
import com.tanaw.app.feature.auth.dialogs.PhoneChangeDialog
import com.tanaw.app.feature.auth.screens.CreateAccountScreen
import com.tanaw.app.feature.auth.screens.ForgotPasswordCardScreen
import com.tanaw.app.feature.auth.screens.LoginScreen
import com.tanaw.app.feature.auth.screens.VerificationCodeScreen
import com.tanaw.app.feature.auth.screens.VerifyRecoveryCodeScreen
import com.tanaw.app.feature.workspace.customer.components.CustomerTopBar
import com.tanaw.app.feature.workspace.customer.components.TanawBrandLogo
import com.tanaw.app.feature.workspace.customer.dialogs.CustomerCancellationSection
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerHistoryPresentation
import com.tanaw.app.feature.workspace.customer.rules.CustomerPaginationRules
import com.tanaw.app.feature.workspace.customer.screens.CustomerBookingFlow
import com.tanaw.app.feature.workspace.customer.screens.CustomerProfile
import com.tanaw.app.feature.workspace.customer.screens.CustomerShipmentsSection
import com.tanaw.app.feature.workspace.customer.screens.ShipmentTrackingPreviewContent
import com.tanaw.app.feature.workspace.customer.screens.ShipmentTrackingScreen
import com.tanaw.app.feature.workspace.driver.components.DriverRouteStatus
import com.tanaw.app.feature.workspace.driver.screens.DriverWorkspace
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.rules.ShipmentDeliveryRules
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.store.RecoveryPolicy
import com.tanaw.app.feature.workspace.tracking.TrackingFreshnessRules
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.ShipmentMap
import com.tanaw.app.ui.components.StatusBadge
import com.tanaw.app.ui.components.SupportingText
import com.tanaw.app.ui.components.TanawSubScreenTopBar
import com.tanaw.app.ui.components.WorkspaceCard
import com.tanaw.app.ui.theme.TanawBodyFontFamily
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@Composable
fun MobileWorkspaceApp(vm: MobileWorkspaceViewModel) {
    val state by vm.state.collectAsState()
    val data = state.data
    val activityContext = LocalContext.current

    if (state.page == "login") {
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
            onGoogleSignIn = if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) ({ vm.googleSignIn(activityContext) }) else null,
        )
        legal?.let { title ->
            InfoDialog(
                title = title,
                text = "The office has not published an approved $title in this app. Ask your administrator for the current document. No policy acceptance is recorded by opening this panel."
            ) { legal = null }
        }
        return
    }

    if (state.page == "signup") {
        BackHandler(!state.busy) { vm.page("login") }
        var terms by remember { mutableStateOf(false) }
        CreateAccountScreen(
            onVerifyAndContinue = vm::signup,
            onSignIn = { vm.page("login") },
            onTermsClick = { terms = true },
            isLoading = state.busy,
            errorMessage = state.error,
            phoneSignup = true
        )
        if (terms) {
            InfoDialog(
                title = "Before creating an account",
                text = "Use accurate contact details. Requests require office review and a confirmed quotation before dispatch. Contact the office for privacy and service terms; the full policy has not been published in this app."
            ) { terms = false }
        }
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
        BackHandler(state.page in listOf("verify", "verify_phone")) {
            if (!state.busy) vm.page("login")
        }
        when (state.page) {
            "loading" -> LoadingSessionScreen(
                state = state,
                onRetry = vm::refresh
            )
            "verify" -> {
                var code by remember(state.recoveryCode) { mutableStateOf(state.recoveryCode.orEmpty()) }
                val canVerify = vm.canVerify(code, "signup")
                val isCodeError = code.isNotBlank() && !canVerify
                VerificationCodeScreen(
                    title = "Verify Email",
                    headline = "Verify your email",
                    subtitle = "Enter the verification code or paste its confirmation link sent to",
                    targetLabel = maskEmailTarget(state.email),
                    code = code,
                    onCodeChange = { code = it.trim() },
                    onVerify = { vm.verifySignup(code) },
                    onResend = vm::resend,
                    onBackToSignIn = { vm.page("login") },
                    verifyButtonLabel = "Verify email",
                    resendButtonLabel = "Resend verification",
                    codeFieldLabel = "Email code or confirmation link",
                    allowTokenPaste = true,
                    canVerify = canVerify,
                    isCodeError = isCodeError,
                    codeErrorText = "Enter a valid verification code or link.",
                    busy = state.busy,
                    offline = state.offline,
                    resendSeconds = state.resendSeconds,
                    errorMessage = state.error,
                    statusMessage = state.message
                )
            }
            "verify_phone" -> {
                var code by remember { mutableStateOf("") }
                val codeValid = code.matches(Regex("[0-9]{6,8}"))
                val isCodeError = code.isNotBlank() && !codeValid
                VerificationCodeScreen(
                    title = "Verify Phone",
                    headline = "Verify your phone",
                    subtitle = "Enter the code sent by SMS to the number you registered. Codes expire; request a new one if needed.",
                    targetLabel = state.email.takeIf { MobileValidation.phone(it) != null }?.let(::maskPhoneTarget).orEmpty(),
                    code = code,
                    onCodeChange = { code = it.trim() },
                    onVerify = { vm.verifyPhoneSignup(code) },
                    onResend = vm::resendPhoneSignup,
                    onBackToSignIn = { vm.page("login") },
                    verifyButtonLabel = "Verify phone",
                    resendButtonLabel = "Resend SMS",
                    codeFieldLabel = "SMS verification code",
                    allowTokenPaste = false,
                    canVerify = codeValid,
                    isCodeError = isCodeError,
                    codeErrorText = "Enter a 6-digit verification code.",
                    busy = state.busy,
                    offline = state.offline,
                    resendSeconds = state.resendSeconds,
                    errorMessage = state.error,
                    statusMessage = state.message
                )
            }
            else -> AccessAttentionScreen(
                state = state,
                onRetry = vm::refresh,
                onLogout = vm::logout
            )
        }
        return
    }

    val isCustomer = data.obj("profile").value("role") == "customer"
    val renderCustomerOrDefault: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (isCustomer) {
            TanawCustomerTheme { content() }
        } else {
            content()
        }
    }

    renderCustomerOrDefault {
        key(data.obj("profile").value("id")) {
            if (isCustomer &&
                MobileValidation.phone(data.obj("profile").value("phone")) == null
            ) {
                OnboardingProfileGate(
                    vm = vm,
                    state = state,
                    data = data
                )
            } else if (data.obj("profile").value("role") == "driver") {
                DriverWorkspace(vm, state, data)
            } else {
                Workspace(vm, state, data)
            }
        }

        if (state.passwordChangeOwner != null) {
            PasswordChangeDialog(
                state,
                vm::cancelPasswordChange,
                { vm.resetEmail(state.email) },
                { vm.canVerify(it, "recovery") },
                vm::recover
            )
        }
        if (state.phoneChangeTarget != null) {
            PhoneChangeDialog(
                state,
                vm::verifyPhoneChange,
                vm::resendPhoneChange,
                vm::cancelPhoneChange
            )
        }
    }
}

@Composable
private fun LoadingSessionScreen(
    state: MobileState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            WorkspaceCard(modifier = Modifier.widthIn(max = 420.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TanawBrandLogo(modifier = Modifier.size(56.dp))
                    Text(
                        text = "TANAW",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.busy) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                    Text(
                        text = "Checking your session…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Notices(state, showLinearProgress = false)
                    if (state.offline) {
                        SupportingText(
                            text = "Offline or reconnecting · showing the last loaded records. Tap Retry to check again."
                        )
                    }
                    if (!state.busy) {
                        Action(label = "Retry", enabled = true, onClick = onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessAttentionScreen(
    state: MobileState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            WorkspaceCard(modifier = Modifier.widthIn(max = 420.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Text(
                        text = "Account access needs attention.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Notices(state, showLinearProgress = false)
                    if (state.offline) {
                        SupportingText("Offline or reconnecting · check your network connection and retry.")
                    }
                    Action(label = "Retry", enabled = !state.busy, onClick = onRetry)
                    OutlinedButton(
                        onClick = onLogout,
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Sign out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProfileGate(
    vm: MobileWorkspaceViewModel,
    state: MobileState,
    data: JsonObject,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TanawSubScreenTopBar(
                title = "Complete your profile",
                onBack = vm::logout,
                busy = state.busy
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Notices(state, showLinearProgress = false)
            SupportingText("Add your contact number before continuing to shipments.")
            WorkspaceCard {
                ProfileForm(
                    vm = vm,
                    state = state,
                    data = data,
                    isOnboardingGate = true
                )
            }
            OutlinedButton(
                onClick = vm::logout,
                enabled = !state.busy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Sign out", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Workspace(vm: MobileWorkspaceViewModel, state: MobileState, data: JsonObject) {
    var tab by rememberSaveable { mutableStateOf(if (state.passwordChangeOwner != null) "Profile" else "Home") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable(tab) { mutableStateOf("") }
    var historyFilter by rememberSaveable { mutableStateOf("All") }
    var fullScreenBookingStep by rememberSaveable { mutableStateOf(false) }
    var profileSubPanelOpen by rememberSaveable { mutableStateOf(false) }
    val bookingDraft = rememberSaveableStateHolder()
    val profile = data.obj("profile")
    val role = profile.value("role")
    val isCustomerHome = role == "customer" && tab == "Home" && detail == null
    val isCustomerProfile = role == "customer" && tab == "Profile" && detail == null
    val isCustomerShipments = tab in listOf("Active", "History") && detail == null

    BackHandler(detail != null) { detail = null }

    val unreadAlertsCount = remember(data) { data.rows("notifications").count { it.value("read_at").isBlank() } }
    val allShipments = remember(state.customerShipments, data) {
        state.customerShipments.orEmpty() + data.rows("shipments")
    }
    val allDocuments = remember(state.customerHistoryDocuments, data) {
        (state.customerHistoryDocuments + data.rows("documents")).distinctBy { it.value("id") }
    }
    val selectedShipment = detail?.let { selectedId ->
        allShipments.firstOrNull { it.value("id") == selectedId }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        val job = launch {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Indefinite
            )
        }
        kotlinx.coroutines.delay(3000L)
        snackbarHostState.currentSnackbarData?.dismiss()
        job.cancel()
        vm.clearMessage()
    }

    val hideChromeForChildFlow = (isCustomerHome && fullScreenBookingStep) || (isCustomerProfile && profileSubPanelOpen)
    val isTrackingDetail = detail != null && selectedShipment != null
    val showDetailFallbackTopBar = detail != null && selectedShipment == null
    val showRootTopBar = detail == null && !hideChromeForChildFlow
    val showBottomBar = detail == null && !hideChromeForChildFlow

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (showDetailFallbackTopBar) {
                TanawSubScreenTopBar(
                    title = "Shipment details",
                    onBack = { detail = null },
                    busy = state.busy || state.detailLoading
                )
            } else if (showRootTopBar) {
                CustomerTopBar(
                    unreadAlertsCount = unreadAlertsCount,
                    isBusy = state.busy,
                    onRefresh = vm::refresh,
                    onOpenAlerts = { tab = "Alerts"; detail = null },
                    applyStatusBarsPadding = true
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .heightIn(min = 64.dp)
                                .selectableGroup(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icons = listOf(
                                Icons.Outlined.Home,
                                Icons.Outlined.LocalShipping,
                                Icons.Outlined.History,
                                Icons.Outlined.Notifications,
                                Icons.Outlined.Person
                            )
                            listOf("Home", "Active", "History", "Alerts", "Profile").forEachIndexed { index, label ->
                                val selected = tab == label
                                val unreadAlerts = if (label == "Alerts") unreadAlertsCount else 0
                                val activeNavColor = Color(0xFF002D54)
                                val inactiveNavColor = Color(0xFF64748B)
                                val itemColor = when {
                                    state.busy -> inactiveNavColor.copy(alpha = 0.45f)
                                    selected -> activeNavColor
                                    else -> inactiveNavColor
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 64.dp)
                                        .selectable(
                                            selected = selected,
                                            enabled = !state.busy,
                                            role = Role.Tab,
                                            onClick = {
                                                tab = label
                                                detail = null
                                                if (label != "Profile") profileSubPanelOpen = false
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .width(36.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                                .background(activeNavColor)
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                                    ) {
                                        if (unreadAlerts > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = Color(0xFF0383D5),
                                                        contentColor = Color.White
                                                    ) { Text("$unreadAlerts") }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = icons[index],
                                                    contentDescription = label,
                                                    tint = itemColor,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = icons[index],
                                                contentDescription = label,
                                                tint = itemColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            fontFamily = TanawBodyFontFamily,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            color = itemColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isTrackingDetail) {
            Box(modifier = Modifier.fillMaxSize()) {
                ShipmentDetails(
                    s = selectedShipment,
                    data = data,
                    role = role,
                    state = state,
                    vm = vm,
                    onBack = { detail = null }
                )
            }
        } else if (isCustomerHome) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (fullScreenBookingStep) PaddingValues(0.dp) else padding)
            ) {
                bookingDraft.SaveableStateProvider("booking") {
                    CustomerBookingFlow(
                        vm = vm,
                        state = state,
                        data = data,
                        onOpenAlerts = { tab = "Alerts"; detail = null },
                        onTrackShipment = { id ->
                            if (id.isNotBlank()) {
                                tab = "Active"
                                detail = id
                            } else {
                                tab = "Active"
                                detail = null
                            }
                        },
                        onFullScreenStepChanged = { fullScreenBookingStep = it },
                        onOpenProfile = { tab = "Profile"; detail = null }
                    )
                }
            }
        } else if (isCustomerProfile) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (profileSubPanelOpen) PaddingValues(0.dp) else padding)
            ) {
                CustomerProfile(
                    vm = vm,
                    state = state,
                    data = data,
                    onOpenAlerts = { tab = "Alerts"; detail = null },
                    onOpenBookings = { tab = "Active"; detail = null },
                    onLogout = vm::logout,
                    showTopBar = false,
                    onSubPanelChanged = { profileSubPanelOpen = it }
                )
            }
        } else if (isCustomerShipments) {
            LaunchedEffect(tab, historyFilter, query) {
                if (query.isNotBlank()) {
                    kotlinx.coroutines.delay(350)
                }
                vm.loadCustomerShipments(tab, historyFilter, query, loadMore = false)
            }
            val isCurrentTabLoaded = state.customerShipmentsTab == tab &&
                (tab != "History" || state.customerShipmentsFilter == historyFilter)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                CustomerShipmentsSection(
                    tab = tab,
                    query = query,
                    onQueryChange = { query = it },
                    historyFilter = historyFilter,
                    onHistoryFilterChange = { historyFilter = it },
                    shipments = displayedShipments,
                    deliveries = data.rows("deliveries"),
                    documents = allDocuments,
                    isCurrentTabLoaded = isCurrentTabLoaded,
                    state = state,
                    onOpenShipment = { detail = it },
                    onViewDocument = { shipmentId, documentId ->
                        vm.viewProof(shipmentId, documentId)
                    },
                    onRetry = {
                        vm.loadCustomerShipments(tab, historyFilter, query, loadMore = false)
                    },
                    onLoadMore = {
                        vm.loadCustomerShipments(tab, historyFilter, query, loadMore = true)
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .let { if (detail != null) it.navigationBarsPadding() else it }
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Notices(state, showLinearProgress = false)
                if (state.offline) {
                    Surface(
                        color = TanawTheme.extendedColors.warningContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Offline or reconnecting · showing the last loaded records.",
                            modifier = Modifier.padding(14.dp),
                            color = TanawTheme.extendedColors.onWarningContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (detail != null) {
                    WorkspaceCard {
                        Text(
                            text = "Shipment unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SupportingText("Shipment not found in the current list. Refresh or return to your shipments.")
                        OutlinedButton(
                            onClick = { detail = null },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Back to shipments", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    when (tab) {
                        "Home" -> {
                            Text(
                                text = "Hello, ${profile.value("full_name").ifBlank { "there" }}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SupportingText("Your deliveries and office updates, in one place.")
                            WorkspaceCard {
                                Text("Delivery overview", style = MaterialTheme.typography.titleMedium)
                                val shipments = data.rows("shipments")
                                Text(
                                    text = "${shipments.count { it.value("status") !in listOf("completed", "cancelled") }} active  ·  ${shipments.count { it.value("status") == "completed" }} completed",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Shipment overview",
                                style = MaterialTheme.typography.titleMedium
                            )
                            SupportingText(
                                text = "Dispatch, quotation and completion review are available on the office website."
                            )
                            ShipmentList(
                                rows = data.rows("shipments").filter { it.value("status") !in listOf("completed", "cancelled") },
                                emptyTitle = "No active shipments",
                                emptyBody = "No active deliveries are currently recorded.",
                                open = { detail = it }
                            )
                        }

                        "Alerts" -> {
                            val lifecycleOwner = LocalLifecycleOwner.current
                            DisposableEffect(lifecycleOwner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    when (event) {
                                        Lifecycle.Event.ON_RESUME -> vm.onCustomerAlertsOpened()
                                        Lifecycle.Event.ON_PAUSE -> vm.onCustomerAlertsClosed()
                                        else -> Unit
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    vm.onCustomerAlertsOpened()
                                }
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                    vm.onCustomerAlertsClosed()
                                }
                            }
                            val displayedNotifications = state.notificationsPage ?: data.rows("notifications")
                            if (state.notificationsLoading && state.notificationsPage == null) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (state.notificationsError != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = state.notificationsError!!,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        TextButton(
                                            onClick = { vm.loadNotifications(loadMore = false) },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Retry", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            if (displayedNotifications.isEmpty() && !state.notificationsLoading) {
                                WorkspaceCard {
                                    Text(
                                        text = "No notifications.",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SupportingText("Delivery milestones, quotation updates, and dispatch alerts will appear here.")
                                }
                            }
                            displayedNotifications.forEach { n ->
                                WorkspaceCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = n.value("title"),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontFamily = TanawDisplayFontFamily,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (n.value("read_at").isBlank()) {
                                            Spacer(Modifier.width(8.dp))
                                            StatusBadge("unread")
                                        }
                                    }
                                    SupportingText(n.value("body"))
                                    Text(
                                        text = MobilePresentation.time(n.value("created_at")),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = TanawMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (allShipments.any { it.value("id") == n.value("shipment_id") }) {
                                        TextButton(
                                            onClick = { detail = n.value("shipment_id") },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Open shipment", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            if (state.notificationsHasMore) {
                                OutlinedButton(
                                    onClick = { vm.loadNotifications(loadMore = true) },
                                    enabled = !state.notificationsLoading,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(if (state.notificationsLoading) "Loading more notifications..." else "Load more notifications")
                                }
                            }
                        }

                        "Profile" -> {
                            WorkspaceCard { ProfileForm(vm, state, data) }
                            Action("Saved address", !state.busy) {
                                info = data.obj("customer").value("address").ifBlank {
                                    "No saved address. Add your address above and save your profile."
                                }
                            }
                            Action("Help and service information", true) {
                                info = "Contact the office with your tracking reference for quotations, dispatch, account activation or changes. Drivers share location during active trips and submit private proof for office review. Chat, payments and profile photos are not available in this release."
                            }
                            Action("Password recovery", !state.busy) { vm.page("recover") }
                            SupportingText("Login email changes require a verified account-change flow. Contact the office; editing profile details does not change your sign-in email.")
                            OutlinedButton(
                                onClick = vm::logout,
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Sign out", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (info != null) InfoDialog("Tanaw", info!!) { info = null }
    state.proof?.let { bitmap ->
        val dialogBg = Color.White
        val dialogBorder = Color(0xFFE2E8F0)
        val dialogTitleColor = Color(0xFF0F172A)
        val dialogSubColor = Color(0xFF475569)
        val dialogActionColor = Color(0xFF002D54)
        Dialog(onDismissRequest = vm::closeProof) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = dialogBg,
                border = BorderStroke(1.dp, dialogBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = state.proofTitle.ifBlank { "Proof document" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = dialogTitleColor
                    )
                    if (state.proofSubtitle.isNotBlank()) {
                        Text(
                            text = state.proofSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = dialogSubColor
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, dialogBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = state.proofTitle.ifBlank { "Recorded proof document" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .padding(4.dp)
                        )
                    }
                    Text(
                        text = "Private document. Close this viewer when finished.",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogSubColor
                    )
                    TextButton(
                        onClick = vm::closeProof,
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Close proof",
                            fontWeight = FontWeight.Bold,
                            color = dialogActionColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShipmentList(
    rows: List<JsonObject>,
    emptyTitle: String = "No shipments in this section",
    emptyBody: String = "No shipments are available in this view.",
    open: (String) -> Unit
) {
    if (rows.isEmpty()) {
        WorkspaceCard {
            Text(
                text = emptyTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SupportingText(emptyBody)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { s ->
            Surface(
                onClick = { open(s.value("id")) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.value("tracking_number"),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = TanawMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(s.value("status"))
                    }
                    Text(
                        text = "${s.value("pickup_address")} → ${s.value("dropoff_address")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = MobilePresentation.time(s.value("created_at")),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = TanawMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val displayAmount = s.value("final_amount").ifBlank { s.value("quoted_amount") }
                        if (displayAmount.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = MobilePresentation.money(displayAmount),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = TanawMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShipmentDetails(
    s: JsonObject,
    data: JsonObject,
    role: String,
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    onBack: () -> Unit
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15.seconds)
            now = Instant.now()
        }
    }
    LaunchedEffect(s.value("id")) { vm.loadDetails(s.value("id")) }

    val shipmentDeliveries = data.rows("deliveries").filter { it.value("shipment_id") == s.value("id") }
    val deliveries = ShipmentDeliveryRules.displayDeliveries(s, shipmentDeliveries)
    val activeDelivery = ShipmentDeliveryRules.primaryDelivery(deliveries)
    val detail = state.details?.takeIf { it.value("shipment_id") == s.value("id") }

    val position = detail?.obj("position")
    val routing = detail?.obj("routing")?.takeIf { it.isNotEmpty() }
    val freshness = position?.let {
        TrackingFreshnessRules.evaluatePositionFreshness(
            recordedAt = it.value("recorded_at"),
            accuracyMeters = it.value("accuracy_m"),
            now = now
        )
    }
    val eta = freshness?.let {
        TrackingFreshnessRules.customerEtaSummary(s, activeDelivery, routing, it, state.offline, now)
    }
    val verifiedRouting = routing?.let {
        DriverRouteStatus.forAssignment(it, s, activeDelivery ?: buildJsonObject {})
    }
    val routeGeometry = DriverRouteStatus.resolvedGeometry(s, verifiedRouting)
    val routeCoords = remember(routeGeometry) { extractRouteCoordinates(routeGeometry) }
    val shipmentStatus = s.value("status").lowercase()
    val deliveryStatus = activeDelivery?.value("status")?.lowercase().orEmpty()
    val isCompleted = shipmentStatus in listOf("completed", "delivered") || deliveryStatus == "delivered"

    val pickupPoint = remember(s) {
        val lat = s.value("pickup_lat").toDoubleOrNull()
        val lng = s.value("pickup_lng").toDoubleOrNull()
        if (lat != null && lng != null) org.maplibre.android.geometry.LatLng(lat, lng) else null
    }
    val dropoffPoint = remember(s) {
        val lat = s.value("dropoff_lat").toDoubleOrNull()
        val lng = s.value("dropoff_lng").toDoubleOrNull()
        if (lat != null && lng != null) org.maplibre.android.geometry.LatLng(lat, lng) else null
    }
    val livePositionPoint = remember(position) {
        val lat = position?.value("latitude")?.toDoubleOrNull()
        val lng = position?.value("longitude")?.toDoubleOrNull()
        if (lat != null && lng != null) org.maplibre.android.geometry.LatLng(lat, lng) else null
    }
    val vehiclePoint = livePositionPoint ?: if (isCompleted) (routeCoords.lastOrNull() ?: dropoffPoint) else null

    val mapPoints = remember(pickupPoint, dropoffPoint, livePositionPoint, vehiclePoint) {
        buildList {
            if (pickupPoint != null) add("Pickup" to pickupPoint)
            if (dropoffPoint != null) add("Destination" to dropoffPoint)
            if (livePositionPoint != null) {
                add("Last recorded vehicle position" to livePositionPoint)
            } else if (vehiclePoint != null) {
                add("Vehicle" to vehiclePoint)
            }
        }
    }
    val vehicleHeadingDeg = remember(position, routeCoords, vehiclePoint, isCompleted, pickupPoint, dropoffPoint) {
        val explicitHeading = listOf("heading_deg", "heading", "bearing")
            .firstNotNullOfOrNull { key -> position?.value(key)?.toFloatOrNull() }
            ?.takeIf { it.isFinite() }
        explicitHeading
            ?: (if (isCompleted) finalRouteBearing(routeCoords) else vehiclePoint?.let { nearestRouteBearing(it, routeCoords) })
            ?: if (pickupPoint != null && dropoffPoint != null) bearingDegrees(pickupPoint, dropoffPoint) else null
    }
    val timelineEvents = remember(detail) {
        if (detail == null) {
            emptyList()
        } else {
            (detail.rows("events").map {
                it.value("occurred_at") to MobilePresentation.status(it.value("event_type").replace('_', ' '))
            } + detail.rows("history").map {
                val note = it.value("note").takeIf(String::isNotBlank)?.let { n -> " · $n" }.orEmpty()
                it.value("created_at") to (MobilePresentation.status(it.value("to_status").replace('_', ' ')) + note)
            }).sortedBy { it.first }
        }
    }

    val density = LocalDensity.current
    val boundsPaddingPx = remember(density) { with(density) { 36.dp.roundToPx() } }
    val boundsPaddingTopPx = remember(density) { with(density) { 128.dp.roundToPx() } }

    ShipmentTrackingScreen(
        shipment = s,
        deliveries = deliveries,
        routing = routing,
        eta = eta,
        freshness = freshness,
        lastTelemetryRecordedAt = position?.value("recorded_at").orEmpty(),
        detailLoaded = detail != null,
        detailLoading = state.detailLoading,
        offline = state.offline,
        busy = state.busy,
        errorMessage = state.error,
        timelineEvents = timelineEvents,
        now = now,
        onBack = onBack,
        onRefresh = { vm.loadDetails(s.value("id")) },
        mapSlot = { recenterTrigger, mapModifier ->
            ShipmentMap(
                points = mapPoints,
                geometry = routeGeometry,
                modifier = mapModifier,
                showCaptionText = false,
                recenterTrigger = recenterTrigger,
                boundsPaddingPx = boundsPaddingPx,
                boundsPaddingTopPx = boundsPaddingTopPx,
                vehicleHeadingDeg = vehicleHeadingDeg
            )
        },
        detail = detail,
        vehicles = data.rows("vehicles"),
        customerCancellationSlot = if (role == "customer") {
            {
                CustomerCancellationSection(
                    shipment = s,
                    deliveries = shipmentDeliveries,
                    state = state,
                    onCancelShipment = { shipmentId, reason, onDone ->
                        vm.cancelShipment(shipmentId, reason, onDone)
                    },
                    onClearError = { vm.clearCancellationError() },
                    showLockedNotice = false
                )
            }
        } else null,
        onAcceptQuote = if (role == "customer") {
            { vm.acceptQuote(s.value("id")) }
        } else null
    )
}

private fun extractRouteCoordinates(geometryJson: String?): List<org.maplibre.android.geometry.LatLng> {
    val raw = geometryJson?.trim()?.takeUnless { it.isBlank() || it == "null" } ?: return emptyList()
    return runCatching {
        val root = Json.parseToJsonElement(raw).jsonObject
        val geometryObj = when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "FeatureCollection" -> root["features"]?.jsonArray?.firstOrNull()?.jsonObject?.get("geometry")?.jsonObject
            "Feature" -> root["geometry"]?.jsonObject
            else -> root
        } ?: return emptyList()
        val coordsArray = when (geometryObj["type"]?.jsonPrimitive?.contentOrNull) {
            "LineString" -> geometryObj["coordinates"]?.jsonArray
            "MultiLineString" -> geometryObj["coordinates"]?.jsonArray?.firstOrNull()?.jsonArray
            else -> null
        } ?: return emptyList()
        coordsArray.mapNotNull { item ->
            val pair = item.jsonArray
            val lng = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull
            val lat = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull
            if (lat != null && lng != null) org.maplibre.android.geometry.LatLng(lat, lng) else null
        }
    }.getOrDefault(emptyList())
}

private fun bearingDegrees(
    from: org.maplibre.android.geometry.LatLng,
    to: org.maplibre.android.geometry.LatLng
): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
        kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
    val deg = Math.toDegrees(kotlin.math.atan2(y, x))
    return ((deg + 360.0) % 360.0).toFloat()
}

private fun finalRouteBearing(routeCoords: List<org.maplibre.android.geometry.LatLng>): Float? {
    for (i in routeCoords.size - 2 downTo 0) {
        val a = routeCoords[i]
        val b = routeCoords[i + 1]
        if (a.latitude != b.latitude || a.longitude != b.longitude) {
            return bearingDegrees(a, b)
        }
    }
    return null
}

private fun nearestRouteBearing(
    point: org.maplibre.android.geometry.LatLng,
    routeCoords: List<org.maplibre.android.geometry.LatLng>
): Float? {
    if (routeCoords.size < 2) return null
    val cosLat = kotlin.math.cos(Math.toRadians(point.latitude))
    var bestDistSq = Double.MAX_VALUE
    var bestBearing: Float? = null
    for (i in 0 until routeCoords.size - 1) {
        val a = routeCoords[i]
        val b = routeCoords[i + 1]
        if (a.latitude == b.latitude && a.longitude == b.longitude) continue
        val ax = a.longitude * cosLat
        val ay = a.latitude
        val bx = b.longitude * cosLat
        val by = b.latitude
        val px = point.longitude * cosLat
        val py = point.latitude
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq > 0.0) (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0) else 0.0
        val projX = ax + t * dx
        val projY = ay + t * dy
        val distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY)
        if (distSq < bestDistSq) {
            bestDistSq = distSq
            bestBearing = bearingDegrees(a, b)
        }
    }
    return bestBearing
}

@Composable
private fun ProfileForm(
    vm: MobileWorkspaceViewModel,
    state: MobileState,
    data: JsonObject,
    isOnboardingGate: Boolean = false
) {
    val p = data.obj("profile")
    val c = data.obj("customer")
    val focusManager = LocalFocusManager.current
    val initialAddr = remember(c) {
        CustomerAddressRules.parseFromSaved(
            rawAddress = c.value("address"),
            rawCity = c.value("city"),
            explicitBarangay = c.value("barangay"),
            explicitProvince = c.value("province")
        )
    }
    var name by rememberSaveable(p.value("full_name")) { mutableStateOf(p.value("full_name")) }
    var phone by rememberSaveable(p.value("phone")) { mutableStateOf(p.value("phone")) }
    var street by rememberSaveable(initialAddr.streetAddress) { mutableStateOf(initialAddr.streetAddress) }
    var barangay by rememberSaveable(initialAddr.barangay) { mutableStateOf(initialAddr.barangay) }
    var city by rememberSaveable(initialAddr.city) { mutableStateOf(initialAddr.city) }
    var province by rememberSaveable(initialAddr.province) {
        mutableStateOf(initialAddr.province.ifBlank { CustomerAddressRules.DEFAULT_PROVINCE_SUGGESTION })
    }
    val isCustomer = p.value("role") == "customer"
    val addressValid = !isCustomer || CustomerAddressRules.isValidAddress(street, barangay, city, province)
    val canSave = !state.busy &&
        name.trim().length in 2..200 &&
        MobileValidation.phone(phone) != null &&
        addressValid

    val saveProfile: () -> Unit = {
        val (formattedAddress, formattedCity) = if (isCustomer) {
            CustomerAddressRules.formatForRpc(street, barangay, city, province)
        } else {
            Pair("", "")
        }
        vm.profile(name, phone, formattedAddress, formattedCity)
    }

    Text(
        text = "Your profile",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
    SupportingText(
        text = if (p.value("email").isNotBlank()) {
            "Sign-in email: ${p.value("email")}"
        } else {
            "Phone-number account"
        }
    )

    Field(
        label = "Full name",
        value = name,
        change = { name = it },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    Field(
        label = "Phone",
        value = phone,
        change = { phone = it },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = if (isCustomer) ImeAction.Next else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = {
                focusManager.clearFocus()
                if (canSave) saveProfile()
            }
        )
    )
    if (isCustomer) {
        Field(
            label = "House / Street address",
            value = street,
            change = { street = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Field(
            label = "Barangay",
            value = barangay,
            change = { barangay = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Field(
            label = "City / Municipality",
            value = city,
            change = { city = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Field(
            label = "Province",
            value = province,
            change = { province = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (canSave) saveProfile()
                }
            )
        )
    }

    val shipmentSmsEnabled = data.rows("sms_preferences").any {
        it.value("type") == "shipment_status" && it["enabled"] == JsonPrimitive(true)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.busy && !state.offline) {
                    vm.smsPreference(!shipmentSmsEnabled)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Shipment SMS updates",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Shipment SMS: ${RecoveryPolicy.sms(data, "shipment_status")} · Delay SMS: ${RecoveryPolicy.sms(data, "delay")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "SMS updates are optional and go only to your verified signup number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = shipmentSmsEnabled,
                onCheckedChange = { vm.smsPreference(it) },
                enabled = !state.busy && !state.offline
            )
        }
    }

    if (state.offline) {
        SupportingText("Saved SMS status may be out of date. Reconnect and refresh.")
    }

    Action(
        label = if (isOnboardingGate) "Save and continue" else "Save profile",
        enabled = canSave,
        onClick = {
            focusManager.clearFocus()
            saveProfile()
        }
    )
}

@Composable
internal fun Notices(
    state: MobileState,
    showLinearProgress: Boolean = true
) {
    if (showLinearProgress && state.busy) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
    }
    state.error?.let {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = it,
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    state.message?.let {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = it,
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun Field(
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error
        ),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        isError = isError,
        supportingText = supportingText
    )
}

@Composable
private fun Action(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InfoDialog(title: String, text: String, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = close) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun maskEmailTarget(email: String): String {
    val trimmed = email.trim()
    val at = trimmed.indexOf('@')
    if (at <= 1) return trimmed
    val local = trimmed.substring(0, at)
    val domain = trimmed.substring(at)
    return "${local.first()}${"*".repeat((local.length - 1).coerceIn(2, 5))}$domain"
}

private fun maskPhoneTarget(phone: String): String {
    val trimmed = phone.trim()
    if (trimmed.length <= 4) return trimmed
    return "${trimmed.take(4)}****${trimmed.takeLast(3)}"
}

// ─── Previews ─────────────────────────────────────────────────────────────────
@Preview(showBackground = true, name = "Loading Session")
@Composable
private fun LoadingSessionPreview() {
    TanawTheme {
        LoadingSessionScreen(
            state = MobileState(page = "loading", busy = true),
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Shipment Details")
@Composable
private fun ShipmentDetailsPreview() {
    ShipmentTrackingPreviewContent()
}
