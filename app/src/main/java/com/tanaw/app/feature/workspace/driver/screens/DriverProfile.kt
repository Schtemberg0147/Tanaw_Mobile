package com.tanaw.app.feature.workspace.driver.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.feature.workspace.driver.components.*
import com.tanaw.app.feature.workspace.driver.rules.DriverProfileRules
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.ResponsiveCenteredBox
import com.tanaw.app.ui.theme.FormFactorPreviews
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
internal fun DriverProfile(
    data: JsonObject,
    busy: Boolean,
    offline: Boolean,
    refresh: () -> Unit,
    recover: () -> Unit,
    logout: () -> Unit,
    messages: () -> Unit,
    modifier: Modifier = Modifier,
    onClearOffline: (() -> Unit)? = null,
    onSubPanelChanged: (Boolean) -> Unit = {},
    initialPanel: String? = null
) {
    var panel by rememberSaveable { mutableStateOf(initialPanel) }
    var linkError by rememberSaveable { mutableStateOf(false) }
    var confirmClearOffline by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val windowSize = LocalTanawWindowSize.current

    fun openUri(value: String) {
        linkError = runCatching { uri.openUri(value) }.isFailure
    }

    LaunchedEffect(panel) { onSubPanelChanged(panel != null) }
    BackHandler(panel != null) { panel = null }

    if (confirmClearOffline && onClearOffline != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmClearOffline = false },
            title = {
                Text(
                    text = "Clear Saved Offline Workspace?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This removes cached assignments and profile records stored on this device. Reconnect and refresh to reload your workspace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearOffline = false
                        onClearOffline()
                    },
                    enabled = !busy
                ) {
                    Text(
                        text = "Clear Cache",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClearOffline = false },
                    enabled = !busy
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val p = data.obj("profile")
    val settings = DriverProfileRules.settings(data)
    val content = DriverProfileRules.content(data)
    val top = remember { BringIntoViewRequester() }
    LaunchedEffect(panel) { top.bringIntoView() }

    ResponsiveCenteredBox(
        maxWidth = if (windowSize.useTwoPane) windowSize.contentMaxWidth else 720.dp,
        modifier = modifier,
        horizontalPadding = windowSize.horizontalGutter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(top))

            if (panel != null) {
                DriverProfileSubPanel(
                    panel = panel!!,
                    content = content,
                    settings = settings,
                    busy = busy,
                    offline = offline,
                    linkError = linkError,
                    onBack = { panel = null },
                    onOpenMessages = messages,
                    onRecover = recover,
                    onRetry = refresh,
                    onOpenUri = ::openUri
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "My Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Your account, assigned vehicle, and compliance credentials",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DriverProfileHeader(
                    fullName = p.value("full_name"),
                    email = p.value("email"),
                    badge = DriverProfileRules.availabilityBadge(data, offline)
                )

                if (windowSize.useTwoPane) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DriverPersonalInfoCard(
                                data = data,
                                busy = busy,
                                onRetry = refresh
                            )
                            DriverVehicleCard(
                                data = data,
                                busy = busy,
                                onRetry = refresh
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DriverComplianceCard(
                                data = data,
                                settings = settings,
                                busy = busy,
                                onRetry = refresh
                            )
                            DriverAccountCard(
                                busy = busy,
                                offline = offline,
                                hasClearOffline = onClearOffline != null,
                                onRecover = recover,
                                onOpenPanel = { title ->
                                    panel = title
                                    linkError = false
                                },
                                onRequestClearOffline = { confirmClearOffline = true }
                            )
                        }
                    }
                } else {
                    DriverPersonalInfoCard(
                        data = data,
                        busy = busy,
                        onRetry = refresh
                    )

                    DriverVehicleCard(
                        data = data,
                        busy = busy,
                        onRetry = refresh
                    )

                    DriverComplianceCard(
                        data = data,
                        settings = settings,
                        busy = busy,
                        onRetry = refresh
                    )

                    DriverAccountCard(
                        busy = busy,
                        offline = offline,
                        hasClearOffline = onClearOffline != null,
                        onRecover = recover,
                        onOpenPanel = { title ->
                            panel = title
                            linkError = false
                        },
                        onRequestClearOffline = { confirmClearOffline = true }
                    )
                }

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = logout,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (busy) MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.error
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
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
                        Spacer(Modifier.width(8.dp))
                        Text("Log Out", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
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
        put("status", "available")
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
            put("capacity_kg", "12000")
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
    put("driver_settings", buildJsonArray {})
    put("driver_sections", buildJsonObject {
        put("driver_roster", "ready")
        put("driver_trucks", "ready")
        put("compliance", "ready")
        put("driver_settings", "ready")
    })
}

private fun previewDriverProfileErrorData(): JsonObject = buildJsonObject {
    put("profile", buildJsonObject {
        put("full_name", "Ramon Dela Cruz")
        put("email", "ramon.driver@mabutol.example")
    })
    put("driver_sections", buildJsonObject {
        put("driver_roster", "error")
        put("driver_trucks", "error")
        put("compliance", "error")
        put("driver_settings", "error")
    })
}

@FormFactorPreviews
@Composable
private fun DriverProfileLightPreview() {
    DriverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DriverProfile(
                    data = previewDriverProfileData(),
                    busy = false,
                    offline = false,
                    refresh = {},
                    recover = {},
                    logout = {},
                    messages = {},
                    onClearOffline = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Driver Profile Offline & Errors")
@Composable
private fun DriverProfileOfflineErrorPreview() {
    DriverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DriverProfile(
                    data = previewDriverProfileErrorData(),
                    busy = false,
                    offline = true,
                    refresh = {},
                    recover = {},
                    logout = {},
                    messages = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Driver Profile Help Sub-Panel")
@Composable
private fun DriverProfileHelpSubPanelPreview() {
    DriverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DriverProfile(
                    data = previewDriverProfileData(),
                    busy = false,
                    offline = false,
                    refresh = {},
                    recover = {},
                    logout = {},
                    messages = {},
                    initialPanel = "Help & Support"
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Driver Profile 200% Font Scale", fontScale = 2.0f)
@Composable
private fun DriverProfileLargeFontPreview() {
    DriverTheme {
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
