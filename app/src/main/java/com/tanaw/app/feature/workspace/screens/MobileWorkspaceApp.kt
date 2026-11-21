package com.tanaw.app.feature.workspace.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import com.tanaw.app.feature.auth.rules.GmailOtpPasswordRules
import com.tanaw.app.feature.auth.screens.CreateAccountScreen
import com.tanaw.app.feature.auth.screens.ForgotPasswordCardScreen
import com.tanaw.app.feature.auth.screens.LoginScreen
import com.tanaw.app.feature.auth.screens.ResetPasswordScreen
import com.tanaw.app.feature.auth.screens.VerificationCodeScreen
import com.tanaw.app.feature.auth.screens.VerifyRecoveryCodeScreen
import com.tanaw.app.feature.workspace.customer.components.AddressQuickAssistBar
import com.tanaw.app.feature.workspace.customer.components.CitySuggestionStrip
import com.tanaw.app.feature.workspace.customer.components.CustomerTopBar
import com.tanaw.app.feature.workspace.customer.components.TanawBrandLogo
import com.tanaw.app.feature.workspace.customer.dialogs.CustomerCancellationSection
import com.tanaw.app.feature.workspace.customer.dialogs.SuspensionAppealDialog
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerHistoryPresentation
import com.tanaw.app.feature.workspace.customer.rules.CustomerNoShowRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerPaginationRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerProfileRules
import com.tanaw.app.feature.workspace.customer.screens.AccountSuspendedScreen
import com.tanaw.app.feature.workspace.customer.screens.CustomerBookingFlow
import com.tanaw.app.feature.workspace.customer.screens.CustomerProfile
import com.tanaw.app.feature.workspace.customer.screens.CustomerShipmentsSection
import com.tanaw.app.feature.workspace.customer.screens.NoShowCancelledScreen
import com.tanaw.app.feature.workspace.customer.screens.ShipmentTrackingPreviewContent
import com.tanaw.app.feature.workspace.customer.screens.ShipmentTrackingScreen
import com.tanaw.app.feature.workspace.driver.components.DriverRouteStatus
import com.tanaw.app.feature.workspace.driver.rules.DriverProfileRules
import com.tanaw.app.feature.workspace.driver.screens.DriverWorkspace
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.rules.ShipmentDeliveryRules
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.tracking.TrackingFreshnessRules
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.ShipmentMap
import com.tanaw.app.ui.components.SupportingText
import com.tanaw.app.ui.components.TanawSubScreenTopBar
import com.tanaw.app.ui.components.WorkspaceCard
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import com.tanaw.app.ui.theme.TanawBodyFontFamily
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
            val policySummary = if (title == "Privacy Policy") {
                "Mabutol Trucking Services collects your contact information, default pickup address, and shipment booking records to fulfill Full Truckload (FTL) deliveries. Live truck GPS telemetry and Proof of Delivery photos are protected by row-level security (RLS) in accordance with the Philippine Data Privacy Act of 2012 (RA 10173). Full policy details are available under Profile > Privacy Policy."
            } else {
                "Tanaw provides scheduled Full Truckload (FTL) freight bookings across Luzon with 24h/48h advance lead-time scheduling (up to 14 days ahead). Pre-booking estimates show base linehaul only; official office quotations itemize Base Linehaul, Estimated Tolls, and any Mobilization Fee and must be confirmed before dispatch. Self-service cancellation is available prior to driver route start. Full terms are available under Profile > Terms of Service."
            }
            InfoDialog(
                title = title,
                text = policySummary
            ) { legal = null }
        }
        return
    }

    if (state.page == "signup") {
        BackHandler(!state.busy) { vm.page("login") }
        var terms by rememberSaveable { mutableStateOf(false) }
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
                title = "Terms of Service & Privacy Policy",
                text = "By creating an account, you agree to provide accurate contact and cargo weight details for scheduled Full Truckload (FTL) bookings (24h/48h minimum lead time). All bookings require an official itemized quotation confirmed prior to dispatch. Your personal data and shipment records are protected under the Data Privacy Act of 2012 (RA 10173). Full terms and privacy sections are available in Profile after sign-in."
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
        BackHandler(!state.busy) { vm.cancelRecovery() }
        VerifyRecoveryCodeScreen(state = state, vm = vm)
        return
    }

    if (state.page == "recover_password") {
        BackHandler(!state.busy) { vm.cancelRecovery() }
        ResetPasswordScreen(state = state, vm = vm)
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
                var code by rememberSaveable { mutableStateOf("") }
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

    val profile = data.obj("profile")
    val role = profile.value("role").trim().lowercase()
    val isCustomer = role == "customer"
    val isDriver = role == "driver"
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )
    var notificationPermissionPrompted by rememberSaveable(profile.value("id")) { mutableStateOf(false) }
    LaunchedEffect(profile.value("id")) {
        if (!notificationPermissionPrompted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionPrompted = true
            val granted = ContextCompat.checkSelfPermission(
                activityContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }
    val renderCustomerOrDefault: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (isCustomer) {
            TanawCustomerTheme { content() }
        } else {
            content()
        }
    }

    renderCustomerOrDefault {
        key(profile.value("id")) {
            val customerStanding = remember(data) {
                if (isCustomer) CustomerNoShowRules.evaluateStanding(data) else null
            }
            if (isCustomer &&
                MobileValidation.phone(profile.value("phone")) == null
            ) {
                OnboardingProfileGate(
                    vm = vm,
                    state = state,
                    data = data
                )
            } else if (isCustomer && customerStanding?.isSuspended == true) {
                var showAppealDialog by rememberSaveable { mutableStateOf(false) }
                AccountSuspendedScreen(
                    standing = customerStanding,
                    busy = state.busy,
                    offline = state.offline,
                    statusMessage = state.message,
                    errorMessage = state.error,
                    onOpenAppealForm = { showAppealDialog = true },
                    onRefresh = vm::refresh,
                    onLogout = vm::logout
                )
                if (showAppealDialog) {
                    SuspensionAppealDialog(
                        initialPhone = profile.value("phone"),
                        isSubmitting = state.busy,
                        offline = state.offline,
                        errorMessage = state.error,
                        onDismiss = { showAppealDialog = false },
                        onSubmit = { reason, contactNumber ->
                            vm.submitSuspensionAppeal(
                                reason = reason,
                                contactNumber = contactNumber,
                                onSubmitted = { showAppealDialog = false }
                            )
                        }
                    )
                }
            } else if (isDriver) {
                DriverWorkspace(vm, state, data)
            } else if (isCustomer) {
                Workspace(vm, state, data)
            } else {
                OfficePortalNoticeScreen(
                    role = role,
                    email = profile.value("email"),
                    state = state,
                    onRetry = vm::refresh,
                    onLogout = vm::logout
                )
            }
        }

        if (state.passwordChangeOwner != null) {
            val useGmailOtp = GmailOtpPasswordRules.shouldUseGmailOtpBackend(
                target = state.email,
                baseUrl = BuildConfig.PASSWORD_API_BASE_URL
            )
            PasswordChangeDialog(
                state,
                vm::cancelPasswordChange,
                { if (useGmailOtp) vm.sendOtp() else vm.resetEmail(state.email) },
                { code ->
                    if (useGmailOtp) GmailOtpPasswordRules.isValidOtp(code) else vm.canVerify(code, "recovery")
                },
                { code, password, confirm ->
                    if (useGmailOtp) {
                        vm.verifyAndChangePassword(code, password, confirm)
                    } else {
                        vm.recover(code, password, confirm)
                    }
                },
                onEmailChange = if (BuildConfig.PASSWORD_API_BASE_URL.isNotBlank()) vm::updatePasswordChangeEmail else null
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
private fun OfficePortalNoticeScreen(
    role: String,
    email: String,
    state: MobileState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roleLabel = role.takeIf { it.isNotBlank() }?.uppercase() ?: "OFFICE ACCOUNT"
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
                    TanawBrandLogo(modifier = Modifier.size(48.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = roleLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TanawMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "Use the Tanaw web portal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "This mobile app supports Customer and Driver accounts. Office admin and staff operations are managed on the Tanaw web dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (email.isNotBlank()) {
                        Text(
                            text = "Signed in as $email",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = TanawMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Notices(state, showLinearProgress = false)
                    Action(label = "Refresh account", enabled = !state.busy, onClick = onRetry)
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
    var showSignOutConfirm by rememberSaveable { mutableStateOf(false) }
    val windowSize = LocalTanawWindowSize.current

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) showSignOutConfirm = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Sign out of Tanaw?",
                    fontFamily = TanawDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = "Any unsaved profile details will be cleared until you sign in again.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        vm.logout()
                    },
                    enabled = !state.busy
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutConfirm = false },
                    enabled = !state.busy
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TanawBrandLogo(modifier = Modifier.size(30.dp))
                        Text(
                            text = "Complete your profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = TanawDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.busy) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = windowSize.formMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.horizontalGutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Notices(state, showLinearProgress = false)
            SupportingText("Add your verified Philippine mobile number and default pickup address before booking shipments.")
            WorkspaceCard {
                OnboardingProfileForm(
                    state = state,
                    data = data,
                    onSaveProfile = vm::profile
                )
            }
            OutlinedButton(
                onClick = { showSignOutConfirm = true },
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
}

@Composable
private fun Workspace(vm: MobileWorkspaceViewModel, state: MobileState, data: JsonObject) {
    var tab by rememberSaveable { mutableStateOf(if (state.passwordChangeOwner != null) "Profile" else "Home") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable(tab) { mutableStateOf("") }
    var historyFilter by rememberSaveable { mutableStateOf("All") }
    var fullScreenBookingStep by rememberSaveable { mutableStateOf(false) }
    var profileSubPanelOpen by rememberSaveable { mutableStateOf(false) }
    val bookingDraft = rememberSaveableStateHolder()
    val profile = data.obj("profile")
    val role = profile.value("role")
    val isCustomerHome = tab == "Home" && detail == null
    val isCustomerProfile = tab == "Profile" && detail == null
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

    LaunchedEffect(state.pendingOpenAlerts, state.pendingNotificationShipmentId, allShipments) {
        if (state.pendingOpenAlerts || state.pendingNotificationShipmentId != null) {
            val targetShipmentId = state.pendingNotificationShipmentId?.takeIf { id ->
                allShipments.any { it.value("id") == id }
            }
            if (targetShipmentId != null) {
                tab = "Active"
                detail = targetShipmentId
            } else {
                tab = "Alerts"
                detail = null
            }
            vm.consumePendingNotificationNavigation()
        }
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
    val windowSize = LocalTanawWindowSize.current
    val useNavRail = showBottomBar && windowSize.useNavRail
    val customerTabs = listOf(
        "Home" to Icons.Outlined.Home,
        "Active" to Icons.Outlined.LocalShipping,
        "History" to Icons.Outlined.History,
        "Alerts" to Icons.Outlined.Notifications,
        "Profile" to Icons.Outlined.Person
    )

    Row(modifier = Modifier.fillMaxSize()) {
        if (useNavRail) {
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxHeight()
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    NavigationRail(
                        containerColor = Color.White,
                        contentColor = Color(0xFF002D54),
                        modifier = Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                            .padding(vertical = 8.dp)
                    ) {
                        customerTabs.forEach { (label, icon) ->
                            val selected = tab == label
                            val unreadAlerts = if (label == "Alerts") unreadAlertsCount else 0
                            NavigationRailItem(
                                selected = selected,
                                enabled = !state.busy,
                                onClick = {
                                    tab = label
                                    detail = null
                                    if (label != "Profile") profileSubPanelOpen = false
                                },
                                icon = {
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
                                                imageVector = icon,
                                                contentDescription = label,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontFamily = TanawBodyFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color(0xFF002D54),
                                    selectedTextColor = Color(0xFF002D54),
                                    indicatorColor = Color(0xFF002D54).copy(alpha = 0.12f),
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B)
                                )
                            )
                        }
                    }
                    VerticalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                }
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
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
                if (showBottomBar && !useNavRail) {
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
                                customerTabs.forEach { (label, icon) ->
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
                                                        imageVector = icon,
                                                        contentDescription = label,
                                                        tint = itemColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = icon,
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
                    onBack = { detail = null },
                    onBookAgain = {
                        detail = null
                        tab = "Home"
                    }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .let { if (detail != null) it.navigationBarsPadding() else it }
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
            Column(
                modifier = Modifier
                    .widthIn(max = windowSize.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.horizontalGutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    // Alerts tab
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
                        text = "Delivery milestones, quotation updates, and dispatch alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsNone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "No notifications yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = TanawDisplayFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                SupportingText(
                                    text = "Delivery milestones, quotation updates, and dispatch alerts will appear here.",
                                )
                            }
                        }
                    }
                    val renderAlertCard: @Composable (JsonObject, Modifier) -> Unit = { n, cardMod ->
                        val isUnread = n.value("read_at").isBlank()
                        val hasLinkedShipment = allShipments.any { it.value("id") == n.value("shipment_id") }
                        val clickableModifier = cardMod
                            .let {
                                if (hasLinkedShipment) {
                                    it.clip(RoundedCornerShape(16.dp)).clickable { detail = n.value("shipment_id") }
                                } else {
                                    it
                                }
                            }
                        Surface(
                            modifier = clickableModifier,
                            shape = RoundedCornerShape(16.dp),
                            color = if (isUnread) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isUnread) {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isUnread) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.secondary)
                                            )
                                        }
                                        Text(
                                            text = n.value("title"),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontFamily = TanawDisplayFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = n.value("body"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = MobilePresentation.time(n.value("created_at")),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = TanawMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (hasLinkedShipment) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = "Open shipment",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (windowSize.gridColumns >= 2 && displayedNotifications.size > 1) {
                        displayedNotifications.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(windowSize.cardSpacing)
                            ) {
                                pair.forEach { n ->
                                    renderAlertCard(n, Modifier.weight(1f))
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        displayedNotifications.forEach { n ->
                            renderAlertCard(n, Modifier.fillMaxWidth())
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
                Spacer(Modifier.height(12.dp))
            }
            }
        }
        }
    }

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
private fun ShipmentDetails(
    s: JsonObject,
    data: JsonObject,
    role: String,
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    onBack: () -> Unit,
    onBookAgain: () -> Unit = onBack
) {
    val shipmentDeliveries = data.rows("deliveries").filter { it.value("shipment_id") == s.value("id") }
    val deliveries = ShipmentDeliveryRules.displayDeliveries(s, shipmentDeliveries)
    val activeDelivery = ShipmentDeliveryRules.primaryDelivery(deliveries)
    val detail = state.details?.takeIf { it.value("shipment_id") == s.value("id") }

    val isWaitingAtPickup = activeDelivery != null &&
        activeDelivery.value("status").equals("assigned", ignoreCase = true) &&
        activeDelivery.value("pickup_arrived_at").isNotBlank() &&
        activeDelivery.value("customer_showed_at").isBlank() &&
        !s.value("status").equals("cancelled", ignoreCase = true)

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(isWaitingAtPickup) {
        while (true) {
            kotlinx.coroutines.delay(if (isWaitingAtPickup) 1.seconds else 15.seconds)
            now = Instant.now()
        }
    }
    LaunchedEffect(s.value("id")) { vm.loadDetails(s.value("id")) }

    val customerStanding = remember(data, now) {
        if (role == "customer") CustomerNoShowRules.evaluateStanding(data, now) else null
    }
    val isNoShowCancelled = role == "customer" && CustomerNoShowRules.isNoShowCancellation(
        shipment = s,
        delivery = activeDelivery,
        detail = detail,
        strikes = customerStanding?.strikes.orEmpty()
    )
    if (isNoShowCancelled && customerStanding != null) {
        var showSupportDialog by rememberSaveable { mutableStateOf(false) }
        val strikeNumber = CustomerNoShowRules.strikeNumberForShipment(s, customerStanding)
        val matchedStrike = customerStanding.strikes.firstOrNull { it.shipmentId == s.value("id") }
        val timelineSteps = remember(s, activeDelivery, detail, matchedStrike) {
            CustomerNoShowRules.buildNoShowTimeline(s, activeDelivery, detail, matchedStrike)
        }
        val cancelledAtRaw = matchedStrike?.confirmedAt.orEmpty()
            .ifBlank { activeDelivery?.value("no_show_confirmed_at").orEmpty() }
            .ifBlank { s.value("cancelled_at") }
            .ifBlank { s.value("updated_at") }
        NoShowCancelledScreen(
            trackingNumber = s.value("tracking_number"),
            cancelledTimestampText = CustomerNoShowRules.formatCancelledTimestamp(cancelledAtRaw),
            strikeNumber = strikeNumber,
            standing = customerStanding,
            timelineSteps = timelineSteps,
            busy = state.busy,
            onBack = onBack,
            onBookAgain = onBookAgain,
            onContactSupport = { showSupportDialog = true }
        )
        if (showSupportDialog) {
            val content = DriverProfileRules.content(data)
            val supportInfo = buildString {
                val contact = content?.get("support_contact")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: content?.get("support_phone")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: content?.get("support_url")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                if (!contact.isNullOrBlank()) {
                    append("Reach Mabutol Trucking dispatch support at:\n$contact\n\n")
                } else {
                    append("Contact Mabutol Trucking Services dispatch office for assistance with no-show cancellations or strike inquiries.\n\n")
                }
                append("Reference: ${s.value("tracking_number").ifBlank { s.value("id") }}")
            }
            InfoDialog(
                title = "Contact Support",
                text = supportInfo
            ) { showSupportDialog = false }
        }
        return
    }

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
        } else null,
        onNotifyOnWay = if (role == "customer") {
            { deliveryId -> vm.notifyDriverOnMyWay(deliveryId) }
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
private fun OnboardingProfileForm(
    state: MobileState,
    data: JsonObject,
    onSaveProfile: (name: String, phone: String, address: String, city: String) -> Unit
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
    val addressValid = CustomerAddressRules.isValidAddress(street, barangay, city, province)
    val canSave = !state.busy &&
        name.trim().length in 2..200 &&
        MobileValidation.phone(phone) != null &&
        addressValid

    val saveProfile: () -> Unit = {
        val (formattedAddress, formattedCity) = CustomerAddressRules.formatForRpc(street, barangay, city, province)
        onSaveProfile(name, phone, formattedAddress, formattedCity)
    }

    Text(
        text = "Account details",
        style = MaterialTheme.typography.titleLarge,
        fontFamily = TanawDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    val resolvedEmail = CustomerProfileRules.resolveEmail(
        profileEmail = p.value("email"),
        customerEmail = c.value("email")
    )
    SupportingText(
        text = if (resolvedEmail.isNotBlank()) {
            "Sign-in email: $resolvedEmail"
        } else {
            "Customer account"
        }
    )

    val isNameError = name.isNotBlank() && name.trim().length !in 2..200
    val isPhoneError = phone.isNotBlank() && MobileValidation.phone(phone) == null

    Field(
        label = "Full name",
        value = name,
        change = { name = it },
        isError = isNameError,
        supportingText = if (isNameError) {
            { Text("Name must be between 2 and 200 characters.") }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    Field(
        label = "Mobile number",
        value = phone,
        change = { phone = it },
        isError = isPhoneError,
        supportingText = {
            if (isPhoneError) {
                Text("Enter a Philippine mobile number, e.g. 0912 345 6789.")
            } else {
                Text("An SMS code will be sent to verify your number.")
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )

    val addressSummary = CustomerAddressRules.StructuredCustomerAddress(
        streetAddress = street,
        barangay = barangay,
        city = city,
        province = province
    ).fullDisplayAddress()

    AddressQuickAssistBar(
        currentAddressSummary = addressSummary,
        enabled = !state.busy,
        onAddressAutofilled = { resolved ->
            if (resolved.streetAddress.isNotBlank()) street = resolved.streetAddress
            if (resolved.barangay.isNotBlank()) barangay = resolved.barangay
            if (resolved.city.isNotBlank()) city = resolved.city
            if (resolved.province.isNotBlank()) province = resolved.province
        }
    )

    val streetInvalid = street.isNotBlank() && !CustomerAddressRules.validateStreet(street)
    val brgyInvalid = barangay.isNotBlank() && !CustomerAddressRules.validateBarangay(barangay)
    val cityInvalid = city.isNotBlank() && !CustomerAddressRules.validateCity(city)
    val provInvalid = province.isNotBlank() && !CustomerAddressRules.validateProvince(province)

    Field(
        label = "House / Street address",
        value = street,
        change = { street = it },
        isError = streetInvalid,
        supportingText = if (streetInvalid) {
            { Text("Street address must be 380 characters or fewer.") }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    Field(
        label = "Barangay",
        value = barangay,
        change = { barangay = it },
        isError = brgyInvalid,
        supportingText = if (brgyInvalid) {
            { Text("Barangay must be 100 characters or fewer.") }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    Field(
        label = "City / Municipality",
        value = city,
        change = { city = it },
        isError = cityInvalid,
        supportingText = if (cityInvalid) {
            { Text("City must be 60 characters or fewer.") }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    CitySuggestionStrip(
        cityQuery = city,
        enabled = !state.busy,
        onSelectCity = { suggestion ->
            city = suggestion.city
            province = suggestion.province
        }
    )
    Field(
        label = "Province",
        value = province,
        change = { province = it },
        isError = provInvalid,
        supportingText = if (provInvalid) {
            { Text("Province must be 35 characters or fewer.") }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                if (canSave) saveProfile()
            }
        )
    )

    Action(
        label = "Save and continue",
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

@Preview(showBackground = true, name = "Office Portal Notice")
@Composable
private fun OfficePortalNoticePreview() {
    TanawTheme {
        OfficePortalNoticeScreen(
            role = "admin",
            email = "dispatcher@mabutol.example",
            state = MobileState(page = "workspace"),
            onRetry = {},
            onLogout = {}
        )
    }
}

@Preview(showBackground = true, name = "Onboarding Profile Form")
@Composable
private fun OnboardingProfileFormPreview() {
    TanawCustomerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                WorkspaceCard {
                    OnboardingProfileForm(
                        state = MobileState(page = "workspace"),
                        data = buildJsonObject {
                            put("profile", buildJsonObject {
                                put("full_name", "Maria Santos")
                                put("email", "maria@example.com")
                            })
                        },
                        onSaveProfile = { _, _, _, _ -> }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Shipment Details")
@Composable
private fun ShipmentDetailsPreview() {
    ShipmentTrackingPreviewContent()
}
