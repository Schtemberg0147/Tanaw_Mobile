package com.tanaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanaw.app.data.model.LocationItem
import com.tanaw.app.data.repository.UserRole
import com.tanaw.app.feature.auth.CreateAccountScreen
import com.tanaw.app.feature.auth.CreateAccountViewModel
import com.tanaw.app.feature.auth.ForgotPasswordScreen
import com.tanaw.app.feature.auth.LoginScreen
import com.tanaw.app.feature.auth.LoginViewModel
import com.tanaw.app.feature.auth.VerifyEmailScreen
import com.tanaw.app.feature.auth.VerifyEmailViewModel
import com.tanaw.app.feature.auth.VerifyPhoneScreen
import com.tanaw.app.feature.auth.VerifyPhoneViewModel
import com.tanaw.app.feature.booking.AddDetailsScreen
import com.tanaw.app.feature.booking.BookingSuccessScreen
import com.tanaw.app.feature.booking.BookingSummaryScreen
import com.tanaw.app.feature.booking.BookingViewModel
import com.tanaw.app.feature.booking.DestinationScreen
import com.tanaw.app.feature.booking.HomeScreen
import com.tanaw.app.feature.booking.HomeViewModel
import com.tanaw.app.feature.driver.DriverHomeScreen
import com.tanaw.app.feature.profile.ProfileScreen
import com.tanaw.app.feature.profile.ProfileViewModel
import com.tanaw.app.feature.booking.PickupLocationScreen
import com.tanaw.app.feature.history.HistoryDetailScreen
import com.tanaw.app.feature.history.HistoryScreen
import com.tanaw.app.feature.history.PODScreen
import com.tanaw.app.feature.profile.ProfileScreen
import com.tanaw.app.feature.tracking.ActiveBookingsScreen
import com.tanaw.app.feature.tracking.TrackDetailScreen
import com.tanaw.app.ui.components.BookingStatus
import com.tanaw.app.ui.components.TanawBottomNavBar
import com.tanaw.app.ui.theme.TanawTheme
import dagger.hilt.android.AndroidEntryPoint

// ─── Routes ───────────────────────────────────────────────────────────────────
object Routes {
    const val LOGIN           = "login"
    const val FORGOT_PASSWORD = "forgot_password"
    const val CREATE_ACCOUNT  = "create_account"
    const val VERIFY_EMAIL    = "verify_email"
    const val VERIFY_PHONE    = "verify_phone"
    const val CHECK_EMAIL     = "check_email"
    const val HOME            = "home"
    const val DRIVER_HOME     = "driver_home"
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
            val createAccountViewModel: CreateAccountViewModel = hiltViewModel()
            val uiState by createAccountViewModel.uiState.collectAsState()

            LaunchedEffect(uiState.isSignUpSuccessful) {
                if (uiState.isSignUpSuccessful) {
                    val email = uiState.registeredEmail
                    val phone = uiState.registeredPhone
                    navController.navigate("${Routes.VERIFY_EMAIL}?email=$email&phone=$phone")
                    createAccountViewModel.consumeSignUpSuccess()
                }
            }

            CreateAccountScreen(
                onVerifyAndContinue = { firstName, lastName, contact, email, password ->
                    createAccountViewModel.signUp(firstName, lastName, contact, email, password)
                },
                onSignIn = {
                    navController.popBackStack()
                },
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage
            )
        }

        // ── Verify Email (OTP) ─────────────────────────────────────────────
        composable(
            route = "${Routes.VERIFY_EMAIL}?email={email}&phone={phone}",
            arguments = listOf(
                navArgument("email") { defaultValue = "maria.santos@gmail.com" },
                navArgument("phone") { defaultValue = "+63 912 345 6789" }
            )
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: "maria.santos@gmail.com"
            val phone = backStackEntry.arguments?.getString("phone") ?: "+63 912 345 6789"

            val verifyEmailViewModel: VerifyEmailViewModel = hiltViewModel()
            val uiState by verifyEmailViewModel.uiState.collectAsState()

            LaunchedEffect(uiState.isVerified) {
                if (uiState.isVerified) {
                    navController.navigate("${Routes.VERIFY_PHONE}?email=$email&phone=$phone")
                    verifyEmailViewModel.consumeVerificationSuccess()
                }
            }

            VerifyEmailScreen(
                email = email,
                onVerify = { otp ->
                    verifyEmailViewModel.verifyOtp(email, otp)
                },
                onResend = {
                    verifyEmailViewModel.resendOtp(email)
                },
                onBack = { navController.popBackStack() },
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                resendSuccessMessage = uiState.resendSuccessMessage
            )
        }

        // ── Verify Phone (OTP) ─────────────────────────────────────────────
        composable(
            route = "${Routes.VERIFY_PHONE}?email={email}&phone={phone}",
            arguments = listOf(
                navArgument("email") { defaultValue = "maria.santos@gmail.com" },
                navArgument("phone") { defaultValue = "+63 912 345 6789" }
            )
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: "+63 912 345 6789"

            val verifyPhoneViewModel: VerifyPhoneViewModel = hiltViewModel()
            val uiState by verifyPhoneViewModel.uiState.collectAsState()

            LaunchedEffect(uiState.isVerified) {
                if (uiState.isVerified) {
                    navController.getBackStackEntry(Routes.LOGIN)
                        .savedStateHandle
                        .set("success_message", "Account successfully created! Please sign in to continue.")

                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                    verifyPhoneViewModel.consumeVerificationSuccess()
                }
            }

            VerifyPhoneScreen(
                phoneNumber = phone,
                onVerify = { otp ->
                    verifyPhoneViewModel.verifyOtp(phone, otp)
                },
                onResend = {
                    verifyPhoneViewModel.resendOtp(phone)
                },
                onBack = { navController.popBackStack() },
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                resendSuccessMessage = uiState.resendSuccessMessage
            )
        }

        // ── Home ───────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            val homeUiState by homeViewModel.uiState.collectAsState()

            HomeScreen(
                uiState            = homeUiState,
                onRetry            = { homeViewModel.loadUserProfile() },
                pickupLocation     = selectedPickup,
                destination        = selectedDestination,
                onPickupClick      = { navController.navigate(Routes.PICKUP_LOCATION) },
                onDestinationClick = { navController.navigate(Routes.DESTINATION) },
                onContinue         = { vehicle, distance ->
                    bookingVM.selectedVehicle  = vehicle
                    bookingVM.distanceKm       = distance
                    bookingVM.pickupName       = selectedPickup?.name ?: ""
                    bookingVM.destinationName  = selectedDestination?.name ?: ""
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
            TrackDetailScreen(bookingId = bookingId, onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onViewPOD = { bookingId -> navController.navigate("pod/$bookingId") },
                onRebook = { bookingId ->
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

        composable(Routes.DRIVER_HOME) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            DriverHomeScreen(
                onLogout = {
                    profileViewModel.logout {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.PROFILE) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val profileUiState by profileViewModel.uiState.collectAsState()

            ProfileScreen(
                uiState             = profileUiState,
                onRetry             = { profileViewModel.loadProfile() },
                onSendPhoneOtp      = { onResult ->
                    profileViewModel.sendPhoneVerificationOtp(onResult)
                },
                onSendEmailOtp      = { email, onResult ->
                    profileViewModel.sendEmailVerificationOtp(email, onResult)
                },
                onVerifyOtp         = { method, destination, token, onResult ->
                    profileViewModel.verifyOtp(method, destination, token, onResult)
                },
                onChangePassword    = { newPassword, onResult ->
                    profileViewModel.changePassword(newPassword, onResult)
                },
                onClearMessages     = { profileViewModel.clearMessages() },
                onLogout            = {
                    profileViewModel.logout {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onEditField         = { fieldKey, newValue ->
                    profileViewModel.updateProfileField(fieldKey, newValue)
                },
                onMenuClick         = { menuId ->
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
