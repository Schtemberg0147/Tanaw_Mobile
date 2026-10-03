package com.tanaw.app.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.coroutines.delay

private enum class PasswordAuthStep {
    METHOD_SELECTION,
    OTP_VERIFICATION,
    NEW_PASSWORD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: ProfileUiState = ProfileUiState(isLoading = false),
    onRetry: () -> Unit = {},
    onEditProfilePhoto: () -> Unit = {},
    onEditField: (fieldKey: String, currentValue: String) -> Unit = { _, _ -> },
    onSendPhoneOtp: (onResult: (Boolean, String, String) -> Unit) -> Unit = { _ -> },
    onSendEmailOtp: (email: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onVerifyOtp: (method: String, destination: String, token: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _, _, _ -> },
    onChangePassword: (newPassword: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onClearMessages: () -> Unit = {},
    onMenuClick: (menuId: String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    LaunchedEffect(uiState.updateSuccessMessage) {
        if (uiState.updateSuccessMessage != null) {
            delay(4000L)
            onClearMessages()
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing database connection...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (uiState.errorMessage != null && uiState.userProfile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Database Connection Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Retry Connection")
                }
            }
        }
        return
    }

    val user = uiState.userProfile
    val fullName = user?.fullName?.takeIf { it.isNotBlank() } ?: "User"
    val email = user?.email?.takeIf { it.isNotBlank() } ?: "No email set"
    val contact = user?.phone?.takeIf { it.isNotBlank() } ?: "No contact set"
    val defaultAddress = user?.address?.takeIf { it.isNotBlank() } ?: "No address set"
    val memberSince = formatMemberSince(user?.createdAt)

    var editingFieldKey by remember { mutableStateOf<String?>(null) }
    var editingFieldLabel by remember { mutableStateOf("") }
    var editingFieldValue by remember { mutableStateOf("") }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var authStep by remember { mutableStateOf(PasswordAuthStep.METHOD_SELECTION) }
    var selectedMethod by remember { mutableStateOf("PHONE") }
    var selectedDestination by remember { mutableStateOf("") }
    var otpToken by remember { mutableStateOf("") }
    var stepError by remember { mutableStateOf<String?>(null) }
    var isSubmittingStep by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    if (editingFieldKey != null) {
        AlertDialog(
            onDismissRequest = { editingFieldKey = null },
            title = { Text(text = "Edit $editingFieldLabel") },
            text = {
                OutlinedTextField(
                    value = editingFieldValue,
                    onValueChange = { editingFieldValue = it },
                    label = { Text(editingFieldLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = editingFieldKey!!
                        val valToSave = editingFieldValue
                        editingFieldKey = null
                        onEditField(key, valToSave)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFieldKey = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingStep) {
                    showChangePasswordDialog = false
                    authStep = PasswordAuthStep.METHOD_SELECTION
                    otpToken = ""
                    newPassword = ""
                    confirmPassword = ""
                    stepError = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_shield_lock),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (authStep) {
                            PasswordAuthStep.METHOD_SELECTION -> "Verify Your Identity"
                            PasswordAuthStep.OTP_VERIFICATION -> "Enter Verification Code"
                            PasswordAuthStep.NEW_PASSWORD -> "Set New Password"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (authStep) {
                        PasswordAuthStep.METHOD_SELECTION -> {
                            Text(
                                text = "Choose a verification method to confirm it's really you before changing your password:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Option 1: Phone (SMS)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSubmittingStep) {
                                        selectedMethod = "PHONE"
                                        stepError = null
                                        isSubmittingStep = true
                                        onSendPhoneOtp { success, phoneUsed, msg ->
                                            isSubmittingStep = false
                                            if (success) {
                                                selectedDestination = phoneUsed
                                                authStep = PasswordAuthStep.OTP_VERIFICATION
                                            } else {
                                                stepError = msg
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_nav_track),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SMS Phone Verification",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = contact,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Option 2: Email (UI placeholder)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSubmittingStep) {
                                        selectedMethod = "EMAIL"
                                        selectedDestination = email
                                        stepError = null
                                        isSubmittingStep = true
                                        onSendEmailOtp(email) { success, msg ->
                                            isSubmittingStep = false
                                            if (success) {
                                                authStep = PasswordAuthStep.OTP_VERIFICATION
                                            } else {
                                                stepError = msg
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_mail_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Email Verification",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "(UI Only)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(
                                            text = email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (stepError != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stepError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        PasswordAuthStep.OTP_VERIFICATION -> {
                            Text(
                                text = "We sent a 6-digit code to $selectedDestination. Please enter it below to verify your identity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = otpToken,
                                onValueChange = {
                                    if (it.length <= 6) {
                                        otpToken = it
                                        stepError = null
                                    }
                                },
                                label = { Text("6-Digit Code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (stepError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stepError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        PasswordAuthStep.NEW_PASSWORD -> {
                            Text(
                                text = "Your identity is verified! Enter your new password below (at least 8 characters).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = {
                                    newPassword = it
                                    stepError = null
                                },
                                label = { Text("New Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    stepError = null
                                },
                                label = { Text("Confirm New Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (stepError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stepError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when (authStep) {
                    PasswordAuthStep.METHOD_SELECTION -> {
                        // Handled by row clicks above
                    }
                    PasswordAuthStep.OTP_VERIFICATION -> {
                        Button(
                            enabled = !isSubmittingStep && otpToken.length == 6,
                            onClick = {
                                isSubmittingStep = true
                                stepError = null
                                onVerifyOtp(selectedMethod, selectedDestination, otpToken) { success, msg ->
                                    isSubmittingStep = false
                                    if (success) {
                                        authStep = PasswordAuthStep.NEW_PASSWORD
                                        stepError = null
                                    } else {
                                        stepError = msg
                                    }
                                }
                            }
                        ) {
                            if (isSubmittingStep) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Verify Code")
                            }
                        }
                    }
                    PasswordAuthStep.NEW_PASSWORD -> {
                        Button(
                            enabled = !isSubmittingStep,
                            onClick = {
                                when {
                                    newPassword.length < 8 -> {
                                        stepError = "Password must be at least 8 characters."
                                    }
                                    newPassword != confirmPassword -> {
                                        stepError = "Passwords do not match."
                                    }
                                    else -> {
                                        isSubmittingStep = true
                                        stepError = null
                                        onChangePassword(newPassword) { success, msg ->
                                            isSubmittingStep = false
                                            if (success) {
                                                showChangePasswordDialog = false
                                                authStep = PasswordAuthStep.METHOD_SELECTION
                                                otpToken = ""
                                                newPassword = ""
                                                confirmPassword = ""
                                            } else {
                                                stepError = msg
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            if (isSubmittingStep) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Update Password")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingStep,
                    onClick = {
                        showChangePasswordDialog = false
                        authStep = PasswordAuthStep.METHOD_SELECTION
                        otpToken = ""
                        newPassword = ""
                        confirmPassword = ""
                        stepError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "My Profile",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 60.dp)
            ) {

                // Top Toast Success Banner inside Column
                AnimatedVisibility(
                    visible = uiState.updateSuccessMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    Surface(
                        color = TanawTheme.extendedColors.successBannerBackground,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_check_circle),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.updateSuccessMessage ?: "",
                                color = TanawTheme.extendedColors.onSuccessBannerText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onClearMessages,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Dismiss",
                                    tint = TanawTheme.extendedColors.onSuccessBannerText,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initialsFromName(fullName),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = onEditProfilePhoto,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(2.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_camera),
                                    contentDescription = "Edit photo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fullName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = memberSince,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Personal Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ProfileField(
                            label = "Full Name",
                            value = fullName,
                            onEdit = {
                                editingFieldKey = "fullName"
                                editingFieldLabel = "Full Name"
                                editingFieldValue = fullName
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileField(
                            label = "Email",
                            value = email,
                            onEdit = {
                                editingFieldKey = "email"
                                editingFieldLabel = "Email"
                                editingFieldValue = email
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileField(
                            label = "Contact",
                            value = contact,
                            onEdit = {
                                editingFieldKey = "contact"
                                editingFieldLabel = "Contact"
                                editingFieldValue = contact
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileField(
                            label = "Default Address",
                            value = defaultAddress,
                            onEdit = {
                                editingFieldKey = "address"
                                editingFieldLabel = "Default Address"
                                editingFieldValue = defaultAddress
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MenuItemRow(text = "My Bookings", onClick = { onMenuClick("my_bookings") })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MenuItemRow(text = "Saved Addresses", onClick = { onMenuClick("saved_addresses") })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MenuItemRow(
                            text = "Change Password",
                            onClick = {
                                authStep = PasswordAuthStep.METHOD_SELECTION
                                otpToken = ""
                                newPassword = ""
                                confirmPassword = ""
                                stepError = null
                                showChangePasswordDialog = true
                                onMenuClick("change_password")
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MenuItemRow(text = "Terms of Service", onClick = { onMenuClick("tos") })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MenuItemRow(text = "Help & Support", onClick = { onMenuClick("help_support") })
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MenuItemRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun initialsFromName(name: String): String {
    return name.split(" ")
        .filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "U" }
}

private fun formatMemberSince(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return "MEMBER SINCE 2025"
    return try {
        val year = createdAt.take(4)
        val monthNum = createdAt.drop(5).take(2).toIntOrNull() ?: 1
        val months = listOf(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
        )
        val monthName = months.getOrElse(monthNum - 1) { "JANUARY" }
        "MEMBER SINCE $monthName $year"
    } catch (_: Exception) {
        "MEMBER SINCE 2025"
    }
}
