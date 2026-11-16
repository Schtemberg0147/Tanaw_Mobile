package com.tanaw.app.feature.workspace.customer.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.customer.rules.MobileBookingRules
import com.tanaw.app.ui.theme.OrangeAccent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Pick-up Time Bottom Sheet (Image 4) ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupTimeBottomSheet(
    isPickupNow: Boolean,
    scheduledRaw: String,
    now: Instant,
    onDismiss: () -> Unit,
    onSelectNow: () -> Unit,
    onSchedule: (String) -> Unit
) {
    val earliest = MobileBookingRules.earliest(now)
    val todayDate = now.atZone(MobileBookingRules.zone).toLocalDate()
    val tomorrowDate = todayDate.plusDays(1)
    val initialDateTime = runCatching { LocalDateTime.parse(scheduledRaw.trim(), MobileBookingRules.format) }
        .getOrNull()
        ?.takeIf { !it.toLocalDate().isBefore(tomorrowDate) && !it.isBefore(earliest) }
        ?: tomorrowDate.atTime(9, 0).let { defaultTomorrow ->
            if (defaultTomorrow.isBefore(earliest)) earliest.plusMinutes(15) else defaultTomorrow
        }

    // Scheduled dates start from Tomorrow to future (30 days out)
    val days = remember(tomorrowDate) {
        CustomerBookingRules.scheduledDays(now)
    }
    var selectedModeNow by remember { mutableStateOf(isPickupNow) }
    var selectedDate by remember {
        mutableStateOf(
            initialDateTime.toLocalDate().takeIf { !it.isBefore(tomorrowDate) } ?: tomorrowDate
        )
    }
    val isSameDayAsEarliest = selectedDate == earliest.toLocalDate()
    val availableHours = remember(isSameDayAsEarliest, earliest.hour) {
        (0..23).filter { !isSameDayAsEarliest || it >= earliest.hour }
    }
    var selectedHour by remember {
        mutableIntStateOf(initialDateTime.hour.coerceIn(availableHours.first(), availableHours.last()))
    }
    LaunchedEffect(availableHours) {
        if (selectedHour !in availableHours) {
            selectedHour = availableHours.first()
        }
    }
    val availableMinutes = remember(isSameDayAsEarliest, selectedHour, earliest.hour, earliest.minute) {
        (0..59).filter { !isSameDayAsEarliest || selectedHour > earliest.hour || it >= earliest.minute }
    }
    var selectedMinute by remember {
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
    val displayScheduledLabel = CustomerBookingRules.formatSchedulePill(false, candidateFormatted, now)

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

            // "Now" Option Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedModeNow = true
                        onSelectNow()
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_timer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.schedule_now),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selectedModeNow) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (selectedModeNow) {
                    Surface(
                        shape = CircleShape,
                        color = OrangeAccent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Scheduled Date/Time Option Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedModeNow = false }
                    .padding(vertical = 8.dp),
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
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = displayScheduledLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (!selectedModeNow) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!selectedModeNow) {
                    Surface(
                        shape = CircleShape,
                        color = OrangeAccent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3-Column Wheel Picker (Day | Hour | Minute) matching Image 4
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
                    // Column 1: Day (Tomorrow -> future dates)
                    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH) }
                    WheelColumn(
                        items = days,
                        selectedItem = selectedDate,
                        label = { d ->
                            when (d) {
                                tomorrowDate -> "Tomorrow"
                                else -> d.format(dayFormatter)
                            }
                        },
                        onSelect = {
                            selectedModeNow = false
                            selectedDate = it
                        },
                        modifier = Modifier.weight(1.3f)
                    )

                    // Column 2: Hour (12-hour AM/PM display)
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
                        onSelect = {
                            selectedModeNow = false
                            selectedHour = it
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Column 3: Minute
                    WheelColumn(
                        items = availableMinutes,
                        selectedItem = selectedMinute,
                        label = { m -> m.toString().padStart(2, '0') },
                        onSelect = {
                            selectedModeNow = false
                            selectedMinute = it
                        },
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }

            Button(
                onClick = {
                    if (selectedModeNow) onSelectNow() else onSchedule(candidateFormatted)
                },
                enabled = selectedModeNow || MobileBookingRules.departure(candidateFormatted, now) != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeAccent,
                    contentColor = Color.White
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
