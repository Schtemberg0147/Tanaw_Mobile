package com.tanaw.app.feature.workspace.driver.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tanaw.app.feature.workspace.driver.rules.DriverPaymentMethod
import com.tanaw.app.feature.workspace.driver.rules.DriverPaymentRules
import com.tanaw.app.feature.workspace.driver.rules.ProofImage
import com.tanaw.app.feature.workspace.driver.screens.DriverTheme
import com.tanaw.app.feature.workspace.driver.store.PaymentDraft
import com.tanaw.app.feature.workspace.driver.store.PaymentDraftStore
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DriverPaymentSheet(
    deliveryId: String,
    trackingNumber: String,
    amountDue: BigDecimal,
    isResubmission: Boolean,
    rejectionReason: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    offline: Boolean,
    owner: String,
    onDismiss: () -> Unit,
    onSubmit: (requestId: String, method: String, referenceNumber: String, receiptBytes: ByteArray, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(owner, deliveryId) { if (owner.isNotBlank()) PaymentDraftStore(context) else null }
    val restored = remember(owner, deliveryId) {
        if (store != null) runCatching { store.read(owner, deliveryId) }.getOrNull() else null
    }
    var draft by remember(deliveryId) { mutableStateOf(restored ?: PaymentDraft()) }
    var filePath by rememberSaveable(deliveryId) { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var preparingPhoto by remember { mutableStateOf(false) }

    fun persist(next: PaymentDraft) {
        draft = next
        if (store != null) {
            scope.launch(Dispatchers.IO) {
                store.save(owner, deliveryId, next)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = filePath?.let(::File)
        if (success && file != null) {
            preparingPhoto = true
            photoError = null
            scope.launch {
                try {
                    val bytes = ProofImage.processCameraPhoto(file)
                    persist(draft.changed().copy(receipt = bytes))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    photoError = failure.message ?: "Unable to read the captured photo. Retry capture."
                } finally {
                    file.delete()
                    filePath = null
                    preparingPhoto = false
                }
            }
        } else {
            file?.delete()
            filePath = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            preparingPhoto = true
            photoError = null
            scope.launch {
                try {
                    val bytes = DriverEvidence.readPhotoAsync(context, uri)
                    persist(draft.changed().copy(receipt = bytes))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    photoError = failure.message ?: "Unable to read the selected receipt photo."
                } finally {
                    preparingPhoto = false
                }
            }
        }
    }

    fun launchCamera() {
        if (preparingPhoto || isSubmitting) return
        photoError = null
        try {
            val directory = File(context.cacheDir, "proof/$owner").apply { mkdirs() }
            val file = File(directory, "payment-${UUID.randomUUID()}.jpg")
            filePath?.let { File(it).delete() }
            filePath = file.absolutePath
            cameraLauncher.launch(FileProvider.getUriForFile(context, context.packageName + ".proof", file))
        } catch (_: Exception) {
            photoError = "No camera is available. Choose a photo from your gallery instead."
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting && !preparingPhoto) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.testTag("driver_payment_sheet")
    ) {
        DriverPaymentFormContent(
            trackingNumber = trackingNumber,
            amountDue = amountDue,
            isResubmission = isResubmission,
            rejectionReason = rejectionReason,
            method = draft.method,
            referenceNumber = draft.referenceNumber,
            notes = draft.notes,
            receipt = draft.receipt,
            preparingPhoto = preparingPhoto,
            isSubmitting = isSubmitting,
            offline = offline,
            errorMessage = photoError ?: errorMessage,
            onMethodSelected = { selected ->
                if (draft.method != selected.value) {
                    persist(draft.changed().copy(method = selected.value))
                }
            },
            onReferenceChanged = { value ->
                if (value.length <= DriverPaymentRules.MAX_REFERENCE_LENGTH) {
                    persist(draft.changed().copy(referenceNumber = value))
                }
            },
            onNotesChanged = { value ->
                if (value.length <= DriverPaymentRules.MAX_NOTES_LENGTH) {
                    persist(draft.changed().copy(notes = value))
                }
            },
            onTakePhoto = ::launchCamera,
            onPickGallery = {
                if (!preparingPhoto && !isSubmitting) {
                    photoError = null
                    galleryLauncher.launch("image/*")
                }
            },
            onRemovePhoto = {
                photoError = null
                persist(draft.changed().copy(receipt = null))
            },
            onDismiss = onDismiss,
            onSubmit = {
                val receiptBytes = draft.receipt ?: return@DriverPaymentFormContent
                onSubmit(draft.request, draft.method, draft.referenceNumber.trim(), receiptBytes, draft.notes.trim())
            }
        )
    }
}

@Composable
internal fun DriverPaymentFormContent(
    trackingNumber: String,
    amountDue: BigDecimal,
    isResubmission: Boolean,
    rejectionReason: String,
    method: String,
    referenceNumber: String,
    notes: String,
    receipt: ByteArray?,
    preparingPhoto: Boolean,
    isSubmitting: Boolean,
    offline: Boolean,
    errorMessage: String?,
    onMethodSelected: (DriverPaymentMethod) -> Unit,
    onReferenceChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedMethod = DriverPaymentMethod.fromValue(method)
    val trimmedRef = referenceNumber.trim()
    val refMissing = selectedMethod.requiresReference && trimmedRef.length < 2
    val isRefError = selectedMethod.requiresReference && referenceNumber.isNotEmpty() && trimmedRef.length < 2
    val canSubmit = !isSubmitting && !preparingPhoto && !offline &&
        amountDue > BigDecimal.ZERO &&
        !refMissing &&
        receipt != null && receipt.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isResubmission) "Resubmit Payment Proof" else "Record Payment Received",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (trackingNumber.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = trackingNumber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TanawMonoFontFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                enabled = !isSubmitting && !preparingPhoto,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (isResubmission && rejectionReason.isNotBlank()) {
            Surface(
                color = ExpiredRedBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ExpiredRed.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "OFFICE REJECTION REASON",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpiredRed
                    )
                    Text(
                        text = rejectionReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpiredRed
                    )
                }
            }
        }

        // Locked full amount finalized by the office
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "FULL AMOUNT DUE (OFFICE FINALIZED)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MobilePresentation.money(amountDue.toPlainString()),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = TanawMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Collect full payment face-to-face and have the customer sign the receipt before submitting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Payment method selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "PAYMENT METHOD *",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val methods = DriverPaymentMethod.entries
            methods.chunked(2).forEach { rowMethods ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowMethods.forEach { item ->
                        val selected = item == selectedMethod
                        Surface(
                            onClick = { onMethodSelected(item) },
                            enabled = !isSubmitting && !preparingPhoto,
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("payment_method_${item.value}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reference / OR Number
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (selectedMethod.requiresReference) {
                    "REFERENCE / TRANSACTION # *"
                } else {
                    "RECEIPT / OR # (OPTIONAL)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = referenceNumber,
                onValueChange = onReferenceChanged,
                placeholder = {
                    Text(
                        when (selectedMethod) {
                            DriverPaymentMethod.CASH -> "e.g. OR-887766 or Cash voucher #"
                            DriverPaymentMethod.GCASH -> "e.g. GCash Ref # 901234567890"
                            DriverPaymentMethod.BANK_TRANSFER -> "e.g. InstaPay / Bank Ref #"
                            DriverPaymentMethod.CHECK -> "e.g. Check # 001245"
                        }
                    )
                },
                enabled = !isSubmitting && !preparingPhoto,
                isError = isRefError,
                supportingText = if (isRefError) {
                    { Text("Enter at least 2 characters for the reference number.") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    errorBorderColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("payment_reference_input")
            )
        }

        // Signed receipt photo (Camera + Gallery, required)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SIGNED RECEIPT PHOTO (REQUIRED) *",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Capture or choose a clear photo of the customer-signed paper receipt (and transfer slip if digital).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTakePhoto,
                    enabled = !isSubmitting && !preparingPhoto,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("payment_camera_button")
                ) {
                    Text(if (receipt != null) "Retake Photo" else "Take Photo")
                }
                OutlinedButton(
                    onClick = onPickGallery,
                    enabled = !isSubmitting && !preparingPhoto,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("payment_gallery_button")
                ) {
                    Text("Gallery")
                }
            }
            if (preparingPhoto) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (receipt != null) {
                PaymentReceiptPreview(
                    receipt = receipt,
                    enabled = !isSubmitting && !preparingPhoto,
                    onRemove = onRemovePhoto
                )
            }
        }

        // Optional notes
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "NOTES / REMARKS (OPTIONAL)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChanged,
                placeholder = { Text("e.g. Received cash in full from site receiver") },
                enabled = !isSubmitting && !preparingPhoto,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_notes_input")
            )
        }

        if (offline) {
            Text(
                text = "Reconnect and refresh before submitting payment proof.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        errorMessage?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("payment_submit_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = when {
                    isSubmitting -> "Submitting…"
                    isResubmission -> "Resubmit Payment Proof"
                    else -> "Submit Payment for Verification"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Departure unlocks automatically once the office verifies this payment.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PaymentReceiptPreview(
    receipt: ByteArray,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    var preview by remember(receipt) { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember(receipt) { mutableStateOf(false) }

    LaunchedEffect(receipt) {
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { ProofImage.decode(receipt, DriverEvidence.MAX_BYTES) }.getOrNull()
        }
        if (isActive) {
            preview?.recycle()
            preview = bitmap
            decodeFailed = bitmap == null
        } else {
            bitmap?.recycle()
        }
    }

    DisposableEffect(receipt) {
        onDispose {
            preview?.recycle()
            preview = null
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmap = preview
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Signed payment receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("payment_receipt_preview")
                )
            } else if (decodeFailed) {
                Text(
                    text = "Unable to display receipt preview. Capture or choose the photo again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = "Remove photo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Record Payment — Cash")
@Composable
private fun DriverPaymentFormCashPreview() {
    DriverTheme {
        DriverPaymentFormContent(
            trackingNumber = "SHP-NE-26-002353",
            amountDue = BigDecimal("2500.00"),
            isResubmission = false,
            rejectionReason = "",
            method = DriverPaymentMethod.CASH.value,
            referenceNumber = "OR-887766",
            notes = "",
            receipt = null,
            preparingPhoto = false,
            isSubmitting = false,
            offline = false,
            errorMessage = null,
            onMethodSelected = {},
            onReferenceChanged = {},
            onNotesChanged = {},
            onTakePhoto = {},
            onPickGallery = {},
            onRemovePhoto = {},
            onDismiss = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true, name = "Resubmit Payment — Rejected GCash")
@Composable
private fun DriverPaymentFormRejectedPreview() {
    DriverTheme {
        DriverPaymentFormContent(
            trackingNumber = "SHP-NE-26-002353",
            amountDue = BigDecimal("2500.00"),
            isResubmission = true,
            rejectionReason = "Receipt signature is blurred and GCash reference number was not found.",
            method = DriverPaymentMethod.GCASH.value,
            referenceNumber = "",
            notes = "",
            receipt = null,
            preparingPhoto = false,
            isSubmitting = false,
            offline = false,
            errorMessage = null,
            onMethodSelected = {},
            onReferenceChanged = {},
            onNotesChanged = {},
            onTakePhoto = {},
            onPickGallery = {},
            onRemovePhoto = {},
            onDismiss = {},
            onSubmit = {}
        )
    }
}
