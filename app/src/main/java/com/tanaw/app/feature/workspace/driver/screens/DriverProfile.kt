package com.tanaw.app.feature.workspace.driver.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.driver.components.*
import com.tanaw.app.feature.workspace.driver.rules.*
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.rules.TruckRestrictionRules
import com.tanaw.app.feature.workspace.viewmodel.*
import com.tanaw.app.ui.theme.*
import kotlinx.serialization.json.*

@Composable
internal fun DriverProfile(
    data: JsonObject,
    busy: Boolean,
    offline: Boolean,
    refresh: () -> Unit,
    recover: () -> Unit,
    logout: () -> Unit,
    messages: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    fun open(value: String) {
        linkError = runCatching { uri.openUri(value) }.isFailure
    }
    BackHandler(panel != null) { panel = null }

    val p = data.obj("profile")
    val d = data.obj("driver")
    val settings = DriverProfileRules.settings(data)
    val content = DriverProfileRules.content(data)
    val top = remember { BringIntoViewRequester() }
    LaunchedEffect(panel) { top.bringIntoView() }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(1.dp).bringIntoViewRequester(top))
        if (panel != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { panel = null },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "← Back to profile",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = panel!!,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (offline) {
                            Text(
                                text = "Offline · these are saved details. Reconnect to check for updates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (linkError) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "This device could not open the link. Check that a suitable app is installed.",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            when (panel) {
                "Help & Support" -> {
                    ProfileCard("Delivery support") {
                        Text(
                            text = "For pickup instructions, delays or unloading questions, open the conversation for the relevant delivery. Include its tracking reference so the office can identify the trip.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = messages,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Open office messages", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            text = "Messages are in-app and may not be read immediately. For urgent help, use your established emergency contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ProfileCard("Profile and documents") {
                        Text(
                            text = "Your personal details, assigned vehicle and compliance records are managed by the administrator. Send the office the field or document that needs correcting. Drivers cannot edit or approve these records here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pending or rejected documents are not approved. Expiry warnings use the office’s policy; they do not replace office dispatch checks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ProfileCard("Account access") {
                        Text(
                            text = "Use Change Password from Profile to verify your account and update your password. Cancel returns to Profile. If your roster link or account access is unavailable, ask the administrator to check your driver account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = recover,
                            enabled = !busy && !offline,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.5.dp,
                                if (!busy && !offline) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                            )
                        ) {
                            Text("Change Password", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    ProfileCard("Published office contacts") {
                        val email = content?.value("support_email")?.let(DriverProfileRules::email)
                        val phone = content?.value("support_phone")?.let(DriverProfileRules::phone)
                        val url = content?.value("support_url")?.let(DriverProfileRules::https)
                        if (settings == null) {
                            Text(
                                text = "Office contacts unavailable. Refresh your profile to retry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (email == null && phone == null && url == null) {
                            Text(
                                text = "The office has not published a support phone, email or website in this app. Use your delivery’s office conversation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        email?.let {
                            TextButton(
                                onClick = { open("mailto:$it") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(it, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        phone?.let {
                            TextButton(
                                onClick = { open("tel:$it") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Call $it", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        url?.let {
                            TextButton(
                                onClick = { open(it) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Open support website", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                else -> ProfileCard(panel!!) {
                    val key = if (panel == "Privacy") "privacy_url" else "terms_url"
                    val url = content?.value(key)?.let(DriverProfileRules::https)
                    if (settings == null) {
                        Text(
                            text = "Published policy information is unavailable. Refresh your profile to retry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (url == null) {
                        Text(
                            text = "The office has not published an approved ${if (panel == "Privacy") "privacy policy" else "terms of service"} destination in this app. Ask your administrator for the current document.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Open the office’s published document in your browser. An internet connection may be required.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = { open(url) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Open ${panel!!.lowercase()}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (panel == "Privacy") {
                        Text(
                            text = "App behavior: trip tracking shares location while tracking is enabled. Delivery proof and conversations use authorized access. This description is not a replacement for the office’s privacy policy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = refresh,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (!busy) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                )
            ) {
                Text("Refresh profile information", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "My profile",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Your account and delivery credentials",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else NavyPrimary,
                shape = RoundedCornerShape(16.dp),
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = if (isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFF244C70),
                        shape = CircleShape
                    ) {
                        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                            val initials = p.value("full_name")
                                .split(' ')
                                .filter(String::isNotBlank)
                                .take(2)
                                .map { it.first().uppercaseChar() }
                                .joinToString("")
                                .ifBlank { "D" }
                            Text(
                                text = initials,
                                color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = p.value("full_name").ifBlank { "Name not recorded" },
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                        Text(
                            text = "Driver account",
                            color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFDBE8F5),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (offline) "Saved information · Offline" else "Managed by your office",
                            color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFDBE8F5),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            ProfileCard("Personal Information", R.drawable.figma_driver_a35c0) {
                val roster = DriverProfileRules.rosterState(data)
                ProfileField("EMAIL", p.value("email"), showDivider = true)
                if (p.value("phone").isNotBlank() && p.value("phone") != d.value("contact_number")) {
                    ProfileField("PHONE", p.value("phone"), showDivider = roster == "ready")
                }
                when (roster) {
                    "ready" -> {
                        ProfileField("CONTACT", d.value("contact_number").ifBlank { p.value("phone") }, showDivider = true)
                        ProfileField("LICENSE NO.", d.value("license_number"), showDivider = true)
                        ProfileField("DRIVER SINCE", d.value("date_hired"), showDivider = false)
                    }
                    "empty" -> Text(
                        text = "No driver roster link was returned. Ask the administrator to link your account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    "loading" -> Text(
                        text = "Loading driver profile…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> {
                        Text(
                            text = "Driver profile unavailable. Refresh to retry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = refresh,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Retry driver profile", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(
                    text = "Contact your administrator to update personal information.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ProfileCard("Assigned vehicle") {
                when (DriverRecordPresentation.section(data, "driver_trucks")) {
                    "error", "unavailable" -> {
                        Text(
                            text = "Assigned vehicle unavailable. Refresh to verify your assignment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = refresh,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Retry assigned vehicle", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    "loading" -> Text(
                        text = "Loading assigned vehicle…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> {
                        val truck = DriverUiRules.truck(data)
                        if (truck == null) {
                            Text(
                                text = DriverRecordPresentation.vehicleLabel(data, null),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        DriverAsset(
                                            id = R.drawable.ic_vehicle,
                                            width = 24,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = truck.value("plate_number").ifBlank { "Plate not recorded" },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = truck.value("model").ifBlank { "Model not recorded" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val spec = TruckRestrictionRules.evaluateTruckSpecs(truck)
                                    Text(
                                        text = TruckRestrictionRules.formatSpecLine(spec),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val coding = TruckRestrictionRules.codingDayForPlate(truck.value("plate_number"))
                                    if (coding != null) {
                                        val dayName = coding.second.name.lowercase(java.util.Locale.US)
                                            .replaceFirstChar { it.titlecase(java.util.Locale.US) }
                                        Text(
                                            text = "Number coding day: $dayName (plate ends in ${coding.first})",
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

            ProfileCard("Compliance Status", R.drawable.figma_driver_77550) {
                Text(
                    text = DriverProfileRules.policyLabel(data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (settings == null || DriverProfileRules.policyLabel(data).contains("invalid")) {
                    TextButton(
                        onClick = refresh,
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Retry warning settings", style = MaterialTheme.typography.labelLarge)
                    }
                }
                when (DriverRecordPresentation.section(data, "compliance")) {
                    "error", "unavailable" -> {
                        Text(
                            text = "Compliance records unavailable. Refresh or contact the office to verify your documents.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = refresh,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Retry compliance records", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    "loading" -> Text(
                        text = "Loading compliance records…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    "empty" -> Text(
                        text = "No compliance documents were returned for your account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    "ready" -> data.rows("compliance").forEach { doc ->
                        val label = DriverProfileRules.compliance(doc, settings)
                        val good = label == "VALID"
                        val severe = label in listOf("EXPIRED", "REJECTED", "CRITICAL")
                        val tint = when {
                            good -> if (isDark) StatusGreenDark else SuccessGreen
                            severe -> if (isDark) ErrorRedDark else ExpiredRed
                            else -> if (isDark) WarningOrangeDark else ExpiringOrange
                        }
                        val background = when {
                            good -> if (isDark) StatusGreenBgDark else SuccessBannerBg
                            severe -> if (isDark) ErrorBgDark else ExpiredRedBg
                            else -> if (isDark) WarningBgDark else ExpiringOrangeBg
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = MobilePresentation.status(doc.value("doc_type")).ifBlank { "Document type not recorded" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = background, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = label,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            color = tint,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Text(
                                    text = "Expiry: ${doc.value("expiry_date").ifBlank { "Not recorded" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Contact your administrator to update compliance documents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ProfileCard("Account & support") {
                OutlinedButton(
                    onClick = recover,
                    enabled = !busy && !offline,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (!busy && !offline) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    )
                ) {
                    DriverAsset(R.drawable.figma_driver_69f3e, 18, description = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Change Password", style = MaterialTheme.typography.labelLarge)
                }
                listOf(
                    "Help & Support" to R.drawable.figma_driver_991e9,
                    "Terms of Service" to R.drawable.figma_driver_8c779,
                    "Privacy" to R.drawable.figma_driver_8c779
                ).forEachIndexed { index, (title, icon) ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    TextButton(
                        onClick = {
                            panel = title
                            linkError = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        DriverAsset(icon, 18)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        DriverAsset(R.drawable.figma_driver_3fa0b, 7, 12, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = logout,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (busy) MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.error
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Log Out", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ProfileCard(
    title: String,
    icon: Int? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                icon?.let { DriverAsset(it, 20) }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            body()
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(0.36f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.ifBlank { "Not recorded" },
                modifier = Modifier.weight(0.64f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun previewDriverProfileData(): JsonObject = buildJsonObject {
    put("profile", buildJsonObject {
        put("full_name", "Ramon Dela Cruz")
        put("email", "ramon.driver@mabutol.example")
        put("phone", "+639171234567")
    })
    put("driver", buildJsonObject {
        put("id", "drv-1")
        put("contact_number", "+639171234567")
        put("license_number", "N01-23-456789")
        put("date_hired", "2022-06-15")
    })
    put("driver_trucks", buildJsonArray {
        add(buildJsonObject {
            put("id", "trk-1")
            put("assigned_driver_id", "drv-1")
            put("plate_number", "ABC 1234")
            put("model", "Isuzu Giga 10-Wheeler")
        })
    })
    put("compliance", buildJsonArray {
        add(buildJsonObject {
            put("doc_type", "drivers_license")
            put("review_status", "approved")
            put("expiry_date", "2026-11-22")
        })
        add(buildJsonObject {
            put("doc_type", "medical_certificate")
            put("review_status", "approved")
            put("expiry_date", "2027-08-01")
        })
    })
    put("driver_sections", buildJsonObject {
        put("driver_roster", "ready")
        put("driver_trucks", "ready")
        put("compliance", "ready")
        put("driver_settings", "ready")
    })
}

@Preview(showBackground = true, name = "Driver Profile Light")
@Composable
private fun DriverProfileLightPreview() {
    DriverTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DriverProfile(
                    data = previewDriverProfileData(),
                    busy = false,
                    offline = false,
                    refresh = {},
                    recover = {},
                    logout = {},
                    messages = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Driver Profile Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DriverProfileDarkPreview() {
    DriverTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DriverProfile(
                    data = previewDriverProfileData(),
                    busy = false,
                    offline = false,
                    refresh = {},
                    recover = {},
                    logout = {},
                    messages = {}
                )
            }
        }
    }
}
