package com.tanaw.app.feature.workspace.customer.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.feature.workspace.customer.screens.CustomerProfilePresentation
import com.tanaw.app.feature.workspace.driver.rules.DriverProfileRules
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.TanawSubScreenTopBar
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Composable
internal fun CustomerProfileHeader(
    fullName: String,
    email: String,
    memberSince: String?,
    onEditPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarBitmap: Bitmap? = null,
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
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onEditPhotoClick),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = fullName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = CustomerProfilePresentation.initials(fullName),
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = 12.dp, y = 12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onEditPhotoClick),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.edit_photo),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = fullName,
            fontFamily = TanawDisplayFontFamily,
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
        if (!memberSince.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = memberSince,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TanawMonoFontFamily,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomerAvatarPhotoSheet(
    hasCurrentPhoto: Boolean,
    isBusy: Boolean,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_photo_sheet_title),
                fontFamily = TanawDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.profile_photo_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = !isBusy, role = Role.Button, onClick = onTakePhoto)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.profile_photo_take_camera),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = !isBusy, role = Role.Button, onClick = onChooseFromGallery)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.profile_photo_choose_gallery),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (hasCurrentPhoto) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(enabled = !isBusy, role = Role.Button, onClick = onRemovePhoto)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.profile_photo_remove),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun CustomerPersonalInfoCard(
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
                imageVector = Icons.Outlined.Badge,
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
                    isTechnicalValue = true,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerProfileRow(
                    label = stringResource(R.string.default_address_label),
                    value = defaultAddress,
                    isLocked = false,
                    onClick = onEditAddress,
                    iconVector = Icons.Outlined.LocationOn,
                )
            }
        }
    }
}

@Composable
internal fun CustomerSmsCard(
    isSmsEnabled: Boolean,
    phone: String,
    hasVerifiedPhone: Boolean,
    smsError: String?,
    isBusy: Boolean,
    isOffline: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canToggle = !isBusy && !isOffline && hasVerifiedPhone
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
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = isSmsEnabled,
                        enabled = canToggle,
                        role = Role.Switch,
                        onValueChange = onSwitchChange,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sms,
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
                    if (!smsError.isNullOrBlank()) {
                        Text(
                            text = smsError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    } else if (!hasVerifiedPhone) {
                        Text(
                            text = stringResource(R.string.sms_phone_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        val verifiedFallback = stringResource(R.string.verified_number_fallback)
                        Text(
                            text = stringResource(
                                R.string.sent_to_verified_number_fmt,
                                phone.ifBlank { verifiedFallback },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TanawMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isSmsEnabled,
                    onCheckedChange = null,
                    enabled = canToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun CustomerMenuCard(
    onOpenBookings: () -> Unit,
    onOpenSavedAddresses: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.account_and_policies),
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
                CustomerMenuItemRow(
                    iconVector = Icons.Outlined.LocalShipping,
                    text = stringResource(R.string.my_bookings),
                    onClick = onOpenBookings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerMenuItemRow(
                    iconVector = Icons.Outlined.LocationOn,
                    text = stringResource(R.string.saved_addresses),
                    onClick = onOpenSavedAddresses,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerMenuItemRow(
                    iconVector = Icons.Outlined.Lock,
                    text = stringResource(R.string.change_password),
                    onClick = onChangePassword,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerMenuItemRow(
                    iconVector = Icons.Outlined.Description,
                    text = stringResource(R.string.terms_of_service),
                    onClick = onOpenTerms,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                CustomerMenuItemRow(
                    iconVector = Icons.Outlined.PrivacyTip,
                    text = stringResource(R.string.privacy_policy),
                    onClick = onOpenPrivacy,
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
}

@Composable
internal fun CustomerSubPanel(
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
            .background(MaterialTheme.colorScheme.background),
    ) {
        TanawSubScreenTopBar(
            title = panel,
            onBack = onBack,
            busy = state.busy,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CustomerProfileNotices(state, showLinearProgress = false)

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
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                TextButton(
                                    onClick = { openUriSafely("mailto:$it") },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.email_contact_fmt, it),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            phone?.let {
                                TextButton(
                                    onClick = { openUriSafely("tel:$it") },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.call_contact_fmt, it),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            url?.let {
                                TextButton(
                                    onClick = { openUriSafely(it) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
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

                "Privacy", "Privacy Policy" -> {
                    CustomerPolicyDocumentCard(
                        badgeText = stringResource(R.string.policy_effective_badge),
                        sections = listOf(
                            stringResource(R.string.privacy_collection_title) to stringResource(R.string.privacy_collection_body),
                            stringResource(R.string.privacy_location_title) to stringResource(R.string.privacy_location_body),
                            stringResource(R.string.privacy_communications_title) to stringResource(R.string.privacy_communications_body),
                            stringResource(R.string.privacy_security_title) to stringResource(R.string.privacy_security_body),
                            stringResource(R.string.privacy_rights_title) to stringResource(R.string.privacy_rights_body),
                        ),
                        externalUrl = content?.value("privacy_url")?.let(DriverProfileRules::https),
                        externalButtonLabel = stringResource(R.string.open_policy_action_fmt, panel.lowercase()),
                        onOpenExternalUrl = openUriSafely,
                    )
                }

                else -> {
                    CustomerPolicyDocumentCard(
                        badgeText = stringResource(R.string.policy_effective_badge),
                        sections = listOf(
                            stringResource(R.string.terms_intro_title) to stringResource(R.string.terms_intro_body),
                            stringResource(R.string.terms_scheduling_title) to stringResource(R.string.terms_scheduling_body),
                            stringResource(R.string.terms_quotation_title) to stringResource(R.string.terms_quotation_body),
                            stringResource(R.string.terms_loading_title) to stringResource(R.string.terms_loading_body),
                            stringResource(R.string.terms_cancellation_title) to stringResource(R.string.terms_cancellation_body),
                            stringResource(R.string.terms_cargo_title) to stringResource(R.string.terms_cargo_body),
                        ),
                        externalUrl = content?.value("terms_url")?.let(DriverProfileRules::https),
                        externalButtonLabel = stringResource(R.string.open_policy_action_fmt, panel.lowercase()),
                        onOpenExternalUrl = openUriSafely,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerPolicyDocumentCard(
    badgeText: String,
    sections: List<Pair<String, String>>,
    externalUrl: String?,
    externalButtonLabel: String,
    onOpenExternalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TanawMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = 0.4.sp,
                )
            }

            sections.forEachIndexed { index, (title, body) ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            if (externalUrl != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                )
                Button(
                    onClick = { onOpenExternalUrl(externalUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = externalButtonLabel,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerProfileRow(
    label: String,
    value: String,
    isLocked: Boolean,
    onClick: () -> Unit,
    iconVector: ImageVector,
    modifier: Modifier = Modifier,
    isTechnicalValue: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TanawMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (isTechnicalValue) TanawMonoFontFamily else MaterialTheme.typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isLocked) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(R.string.field_locked),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.edit_field_fmt, label),
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
    iconVector: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
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
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun CustomerProfileNotices(
    state: MobileState,
    modifier: Modifier = Modifier,
    showLinearProgress: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLinearProgress && state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }
        if (state.offline) {
            Surface(
                color = TanawTheme.extendedColors.warningContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.offline_notice),
                    modifier = Modifier.padding(14.dp),
                    color = TanawTheme.extendedColors.onWarningContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Profile Header")
@Composable
private fun CustomerProfileHeaderPreview() {
    TanawCustomerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CustomerProfileHeader(
                fullName = "Maria Santos",
                email = "maria.santos@example.com",
                memberSince = "MEMBER SINCE OCTOBER 2024",
                onEditPhotoClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Personal Info")
@Composable
private fun CustomerPersonalInfoCardPreview() {
    TanawCustomerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CustomerPersonalInfoCard(
                fullName = "Maria Santos",
                email = "maria.santos@example.com",
                contact = "+63 917 555 0142",
                defaultAddress = "123 Rizal Ave, Brgy. San Antonio, Quezon City, Metro Manila",
                onEditName = {},
                onEmailInfo = {},
                onEditPhone = {},
                onEditAddress = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "SMS Card")
@Composable
private fun CustomerSmsCardPreview() {
    TanawCustomerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CustomerSmsCard(
                    isSmsEnabled = true,
                    phone = "+63 917 555 0142",
                    hasVerifiedPhone = true,
                    smsError = null,
                    isBusy = false,
                    isOffline = false,
                    onSwitchChange = {},
                )
                CustomerSmsCard(
                    isSmsEnabled = false,
                    phone = "",
                    hasVerifiedPhone = false,
                    smsError = null,
                    isBusy = false,
                    isOffline = false,
                    onSwitchChange = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Menu Card")
@Composable
private fun CustomerMenuCardPreview() {
    TanawCustomerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CustomerMenuCard(
                onOpenBookings = {},
                onOpenSavedAddresses = {},
                onChangePassword = {},
                onOpenTerms = {},
                onOpenPrivacy = {},
                onOpenHelp = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Profile Notices")
@Composable
private fun CustomerProfileNoticesPreview() {
    TanawCustomerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CustomerProfileNotices(
                state = MobileState(
                    busy = true,
                    offline = true,
                    error = "Could not sync SMS preferences.",
                    message = "Profile saved.",
                ),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Terms of Service Panel")
@Composable
private fun CustomerTermsSubPanelPreview() {
    TanawCustomerTheme {
        CustomerSubPanel(
            panel = "Terms of Service",
            content = null,
            settings = null,
            state = MobileState(),
            savedStructuredAddress = CustomerAddressRules.StructuredCustomerAddress(),
            linkError = false,
            openUriSafely = {},
            onBack = {},
            onEditAddress = {},
        )
    }
}
