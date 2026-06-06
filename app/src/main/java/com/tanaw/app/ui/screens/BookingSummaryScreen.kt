package com.tanaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
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

// ─── Booking Summary Screen ───────────────────────────────────────────────────
/**
 * @param vehicle           Selected vehicle
 * @param distanceKm        Computed distance
 * @param pickupName        Pickup display name
 * @param destinationName   Destination display name
 * @param weightKg          Cargo weight entered in AddDetails
 * @param handling          Selected special handling
 * @param notes             Notes to driver
 * @param totalFee          Pre-computed total fee
 * @param onBack            Navigate back
 * @param onConfirmBooking  Called when user confirms — navigate to success screen
 * @param isLoading         Show loading on confirm button
 */
@Composable
fun BookingSummaryScreen(
    vehicle         : Vehicle,
    distanceKm      : Int,
    pickupName      : String,
    destinationName : String,
    weightKg        : String,
    handling        : SpecialHandling,
    notes           : String,
    totalFee        : Int,
    onBack          : () -> Unit,
    onConfirmBooking: () -> Unit,
    isLoading       : Boolean = false,
) {
    val deliveryFee = vehicle.computePrice(distanceKm)
    val handlingFee = handling.extraFee

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Booking Summary", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
        }

        // Progress bar — full
        LinearProgressIndicator(
            progress   = { 1f },
            modifier   = Modifier.fillMaxWidth().height(4.dp),
            color      = NavyPrimary,
            trackColor = BorderGray
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Route card ───────────────────────────────────────────────────
            RouteCard(
                pickupName      = pickupName,
                destinationName = destinationName,
                distanceKm      = distanceKm,
                modifier        = Modifier.padding(horizontal = 0.dp)
            )

            // ── Shipment details card ────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Shipment Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VEHICLE", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(vehicle.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CARGO WEIGHT", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$weightKg kg", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        }
                    }

                    if (handling != SpecialHandling.NONE) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("SPECIAL HANDLING", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OrangeAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(handling.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OrangeAccent)
                        }
                    }

                    if (notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("NOTES", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputGray)
                                .padding(12.dp)
                        ) {
                            Text("\"$notes\"", fontSize = 13.sp, color = TextDark)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = HintGray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Oct 24, 2023 · 08:00 AM", fontSize = 13.sp, color = HintGray)
                    }
                }
            }

            // ── Price breakdown card ─────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Price Breakdown", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    Spacer(modifier = Modifier.height(14.dp))

                    PriceRow(
                        label = "Base Rate (${vehicle.ratePerKm}/km × ${distanceKm}km)",
                        amount = "₱${"%,d".format(deliveryFee)}"
                    )

                    if (handlingFee > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        PriceRow(
                            label  = "Special Handling (${handling.label})",
                            amount = "+₱${"%,d".format(handlingFee)}"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderGray)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("₱${"%,d".format(totalFee)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text      = "Price is fixed upon booking confirmation. No hidden charges.",
                        fontSize  = 11.sp,
                        color     = HintGray,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Review notice ────────────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Text(
                    text      = "Your booking will be reviewed by our team before a driver is assigned. You will be notified once confirmed.",
                    fontSize  = 13.sp,
                    color     = HintGray,
                    modifier  = Modifier.padding(16.dp),
                    lineHeight = 20.sp
                )
            }
        }

        // ── Confirm button ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick  = onConfirmBooking,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("CONFIRM BOOKING", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = "By confirming you agree to Tanaw's Terms of Service",
                fontSize = 11.sp,
                color    = HintGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PriceRow(label: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextDark, modifier = Modifier.weight(1f))
        Text(amount, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
    }
}