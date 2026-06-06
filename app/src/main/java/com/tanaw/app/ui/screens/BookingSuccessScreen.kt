package com.tanaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.ui.theme.*

// ─── Booking Timeline Step ────────────────────────────────────────────────────
enum class TimelineStatus { DONE, PENDING, WAITING }

data class TimelineStep(
    val title    : String,
    val subtitle : String,
    val time     : String,
    val status   : TimelineStatus
)

// ─── Booking Success Screen ───────────────────────────────────────────────────
/**
 * @param bookingReference  e.g. "SHP-NE-8063"
 * @param pickupName        Pickup display name
 * @param destinationName   Destination display name
 * @param vehicleName       Selected vehicle name
 * @param totalFee          Final total fee
 * @param onTrackShipment   Navigate to Track screen
 * @param onBookAnother     Navigate back to Home/Book screen
 */
@Composable
fun BookingSuccessScreen(
    bookingReference : String = "SHP-NE-8063",
    pickupName       : String = "Cabanatuan City",
    destinationName  : String = "Gapan City",
    vehicleName      : String = "10-Wheeler Wing Van",
    totalFee         : Int    = 3_730,
    onTrackShipment  : () -> Unit,
    onBookAnother    : () -> Unit,
) {
    val timelineSteps = listOf(
        TimelineStep("Booking Submitted",    "",                          "JUST NOW", TimelineStatus.DONE),
        TimelineStep("Pending Admin Review", "Usually within 30 minutes", "",         TimelineStatus.PENDING),
        TimelineStep("Driver Assigned",      "You'll be notified",        "",         TimelineStatus.WAITING),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(48.dp))

        // ── Success icon ─────────────────────────────────────────────────────
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF2ECC71), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Title ────────────────────────────────────────────────────────────
        Text(
            text       = "Booking Submitted!",
            fontSize   = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = NavyPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text      = "Your booking is pending admin review. You'll\nreceive a notification once a driver is assigned.",
            fontSize  = 13.sp,
            color     = HintGray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier  = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Booking reference card ───────────────────────────────────────────
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            // Green accent top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF2ECC71))
            )

            Column(
                modifier            = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("BOOKING REFERENCE", fontSize = 10.sp, color = HintGray, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(bookingReference, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = NavyPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Save this number to track your shipment", fontSize = 12.sp, color = HintGray)

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Route
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(NavyPrimary, RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(pickupName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        Text("Pickup", fontSize = 11.sp, color = HintGray)
                    }
                }
                Row {
                    Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(2.dp).height(20.dp).background(BorderGray))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(OrangeAccent, RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(destinationName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        Text("Drop-off", fontSize = 11.sp, color = HintGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Vehicle + Schedule
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⊡ VEHICLE", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(vehicleName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📅 SCHEDULE", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Oct 24, 2023 · 08:00 AM", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL VALUE", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                    Text("₱${"%,d".format(totalFee)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Timeline ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            timelineSteps.forEachIndexed { index, step ->
                TimelineRow(step = step, isLast = index == timelineSteps.lastIndex)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Buttons ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick  = onTrackShipment,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("TRACK SHIPMENT", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
            }

            OutlinedButton(
                onClick  = onBookAnother,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary)
            ) {
                Text("BOOK ANOTHER DELIVERY", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

// ─── Timeline Row ─────────────────────────────────────────────────────────────
@Composable
private fun TimelineRow(step: TimelineStep, isLast: Boolean) {
    val dotColor = when (step.status) {
        TimelineStatus.DONE    -> Color(0xFF2ECC71)
        TimelineStatus.PENDING -> OrangeAccent
        TimelineStatus.WAITING -> BorderGray
    }
    val textColor = if (step.status == TimelineStatus.WAITING) HintGray else TextDark

    Row(modifier = Modifier.fillMaxWidth()) {
        // Dot + connector line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(dotColor, RoundedCornerShape(50))
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(BorderGray)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Text
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                if (step.subtitle.isNotEmpty()) {
                    Text(step.subtitle, fontSize = 12.sp, color = HintGray)
                }
            }
            if (step.time.isNotEmpty()) {
                Text(step.time, fontSize = 12.sp, color = HintGray)
            }
        }
    }
}