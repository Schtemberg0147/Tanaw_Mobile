package com.tanaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import com.tanaw.app.ui.components.TanawBottomNavBar
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import com.tanaw.app.ui.screens.LocationItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanaw.app.ui.screens.ActiveBookingsScreen
import com.tanaw.app.ui.screens.AddDetailsScreen
import com.tanaw.app.ui.screens.BookingStatus
import com.tanaw.app.ui.screens.BookingSuccessScreen
import com.tanaw.app.ui.screens.BookingSummaryScreen
import com.tanaw.app.ui.screens.LoginScreen
import com.tanaw.app.ui.screens.ForgotPasswordScreen
import com.tanaw.app.ui.screens.CreateAccountScreen
import com.tanaw.app.ui.screens.DestinationScreen
import com.tanaw.app.ui.screens.HistoryDetailScreen
import com.tanaw.app.ui.screens.HistoryScreen
import com.tanaw.app.ui.screens.HomeScreen
import com.tanaw.app.ui.screens.PODScreen
import com.tanaw.app.ui.screens.PickupLocationScreen
import com.tanaw.app.ui.screens.TrackDetailScreen
import com.tanaw.app.ui.screens.VerifyEmailScreen
import com.tanaw.app.ui.screens.VerifyPhoneScreen
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanaw.app.ui.screens.MockData
import com.tanaw.app.viewmodel.BookingViewModel

// ─── Routes ───────────────────────────────────────────────────────────────────
object Routes {
    const val LOGIN           = "login"
    const val FORGOT_PASSWORD = "forgot_password"
    const val CREATE_ACCOUNT  = "create_account"
    const val VERIFY_EMAIL    = "verify_email"
    const val VERIFY_PHONE    = "verify_phone"
    const val CHECK_EMAIL     = "check_email"
    const val HOME            = "home"
    const val TRACK           = "track"
    const val HISTORY         = "history"
    const val PROFILE         = "profile"
    const val PICKUP_LOCATION = "pickup_location"
    const val DESTINATION     = "destination"
    const val ADD_DETAILS       = "add_details"
    const val BOOKING_SUMMARY   = "booking_summary"
    const val BOOKING_SUCCESS   = "booking_success"
}

// ─── Hardcoded mock credentials ───────────────────────────────────────────────
private const val MOCK_EMAIL    = "test@tanaw.com"
private const val MOCK_PASSWORD = "password123"

// ─── MainActivity ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TanawTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val bottomNavRoutes = listOf("home", "track", "history", "profile")

                Scaffold(
                    bottomBar = {
                        if (currentRoute in bottomNavRoutes) {
                            TanawBottomNavBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        TanawNavHost(navController = navController)
                    }
                }
            }
        }
    }
}

// ─── Navigation Host ──────────────────────────────────────────────────────────
@Composable
fun TanawNavHost(
    navController: NavHostController = rememberNavController()
) {
    var selectedPickup      by remember { mutableStateOf<LocationItem?>(null) }
    var selectedDestination by remember { mutableStateOf<LocationItem?>(null) }
    val bookingVM: BookingViewModel = viewModel()

    NavHost(
        navController    = navController,
        startDestination = Routes.LOGIN
    ) {

        // ── Login ──────────────────────────────────────────────────────────
        composable(Routes.LOGIN) { backStackEntry ->

            val successMessage = backStackEntry
                .savedStateHandle
                .get<String>("success_message")

            // Login UI state — lives here so it resets on back-navigation
            var isLoading    by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val scope        = rememberCoroutineScope()

            LoginScreen(
                onLoginClick = { email, password ->

                    // ── Validation ─────────────────────────────────────────
                    when {
                        email.isBlank() && password.isBlank() -> {
                            errorMessage = "Please enter your email and password."
                            return@LoginScreen
                        }
                        email.isBlank() -> {
                            errorMessage = "Email address is required."
                            return@LoginScreen
                        }
                        password.isBlank() -> {
                            errorMessage = "Password is required."
                            return@LoginScreen
                        }
                    }

                    // ── Simulate network call ──────────────────────────────
                    scope.launch {
                        isLoading    = true
                        errorMessage = null
                        delay(1_500L)           // fake 1.5 s loading state
                        isLoading = false

                        if (email.trim() == MOCK_EMAIL && password == MOCK_PASSWORD) {
                            // Success — go to Home, clear back-stack
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        } else {
                            // Wrong credentials
                            errorMessage = "Incorrect email or password. Please try again."
                        }
                    }
                },
                onForgotPassword = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                },
                onCreateAccount = {
                    navController.navigate(Routes.CREATE_ACCOUNT)
                },
                successMessage = successMessage,
                isLoading      = isLoading,
                errorMessage   = errorMessage,
            )
        }

        // ── Forgot Password ────────────────────────────────────────────────
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onSendResetLink = { email ->
                    navController.navigate(Routes.CHECK_EMAIL)
                },
                onBackToSignIn = {
                    navController.popBackStack()
                }
            )
        }

        // ── Create Account ─────────────────────────────────────────────────
        composable(Routes.CREATE_ACCOUNT) {
            CreateAccountScreen(
                onVerifyAndContinue = { firstName, lastName, contact, email, password ->
                    navController.navigate(Routes.VERIFY_EMAIL)
                },
                onSignIn = {
                    navController.popBackStack()
                }
            )
        }

        // ── Verify Email (OTP) ─────────────────────────────────────────────
        composable(Routes.VERIFY_EMAIL) {
            VerifyEmailScreen(
                email    = "maria.santos@gmail.com",
                onVerify = { otp ->
                    navController.navigate(Routes.VERIFY_PHONE)
                },
                onResend = {},
                onBack   = { navController.popBackStack() }
            )
        }

        // ── Verify Phone (OTP) ─────────────────────────────────────────────
        composable(Routes.VERIFY_PHONE) {
            VerifyPhoneScreen(
                phoneNumber = "+63 912 345 6789",
                onVerify    = { otp ->
                    // Set the message on the LOGIN destination BEFORE navigating there
                    navController.getBackStackEntry(Routes.LOGIN)
                        .savedStateHandle
                        .set("success_message", "Account successfully created! Please sign in to continue.")

                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onResend = {},
                onBack   = { navController.popBackStack() }
            )
        }

        // ── Home ───────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                userName           = "Maria Santos",
                userCity           = "Cabanatuan City",
                pickupLocation     = selectedPickup,
                destination        = selectedDestination,
                onPickupClick      = { navController.navigate(Routes.PICKUP_LOCATION) },
                onDestinationClick = { navController.navigate(Routes.DESTINATION) },
                onContinue = { vehicle, distance ->
                    // Set ALL values first
                    bookingVM.selectedVehicle  = vehicle
                    bookingVM.distanceKm       = distance
                    bookingVM.pickupName       = selectedPickup?.name ?: ""
                    bookingVM.destinationName  = selectedDestination?.name ?: ""
                    // Navigate AFTER everything is saved
                    navController.navigate(Routes.ADD_DETAILS)
                }
            )
        }

        composable(Routes.TRACK) {
            ActiveBookingsScreen(
                onTrackClick = { bookingId ->
                    navController.navigate("track_detail/$bookingId")
                }
            )
        }

        composable("track_detail/{bookingId}") { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            TrackDetailScreen(bookingId = bookingId)
        }

        // inside TanawNavHost (replace or add these composable entries)

        // inside TanawNavHost

        // inside TanawNavHost

        composable(Routes.HISTORY) {
            HistoryScreen(
                onViewPOD = { bookingId -> navController.navigate("pod/$bookingId") },
                onRebook = { bookingId ->
                    // navigate to booking flow or prefill booking screen
                },
                onViewDetails = { bookingId, status ->
                    when (status) {
                        BookingStatus.DELIVERED, BookingStatus.CANCELLED -> {
                            navController.navigate("history_detail/$bookingId")
                        }
                        else -> {
                            navController.navigate("track_detail/$bookingId")
                        }
                    }
                }
            )
        }

        composable("pod/{bookingId}") { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            PODScreen(bookingId = bookingId, onBack = { navController.popBackStack() })
        }

        composable("history_detail/{bookingId}") { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            HistoryDetailScreen(bookingId = bookingId, onBack = { navController.popBackStack() })
        }

        composable("track_detail/{bookingId}") { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            TrackDetailScreen(bookingId = bookingId, onBack = { navController.popBackStack() })
        }


        composable(Routes.PROFILE) {
            // ProfileScreen() — coming soon
        }

        // ── Pickup Location Picker ─────────────────────────────────────────
        composable(Routes.PICKUP_LOCATION) {
            PickupLocationScreen(
                onConfirm = { location ->
                    selectedPickup = location
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Destination Picker ─────────────────────────────────────────────
        composable(Routes.DESTINATION) {
            DestinationScreen(
                origin    = selectedPickup,
                onConfirm = { location ->
                    selectedDestination = location
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_DETAILS) {
            val vehicle = bookingVM.selectedVehicle ?: return@composable
            AddDetailsScreen(
                vehicle         = vehicle,
                distanceKm      = bookingVM.distanceKm,
                pickupName      = bookingVM.pickupName,
                destinationName = bookingVM.destinationName,
                onBack          = { navController.popBackStack() },
                onConfirm       = { contact, weight, handling, notes, total ->
                    bookingVM.contactNumber = contact
                    bookingVM.weightKg      = weight
                    bookingVM.handling      = handling
                    bookingVM.notes         = notes
                    bookingVM.totalFee      = total
                    navController.navigate(Routes.BOOKING_SUMMARY)
                }
            )
        }

        composable(Routes.BOOKING_SUMMARY) {
            val vehicle = bookingVM.selectedVehicle ?: return@composable
            BookingSummaryScreen(
                vehicle         = vehicle,
                distanceKm      = bookingVM.distanceKm,
                pickupName      = bookingVM.pickupName,
                destinationName = bookingVM.destinationName,
                weightKg        = bookingVM.weightKg,
                handling        = bookingVM.handling,
                notes           = bookingVM.notes,
                totalFee        = bookingVM.totalFee,
                onBack           = { navController.popBackStack() },
                onConfirmBooking = {
                    bookingVM.bookingReference = "SHP-NE-8063"
                    navController.navigate(Routes.BOOKING_SUCCESS)
                }
            )
        }

        composable(Routes.BOOKING_SUCCESS) {
            BookingSuccessScreen(
                bookingReference = bookingVM.bookingReference,
                pickupName       = bookingVM.pickupName,
                destinationName  = bookingVM.destinationName,
                vehicleName      = bookingVM.selectedVehicle?.name ?: "",
                totalFee         = bookingVM.totalFee,
                onTrackShipment  = { navController.navigate(Routes.HOME) },
                onBookAnother    = {
                    bookingVM.reset()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
