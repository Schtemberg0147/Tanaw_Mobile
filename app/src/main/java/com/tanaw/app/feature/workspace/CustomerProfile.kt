package com.tanaw.app.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import kotlinx.serialization.json.*

@Composable
internal fun CustomerProfile(
    vm: MobileWorkspaceViewModel,
    state: MobileState,
    data: JsonObject,
    onOpenAlerts: () -> Unit,
    onOpenBookings: () -> Unit,
    onLogout: () -> Unit
) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current

    val p = data.obj("profile")
    val c = data.obj("customer")
    val settings = DriverProfileRules.settings(data)
    val content = DriverProfileRules.content(data)

    var editingFieldKey by remember { mutableStateOf<String?>(null) }
    var editingFieldValue by remember { mutableStateOf("") }
    var editingCityValue by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    fun openUriSafely(value: String) {
        linkError = runCatching { uri.openUri(value) }.isFailure
    }

    BackHandler(panel != null) { panel = null }

    if (panel != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = { panel = null }) {
                Text("← Back to profile", color = DriverNavy, fontWeight = FontWeight.Bold)
            }
            Text(
                text = panel!!,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = DriverNavy
            )

            if (linkError) {
                Text(
                    text = "This device could not open the link. Check that a browser or suitable app is installed.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when (panel) {
                "Saved Addresses" -> {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DriverLine)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Default Saved Address",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DriverNavy
                            )
                            Text(
                                text = "Your saved address is prefilled as your pickup address when booking new deliveries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DriverMuted
                            )
                            HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "ADDRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DriverMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = c.value("address").ifBlank { "No default address set" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DriverNavy
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "CITY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DriverMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = c.value("city").ifBlank { "Not set" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DriverNavy
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    editingFieldKey = "address"
                                    editingFieldValue = c.value("address")
                                    editingCityValue = c.value("city")
                                    editError = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DriverNavy),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Edit Default Address", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                "Help & Support" -> {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DriverLine)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Delivery Support",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DriverNavy
                            )
                            Text(
                                text = "For pickup instructions, quotation questions, or delivery inquiries, have your tracking reference ready before contacting the office.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DriverMuted
                            )
                        }
                    }

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DriverLine)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Published Office Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DriverNavy
                            )
                            val email = content?.value("support_email")?.let(DriverProfileRules::email)
                            val phone = content?.value("support_phone")?.let(DriverProfileRules::phone)
                            val url = content?.value("support_url")?.let(DriverProfileRules::https)

                            if (settings == null) {
                                Text("Office contacts unavailable. Tap Refresh to retry.", style = MaterialTheme.typography.bodySmall, color = DriverMuted)
                            } else if (email == null && phone == null && url == null) {
                                Text("The office has not published contact channels in this app. Please contact your dispatch representative.", style = MaterialTheme.typography.bodySmall, color = DriverMuted)
                            }
                            email?.let { TextButton(onClick = { openUriSafely("mailto:$it") }) { Text("Email: $it", color = DriverNavy) } }
                            phone?.let { TextButton(onClick = { openUriSafely("tel:$it") }) { Text("Call: $it", color = DriverNavy) } }
                            url?.let { TextButton(onClick = { openUriSafely(it) }) { Text("Open Support Website", color = DriverNavy) } }
                        }
                    }
                }

                else -> {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DriverLine)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val key = if (panel == "Privacy") "privacy_url" else "terms_url"
                            val url = content?.value(key)?.let(DriverProfileRules::https)
                            if (settings == null) {
                                Text("Published policy information is unavailable. Tap Refresh to retry.", style = MaterialTheme.typography.bodySmall, color = DriverMuted)
                            } else if (url == null) {
                                Text("The office has not published an approved ${panel?.lowercase()} link in this app. Contact the office for the current document.", style = MaterialTheme.typography.bodySmall, color = DriverMuted)
                            } else {
                                Text("Open the official ${panel?.lowercase()} in your web browser.", style = MaterialTheme.typography.bodySmall, color = DriverMuted)
                                Button(
                                    onClick = { openUriSafely(url) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DriverNavy),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Open ${panel?.lowercase()}")
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (editingFieldKey != null) {
        val title = when (editingFieldKey) {
            "full_name" -> "Edit Full Name"
            "phone" -> "Edit Contact Number"
            "address" -> "Edit Default Address"
            else -> "Edit Profile"
        }
        AlertDialog(
            onDismissRequest = { if (!state.busy) editingFieldKey = null },
            title = { Text(title, fontWeight = FontWeight.Bold, color = DriverNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (editingFieldKey) {
                        "full_name" -> {
                            OutlinedTextField(
                                value = editingFieldValue,
                                onValueChange = { editingFieldValue = it; editError = null },
                                label = { Text("Full Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "phone" -> {
                            OutlinedTextField(
                                value = editingFieldValue,
                                onValueChange = { editingFieldValue = it; editError = null },
                                label = { Text("Phone Number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "address" -> {
                            OutlinedTextField(
                                value = editingFieldValue,
                                onValueChange = { editingFieldValue = it; editError = null },
                                label = { Text("Address") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editingCityValue,
                                onValueChange = { editingCityValue = it; editError = null },
                                label = { Text("City") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (editError != null) {
                        Text(editError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (editingFieldKey) {
                            "full_name" -> {
                                if (editingFieldValue.trim().length !in 2..200) {
                                    editError = "Name must be between 2 and 200 characters."
                                    return@Button
                                }
                                vm.profile(editingFieldValue, p.value("phone"), c.value("address"), c.value("city"))
                                editingFieldKey = null
                            }
                            "phone" -> {
                                val normalized = MobileValidation.phone(editingFieldValue)
                                if (normalized == null) {
                                    editError = "Please enter a valid phone number."
                                    return@Button
                                }
                                vm.profile(p.value("full_name"), normalized, c.value("address"), c.value("city"))
                                editingFieldKey = null
                            }
                            "address" -> {
                                if (editingFieldValue.trim().length > 500 || editingCityValue.trim().length > 100) {
                                    editError = "Address or city exceeds maximum length."
                                    return@Button
                                }
                                vm.profile(p.value("full_name"), p.value("phone"), editingFieldValue, editingCityValue)
                                editingFieldKey = null
                            }
                        }
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = DriverNavy)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFieldKey = null }, enabled = !state.busy) {
                    Text("Cancel")
                }
            }
        )
    }

    if (infoDialog != null) {
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text("Profile Information", fontWeight = FontWeight.Bold, color = DriverNavy) },
            text = { Text(infoDialog!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text("Close", color = DriverNavy, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val unreadNotifications = data.rows("notifications").count { it.value("read_at").isBlank() }
    val isSmsEnabled = data.rows("sms_preferences").any {
        it.value("type") == "shipment_status" && it.get("enabled") == JsonPrimitive(true)
    }

    val fullName = p.value("full_name").ifBlank { "Customer" }
    val email = p.value("email").ifBlank { "Phone-number account" }
    val contact = p.value("phone").ifBlank { "No contact set" }
    val defaultAddress = c.value("address").ifBlank { "No default address set" }
    val memberSince = CustomerProfilePresentation.memberSince(p.value("created_at"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // ── Top Header Bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_tanaw_logo),
                contentDescription = "Tanaw",
                modifier = Modifier.height(28.dp)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenAlerts)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = DriverNavy,
                    modifier = Modifier.size(26.dp)
                )
                if (unreadNotifications > 0) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(MaterialTheme.colorScheme.secondary, shape = CircleShape)
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
        HorizontalDivider(color = DriverLine, thickness = 1.dp)

        // ── Scrollable Body ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Screen Title
            Text(
                text = "My Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = DriverNavy
            )

            // Status Notices (Loading / Error / Success)
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DriverNavy)
            }
            state.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp)
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
                    color = DriverNavy.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = DriverNavy,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Hero Avatar Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(DriverNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = CustomerProfilePresentation.initials(fullName),
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color.White
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, DriverLine),
                        modifier = Modifier
                            .offset(x = 6.dp, y = 6.dp)
                            .size(32.dp)
                            .clickable {
                                infoDialog = "Profile photos are managed with your office profile. Contact your administrator to update your photo."
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_camera),
                                contentDescription = "Edit photo",
                                tint = DriverNavy,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = fullName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = DriverNavy
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DriverMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = memberSince,
                    style = MaterialTheme.typography.labelSmall,
                    color = DriverMuted.copy(alpha = 0.8f),
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Personal Information Section ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = DriverNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Personal Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DriverNavy
                    )
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DriverLine)
                ) {
                    Column {
                        CustomerProfileRow(
                            label = "Full Name",
                            value = fullName,
                            isTinted = false,
                            isLocked = false,
                            onClick = {
                                editingFieldKey = "full_name"
                                editingFieldValue = p.value("full_name")
                                editError = null
                            }
                        )
                        HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                        CustomerProfileRow(
                            label = "Email",
                            value = email,
                            isTinted = true,
                            isLocked = true,
                            onClick = {
                                infoDialog = "Login email changes require a verified account-change flow. Contact the office; editing profile details does not change your sign-in email."
                            }
                        )
                        HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                        CustomerProfileRow(
                            label = "Contact",
                            value = contact,
                            isTinted = false,
                            isLocked = false,
                            onClick = {
                                editingFieldKey = "phone"
                                editingFieldValue = p.value("phone")
                                editError = null
                            }
                        )
                        HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                        CustomerProfileRow(
                            label = "Default Address",
                            value = defaultAddress,
                            isTinted = true,
                            isLocked = false,
                            onClick = {
                                editingFieldKey = "address"
                                editingFieldValue = c.value("address")
                                editingCityValue = c.value("city")
                                editError = null
                            }
                        )
                    }
                }
            }

            // ── SMS Notifications Section ───────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = DriverNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SMS Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DriverNavy
                    )
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DriverLine)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.busy && !state.offline) {
                                vm.smsPreference(!isSmsEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shipment SMS Alerts",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DriverNavy
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Receive SMS updates for dispatch, tracking milestones, and delivery delays.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DriverMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sent to ${p.value("phone").ifBlank { "your verified number" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = isSmsEnabled,
                            onCheckedChange = { vm.smsPreference(it) },
                            enabled = !state.busy && !state.offline,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DriverNavy,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            )
                        )
                    }
                }
            }

            // ── Menu Section Card ──────────────────────────────────────────────────
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DriverLine)
            ) {
                Column {
                    CustomerMenuItemRow(
                        iconRes = R.drawable.ic_nav_book,
                        text = "My Bookings",
                        onClick = onOpenBookings
                    )
                    HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                    CustomerMenuItemRow(
                        iconRes = R.drawable.ic_location_pin,
                        text = "Saved Addresses",
                        onClick = { panel = "Saved Addresses" }
                    )
                    HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                    CustomerMenuItemRow(
                        iconRes = R.drawable.ic_shield_lock,
                        text = "Change Password",
                        onClick = { vm.openPasswordChange() }
                    )
                    HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                    CustomerMenuItemRow(
                        iconRes = R.drawable.ic_check_circle,
                        text = "Terms of Service",
                        onClick = { panel = "Terms of Service" }
                    )
                    HorizontalDivider(color = DriverLine, thickness = 0.5.dp)
                    CustomerMenuItemRow(
                        iconVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        text = "Help & Support",
                        onClick = { panel = "Help & Support" }
                    )
                }
            }

            // ── Log Out Button ──────────────────────────────────────────────────────
            OutlinedButton(
                onClick = onLogout,
                enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    containerColor = Color.White
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CustomerProfileRow(
    label: String,
    value: String,
    isTinted: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isTinted) Color(0xFFF4F6FB) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = DriverMuted,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = DriverNavy
            )
        }
        if (isLocked) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = DriverMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit $label",
                tint = DriverMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CustomerMenuItemRow(
    iconRes: Int? = null,
    iconVector: ImageVector? = null,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = DriverNavy,
                modifier = Modifier.size(20.dp)
            )
        } else if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = DriverNavy,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = DriverNavy,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = DriverMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

internal object CustomerProfilePresentation {
    fun initials(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifEmpty { "C" }
    }

    fun memberSince(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return "MEMBER SINCE JANUARY 2025"
        return try {
            val year = createdAt.take(4).toIntOrNull() ?: return "MEMBER SINCE JANUARY 2025"
            val monthNum = createdAt.drop(5).take(2).toIntOrNull() ?: return "MEMBER SINCE JANUARY 2025"
            if (monthNum !in 1..12) return "MEMBER SINCE JANUARY 2025"
            val months = listOf(
                "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
            )
            val monthName = months[monthNum - 1]
            "MEMBER SINCE $monthName $year"
        } catch (_: Exception) {
            "MEMBER SINCE JANUARY 2025"
        }
    }
}
