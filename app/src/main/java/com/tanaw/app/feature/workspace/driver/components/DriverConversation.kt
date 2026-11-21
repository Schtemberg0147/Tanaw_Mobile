package com.tanaw.app.feature.workspace.driver.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tanaw.app.feature.workspace.driver.screens.DriverTheme
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

internal object ConversationRules {
    fun timestamp(value: String): String = try {
        DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Manila"))
            .format(OffsetDateTime.parse(value.replace(' ', 'T')).toInstant()) + " PHT"
    } catch (_: Exception) {
        "Time unavailable"
    }

    fun valid(body: String) = body.trim().codePointCount(0, body.trim().length) in 1..2000

    fun unread(rows: List<JsonObject>, delivery: String) =
        rows.firstOrNull { it.value("delivery_id") == delivery }?.value("unread")?.toIntOrNull() ?: 0
}

/** No message body is added to the saved offline workspace. Drafts are scoped to account/assignment. */
@Composable
internal fun DriverConversation(
    delivery: String,
    offline: Boolean,
    unread: Int?,
    load: suspend (String?) -> JsonObject,
    send: suspend (String, String) -> Unit,
    read: suspend (List<String>) -> Unit,
    changed: () -> Unit,
    expectedDriver: String? = null,
    initiallyOpen: Boolean = false,
    reference: String = ""
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    var draft by rememberSaveable { mutableStateOf("") }
    var request by rememberSaveable { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<JsonObject?>(null) }
    var before by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val currentLoad by rememberUpdatedState(load)

    LaunchedEffect(delivery, expectedDriver, open, before, revision, offline, owner) {
        if (!open || offline) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val result = currentLoad(before)
                    check(expectedDriver == null || result.value("driver_id") == expectedDriver) {
                        "ASSIGNMENT_CHANGED_RELOAD"
                    }
                    page = result
                    error = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    page = null
                    error = "Messages unavailable. Check your connection and delivery access, then retry."
                }
                delay(15.seconds)
            }
        }
    }

    fun action(block: suspend () -> Unit) {
        if (busy || offline) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = if (request != null) {
                    "Send unconfirmed. Retry the same message to avoid a duplicate."
                } else {
                    "Messages unavailable. Reconnect and retry."
                }
            } finally {
                busy = false
            }
        }
    }

    val historyScroll = rememberScrollState()
    val messages = page?.rows("messages").orEmpty()
    LaunchedEffect(open, before, messages.lastOrNull()?.value("id")) {
        if (open && before == null) historyScroll.animateScrollTo(historyScroll.maxValue)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (initiallyOpen) Modifier.fillMaxHeight() else Modifier),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            if (!initiallyOpen) {
                TextButton(
                    onClick = { open = !open },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "${if (open) "Close" else "Open"} office conversation · ${unread?.let { "$it unread" } ?: "Unread unavailable"}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            if (open) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Office",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (reference.isNotBlank()) {
                            Text(
                                text = reference,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (offline) "Offline" else unread?.let { "$it unread · Delivery updates" } ?: "Unread unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { revision++ },
                        enabled = !busy && !offline,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh messages",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val listModifier = if (initiallyOpen) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp)
                }
                Column(
                    modifier = listModifier
                        .verticalScroll(historyScroll)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (offline) {
                        Text(
                            text = "Offline · reconnect to read or send messages.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                TextButton(
                                    onClick = { revision++ },
                                    enabled = !busy && !offline,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    notice?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (before != null) {
                        TextButton(
                            onClick = {
                                page = null
                                before = null
                            },
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Latest messages", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (page == null && error == null && !offline) {
                        Text(
                            text = "Loading messages…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!offline) page?.let { data ->
                        if (data.value("has_more") == "true") {
                            TextButton(
                                onClick = {
                                    before = data.rows("messages").first().value("id")
                                    page = null
                                },
                                enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Older messages", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (messages.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "No messages yet.",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Send the office a delivery update below.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        messages.forEach { message ->
                            val mine = message.value("sender_role") == "driver"
                            val bubbleColor = if (mine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                            val textColor = if (mine) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            val timeColor = if (mine) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = bubbleColor,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomEnd = if (mine) 4.dp else 16.dp,
                                        bottomStart = if (mine) 16.dp else 4.dp
                                    ),
                                    border = if (mine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth(0.86f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (mine) "You" else "Office",
                                            color = textColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = message.value("body"),
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
                                        )
                                        Text(
                                            text = ConversationRules.timestamp(message.value("created_at")),
                                            color = timeColor,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        if (messages.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    action {
                                        read(messages.map { it.value("id") })
                                        notice = "Displayed messages marked read."
                                        changed()
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Mark displayed messages read", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (data.value("can_send") != "true") {
                            Text(
                                text = "This delivery conversation is read-only.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (initiallyOpen) Modifier.navigationBarsPadding() else Modifier)
                        .imePadding()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (request != null) {
                        Text(
                            text = "Last send unconfirmed. The message is kept for a safe retry.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val isDraftOverLimit = draft.trim().length > 2000
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 4000) draft = it },
                        label = { Text("Message to office") },
                        placeholder = { Text("Write a delivery update…") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy && !offline && request == null && page?.value("can_send") == "true",
                        isError = isDraftOverLimit,
                        minLines = 1,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            errorBorderColor = MaterialTheme.colorScheme.error
                        ),
                        supportingText = {
                            Text(
                                text = "${draft.trim().length}/2000 characters" + if (isDraftOverLimit) " · exceeds limit" else "",
                                color = if (isDraftOverLimit) MaterialTheme.colorScheme.error else Color.Unspecified
                            )
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "For urgent help, use your emergency contact.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                action {
                                    val id = request ?: UUID.randomUUID().toString().also { request = it }
                                    send(id, draft.trim())
                                    request = null
                                    draft = ""
                                    notice = "Message sent."
                                    before = null
                                    revision++
                                    changed()
                                }
                            },
                            enabled = !busy && !offline && (request != null || (page?.value("can_send") == "true" && ConversationRules.valid(draft))),
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (busy) "Working…" else if (request != null) "Retry send" else "Send message",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun previewConversationPage(): JsonObject = buildJsonObject {
    put("driver_id", "driver-1")
    put("can_send", true)
    put("has_more", false)
    put("messages", buildJsonArray {
        add(buildJsonObject {
            put("id", "1")
            put("sender_role", "staff")
            put("body", "Please proceed to Bay 4 upon arrival at the warehouse.")
            put("created_at", "2026-10-29T07:00:00Z")
        })
        add(buildJsonObject {
            put("id", "2")
            put("sender_role", "driver")
            put("body", "Copy that, 15 minutes out from the north gate.")
            put("created_at", "2026-10-29T07:05:00Z")
        })
    })
}

@Preview(showBackground = true, name = "Driver Conversation")
@Composable
private fun DriverConversationLightPreview() {
    DriverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.height(520.dp).padding(16.dp)) {
                DriverConversation(
                    delivery = "del-1",
                    offline = false,
                    unread = 1,
                    load = { previewConversationPage() },
                    send = { _, _ -> },
                    read = {},
                    changed = {},
                    expectedDriver = "driver-1",
                    initiallyOpen = true,
                    reference = "TNW-2026-0042"
                )
            }
        }
    }
}
