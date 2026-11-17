package com.tanaw.app.feature.workspace.customer.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.workspace.customer.components.CustomerMenuCard
import com.tanaw.app.feature.workspace.customer.components.CustomerPersonalInfoCard
import com.tanaw.app.feature.workspace.customer.components.CustomerProfileHeader
import com.tanaw.app.feature.workspace.customer.components.CustomerProfileNotices
import com.tanaw.app.feature.workspace.customer.components.CustomerSmsCard
import com.tanaw.app.feature.workspace.customer.components.CustomerSubPanel
import com.tanaw.app.feature.workspace.customer.components.CustomerTopBar
import com.tanaw.app.feature.workspace.customer.dialogs.CustomerFieldEditDialog
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerProfileRules
import com.tanaw.app.feature.workspace.driver.rules.DriverProfileRules
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun CustomerProfile(
    vm: MobileWorkspaceViewModel,
    state: MobileState,
    data: JsonObject,
    onOpenAlerts: () -> Unit,
    onOpenBookings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    onSubPanelChanged: (Boolean) -> Unit = {},
) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(panel) { onSubPanelChanged(panel != null) }
    var linkError by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    val p = data.obj("profile")
    val c = data.obj("customer")
    val settings = DriverProfileRules.settings(data)
    val content = DriverProfileRules.content(data)

    var editingFieldKey by remember { mutableStateOf<String?>(null) }
    var editingFieldValue by remember { mutableStateOf("") }
    var editingBarangayValue by remember { mutableStateOf("") }
    var editingCityValue by remember { mutableStateOf("") }
    var editingProvinceValue by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    var showLogoutConfirm by remember { mutableStateOf(false) }

    val savedStructuredAddress = CustomerAddressRules.parseFromSaved(
        rawAddress = c.value("address"),
        rawCity = c.value("city"),
        explicitBarangay = c.value("barangay"),
        explicitProvince = c.value("province"),
    )

    fun openUriSafely(value: String) {
        linkError = runCatching { uri.openUri(value) }.isFailure
    }

    val nameLengthError = stringResource(R.string.name_length_error)
    val phoneRequiredForName = stringResource(R.string.phone_required_for_name)
    val validPhoneError = stringResource(R.string.valid_phone_error)
    val streetEmptyError = stringResource(R.string.street_empty_error)
    val streetLengthError = stringResource(R.string.street_length_error)
    val barangayLengthError = stringResource(R.string.barangay_length_error)
    val cityEmptyError = stringResource(R.string.city_empty_error)
    val cityLengthError = stringResource(R.string.city_length_error)
    val provinceLengthError = stringResource(R.string.province_length_error)
    val phoneRequiredForAddress = stringResource(R.string.phone_required_for_address)

    if (editingFieldKey != null) {
        val saveEdit: () -> Unit = {
            when (editingFieldKey) {
                "full_name" -> {
                    if (!CustomerProfileRules.validateName(editingFieldValue)) {
                        editError = nameLengthError
                    } else {
                        val currentPhone = MobileValidation.phone(p.value("phone"))
                        if (currentPhone == null) {
                            editError = phoneRequiredForName
                        } else {
                            vm.profile(editingFieldValue, currentPhone, c.value("address"), c.value("city"))
                            editingFieldKey = null
                        }
                    }
                }
                "phone" -> {
                    val normalized = MobileValidation.phone(editingFieldValue)
                    if (normalized == null) {
                        editError = validPhoneError
                    } else {
                        val currentName = p.value("full_name").trim()
                        if (!CustomerProfileRules.validateName(currentName)) {
                            editError = nameLengthError
                        } else {
                            vm.profile(currentName, normalized, c.value("address"), c.value("city"))
                            editingFieldKey = null
                        }
                    }
                }
                "address" -> {
                    if (editingFieldValue.isBlank()) {
                        editError = streetEmptyError
                    } else if (!CustomerAddressRules.validateStreet(editingFieldValue)) {
                        editError = streetLengthError
                    } else if (editingBarangayValue.isNotBlank() && !CustomerAddressRules.validateBarangay(editingBarangayValue)) {
                        editError = barangayLengthError
                    } else if (editingCityValue.isBlank()) {
                        editError = cityEmptyError
                    } else if (!CustomerAddressRules.validateCity(editingCityValue)) {
                        editError = cityLengthError
                    } else if (editingProvinceValue.isNotBlank() && !CustomerAddressRules.validateProvince(editingProvinceValue)) {
                        editError = provinceLengthError
                    } else {
                        val (formattedAddress, formattedCity) = CustomerAddressRules.formatForRpc(
                            street = editingFieldValue,
                            barangay = editingBarangayValue,
                            city = editingCityValue,
                            province = editingProvinceValue,
                        )
                        val currentPhone = MobileValidation.phone(p.value("phone"))
                        if (currentPhone == null) {
                            editError = phoneRequiredForAddress
                        } else {
                            val currentName = p.value("full_name").trim()
                            if (!CustomerProfileRules.validateName(currentName)) {
                                editError = nameLengthError
                            } else {
                                vm.profile(currentName, currentPhone, formattedAddress, formattedCity)
                                editingFieldKey = null
                            }
                        }
                    }
                }
            }
        }

        CustomerFieldEditDialog(
            fieldKey = editingFieldKey!!,
            fieldValue = editingFieldValue,
            barangayValue = editingBarangayValue,
            cityValue = editingCityValue,
            provinceValue = editingProvinceValue,
            editError = editError,
            isBusy = state.busy,
            focusManager = focusManager,
            onFieldValueChange = { editingFieldValue = it; editError = null },
            onBarangayChange = { editingBarangayValue = it; editError = null },
            onCityChange = { editingCityValue = it; editError = null },
            onProvinceChange = { editingProvinceValue = it; editError = null },
            onDismiss = { if (!state.busy) editingFieldKey = null },
            onSave = saveEdit,
        )
    }

    if (infoDialog != null) {
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = stringResource(R.string.profile_information),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            text = { Text(infoDialog!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text(
                        text = stringResource(R.string.close),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) showLogoutConfirm = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = stringResource(R.string.log_out_confirm_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.log_out_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    enabled = !state.busy,
                ) {
                    Text(
                        text = stringResource(R.string.log_out_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirm = false },
                    enabled = !state.busy,
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    BackHandler(panel != null) { panel = null }

    if (panel != null) {
        CustomerSubPanel(
            panel = panel!!,
            content = content,
            settings = settings,
            state = state,
            savedStructuredAddress = savedStructuredAddress,
            linkError = linkError,
            openUriSafely = ::openUriSafely,
            onBack = { panel = null },
            onEditAddress = {
                editingFieldKey = "address"
                editingFieldValue = savedStructuredAddress.streetAddress
                editingBarangayValue = savedStructuredAddress.barangay
                editingCityValue = savedStructuredAddress.city
                editingProvinceValue = savedStructuredAddress.province.ifBlank { CustomerAddressRules.DEFAULT_PROVINCE_SUGGESTION }
                editError = null
            },
        )
        return
    }

    val unreadNotifications = data.rows("notifications").count { it.value("read_at").isBlank() }
    val smsError = data.value("sms_error").takeIf { it.isNotBlank() }
    val hasVerifiedPhone = MobileValidation.phone(p.value("phone")) != null
    val isSmsEnabled = data.rows("sms_preferences").any {
        it.value("type") == "shipment_status" && it.get("enabled") == JsonPrimitive(true)
    }

    val defaultCustomerName = stringResource(R.string.customer_default_name)
    val phoneAccountFallback = stringResource(R.string.phone_number_account)
    val noContactFallback = stringResource(R.string.no_contact_set)
    val noAddressFallback = stringResource(R.string.no_default_address_set)
    val photoDialogInfo = stringResource(R.string.profile_photo_dialog_info)
    val emailLockedInfo = stringResource(R.string.email_locked_dialog_info)

    val fullName = p.value("full_name").ifBlank { defaultCustomerName }
    val email = p.value("email").ifBlank { phoneAccountFallback }
    val contact = p.value("phone").ifBlank { noContactFallback }
    val defaultAddress = savedStructuredAddress.fullDisplayAddress().ifBlank { c.value("address").ifBlank { noAddressFallback } }
    val memberSince = CustomerProfilePresentation.memberSince(p.value("created_at"))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (showTopBar) {
            CustomerTopBar(
                unreadAlertsCount = unreadNotifications,
                isBusy = state.busy,
                onRefresh = vm::refresh,
                onOpenAlerts = onOpenAlerts,
            )
        }

        // ── Scrollable Body ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Screen Title
            Text(
                text = stringResource(R.string.my_profile),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )

            // Status Notices (Loading / Error / Success)
            CustomerProfileNotices(state, showLinearProgress = showTopBar)

            // Hero Avatar Section
            CustomerProfileHeader(
                fullName = fullName,
                email = email,
                memberSince = memberSince,
                onEditPhotoClick = { infoDialog = photoDialogInfo },
            )

            // ── Personal Information Section ──────────────────────────────────────────
            CustomerPersonalInfoCard(
                fullName = fullName,
                email = email,
                contact = contact,
                defaultAddress = defaultAddress,
                onEditName = {
                    editingFieldKey = "full_name"
                    editingFieldValue = p.value("full_name")
                    editError = null
                },
                onEmailInfo = { infoDialog = emailLockedInfo },
                onEditPhone = {
                    editingFieldKey = "phone"
                    editingFieldValue = p.value("phone")
                    editError = null
                },
                onEditAddress = {
                    editingFieldKey = "address"
                    editingFieldValue = savedStructuredAddress.streetAddress
                    editingBarangayValue = savedStructuredAddress.barangay
                    editingCityValue = savedStructuredAddress.city
                    editingProvinceValue = savedStructuredAddress.province.ifBlank { CustomerAddressRules.DEFAULT_PROVINCE_SUGGESTION }
                    editError = null
                },
            )

            // ── SMS Notifications Section ───────────────────────────────────────────
            CustomerSmsCard(
                isSmsEnabled = isSmsEnabled,
                phone = p.value("phone"),
                hasVerifiedPhone = hasVerifiedPhone,
                smsError = smsError,
                isBusy = state.busy,
                isOffline = state.offline,
                onSwitchChange = { if (hasVerifiedPhone) vm.smsPreference(it) },
            )

            // ── Menu Section Card ──────────────────────────────────────────────────
            CustomerMenuCard(
                onOpenBookings = onOpenBookings,
                onOpenSavedAddresses = { panel = "Saved Addresses" },
                onChangePassword = { vm.openPasswordChange() },
                onOpenTerms = { panel = "Terms of Service" },
                onOpenPrivacy = { panel = "Privacy Policy" },
                onOpenHelp = { panel = "Help & Support" },
            )

            // ── Log Out Button ──────────────────────────────────────────────────────
            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.log_out),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
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

    fun memberSince(createdAt: String?): String? {
        if (createdAt.isNullOrBlank()) return null
        return try {
            val year = createdAt.take(4).toIntOrNull() ?: return null
            val monthNum = createdAt.drop(5).take(2).toIntOrNull() ?: return null
            if (monthNum !in 1..12) return null
            val months = listOf(
                "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
            )
            val monthName = months[monthNum - 1]
            "MEMBER SINCE $monthName $year"
        } catch (_: Exception) {
            null
        }
    }
}
