package com.tanaw.app.feature.workspace.driver.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.driver.rules.AvailabilityTone
import com.tanaw.app.feature.workspace.driver.rules.DriverAvailabilityBadge
import com.tanaw.app.feature.workspace.driver.rules.DriverProfileRules
import com.tanaw.app.feature.workspace.driver.screens.DriverAsset
import com.tanaw.app.feature.workspace.driver.screens.DriverUiRules
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.rules.TruckRestrictionRules
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.WorkspaceCard
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.Locale

@Composable
internal fun DriverProfileHeader(
    fullName: String,
    email: String,
    badge: DriverAvailabilityBadge,
    modifier: Modifier = Modifier
) {
    val extendedColors = TanawTheme.extendedColors
    val (badgeBg, badgeFg) = when (badge.tone) {
        AvailabilityTone.SUCCESS -> extendedColors.successBannerBackground to extendedColors.onSuccessBannerText
        AvailabilityTone.INFO -> extendedColors.infoContainer to extendedColors.onInfoContainer
        AvailabilityTone.WARNING -> extendedColors.warningContainer to extendedColors.onWarningContainer
        AvailabilityTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DriverProfileRules.initials(fullName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = fullName.ifBlank { "Name not recorded" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (email.isNotBlank()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeFg
                    )
                }
            }
        }
    }
}

@Composable
internal fun DriverPersonalInfoCard(
    data: JsonObject,
    busy: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = data.obj("profile")
    val d = data.obj("driver")
    val roster = DriverProfileRules.rosterState(data)

    DriverSectionCard(
        title = "Personal Information",
        icon = Icons.Outlined.Person,
        modifier = modifier
    ) {
        DriverProfileFieldRow("EMAIL", p.value("email"), showDivider = true)
        if (p.value("phone").isNotBlank() && p.value("phone") != d.value("contact_number")) {
            DriverProfileFieldRow("PHONE", p.value("phone"), showDivider = roster == "ready")
        }
        when (roster) {
            "ready" -> {
                DriverProfileFieldRow("CONTACT", d.value("contact_number").ifBlank { p.value("phone") }, showDivider = true)
                DriverProfileFieldRow("LICENSE NO.", d.value("license_number"), showDivider = true)
                DriverProfileFieldRow("DRIVER SINCE", d.value("date_hired"), showDivider = false)
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
                    onClick = onRetry,
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Retry driver profile", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            text = "Managed by your office. Contact your administrator to update personal information.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DriverVehicleCard(
    data: JsonObject,
    busy: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    DriverSectionCard(
        title = "Assigned Vehicle",
        icon = Icons.Outlined.LocalShipping,
        modifier = modifier
    ) {
        when (DriverRecordPresentation.section(data, "driver_trucks")) {
            "error", "unavailable" -> {
                Text(
                    text = "Assigned vehicle unavailable. Refresh to verify your assignment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(
                    onClick = onRetry,
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
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = truck.value("plate_number").ifBlank { "Plate not recorded" },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = truck.value("model").ifBlank { "Model not recorded" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    val spec = TruckRestrictionRules.evaluateTruckSpecs(truck)
                    DriverProfileFieldRow(
                        label = "SPECS & LIMITS",
                        value = TruckRestrictionRules.formatSpecLine(spec),
                        showDivider = TruckRestrictionRules.codingDayForPlate(truck.value("plate_number")) != null
                    )
                    TruckRestrictionRules.codingDayForPlate(truck.value("plate_number"))?.let { coding ->
                        val dayName = coding.second.name.lowercase(Locale.US)
                            .replaceFirstChar { it.titlecase(Locale.US) }
                        DriverProfileFieldRow(
                            label = "NUMBER CODING",
                            value = "$dayName (plate ends in ${coding.first})",
                            showDivider = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DriverComplianceCard(
    data: JsonObject,
    settings: JsonArray?,
    busy: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = TanawTheme.extendedColors
    val section = DriverRecordPresentation.section(data, "compliance")
    val docs = if (section == "ready") data.rows("compliance") else emptyList()
    val summary = if (section == "ready") DriverProfileRules.complianceSummary(docs, settings) else null
    val summaryLine = summary?.let(DriverProfileRules::formatSummaryLine).orEmpty()
    val policyLabel = DriverProfileRules.policyLabel(data)

    DriverSectionCard(
        title = "Compliance Status",
        icon = Icons.Outlined.VerifiedUser,
        modifier = modifier
    ) {
        if (summaryLine.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = summaryLine,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        when (section) {
            "error", "unavailable" -> {
                Text(
                    text = "Compliance records unavailable. Refresh or contact the office to verify your documents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(
                    onClick = onRetry,
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
            "ready" -> docs.forEach { doc ->
                val label = DriverProfileRules.compliance(doc, settings)
                val (badgeBg, badgeFg) = when {
                    label == "VALID" ->
                        extendedColors.successBannerBackground to extendedColors.onSuccessBannerText
                    label in listOf("EXPIRED", "REJECTED", "CRITICAL") ->
                        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                    else ->
                        extendedColors.warningContainer to extendedColors.onWarningContainer
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = MobilePresentation.status(doc.value("doc_type"))
                                    .ifBlank { "Document type not recorded" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = badgeFg,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text(
                            text = "Expires: ${DriverProfileRules.formatExpiryDate(doc.value("expiry_date"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (settings == null || policyLabel.contains("invalid")) {
            Text(
                text = policyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(
                onClick = onRetry,
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Retry warning settings", style = MaterialTheme.typography.labelLarge)
            }
        } else if (section == "ready") {
            Text(
                text = policyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Contact your administrator to update compliance documents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DriverAccountCard(
    busy: Boolean,
    offline: Boolean,
    hasClearOffline: Boolean,
    onRecover: () -> Unit,
    onOpenPanel: (String) -> Unit,
    onRequestClearOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Account & Support",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                DriverProfileMenuRow(
                    icon = Icons.Outlined.Lock,
                    title = "Change Password",
                    subtitle = if (offline) "Available when online" else null,
                    enabled = !busy && !offline,
                    onClick = onRecover
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                DriverProfileMenuRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "Help & Support",
                    enabled = true,
                    onClick = { onOpenPanel("Help & Support") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                DriverProfileMenuRow(
                    icon = Icons.Outlined.Description,
                    title = "Terms of Service",
                    enabled = true,
                    onClick = { onOpenPanel("Terms of Service") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                DriverProfileMenuRow(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "Privacy",
                    enabled = true,
                    onClick = { onOpenPanel("Privacy") }
                )
                if (hasClearOffline) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    DriverProfileMenuRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Clear Saved Offline Workspace",
                        enabled = !busy,
                        onClick = onRequestClearOffline
                    )
                }
            }
        }
    }
}

@Composable
internal fun DriverProfileSubPanel(
    panel: String,
    content: JsonObject?,
    settings: JsonArray?,
    busy: Boolean,
    offline: Boolean,
    linkError: Boolean,
    onBack: () -> Unit,
    onOpenMessages: () -> Unit,
    onRecover: () -> Unit,
    onRetry: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        onClick = onBack,
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
                        text = panel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (offline) {
                        Text(
                            text = "Offline · Saved details are shown. Reconnect to check for updates.",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        when (panel) {
            "Help & Support" -> {
                DriverSectionCard(title = "Delivery Support") {
                    Text(
                        text = "For pickup instructions, delays, or unloading questions, open the conversation for the relevant delivery. Include its tracking reference so the office can identify the trip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onOpenMessages,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
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

                DriverSectionCard(title = "Published Office Contacts") {
                    val email = content?.value("support_email")?.let(DriverProfileRules::email)
                    val phone = content?.value("support_phone")?.let(DriverProfileRules::phone)
                    val url = content?.value("support_url")?.let(DriverProfileRules::https)
                    if (settings == null) {
                        Text(
                            text = "Office contacts unavailable. Refresh your profile to retry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = onRetry,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Retry office contacts", style = MaterialTheme.typography.labelLarge)
                        }
                    } else if (email == null && phone == null && url == null) {
                        Text(
                            text = "The office has not published a support phone, email, or website in this app. Use your delivery’s office conversation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    email?.let {
                        DriverContactActionRow(
                            icon = Icons.Outlined.Email,
                            label = it,
                            onClick = { onOpenUri("mailto:$it") }
                        )
                    }
                    phone?.let {
                        DriverContactActionRow(
                            icon = Icons.Outlined.Phone,
                            label = "Call $it",
                            onClick = { onOpenUri("tel:$it") }
                        )
                    }
                    url?.let {
                        DriverContactActionRow(
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            label = "Open support website",
                            onClick = { onOpenUri(it) }
                        )
                    }
                }

                DriverSectionCard(title = "Account & Record Changes") {
                    Text(
                        text = "Personal details, assigned vehicles, and compliance records are managed by your administrator. Pending or rejected documents are not approved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "If your roster link or account access is unavailable, ask the administrator to check your driver account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onRecover,
                        enabled = !busy && !offline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
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
            }

            else -> DriverSectionCard(title = panel) {
                val key = if (panel == "Privacy") "privacy_url" else "terms_url"
                val url = content?.value(key)?.let(DriverProfileRules::https)
                if (settings == null) {
                    Text(
                        text = "Published policy information is unavailable. Refresh your profile to retry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = onRetry,
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Retry policy settings", style = MaterialTheme.typography.labelLarge)
                    }
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
                        onClick = { onOpenUri(url) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Open ${panel.lowercase()}", style = MaterialTheme.typography.labelLarge)
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
    }
}

@Composable
private fun DriverSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    WorkspaceCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
private fun DriverProfileFieldRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.ifBlank { "Not recorded" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun DriverProfileMenuRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DriverContactActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
