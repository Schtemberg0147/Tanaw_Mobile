package com.tanaw.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tanaw.app.ui.screens.SpecialHandling
import com.tanaw.app.ui.screens.Vehicle

class BookingViewModel : ViewModel() {

    // ── Set from HomeScreen ───────────────────────────────────────────────────
    var selectedVehicle  by mutableStateOf<Vehicle?>(null)
    var distanceKm       by mutableStateOf(0)
    var pickupName       by mutableStateOf("")
    var destinationName  by mutableStateOf("")

    // ── Set from AddDetailsScreen ─────────────────────────────────────────────
    var contactNumber    by mutableStateOf("")
    var weightKg         by mutableStateOf("")
    var handling         by mutableStateOf(SpecialHandling.NONE)
    var notes            by mutableStateOf("")
    var totalFee         by mutableStateOf(0)

    // ── Set after booking confirmed ───────────────────────────────────────────
    var bookingReference by mutableStateOf("")

    fun reset() {
        selectedVehicle = null
        distanceKm      = 0
        pickupName      = ""
        destinationName = ""
        contactNumber   = ""
        weightKg        = ""
        handling        = SpecialHandling.NONE
        notes           = ""
        totalFee        = 0
        bookingReference = ""
    }
}