package com.tanaw.app.feature.workspace.driver.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.driver.rules.ProofImage
import com.tanaw.app.feature.workspace.driver.screens.DriverAsset
import com.tanaw.app.feature.workspace.driver.screens.DriverTheme
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.theme.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object EvidenceRules {
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    val PHOTO_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    val RECEIPT_MIME_TYPES = setOf("image/jpeg", "image/png")

    fun validatePhotoMime(mimeType: String?) {
        require(mimeType in PHOTO_MIME_TYPES) { "Choose a JPG, PNG or WebP photo." }
    }

    fun validateReceiptMime(mimeType: String?) {
        require(mimeType in RECEIPT_MIME_TYPES) { "Choose a JPG or PNG receipt." }
    }

    fun readStreamWithinLimit(input: java.io.InputStream, maxBytes: Int, limitExceededMessage: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= maxBytes) { limitExceededMessage }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.isNotEmpty()) { "File is empty." }
        return bytes
    }
}

internal object DriverEvidence {
    const val MAX_BYTES = EvidenceRules.MAX_IMAGE_BYTES

    suspend fun readPhotoAsync(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        EvidenceRules.validatePhotoMime(context.contentResolver.getType(uri))
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
            EvidenceRules.readStreamWithinLimit(input, MAX_BYTES, "Choose a photo no larger than 5 MB.")
        } ?: error("Unable to read photo.")
        withContext(Dispatchers.Default) { jpeg(rawBytes) }
    }

    fun jpeg(bytes: ByteArray): ByteArray {
        val bitmap = ProofImage.decode(bytes, MAX_BYTES)
        return try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray().also {
                require(it.size in 1..MAX_BYTES) { "Prepared photo exceeds 5 MB. Choose a smaller photo." }
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun jpegAsync(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        jpeg(bytes)
    }

    suspend fun readReceiptAsync(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        EvidenceRules.validateReceiptMime(context.contentResolver.getType(uri))
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            EvidenceRules.readStreamWithinLimit(input, MAX_BYTES, "Receipt must be 5 MB or smaller.")
        } ?: error("Unable to read the receipt.")
        require(bytes.isNotEmpty()) { "Unable to read the receipt. Choose it again." }
        withContext(Dispatchers.Default) {
            val bitmap = ProofImage.decode(bytes)
            bitmap.recycle()
        }
        bytes
    }

    fun status(record: JsonObject) = when (record.value("status")) {
        "pending" -> "Pending office review"
        "approved" -> "Approved"
        "rejected" -> "Rejected"
        else -> "Review status unavailable"
    }
}

@Composable
internal fun DriverEvidenceRecords(
    shipment: String,
    details: JsonObject,
    busy: Boolean,
    view: ((String, String, String) -> Unit)?
) {
    val isDark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        details.value("evidence_error").takeIf(String::isNotBlank)?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = err,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (details["expenses"] is JsonArray && details.value("evidence_error").isBlank()) {
            Text(
                text = "Trip expenses",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val expenses = details.rows("expenses")
            if (expenses.isEmpty()) {
                Text(
                    text = "No expenses submitted for this trip.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            expenses.forEach { expense ->
                val label = expense.value("description").ifBlank { expense.value("category") }
                val statusLabel = DriverEvidence.status(expense)
                val statusValue = expense.value("status")
                val (badgeBg, badgeFg) = when (statusValue) {
                    "approved" -> (if (isDark) StatusGreenBgDark else SuccessBannerBg) to (if (isDark) StatusGreenDark else SuccessGreen)
                    "rejected" -> (if (isDark) ErrorBgDark else ExpiredRedBg) to (if (isDark) ErrorRedDark else ExpiredRed)
                    "pending" -> (if (isDark) WarningBgDark else ExpiringOrangeBg) to (if (isDark) WarningOrangeDark else ExpiringOrange)
                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = "$label · ${MobilePresentation.money(expense.value("amount"))}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = statusLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeFg
                                )
                            }
                        }
                        Text(
                            text = "Submitted ${MobilePresentation.time(expense.value("created_at"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        expense.value("reviewed_at").takeIf(String::isNotBlank)?.let {
                            Text(
                                text = "Reviewed ${MobilePresentation.time(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        expense.value("review_reason").takeIf(String::isNotBlank)?.let {
                            Text(
                                text = "Office feedback: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusValue == "rejected") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (expense.value("receipt_path").isNotBlank() && view != null) {
                            OutlinedButton(
                                onClick = { view(shipment, "expense", expense.value("id")) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (!busy) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            ) {
                                Text("View receipt · $label", style = MaterialTheme.typography.labelMedium)
                            }
                        } else if (expense.value("receipt_path").isBlank()) {
                            Text(
                                text = "No receipt attached",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Expense history unavailable. Refresh trip records to retry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        details.rows("incidents").forEach { incident ->
            val resolved = incident.value("resolved_at").isNotBlank()
            val badgeBg = if (resolved) {
                if (isDark) StatusGreenBgDark else SuccessBannerBg
            } else {
                if (isDark) WarningBgDark else ExpiringOrangeBg
            }
            val badgeFg = if (resolved) {
                if (isDark) StatusGreenDark else SuccessGreen
            } else {
                if (isDark) WarningOrangeDark else ExpiringOrange
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${incident.value("category")} · ${incident.value("description")}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = if (resolved) "Incident resolved" else "Incident awaiting office resolution",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg
                        )
                    }
                    details.rows("incident_evidence")
                        .filter { it.value("incident_id") == incident.value("id") }
                        .forEach { photo ->
                            if (view != null) {
                                OutlinedButton(
                                    onClick = { view(shipment, "incident", photo.value("id")) },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!busy) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                    )
                                ) {
                                    Text(
                                        text = "View incident photo ${photo.value("slot")}",
                                        style = MaterialTheme.typography.labelMedium
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
internal fun IncidentPhotoPicker(
    photos: List<ByteArray>,
    busy: Boolean,
    onPreparingChange: (Boolean) -> Unit = {},
    onChange: (List<ByteArray>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            preparing = true
            onPreparingChange(true)
            error = null
            scope.launch {
                try {
                    if (photos.size < 3) {
                        val photo = DriverEvidence.readPhotoAsync(context, uri)
                        onChange(photos + listOf(photo))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Unable to prepare photo. Choose another image."
                } finally {
                    preparing = false
                    onPreparingChange(false)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DriverAsset(R.drawable.figma_driver_f0840, 20, 18, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = "Private incident photos · ${photos.size}/3 · up to 5 MB each",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            enabled = !busy && !preparing && photos.size < 3,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.5.dp,
                if (!busy && !preparing && photos.size < 3) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
            )
        ) {
            Text(
                text = if (preparing) "Preparing photo…" else "Add incident photo",
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (preparing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        photos.forEachIndexed { index, bytes ->
            key(bytes) {
                IncidentPhotoThumbnail(bytes, index, busy || preparing) {
                    onChange(photos.filterIndexed { position, _ -> position != index })
                }
            }
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun IncidentPhotoThumbnail(
    bytes: ByteArray,
    index: Int,
    disabled: Boolean,
    onRemove: () -> Unit
) {
    var preview by remember(bytes) { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember(bytes) { mutableStateOf(false) }

    LaunchedEffect(bytes) {
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { ProofImage.decode(bytes, DriverEvidence.MAX_BYTES) }.getOrNull()
        }
        if (isActive) {
            preview?.recycle()
            preview = bitmap
            decodeFailed = (bitmap == null)
        } else {
            bitmap?.recycle()
        }
    }

    DisposableEffect(bytes) {
        onDispose {
            preview?.recycle()
            preview = null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview!!.asImageBitmap(),
                    contentDescription = "Incident photo ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else if (decodeFailed) {
                Text(
                    text = "Unable to display photo preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(
                onClick = onRemove,
                enabled = !disabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(
                    text = "Remove incident photo ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!disabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

private fun previewEvidenceDetails(): JsonObject = buildJsonObject {
    put("expenses", buildJsonArray {
        add(buildJsonObject {
            put("id", "exp-1")
            put("category", "Toll fee")
            put("description", "NLEX Bocaue Toll")
            put("amount", "450.00")
            put("status", "approved")
            put("receipt_path", "receipts/toll.jpg")
            put("created_at", "2026-10-29T07:15:00Z")
        })
        add(buildJsonObject {
            put("id", "exp-2")
            put("category", "Parking")
            put("amount", "120.00")
            put("status", "rejected")
            put("receipt_path", "receipts/parking.jpg")
            put("review_reason", "Receipt image blurry")
            put("created_at", "2026-10-29T09:10:00Z")
        })
    })
    put("incidents", buildJsonArray {
        add(buildJsonObject {
            put("id", "inc-1")
            put("category", "traffic")
            put("description", "Road closure along Quirino Highway")
        })
    })
    put("incident_evidence", buildJsonArray {
        add(buildJsonObject {
            put("id", "ev-1")
            put("incident_id", "inc-1")
            put("slot", "1")
        })
    })
}

@Preview(showBackground = true, name = "Driver Evidence Light")
@Composable
private fun DriverEvidenceLightPreview() {
    DriverTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) {
                DriverEvidenceRecords("ship-1", previewEvidenceDetails(), false) { _, _, _ -> }
            }
        }
    }
}

@Preview(showBackground = true, name = "Driver Evidence Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DriverEvidenceDarkPreview() {
    DriverTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) {
                DriverEvidenceRecords("ship-1", previewEvidenceDetails(), false) { _, _, _ -> }
            }
        }
    }
}
