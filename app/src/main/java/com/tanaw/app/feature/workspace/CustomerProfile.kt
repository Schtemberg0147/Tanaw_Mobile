package com.tanaw.app.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import kotlinx.serialization.json.JsonArray
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
) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
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

    val savedStructuredAddress = CustomerAddressRules.parseFromSaved(
        rawAddress = c.value("address"),
        rawCity = c.value("city"),
        explicitBarangay = c.value("barangay"),
        explicitProvince = c.value("province"),
    )

    fun openUriSafely(value: String) {
        linkError = runCatching { uri.openUri(value) }.isFailure
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

    val unreadNotifications = data.rows("notifications").count { it.value("read_at").isBlank() }
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
        // ── Top Header Bar ─────────────────────────────────────────────────────────
        CustomerTopBar(
            unreadAlertsCount = unreadNotifications,
            isBusy = state.busy,
            onRefresh = vm::refresh,
            onOpenAlerts = onOpenAlerts,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // ── Scrollable Body ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
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
            CustomerProfileNotices(state)

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
                isBusy = state.busy,
                isOffline = state.offline,
                onToggleSms = { vm.smsPreference(!isSmsEnabled) },
                onSwitchChange = { vm.smsPreference(it) },
            )

            // ── Menu Section Card ──────────────────────────────────────────────────
            CustomerMenuCard(
                onOpenBookings = onOpenBookings,
                onOpenSavedAddresses = { panel = "Saved Addresses" },
                onChangePassword = { vm.openPasswordChange() },
                onOpenTerms = { panel = "Terms of Service" },
                onOpenHelp = { panel = "Help & Support" },
            )

            // ── Log Out Button ──────────────────────────────────────────────────────
            OutlinedButton(
                onClick = onLogout,
                enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CustomerProfileHeader(
    fullName: String,
    email: String,
    memberSince: String,
    onEditPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = CustomerProfilePresentation.initials(fullName),
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .offset(x = 6.dp, y = 6.dp)
                    .minimumInteractiveComponentSize()
                    .size(34.dp)
                    .clickable(onClick = onEditPhotoClick),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = stringResource(R.string.edit_photo),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = fullName,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                text = memberSince,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CustomerPersonalInfoCard(
    fullName: String,
    email: String,
    contact: String,
    defaultAddress: String,
    onEditName: () -> Unit,
    onEmailInfo: () -> Unit,
    onEditPhone: () -> Unit,
    onEditAddress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.personal_information),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                CustomerProfileRow(
                    label = stringResource(R.string.full_name_label),
                    value = fullName,
                    isLocked = false,
                    onClick = onEditName,
                    iconVector = Icons.Outlined.Person,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerProfileRow(
                    label = stringResource(R.string.email_label),
                    value = email,
                    isLocked = true,
                    onClick = onEmailInfo,
                    iconVector = Icons.Outlined.Email,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerProfileRow(
                    label = stringResource(R.string.contact_label),
                    value = contact,
                    isLocked = false,
                    onClick = onEditPhone,
                    iconVector = Icons.Outlined.Phone,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerProfileRow(
                    label = stringResource(R.string.default_address_label),
                    value = defaultAddress,
                    isLocked = false,
                    onClick = onEditAddress,
                    iconRes = R.drawable.ic_location_pin,
                )
            }
        }
    }
}

@Composable
private fun CustomerSmsCard(
    isSmsEnabled: Boolean,
    phone: String,
    isBusy: Boolean,
    isOffline: Boolean,
    onToggleSms: () -> Unit,
    onSwitchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sms_notifications),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy && !isOffline, onClick = onToggleSms)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shipment_sms_alerts),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.shipment_sms_alerts_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val verifiedFallback = stringResource(R.string.verified_number_fallback)
                    Text(
                        text = stringResource(
                            R.string.sent_to_verified_number_fmt,
                            phone.ifBlank { verifiedFallback },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isSmsEnabled,
                    onCheckedChange = onSwitchChange,
                    enabled = !isBusy && !isOffline,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CustomerMenuCard(
    onOpenBookings: () -> Unit,
    onOpenSavedAddresses: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            CustomerMenuItemRow(
                iconRes = R.drawable.ic_nav_book,
                text = stringResource(R.string.my_bookings),
                onClick = onOpenBookings,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            CustomerMenuItemRow(
                iconRes = R.drawable.ic_location_pin,
                text = stringResource(R.string.saved_addresses),
                onClick = onOpenSavedAddresses,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            CustomerMenuItemRow(
                iconRes = R.drawable.ic_shield_lock,
                text = stringResource(R.string.change_password),
                onClick = onChangePassword,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            CustomerMenuItemRow(
                iconRes = R.drawable.ic_check_circle,
                text = stringResource(R.string.terms_of_service),
                onClick = onOpenTerms,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            CustomerMenuItemRow(
                iconVector = Icons.AutoMirrored.Outlined.HelpOutline,
                text = stringResource(R.string.help_and_support),
                onClick = onOpenHelp,
            )
        }
    }
}

@Composable
private fun CustomerSubPanel(
    panel: String,
    content: JsonObject?,
    settings: JsonArray?,
    state: MobileState,
    savedStructuredAddress: CustomerAddressRules.StructuredCustomerAddress,
    linkError: Boolean,
    openUriSafely: (String) -> Unit,
    onBack: () -> Unit,
    onEditAddress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.back_to_profile),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = panel,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        CustomerProfileNotices(state)

        if (linkError) {
            Text(
                text = stringResource(R.string.browser_unavailable_msg),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        when (panel) {
            "Saved Addresses" -> {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.default_saved_address),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.default_saved_address_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.house_street_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = savedStructuredAddress.streetAddress.ifBlank { stringResource(R.string.no_street_address_set) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.barangay_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = savedStructuredAddress.barangay.ifBlank { stringResource(R.string.not_set) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.city_municipality_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = savedStructuredAddress.city.ifBlank { stringResource(R.string.not_set) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.province_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = savedStructuredAddress.province.ifBlank { stringResource(R.string.not_set) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onEditAddress,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.edit_default_address),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            "Help & Support" -> {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.delivery_support),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.delivery_support_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.published_office_contacts),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val email = content?.value("support_email")?.let(DriverProfileRules::email)
                        val phone = content?.value("support_phone")?.let(DriverProfileRules::phone)
                        val url = content?.value("support_url")?.let(DriverProfileRules::https)

                        if (settings == null) {
                            Text(
                                text = stringResource(R.string.office_contacts_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (email == null && phone == null && url == null) {
                            Text(
                                text = stringResource(R.string.office_contacts_not_published),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        email?.let {
                            TextButton(onClick = { openUriSafely("mailto:$it") }) {
                                Text("Email: $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        phone?.let {
                            TextButton(onClick = { openUriSafely("tel:$it") }) {
                                Text("Call: $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        url?.let {
                            TextButton(onClick = { openUriSafely(it) }) {
                                Text(
                                    text = stringResource(R.string.open_support_website),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val key = if (panel == "Privacy") "privacy_url" else "terms_url"
                        val url = content?.value(key)?.let(DriverProfileRules::https)
                        if (settings == null) {
                            Text(
                                text = stringResource(R.string.published_policy_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (url == null) {
                            Text(
                                text = stringResource(R.string.policy_not_published_fmt, panel.lowercase()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.open_policy_in_browser_fmt, panel.lowercase()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { openUriSafely(url) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.open_policy_action_fmt, panel.lowercase()),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerFieldEditDialog(
    fieldKey: String,
    fieldValue: String,
    barangayValue: String,
    cityValue: String,
    provinceValue: String,
    editError: String?,
    isBusy: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onFieldValueChange: (String) -> Unit,
    onBarangayChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onProvinceChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val title = when (fieldKey) {
        "full_name" -> stringResource(R.string.edit_full_name)
        "phone" -> stringResource(R.string.edit_contact_number)
        "address" -> stringResource(R.string.edit_default_address)
        else -> stringResource(R.string.edit_profile)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (fieldKey) {
                    "full_name" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = onFieldValueChange,
                            label = { Text(stringResource(R.string.full_name_label)) },
                            isError = editError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSave() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "phone" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = onFieldValueChange,
                            label = { Text(stringResource(R.string.contact_number)) },
                            placeholder = { Text("09XXXXXXXXX") },
                            supportingText = { Text("Verification SMS will be sent if changed.") },
                            isError = editError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSave() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "address" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = onFieldValueChange,
                            label = { Text(stringResource(R.string.house_street_label)) },
                            isError = editError != null && (fieldValue.isBlank() || !CustomerAddressRules.validateStreet(fieldValue)),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = barangayValue,
                            onValueChange = onBarangayChange,
                            label = { Text(stringResource(R.string.barangay_label)) },
                            isError = editError != null && barangayValue.isNotBlank() && !CustomerAddressRules.validateBarangay(barangayValue),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = cityValue,
                            onValueChange = onCityChange,
                            label = { Text(stringResource(R.string.city_municipality_label)) },
                            isError = editError != null && (cityValue.isBlank() || !CustomerAddressRules.validateCity(cityValue)),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = provinceValue,
                            onValueChange = onProvinceChange,
                            label = { Text(stringResource(R.string.province_label)) },
                            isError = editError != null && provinceValue.isNotBlank() && !CustomerAddressRules.validateProvince(provinceValue),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSave() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (editError != null) {
                    Text(editError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isBusy,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun CustomerProfileRow(
    label: String,
    value: String,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isLocked) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CustomerMenuItemRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    iconVector: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CustomerProfileNotices(state: MobileState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.message?.let {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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

    fun memberSince(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return "MEMBER SINCE JANUARY 2025"
        return try {
            val year = createdAt.take(4).toIntOrNull() ?: return "MEMBER SINCE JANUARY 2025"
            val monthNum = createdAt.drop(5).take(2).toIntOrNull() ?: return "MEMBER SINCE JANUARY 2025"
            if (monthNum !in 1..12) return "MEMBER SINCE JANUARY 2025"
            val months = listOf(
                "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
            )
            val monthName = months[monthNum - 1]
            "MEMBER SINCE $monthName $year"
        } catch (_: Exception) {
            "MEMBER SINCE JANUARY 2025"
        }
    }
}
