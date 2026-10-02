package com.tanaw.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.data.model.Vehicle
import com.tanaw.app.ui.components.TanawInputLabel
import com.tanaw.app.ui.components.TanawTextField

enum class SpecialHandling(val label: String, val extraFee: Int) {
    NONE("None", 0),
    FRAGILE("Fragile", 500),
    KEEP_DRY("Keep Dry", 300),
    TEMP_SENSITIVE("Temperature Sensitive", 800),
    PERISHABLE("Perishable", 600),
}

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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = "Add More Details",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress   = { 0.75f },
                modifier   = Modifier.fillMaxWidth().height(4.dp),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            RouteCard(
                pickupName      = pickupName,
                destinationName = destinationName,
                distanceKm      = distanceKm,
                onEdit          = onBack
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text       = "Cargo Details",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(14.dp))
                TanawInputLabel(text = "ORDER CONTACT NUMBER")
                Spacer(modifier = Modifier.height(6.dp))
                TanawTextField(
                    value = contactNumber,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(11)
                        contactNumber = digits
                    },
                    placeholder = "+63 912 345 6789",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = VisualTransformation { text ->
                        val digits = text.text.take(11)
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
                                        offset == 0 -> 0
                                        offset <= 4 -> offset + 4
                                        offset <= 7 -> offset + 5
                                        else -> offset + 6
                                    }

                                override fun transformedToOriginal(offset: Int): Int =
                                    when {
                                        offset <= 4 -> 0
                                        offset <= 8 -> offset - 4
                                        offset <= 12 -> offset - 5
                                        else -> offset - 6
                                    }
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                TanawInputLabel(text = "EST. WEIGHT")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value         = weightKg,
                    onValueChange = { weightKg = it.filter { c -> c.isDigit() } },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    placeholder   = { Text("6000", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    suffix        = { Text("kg", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        cursorColor          = MaterialTheme.colorScheme.primary,
                        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                TanawInputLabel(text = "SPECIAL HANDLING")
                Spacer(modifier = Modifier.height(10.dp))
                HandlingChips(
                    selected = selectedHandling,
                    onSelect = { selectedHandling = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                TanawInputLabel(text = "NOTES TO DRIVER")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    modifier      = Modifier.fillMaxWidth().height(120.dp),
                    shape         = RoundedCornerShape(12.dp),
                    placeholder   = { Text("e.g., Gate code 1234, call upon arrival", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        cursorColor             = MaterialTheme.colorScheme.primary,
                        focusedTextColor        = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text          = "TOTAL FEE",
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Bold,
                        color         = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text       = "₱${"%,d".format(totalFee)}",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text  = "DELIVERY FEE    ₱${"%,d".format(deliveryFee)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (handlingFee > 0) {
                        Text(
                            text  = "SPECIAL HANDLING    ₱${"%,d".format(handlingFee)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick  = { onConfirm(contactNumber, weightKg, selectedHandling, notes, totalFee) },
                    enabled  = canConfirm,
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = MaterialTheme.colorScheme.primary,
                        contentColor           = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        text       = "Confirm →",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

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
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("PICK-UP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                            Text(pickupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Row {
                        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.width(2.dp).height(16.dp).background(MaterialTheme.colorScheme.outline))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("DESTINATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                            Text(destinationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (onEdit != null) {
                    Text(
                        text       = "EDIT",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.secondary,
                        modifier   = Modifier.clickable { onEdit() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⇅ $distanceKm km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("⏱ Est. 1h 15min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("⊙ Toll Road", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HandlingChips(
    selected : SpecialHandling,
    onSelect : (SpecialHandling) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpecialHandling.entries.take(3).forEach { option ->
                ChipItem(option, selected, onSelect)
            }
        }
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
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .clickable { onSelect(option) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text       = option.label,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
