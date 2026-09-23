package com.tanaw.app

import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.tanaw.app.feature.auth.LoginViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import com.tanaw.app.ui.components.TanawBottomNavBar
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import com.tanaw.app.ui.screens.ProfileScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import com.tanaw.app.ui.screens.LocationItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanaw.app.ui.screens.ActiveBookingsScreen
import com.tanaw.app.ui.screens.AddDetailsScreen
import com.tanaw.app.ui.screens.BookingStatus
import com.tanaw.app.ui.screens.BookingSuccessScreen
import com.tanaw.app.ui.screens.BookingSummaryScreen
import com.tanaw.app.feature.auth.LoginScreen
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
import androidx.lifecycle.viewmodel.compose.viewModel
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


// ─── MainActivity ─────────────────────────────────────────────────────────────
@AndroidEntryPoint
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

            val loginViewModel: LoginViewModel = hiltViewModel()
            val uiState by loginViewModel.uiState.collectAsState()

            // Navigate once login succeeds, then tell the ViewModel we've
            // acted on it so it doesn't fire again if this screen is re-entered.
            LaunchedEffect(uiState.isLoginSuccessful) {
                if (uiState.isLoginSuccessful) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                    loginViewModel.consumeLoginSuccess()
                }
            }

            LoginScreen(
                onLoginClick = { email, password ->
                    loginViewModel.login(email, password)
                },
                onForgotPassword = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                },
                onCreateAccount = {
                    navController.navigate(Routes.CREATE_ACCOUNT)
                },
                successMessage = successMessage,
                isLoading      = uiState.isLoading,
                errorMessage   = uiState.errorMessage,
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
            ProfileScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onMenuClick = { menuId ->
                    // TODO: handle menu navigation later
                },
                onEditField = { fieldKey, currentValue ->
                    // TODO: handle field editing later
                }
            )
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
