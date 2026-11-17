package com.tanaw.app.feature.workspace.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.tanaw.app.feature.workspace.customer.rules.CustomerPaginationRules
import com.tanaw.app.feature.workspace.customer.screens.CustomerBookingFlow
import com.tanaw.app.feature.workspace.customer.screens.CustomerProfile
import com.tanaw.app.feature.workspace.driver.components.DriverRouteStatus
import com.tanaw.app.feature.workspace.driver.components.DriverTripActions
import com.tanaw.app.feature.workspace.driver.screens.DriverWorkspace
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.rules.ShipmentDeliveryRules
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.store.RecoveryPolicy
import com.tanaw.app.feature.workspace.tracking.CustomerEtaPresentation
import com.tanaw.app.feature.workspace.tracking.GpsFreshnessBadge
import com.tanaw.app.feature.workspace.tracking.PositionFreshness
import com.tanaw.app.feature.workspace.tracking.PositionFreshnessStatus
import com.tanaw.app.feature.workspace.tracking.TrackingFreshnessRules
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.KeyValueRow
import com.tanaw.app.ui.components.ShipmentMap
import com.tanaw.app.ui.components.StatusBadge
import com.tanaw.app.ui.components.SupportingText
import com.tanaw.app.ui.components.TanawSubScreenTopBar
import com.tanaw.app.ui.components.WorkspaceCard
import com.tanaw.app.ui.theme.MapPinDestination
import com.tanaw.app.ui.theme.MapPinPickup
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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

    key(data.obj("profile").value("id")) {
        if (data.obj("profile").value("role") == "customer" &&
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
    val focusManager = LocalFocusManager.current
    val bookingDraft = rememberSaveableStateHolder()
    val profile = data.obj("profile")
    val role = profile.value("role")
    val isCustomerHome = role == "customer" && tab == "Home" && detail == null
    val isCustomerProfile = role == "customer" && tab == "Profile" && detail == null

    BackHandler(detail != null) { detail = null }

    val unreadAlertsCount = remember(data) { data.rows("notifications").count { it.value("read_at").isBlank() } }
    val allShipments = remember(state.customerShipments, data) {
        state.customerShipments.orEmpty() + data.rows("shipments")
    }
    val selectedShipment = detail?.let { selectedId ->
        allShipments.firstOrNull { it.value("id") == selectedId }
    }

    val hideChromeForChildFlow = (isCustomerHome && fullScreenBookingStep) || (isCustomerProfile && profileSubPanelOpen)
    val showDetailTopBar = detail != null
    val showRootTopBar = !showDetailTopBar && !hideChromeForChildFlow
    val showBottomBar = !showDetailTopBar && !hideChromeForChildFlow

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showDetailTopBar) {
                TanawSubScreenTopBar(
                    title = selectedShipment?.value("tracking_number")?.ifBlank { "Shipment details" } ?: "Shipment details",
                    onBack = { detail = null },
                    busy = state.busy || state.detailLoading,
                    trailing = {
                        if (selectedShipment != null) {
                            StatusBadge(selectedShipment.value("status"))
                            IconButton(
                                onClick = { vm.loadDetails(selectedShipment.value("id")) },
                                enabled = !state.detailLoading && !state.busy,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Refresh shipment details",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val icons = listOf(
                            Icons.Outlined.Home,
                            Icons.Outlined.LocalShipping,
                            Icons.Outlined.History,
                            Icons.Outlined.Notifications,
                            Icons.Outlined.Person
                        )
                        listOf("Home", "Active", "History", "Alerts", "Profile").forEachIndexed { index, label ->
                            val unreadAlerts = if (label == "Alerts") unreadAlertsCount else 0
                            NavigationBarItem(
                                selected = tab == label,
                                onClick = {
                                    tab = label
                                    detail = null
                                    if (label != "Profile") profileSubPanelOpen = false
                                },
                                enabled = !state.busy,
                                icon = {
                                    if (unreadAlerts > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadAlerts") } }) {
                                            Icon(icons[index], contentDescription = label)
                                        }
                                    } else {
                                        Icon(icons[index], contentDescription = label)
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isCustomerHome) {
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
                        onFullScreenStepChanged = { fullScreenBookingStep = it }
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
                    if (selectedShipment == null) {
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
                        ShipmentDetails(
                            s = selectedShipment,
                            data = data,
                            role = role,
                            state = state,
                            vm = vm
                        )
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
                                text = if (role == "driver") "Your assigned trips" else "Shipment overview",
                                style = MaterialTheme.typography.titleMedium
                            )
                            SupportingText(
                                text = if (role == "driver") {
                                    "Open your assigned trip to start location sharing, record pickup milestones and submit delivery proof."
                                } else {
                                    "Dispatch, quotation and completion review are available on the office website."
                                }
                            )
                            ShipmentList(
                                rows = data.rows("shipments").filter { it.value("status") !in listOf("completed", "cancelled") },
                                emptyTitle = "No active shipments",
                                emptyBody = "No active deliveries are currently recorded.",
                                open = { detail = it }
                            )
                        }

                        "Active", "History" -> {
                            LaunchedEffect(tab, historyFilter, query) {
                                if (query.isNotBlank()) {
                                    kotlinx.coroutines.delay(350)
                                }
                                vm.loadCustomerShipments(tab, historyFilter, query, loadMore = false)
                            }
                            Text(
                                text = if (tab == "History") "Shipment history" else "Active shipments",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SupportingText("Search all your shipments by tracking reference or address.")
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Search reference or address") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Cancel,
                                                contentDescription = "Clear search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                            )
                            if (tab == "History") {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("All", "Completed", "Cancelled").forEach { f ->
                                        FilterChip(
                                            selected = historyFilter == f,
                                            onClick = { historyFilter = f },
                                            shape = RoundedCornerShape(10.dp),
                                            label = { Text(f) }
                                        )
                                    }
                                }
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
                            if (state.customerShipmentsLoading && (!isCurrentTabLoaded || state.customerShipments == null)) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (state.customerShipmentsError != null) {
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
                                            text = state.customerShipmentsError!!,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        TextButton(
                                            onClick = { vm.loadCustomerShipments(tab, historyFilter, query, loadMore = false) },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Retry", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            val emptyTitle = when {
                                query.isNotBlank() -> "No matching shipments"
                                tab == "History" -> "No past shipments"
                                else -> "No active shipments"
                            }
                            val emptyBody = when {
                                query.isNotBlank() -> "No shipments match \"$query\"."
                                tab == "History" -> "Completed and cancelled deliveries will appear here."
                                else -> "Book a pickup from the Home tab to request an office quotation."
                            }
                            if (!state.customerShipmentsLoading || displayedShipments.isNotEmpty()) {
                                ShipmentList(
                                    rows = displayedShipments,
                                    emptyTitle = emptyTitle,
                                    emptyBody = emptyBody,
                                    open = { detail = it }
                                )
                            }
                            if (isCurrentTabLoaded && state.customerShipmentsHasMore) {
                                OutlinedButton(
                                    onClick = { vm.loadCustomerShipments(tab, historyFilter, query, loadMore = true) },
                                    enabled = !state.customerShipmentsLoading,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(if (state.customerShipmentsLoading) "Loading more shipments..." else "Load more shipments")
                                }
                            }
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
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
        Dialog(onDismissRequest = vm::closeProof) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(state.proofTitle, style = MaterialTheme.typography.titleMedium)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Recorded proof of delivery",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                    )
                    Text(
                        text = "Private document. Close this viewer when finished.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = vm::closeProof,
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Close proof", fontWeight = FontWeight.Bold)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val displayAmount = s.value("final_amount").ifBlank { s.value("quoted_amount") }
                        if (displayAmount.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = MobilePresentation.money(displayAmount),
                                style = MaterialTheme.typography.labelMedium,
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
    vm: MobileWorkspaceViewModel
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
    val mapPoints = remember(s, position) {
        buildList {
            fun addPoint(label: String, lat: String, lng: String) {
                val a = lat.toDoubleOrNull()
                val b = lng.toDoubleOrNull()
                if (a != null && b != null) add(label to org.maplibre.android.geometry.LatLng(a, b))
            }
            addPoint("Pickup", s.value("pickup_lat"), s.value("pickup_lng"))
            addPoint("Destination", s.value("dropoff_lat"), s.value("dropoff_lng"))
            if (position != null) {
                addPoint("Last recorded vehicle position", position.value("latitude"), position.value("longitude"))
            }
        }
    }
    val verifiedRouting = routing?.let {
        DriverRouteStatus.forAssignment(it, s, activeDelivery ?: buildJsonObject {})
    }
    val routeGeometry = DriverRouteStatus.resolvedGeometry(s, verifiedRouting)
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

    ShipmentDetailsCards(
        shipment = s,
        deliveries = deliveries,
        eta = eta,
        freshness = freshness,
        lastTelemetryRecordedAt = position?.value("recorded_at").orEmpty(),
        detailLoaded = detail != null,
        detailLoading = state.detailLoading,
        mapSlot = if (detail != null && mapPoints.isNotEmpty()) {
            { ShipmentMap(mapPoints, routeGeometry) }
        } else null,
        timelineEvents = timelineEvents,
        documents = detail?.rows("documents").orEmpty(),
        busy = state.busy,
        onViewProof = { docId -> vm.viewProof(s.value("id"), docId) },
        driverTripActionsSlot = { d ->
            if (role == "driver") {
                DriverTripActions(d, state, data, vm, shipment = s)
            }
        },
        customerCancellationSlot = if (role == "customer") {
            {
                CustomerCancellationSection(
                    shipment = s,
                    deliveries = shipmentDeliveries,
                    state = state,
                    onCancelShipment = { shipmentId, reason, onDone ->
                        vm.cancelShipment(shipmentId, reason, onDone)
                    },
                    onClearError = { vm.clearCancellationError() }
                )
            }
        } else null
    )
}

@Composable
private fun ShipmentDetailsCards(
    shipment: JsonObject,
    deliveries: List<JsonObject>,
    eta: CustomerEtaPresentation?,
    freshness: PositionFreshness?,
    lastTelemetryRecordedAt: String,
    detailLoaded: Boolean,
    detailLoading: Boolean,
    mapSlot: (@Composable () -> Unit)?,
    timelineEvents: List<Pair<String, String>>,
    documents: List<JsonObject>,
    busy: Boolean,
    onViewProof: (String) -> Unit,
    driverTripActionsSlot: @Composable (JsonObject) -> Unit = {},
    customerCancellationSlot: (@Composable () -> Unit)? = null
) {
    var showFullEventLog by rememberSaveable(shipment.value("id")) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Card 1: Live Tracking & ETA Hero Card
        WorkspaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Tracking & ETA",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (freshness != null) {
                    Spacer(Modifier.width(8.dp))
                    GpsFreshnessBadge(freshness)
                }
            }

            if (mapSlot != null) {
                mapSlot()
            }

            if (detailLoaded && eta != null && freshness != null) {
                KeyValueRow(
                    label = "Live ETA",
                    value = eta.liveEta,
                    valueColor = if (eta.isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    boldValue = eta.isLive
                )
                SupportingText(eta.liveEtaDetail)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                KeyValueRow(label = "Scheduled Arrival", value = eta.scheduledArrival)
                if (shipment.value("scheduled_departure").isNotBlank()) {
                    KeyValueRow(label = "Scheduled Departure", value = eta.scheduledDeparture)
                }
                if (lastTelemetryRecordedAt.isNotBlank()) {
                    KeyValueRow(
                        label = "Last Telemetry (PHT)",
                        value = "${MobilePresentation.time(lastTelemetryRecordedAt)} · ${freshness.accuracyText}"
                    )
                } else {
                    SupportingText("No GPS location has been recorded for this delivery.")
                }
            } else if (detailLoading) {
                SupportingText("Loading live tracking and route details…")
            } else {
                SupportingText("Live tracking details are unavailable right now. Tap refresh in the top bar to retry.")
            }
        }

        // Card 2: Route & Cargo Summary Card
        WorkspaceCard {
            Text(
                text = "Route & Cargo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            RouteConnectorBlock(
                pickupAddress = shipment.value("pickup_address"),
                dropoffAddress = shipment.value("dropoff_address")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (shipment.value("cargo_description").isNotBlank()) {
                KeyValueRow(label = "Cargo", value = shipment.value("cargo_description"))
            }
            if (shipment.value("weight_kg").isNotBlank()) {
                KeyValueRow(
                    label = "Weight",
                    value = MobilePresentation.number(shipment.value("weight_kg"), "kg")
                )
            }
            if (shipment.value("driver_notes").isNotBlank()) {
                KeyValueRow(label = "Notes", value = shipment.value("driver_notes"))
            }
            if (shipment.value("cancelled_reason").isNotBlank()) {
                KeyValueRow(
                    label = "Cancellation Reason",
                    value = shipment.value("cancelled_reason"),
                    valueColor = MaterialTheme.colorScheme.error
                )
            }
        }

        // Card 3: Quotation & Pricing Card
        val quotedRaw = shipment.value("quoted_amount")
        val finalRaw = shipment.value("final_amount")
        WorkspaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quotation & Pricing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (quotedRaw.isBlank() && finalRaw.isBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Awaiting office quotation",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            KeyValueRow(
                label = "Quoted Amount",
                value = MobilePresentation.money(quotedRaw),
                boldValue = quotedRaw.isNotBlank()
            )
            KeyValueRow(
                label = "Final Amount",
                value = MobilePresentation.money(finalRaw),
                valueColor = if (finalRaw.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                boldValue = finalRaw.isNotBlank()
            )
            SupportingText("Amounts in PHP · Philippine time")
        }

        // Card 4: Delivery Milestone Stepper & Expandable Event Log
        WorkspaceCard {
            Text(
                text = "Delivery Progress",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (deliveries.isEmpty()) {
                SupportingText("No delivery assignment has been recorded yet.")
            } else {
                deliveries.forEachIndexed { idx, d ->
                    if (idx > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assignment status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(d.value("status"))
                    }
                    MilestoneStepper(delivery = d)
                    if (d.value("recipient_name").isNotBlank()) {
                        KeyValueRow(
                            label = "Recipient",
                            value = d.value("recipient_name"),
                            boldValue = true
                        )
                    }
                    driverTripActionsSlot(d)
                }
            }

            if (detailLoaded) {
                if (timelineEvents.isEmpty()) {
                    if (deliveries.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    SupportingText("No additional status events have been recorded.")
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = { showFullEventLog = !showFullEventLog },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = if (showFullEventLog) "Hide event log" else "View full event log (${timelineEvents.size})",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (showFullEventLog) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            timelineEvents.forEach { (time, label) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = MobilePresentation.time(time),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 5: Proof of Delivery & Actions
        WorkspaceCard {
            Text(
                text = "Proof of Delivery & Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (detailLoaded) {
                if (documents.isEmpty()) {
                    SupportingText("No proof documents are available for this shipment.")
                } else {
                    documents.forEach { d ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${MobilePresentation.status(d.value("kind").replace('_', ' '))} · ${d.value("file_name").ifBlank { "Document recorded" }}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Recipient: ${d.value("recipient_name").ifBlank { "Not recorded" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MobilePresentation.time(d.value("created_at")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                OutlinedButton(
                                    onClick = { onViewProof(d.value("id")) },
                                    enabled = !busy,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("View / download", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else if (!detailLoading) {
                SupportingText("Proof documents will appear here once loaded.")
            }

            if (customerCancellationSlot != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                customerCancellationSlot()
            }
        }
    }
}

@Composable
private fun RouteConnectorBlock(
    pickupAddress: String,
    dropoffAddress: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MapPinPickup)
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "PICKUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = pickupAddress.ifBlank { "Not provided" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MapPinDestination)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "DESTINATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dropoffAddress.ifBlank { "Not provided" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MilestoneStepper(delivery: JsonObject) {
    val steps = listOf(
        "assigned_at" to "Driver Assigned",
        "route_started_at" to "En Route to Pickup",
        "pickup_arrived_at" to "Arrived at Pickup",
        "customer_showed_at" to "Cargo Loading",
        "departed_at" to "In Transit",
        "arrived_at" to "Delivered"
    )
    val firstIncompleteIndex = steps.indexOfFirst { (key, _) -> delivery.value(key).isBlank() }

    Column(modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, (key, title) ->
            val ts = delivery.value(key)
            val isCompleted = ts.isNotBlank()
            val isCurrent = !isCompleted && index == firstIncompleteIndex
            val dotColor = when {
                isCompleted -> TanawTheme.extendedColors.onSuccessBannerText
                isCurrent -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            val lineColor = if (isCompleted) {
                TanawTheme.extendedColors.onSuccessBannerText.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(26.dp)
                                .background(lineColor)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (index < steps.lastIndex) 10.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCompleted || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCompleted || isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = if (isCompleted) MobilePresentation.time(ts) else "Pending",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
@Preview(showBackground = true, name = "Loading Session Light")
@Composable
private fun LoadingSessionPreview() {
    TanawTheme(darkTheme = false) {
        LoadingSessionScreen(
            state = MobileState(page = "loading", busy = true),
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Shipment Details Light")
@Composable
private fun ShipmentDetailsLightPreview() {
    ShipmentDetailsPreviewContent(darkTheme = false)
}

@Preview(showBackground = true, name = "Shipment Details Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ShipmentDetailsDarkPreview() {
    ShipmentDetailsPreviewContent(darkTheme = true)
}

@Composable
private fun ShipmentDetailsPreviewContent(darkTheme: Boolean) {
    val sampleShipment = buildJsonObject {
        put("id", "ship-1")
        put("tracking_number", "TNW-2026-0042")
        put("status", "in_transit")
        put("pickup_address", "Cabanatuan City Public Market, Nueva Ecija")
        put("dropoff_address", "Pier 14, Manila North Harbor, Manila")
        put("cargo_description", "50 sacks of milled rice")
        put("weight_kg", "2500")
        put("driver_notes", "Call warehouse supervisor upon arrival at Gate 3.")
        put("quoted_amount", "12500")
        put("final_amount", "")
        put("scheduled_departure", "2026-04-20T06:00:00Z")
        put("estimated_arrival", "2026-04-20T10:30:00Z")
    }
    val sampleDelivery = buildJsonObject {
        put("id", "del-1")
        put("shipment_id", "ship-1")
        put("status", "in_transit")
        put("assigned_at", "2026-04-20T05:10:00Z")
        put("route_started_at", "2026-04-20T05:30:00Z")
        put("pickup_arrived_at", "2026-04-20T05:55:00Z")
        put("customer_showed_at", "2026-04-20T06:05:00Z")
        put("departed_at", "2026-04-20T06:45:00Z")
        put("arrived_at", "")
    }
    val sampleFreshness = PositionFreshness(
        status = PositionFreshnessStatus.FRESH,
        ageSeconds = 35L,
        relativeText = "Updated 35s ago",
        accuracyText = "accuracy 8 m"
    )
    val sampleEta = CustomerEtaPresentation(
        scheduledDeparture = "Apr 20, 2026 · 2:00 PM PHT",
        scheduledArrival = "Apr 20, 2026 · 6:30 PM PHT",
        liveEta = "ETA to destination: 6:15 PM PHT",
        liveEtaDetail = "Office-confirmed truck route · live GPS",
        isLive = true,
        freshness = sampleFreshness
    )
    TanawTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TanawSubScreenTopBar(
                    title = "TNW-2026-0042",
                    onBack = {},
                    trailing = { StatusBadge("in_transit") }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                ShipmentDetailsCards(
                    shipment = sampleShipment,
                    deliveries = listOf(sampleDelivery),
                    eta = sampleEta,
                    freshness = sampleFreshness,
                    lastTelemetryRecordedAt = "2026-04-20T08:12:00Z",
                    detailLoaded = true,
                    detailLoading = false,
                    mapSlot = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SupportingText("Live Map Preview")
                            }
                        }
                    },
                    timelineEvents = listOf(
                        "2026-04-20T05:10:00Z" to "Assigned",
                        "2026-04-20T06:45:00Z" to "In transit"
                    ),
                    documents = emptyList(),
                    busy = false,
                    onViewProof = {}
                )
            }
        }
    }
}
