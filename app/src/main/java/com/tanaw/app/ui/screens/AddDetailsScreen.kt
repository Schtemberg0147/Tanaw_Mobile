package com.tanaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.ui.theme.*


// ─── Special Handling Options ─────────────────────────────────────────────────
enum class SpecialHandling(val label: String, val extraFee: Int) {
    NONE("None", 0),
    FRAGILE("Fragile", 500),
    KEEP_DRY("Keep Dry", 300),
    TEMP_SENSITIVE("Temperature Sensitive", 800),
    PERISHABLE("Perishable", 600),
}

// ─── Add More Details Screen ──────────────────────────────────────────────────
/**
 * @param vehicle           The vehicle selected in HomeScreen
 * @param distanceKm        Computed distance between pickup and destination
 * @param pickupName        Display name of pickup location
 * @param destinationName   Display name of destination
 * @param onBack            Navigate back to HomeScreen
 * @param onConfirm         Called with all details when Confirm is tapped
 */
@Composable
fun AddDetailsScreen(
    vehicle         : Vehicle,
    distanceKm      : Int,
    pickupName      : String = "Cabanatuan City Hub Alpha",
    destinationName : String = "Gapan City",
    onBack          : () -> Unit,
    onConfirm       : (
        contactNumber   : String,
        weightKg        : String,
        handling        : SpecialHandling,
        notes           : String,
        totalFee        : Int
    ) -> Unit,
) {
    var contactNumber    by remember { mutableStateOf("") }
    var weightKg         by remember { mutableStateOf("") }
    var selectedHandling by remember { mutableStateOf(SpecialHandling.NONE) }
    var notes            by remember { mutableStateOf("") }

    val deliveryFee  = vehicle.computePrice(distanceKm)
    val handlingFee  = selectedHandling.extraFee
    val totalFee     = deliveryFee + handlingFee

    val canConfirm = contactNumber.length == 10 && weightKg.isNotBlank()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = "Add More Details",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = NavyPrimary
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress   = { 0.75f },
                modifier   = Modifier.fillMaxWidth().height(4.dp),
                color      = NavyPrimary,
                trackColor = BorderGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Route summary card ───────────────────────────────────────────
            RouteCard(
                pickupName      = pickupName,
                destinationName = destinationName,
                distanceKm      = distanceKm,
                onEdit          = onBack
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Order Contact ────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Cargo Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                Spacer(modifier = Modifier.height(14.dp))
                TanawInputLabel(text = "ORDER CONTACT NUMBER")
                Spacer(modifier = Modifier.height(6.dp))
                TanawTextField(
                    value         = contactNumber,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(11)
                        contactNumber = digits
                    },
                    placeholder   = "+63 912 345 6789",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = VisualTransformation { text ->
                        val digits    = text.text.take(11)
                        val formatted = buildString {
                            digits.forEachIndexed { i, c ->
                                if (i == 0) append("+63 ")
                                else if (i == 3 || i == 6) append(' ')
                                append(c)
                            }
                        }
                        TransformedText(
                            AnnotatedString(formatted),
                            object : OffsetMapping {
                                override fun originalToTransformed(offset: Int): Int =
                                    when {
                                        offset == 0  -> 0
                                        offset <= 4  -> offset + 4
                                        offset <= 7  -> offset + 5
                                        else         -> offset + 6
                                    }
                                override fun transformedToOriginal(offset: Int): Int =
                                    when {
                                        offset <= 4  -> 0
                                        offset <= 8  -> offset - 4
                                        offset <= 12 -> offset - 5
                                        else         -> offset - 6
                                    }
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Cargo details section ────────────────────────────────────
                Text("Cargo Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                Spacer(modifier = Modifier.height(14.dp))

                // Est. weight
                TanawInputLabel(text = "EST. WEIGHT")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value         = weightKg,
                    onValueChange = { weightKg = it.filter { c -> c.isDigit() } },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    placeholder   = { Text("6000", color = HintGray) },
                    suffix        = { Text("kg", color = HintGray, fontWeight = FontWeight.Medium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = NavyPrimary,
                        unfocusedBorderColor = BorderGray,
                        focusedContainerColor   = InputGray,
                        unfocusedContainerColor = InputGray,
                        cursorColor          = NavyPrimary,
                        focusedTextColor     = TextDark,
                        unfocusedTextColor   = TextDark,
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Special handling chips
                TanawInputLabel(text = "SPECIAL HANDLING")
                Spacer(modifier = Modifier.height(10.dp))
                HandlingChips(
                    selected = selectedHandling,
                    onSelect = { selectedHandling = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Notes to driver
                TanawInputLabel(text = "NOTES TO DRIVER")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    modifier      = Modifier.fillMaxWidth().height(120.dp),
                    shape         = RoundedCornerShape(12.dp),
                    placeholder   = { Text("e.g., Gate code 1234, call upon arrival", color = HintGray, fontSize = 13.sp) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = NavyPrimary,
                        unfocusedBorderColor    = BorderGray,
                        focusedContainerColor   = InputGray,
                        unfocusedContainerColor = InputGray,
                        cursorColor             = NavyPrimary,
                        focusedTextColor        = TextDark,
                        unfocusedTextColor      = TextDark,
                    )
                )
            }
        }

        // ── Sticky bottom fee bar ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TOTAL FEE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HintGray, letterSpacing = 0.5.sp)
                    Text("₱${"%,d".format(totalFee)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    Text("DELIVERY FEE    ₱${"%,d".format(deliveryFee)}", fontSize = 11.sp, color = HintGray)
                    if (handlingFee > 0)
                        Text("SPECIAL HANDLING    ₱${"%,d".format(handlingFee)}", fontSize = 11.sp, color = HintGray)
                }
                Button(
                    onClick  = { onConfirm(contactNumber, weightKg, selectedHandling, notes, totalFee) },
                    enabled  = canConfirm,
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = NavyPrimary,
                        disabledContainerColor = NavyPrimary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Confirm →", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

// ─── Route Card (reused in Summary too) ──────────────────────────────────────
@Composable
fun RouteCard(
    pickupName      : String,
    destinationName : String,
    distanceKm      : Int,
    onEdit          : (() -> Unit)? = null,
    modifier        : Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(NavyPrimary, RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("PICK-UP", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                            Text(pickupName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        }
                    }
                    Row {
                        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.width(2.dp).height(16.dp).background(BorderGray))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(OrangeAccent, RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("DESTINATION", fontSize = 10.sp, color = HintGray, letterSpacing = 0.5.sp)
                            Text(destinationName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        }
                    }
                }
                if (onEdit != null) {
                    Text("EDIT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OrangeAccent,
                        modifier = Modifier.clickable { onEdit() })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderGray)
            Spacer(modifier = Modifier.height(12.dp))

            // Distance · ETA · Road type
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⇅ $distanceKm km", fontSize = 12.sp, color = HintGray)
                Text("⏱ Est. 1h 15min", fontSize = 12.sp, color = HintGray)
                Text("⊙ Toll Road", fontSize = 12.sp, color = HintGray)
            }
        }
    }
}

// ─── Handling Chips ───────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandlingChips(
    selected : SpecialHandling,
    onSelect : (SpecialHandling) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpecialHandling.entries.take(3).forEach { option ->
                ChipItem(option, selected, onSelect)
            }
        }
        // Row 2
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpecialHandling.entries.drop(3).forEach { option ->
                ChipItem(option, selected, onSelect)
            }
        }
    }
}

@Composable
private fun ChipItem(
    option   : SpecialHandling,
    selected : SpecialHandling,
    onSelect : (SpecialHandling) -> Unit
) {
    val isSelected = selected == option
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) NavyPrimary else BorderGray,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isSelected) NavyPrimary.copy(alpha = 0.05f) else Color.White)
            .clickable { onSelect(option) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text       = option.label,
            fontSize   = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = if (isSelected) NavyPrimary else TextDark
        )
    }
}
