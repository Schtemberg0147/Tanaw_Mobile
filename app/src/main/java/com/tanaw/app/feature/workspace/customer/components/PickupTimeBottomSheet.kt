package com.tanaw.app.feature.workspace.customer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.customer.rules.MobileBookingRules
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Schedule Pick-up Bottom Sheet (FTL Lead-Time Constrained) ────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupTimeBottomSheet(
    scheduledRaw: String,
    minLeadTimeHours: Int,
    helperText: String,
    now: Instant,
    onDismiss: () -> Unit,
    onSchedule: (String) -> Unit
) {
    val earliest = MobileBookingRules.earliest(now, minLeadTimeHours.toLong())
    val todayDate = now.atZone(MobileBookingRules.zone).toLocalDate()
    val tomorrowDate = todayDate.plusDays(1)
    val days = remember(now, minLeadTimeHours) {
        CustomerBookingRules.scheduledDays(now, minLeadTimeHours)
    }
    val defaultRaw = remember(now, minLeadTimeHours) {
        CustomerBookingRules.defaultScheduledRaw(now, minLeadTimeHours)
    }
    val initialDateTime = remember(scheduledRaw, minLeadTimeHours, defaultRaw) {
        val trimmed = scheduledRaw.trim()
        runCatching { LocalDateTime.parse(trimmed, MobileBookingRules.format) }
            .getOrNull()
            ?.takeIf { MobileBookingRules.departure(trimmed, now, minLeadTimeHours.toLong()) != null }
            ?: LocalDateTime.parse(defaultRaw, MobileBookingRules.format)
    }

    var selectedDate by remember(initialDateTime, days) {
        mutableStateOf(
            initialDateTime.toLocalDate().takeIf { it in days } ?: days.first()
        )
    }
    val availableHours = remember(selectedDate, now, minLeadTimeHours) {
        CustomerBookingRules.availableHoursForDate(selectedDate, now, minLeadTimeHours)
            .ifEmpty { listOf(earliest.hour) }
    }
    var selectedHour by remember(initialDateTime, availableHours) {
        mutableIntStateOf(
            initialDateTime.hour.takeIf { it in availableHours } ?: availableHours.first()
        )
    }
    LaunchedEffect(availableHours) {
        if (selectedHour !in availableHours) {
            selectedHour = availableHours.first()
        }
    }
    val availableMinutes = remember(selectedDate, selectedHour, now, minLeadTimeHours) {
        CustomerBookingRules.availableMinutesForDateTime(selectedDate, selectedHour, now, minLeadTimeHours)
            .ifEmpty { listOf(earliest.minute) }
    }
    var selectedMinute by remember(initialDateTime, availableMinutes) {
        mutableIntStateOf(
            initialDateTime.minute.takeIf { it in availableMinutes } ?: availableMinutes.first()
        )
    }
    LaunchedEffect(availableMinutes) {
        if (selectedMinute !in availableMinutes) {
            selectedMinute = availableMinutes.first()
        }
    }

    val candidateDateTime = selectedDate.atTime(selectedHour, selectedMinute)
    val candidateFormatted = candidateDateTime.format(MobileBookingRules.format)
    val displayScheduledLabel = CustomerBookingRules.formatSchedulePill(candidateFormatted, now)
    val canConfirm = MobileBookingRules.departure(candidateFormatted, now, minLeadTimeHours.toLong()) != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.pickup_time_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$helperText · Up to 14 days ahead",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Selected Date & Time Summary Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = displayScheduledLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // 3-Column Wheel Picker (Day | Hour | Minute)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp),
                contentAlignment = Alignment.Center
            ) {
                // Center Selection Band
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH) }
                    WheelColumn(
                        items = days,
                        selectedItem = selectedDate,
                        label = { d ->
                            when (d) {
                                todayDate -> "Today"
                                tomorrowDate -> "Tomorrow"
                                else -> d.format(dayFormatter)
                            }
                        },
                        onSelect = { selectedDate = it },
                        modifier = Modifier.weight(1.3f)
                    )

                    WheelColumn(
                        items = availableHours,
                        selectedItem = selectedHour,
                        label = { h ->
                            val amPm = if (h < 12) "AM" else "PM"
                            val hour12 = when {
                                h == 0 -> 12
                                h > 12 -> h - 12
                                else -> h
                            }
                            "$hour12 $amPm"
                        },
                        onSelect = { selectedHour = it },
                        modifier = Modifier.weight(1f)
                    )

                    WheelColumn(
                        items = availableMinutes,
                        selectedItem = selectedMinute,
                        label = { m -> m.toString().padStart(2, '0') },
                        onSelect = { selectedMinute = it },
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }

            Button(
                onClick = { onSchedule(candidateFormatted) },
                enabled = canConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ConfirmScheduleButton")
            ) {
                Text(
                    text = stringResource(R.string.schedule_button),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun <T> WheelColumn(
    items: List<T>,
    selectedItem: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var userScrolled by remember { mutableStateOf(false) }

    val centeredIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                listState.firstVisibleItemIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { itemInfo ->
                    kotlin.math.abs((itemInfo.offset + itemInfo.size / 2) - viewportCenter)
                }?.index ?: listState.firstVisibleItemIndex
            }
        }
    }

    // Track user-initiated scroll and auto-select centered item while scrolling & when finger is released
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, centeredIndex, currentItems.size) }
            .distinctUntilChanged()
            .collect { (scrolling, centerIdx, _) ->
                if (scrolling) {
                    userScrolled = true
                }
                if (userScrolled) {
                    currentItems.getOrNull(centerIdx)?.let { centeredItem ->
                        currentOnSelect(centeredItem)
                    }
                    if (!scrolling) {
                        userScrolled = false
                    }
                }
            }
    }

    // Sync list position when selectedItem changes externally (e.g., available hours/minutes list updates)
    LaunchedEffect(selectedIndex, items.size) {
        if (!listState.isScrollInProgress && !userScrolled && centeredIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight()
    ) {
        itemsIndexed(items) { index, item ->
            val isCentered = index == centeredIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        userScrolled = false
                        onSelect(item)
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCentered) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
