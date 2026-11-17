package com.tanaw.app.feature.workspace.driver.dialogs

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.driver.components.*
import com.tanaw.app.feature.workspace.driver.rules.*
import com.tanaw.app.feature.workspace.driver.screens.*
import com.tanaw.app.feature.workspace.driver.store.*
import com.tanaw.app.feature.workspace.state.*
import com.tanaw.app.feature.workspace.viewmodel.*
import com.tanaw.app.ui.theme.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DriverTransaction(
    title: String,
    busy: Boolean,
    close: () -> Unit,
    label: String,
    enabled: Boolean,
    submit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        DriverTheme {
            BackHandler { if (!busy) close() }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column(Modifier.statusBarsPadding()) {
                        DriverPageHeading(title) { if (!busy) close() }
                        if (busy) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                                Button(
                                    onClick = submit,
                                    enabled = enabled && !busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        text = if (busy) "Submitting…" else label,
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    if (!busy && label == "COMPLETE DELIVERY") {
                                        Spacer(Modifier.width(8.dp))
                                        DriverAsset(R.drawable.figma_driver_dafed, 17, tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    if (!busy && label == "BACK TO HOME") {
                                        Spacer(Modifier.width(8.dp))
                                        DriverAsset(R.drawable.figma_driver_43c84, 16, 18, tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DriverIncidentDialog(
    id: String,
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    owner: String = "",
    close: () -> Unit
) {
    DriverIncidentForm(id, state, close, owner) { request, category, description, minutes, photos ->
        vm.reportIncident(id, request, category, description, minutes, photos, close)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DriverIncidentDialog(
    id: String,
    owner: String,
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    close: () -> Unit
) {
    DriverIncidentForm(id, state, close, owner) { request, category, description, minutes, photos ->
        vm.reportIncident(id, request, category, description, minutes, photos, close)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DriverIncidentForm(
    id: String,
    state: MobileState,
    close: () -> Unit,
    owner: String = "",
    submit: (String, String, String, Int, List<ByteArray>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(owner, id) { if (owner.isNotBlank()) IncidentDraftStore(context) else null }
    val restored = remember(owner, id) {
        if (store != null) runCatching { store.read(owner, id) }.getOrNull() else null
    }
    var category by rememberSaveable(id) { mutableStateOf(restored?.category ?: "traffic") }
    var description by rememberSaveable(id) { mutableStateOf(restored?.description ?: "") }
    var minutes by rememberSaveable(id) { mutableStateOf(restored?.minutes?.toString() ?: "0") }
    var request by rememberSaveable(id) { mutableStateOf(restored?.request ?: UUID.randomUUID().toString()) }
    var photos by remember(id) { mutableStateOf(restored?.photos ?: emptyList<ByteArray>()) }
    var preparingPhotos by remember { mutableStateOf(false) }

    fun persist(
        c: String = category,
        d: String = description,
        m: String = minutes,
        p: List<ByteArray> = photos,
        r: String = request
    ) {
        if (store != null) {
            scope.launch(Dispatchers.IO) {
                store.save(
                    owner,
                    id,
                    IncidentDraft(
                        request = r,
                        category = c,
                        description = d,
                        minutes = m.toIntOrNull()?.coerceIn(0, 10080) ?: 0,
                        photos = p
                    )
                )
            }
        }
    }

    val isDescError = description.isNotEmpty() && (description.trim().length < 5 || description.trim().length > 2000)
    val delayVal = minutes.toIntOrNull()
    val isMinutesError = minutes.isNotEmpty() && (delayVal == null || delayVal !in 0..1440)

    DriverTransaction(
        title = "Report an Issue",
        busy = state.busy || preparingPhotos,
        close = close,
        label = if (preparingPhotos) "PREPARING PHOTO…" else "Submit Report",
        enabled = !preparingPhotos && !state.offline && description.trim().length in 5..2000 && minutes.toIntOrNull() in 0..1440,
        submit = { if (!preparingPhotos) submit(request, category, description, minutes.toInt(), photos) }
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
                Text(
                    text = "SELECT ISSUE TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "traffic" to "Traffic/Delay",
                        "breakdown" to "Vehicle Issue",
                        "accident" to "Safety Concern",
                        "weather" to "Weather",
                        "other" to "Other"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = category == value,
                            onClick = {
                                if (category != value) {
                                    category = value
                                    val nextReq = UUID.randomUUID().toString()
                                    request = nextReq
                                    persist(c = value, r = nextReq)
                                }
                            },
                            enabled = !state.busy && !preparingPhotos,
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "DESCRIPTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        val nextReq = UUID.randomUUID().toString()
                        request = nextReq
                        persist(d = it, r = nextReq)
                    },
                    placeholder = { Text("Describe the issue in detail…") },
                    enabled = !state.busy && !preparingPhotos,
                    isError = isDescError,
                    supportingText = {
                        if (isDescError) {
                            Text(
                                if (description.trim().length < 5) "Description must be at least 5 characters."
                                else "Description cannot exceed 2000 characters."
                            )
                        } else {
                            Text("${description.trim().length}/2000 characters")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = {
                        minutes = it
                        val nextReq = UUID.randomUUID().toString()
                        request = nextReq
                        persist(m = it, r = nextReq)
                    },
                    label = { Text("Expected delay in minutes") },
                    enabled = !state.busy && !preparingPhotos,
                    singleLine = true,
                    isError = isMinutesError,
                    supportingText = if (isMinutesError) {
                        { Text("Enter a delay between 0 and 1440 minutes (up to 24 hours).") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ATTACH PHOTOS (Optional)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IncidentPhotoPicker(
                    photos = photos,
                    busy = state.busy || state.offline,
                    onPreparingChange = { preparingPhotos = it }
                ) {
                    photos = it
                    val nextReq = UUID.randomUUID().toString()
                    request = nextReq
                    persist(p = it, r = nextReq)
                }
            }
        }

        if (state.offline) {
            Text(
                text = "Reconnect and refresh before submitting an incident. Your current draft is retained.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        TextButton(
            onClick = close,
            enabled = !state.busy && !preparingPhotos,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun DriverProofDialog(
    id: String,
    owner: String,
    reference: String,
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    onBackHome: () -> Unit,
    close: () -> Unit
) {
    DriverProofForm(id, owner, reference, state, onBackHome, close) { recipient, photo, signature, attempt, saved ->
        vm.submitProof(id, recipient, photo, signature, attempt, saved)
    }
}

@Composable
internal fun DriverProofForm(
    id: String,
    owner: String,
    reference: String,
    state: MobileState,
    onBackHome: () -> Unit,
    close: () -> Unit,
    submit: (String, ByteArray, ByteArray, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(owner, id) { ProofDraftStore(context) }
    val restored = remember(owner, id) { runCatching { store.read(owner, id) } }
    var draft by remember(owner, id) { mutableStateOf(restored.getOrNull() ?: ProofDraft()) }
    var filePath by rememberSaveable(owner, id) { mutableStateOf<String?>(null) }
    var current by remember(owner, id) { mutableStateOf(emptyList<Offset>()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by rememberSaveable(owner, id) { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    val editable = !state.busy && !processing && !draft.frozen && restored.isSuccess
    val isDark = isSystemInDarkTheme()

    fun persist(next: ProofDraft): Boolean {
        if (!store.save(owner, id, next)) {
            error = "Unable to save your private draft. Free device storage and retry before leaving."
            return false
        }
        draft = next
        error = null
        return true
    }

    var preview by remember(draft.photo) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(draft.photo) {
        val bytes = draft.photo
        if (bytes == null) {
            preview?.recycle()
            preview = null
        } else {
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { ProofImage.decode(bytes) }.getOrNull()
            }
            if (isActive) {
                preview?.recycle()
                preview = bitmap
            } else {
                bitmap?.recycle()
            }
        }
    }
    DisposableEffect(draft.photo) {
        onDispose {
            preview?.recycle()
            preview = null
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = filePath?.let(::File)
        if (success && file != null) {
            processing = true
            error = null
            scope.launch {
                try {
                    val bytes = ProofImage.processCameraPhoto(file)
                    persist(draft.changed().copy(photo = bytes))
                    file.delete()
                    filePath = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    file.delete()
                    filePath = null
                    error = "Unable to read the photo. Your existing draft is retained; retry capture."
                } finally {
                    processing = false
                }
            }
        } else {
            file?.delete()
            filePath = null
            error = "Photo capture cancelled. Your previous photo and signature are retained."
        }
    }

    fun takePhoto() {
        if (processing) return
        try {
            val directory = File(context.cacheDir, "proof/$owner").apply { mkdirs() }
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            filePath?.let { File(it).delete() }
            filePath = file.absolutePath
            camera.launch(FileProvider.getUriForFile(context, context.packageName + ".proof", file))
        } catch (_: Exception) {
            error = "No camera is available. Retry on a camera-equipped device."
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Discard saved proof draft?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "This removes this device's photo, recipient and signature. Uploaded evidence is retained for reconciliation.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.clear(owner, id)
                        confirmDiscard = false
                        close()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Discard draft", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDiscard = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Keep draft", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    if (submitted) {
        val exitSubmitted = {
            close()
            onBackHome()
        }
        DriverTransaction("Delivery proof submitted", false, exitSubmitted, "BACK TO HOME", true, exitSubmitted) {
            Surface(
                color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else NavyPrimary,
                shape = RoundedCornerShape(16.dp),
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DriverAsset(R.drawable.figma_driver_966f6, 33, 24)
                    Text(
                        text = "Proof Submitted!",
                        color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = reference,
                        color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFDBE8F5),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            Text(
                text = "Your delivery photo and recipient signature are saved. Refresh your trip for the current office review or correction status.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Final completion and allowance processing are confirmed by the office.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    DriverTransaction(
        title = "Submit Proof of Delivery",
        busy = state.busy || processing,
        close = close,
        label = if (processing) "PREPARING PROOF…" else if (draft.frozen) "RETRY / CHECK SAVED PROOF" else "COMPLETE DELIVERY",
        enabled = !processing && !state.offline && restored.isSuccess &&
            draft.recipient.trim().length in 2..100 && preview != null && draft.strokes.isNotEmpty(),
        submit = {
            if (processing) return@DriverTransaction
            processing = true
            scope.launch {
                try {
                    var ready = draft
                    if (!ready.frozen) {
                        val bytes = ProofImage.renderSignaturePng(ready.strokes)
                        ready = ready.copy(signature = bytes, frozen = true)
                    }
                    if (persist(ready)) {
                        submit(ready.recipient, requireNotNull(ready.photo), requireNotNull(ready.signature), ready.attempt) {
                            store.clear(owner, id)
                            submitted = true
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "Unable to prepare proof. Your saved draft is retained; retry or contact the office."
                } finally {
                    processing = false
                }
            }
        }
    ) {
        if (processing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SHIPMENT REFERENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = reference,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pending Delivery Confirmation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = if (draft.frozen) {
                "This attempt is saved and locked while its result is uncertain. Retry checks existing proof before sending again."
            } else {
                "Your draft is saved privately on this device. Back keeps it for this account and delivery."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.offline) {
            Text(
                text = "Reconnect and refresh before submitting. Your saved draft is retained.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (restored.isFailure) {
            Text(
                text = "The saved proof draft cannot be read. Contact the office before submitting another attempt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Step 1: Photo of Cargo
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. PHOTO OF CARGO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val photoBg = if (preview != null) {
                        if (isDark) StatusGreenBgDark else SuccessBannerBg
                    } else {
                        if (isDark) ErrorBgDark else ExpiredRedBg
                    }
                    val photoFg = if (preview != null) {
                        if (isDark) StatusGreenDark else SuccessGreen
                    } else {
                        if (isDark) ErrorRedDark else ExpiredRed
                    }
                    Surface(color = photoBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = if (preview != null) "PHOTO ADDED" else "REQUIRED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = photoFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    onClick = ::takePhoto,
                    enabled = editable,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 172.dp).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (preview != null) {
                            Image(
                                bitmap = preview!!.asImageBitmap(),
                                contentDescription = "Captured delivery photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            DriverAsset(R.drawable.figma_driver_ceb65, 48, 46)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (preview != null) "RETAKE PHOTO" else "TAKE PHOTO",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Capture a clear view of items & labels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Step 2: Recipient Name
        val isRecipientError = draft.recipient.isNotBlank() && draft.recipient.trim().length < 2
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "2. RECIPIENT NAME",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = draft.recipient,
                    onValueChange = { persist(draft.changed().copy(recipient = it.take(100))) },
                    placeholder = { Text("Enter full name") },
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    isError = isRecipientError,
                    supportingText = if (isRecipientError) {
                        { Text("Recipient name must be at least 2 characters.") }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }

        // Step 3: Digital Signature
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. DIGITAL SIGNATURE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sigBg = if (draft.strokes.isNotEmpty()) {
                        if (isDark) StatusGreenBgDark else SuccessBannerBg
                    } else {
                        if (isDark) ErrorBgDark else ExpiredRedBg
                    }
                    val sigFg = if (draft.strokes.isNotEmpty()) {
                        if (isDark) StatusGreenDark else SuccessGreen
                    } else {
                        if (isDark) ErrorRedDark else ExpiredRed
                    }
                    Surface(color = sigBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = if (draft.strokes.isNotEmpty()) "SIGNED" else "REQUIRED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = sigFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    if (draft.strokes.isEmpty() && current.isEmpty()) {
                        Text(
                            text = "Tap and draw to sign",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    fun commitStroke(points: List<Offset>) {
                        if (points.size > 1) {
                            val w = canvasSize.width.coerceAtLeast(1)
                            val h = canvasSize.height.coerceAtLeast(1)
                            persist(
                                draft.changed().copy(
                                    strokes = draft.strokes + listOf(
                                        points.map {
                                            SignaturePoint(
                                                (it.x / w).coerceIn(0f, 1f),
                                                (it.y / h).coerceIn(0f, 1f)
                                            )
                                        }
                                    )
                                )
                            )
                        }
                    }
                    val strokeColor = MaterialTheme.colorScheme.onSurface
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("proof_signature_canvas")
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(editable) {
                                if (editable) {
                                    detectDragGestures(
                                        onDragStart = { current = listOf(it) },
                                        onDragEnd = {
                                            val pts = current
                                            current = emptyList()
                                            commitStroke(pts)
                                        },
                                        onDragCancel = {
                                            val pts = current
                                            current = emptyList()
                                            commitStroke(pts)
                                        }
                                    ) { change, _ ->
                                        change.consume()
                                        if (current.size < 2000) current = current + change.position
                                    }
                                }
                            }
                    ) {
                        (draft.strokes.map { line ->
                            line.map { Offset(it.x * size.width, it.y * size.height) }
                        } + listOf(current)).forEach { line ->
                            line.zipWithNext().forEach { (a, b) -> drawLine(strokeColor, a, b, 4f) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            persist(draft.changed().copy(strokes = emptyList()))
                            current = emptyList()
                        },
                        enabled = editable,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Clear signature", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        TextButton(
            onClick = { confirmDiscard = true },
            enabled = editable,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                text = "Discard saved draft",
                style = MaterialTheme.typography.labelLarge,
                color = if (editable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        (error ?: state.error)?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Incident Form Light")
@Composable
private fun DriverIncidentFormLightPreview() {
    DriverTheme(darkTheme = false) {
        DriverIncidentForm("fixture", MobileState(), {}) { _, _, _, _, _ -> }
    }
}

@Preview(showBackground = true, name = "Incident Form Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DriverIncidentFormDarkPreview() {
    DriverTheme(darkTheme = true) {
        DriverIncidentForm("fixture", MobileState(), {}) { _, _, _, _, _ -> }
    }
}
