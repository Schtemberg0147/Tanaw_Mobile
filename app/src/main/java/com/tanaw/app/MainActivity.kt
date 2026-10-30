package com.tanaw.app

import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tanaw.app.feature.workspace.MobileWorkspaceApp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tanaw.app.data.model.LocationItem
import com.tanaw.app.data.model.MockData
import com.tanaw.app.data.repository.UserRole
import com.tanaw.app.feature.auth.CreateAccountScreen
import com.tanaw.app.feature.auth.CreateAccountViewModel
import com.tanaw.app.feature.auth.LoginScreen
import com.tanaw.app.feature.auth.LoginViewModel
import com.tanaw.app.feature.auth.VerifyEmailScreen
import com.tanaw.app.feature.auth.VerifyEmailViewModel
import com.tanaw.app.feature.auth.VerifyPhoneScreen
import com.tanaw.app.feature.auth.VerifyPhoneViewModel
import com.tanaw.app.feature.booking.AddDetailsScreen
import com.tanaw.app.feature.booking.BookingSuccessScreen
import com.tanaw.app.feature.booking.BookingSummaryScreen
import com.tanaw.app.feature.booking.BookingUiState
import com.tanaw.app.feature.booking.BookingViewModel
import com.tanaw.app.feature.booking.DestinationScreen
import com.tanaw.app.feature.booking.HomeScreen
import com.tanaw.app.feature.booking.HomeViewModel
import com.tanaw.app.feature.booking.PickupLocationScreen
import com.tanaw.app.feature.driver.DeliveryCompleteScreen
import com.tanaw.app.feature.driver.DriverHomeScreen
import com.tanaw.app.feature.driver.DriverNavigationScreen
import com.tanaw.app.feature.driver.DriverProfileScreen
import com.tanaw.app.feature.driver.MyTripsScreen
import com.tanaw.app.feature.driver.SubmitPodScreen
import com.tanaw.app.feature.history.HistoryDetailScreen
import com.tanaw.app.feature.history.HistoryScreen
import com.tanaw.app.feature.history.PODScreen
import com.tanaw.app.feature.notifications.NotificationsScreen
import com.tanaw.app.feature.profile.ProfileScreen
import com.tanaw.app.feature.profile.ProfileViewModel
import com.tanaw.app.feature.tracking.ActiveBookingsScreen
import com.tanaw.app.feature.tracking.TrackDetailScreen
import com.tanaw.app.ui.theme.TanawTheme
import dagger.hilt.android.AndroidEntryPoint

// ─── Routes ───────────────────────────────────────────────────────────────────
object Routes {
    const val LOGIN             = "login"
    const val CREATE_ACCOUNT    = "create_account"
    const val VERIFY_EMAIL      = "verify_email"
    const val VERIFY_PHONE      = "verify_phone"
    const val HOME              = "home"
    const val DRIVER_HOME       = "driver_home"
    const val TRACK             = "track"
    const val HISTORY           = "history"
    const val PROFILE           = "profile"
    const val MY_TRIPS          = "my_trips"
    const val DRIVER_PROFILE    = "driver_profile"
    const val DRIVER_NAVIGATION = "driver_navigation"
    const val SUBMIT_POD        = "submit_pod"
    const val DELIVERY_COMPLETE = "delivery_complete"
    const val NOTIFICATIONS     = "notifications"
    const val PICKUP_LOCATION   = "pickup_location"
    const val DESTINATION       = "destination"
    const val ADD_DETAILS       = "add_details"
    const val BOOKING_SUMMARY   = "booking_summary"
    const val BOOKING_SUCCESS   = "booking_success"
}

// ─── MainActivity ─────────────────────────────────────────────────────────────
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TanawTheme { MobileWorkspaceApp(hiltViewModel()) } }
    }
}

@Composable
fun TanawNavHost(
    navController: NavHostController = rememberNavController()
) {
    var selectedPickup      by remember { mutableStateOf<LocationItem?>(null) }
    var selectedDestination by remember { mutableStateOf<LocationItem?>(null) }
    val bookingVM: BookingViewModel = hiltViewModel()

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
                    val targetDestination = if (uiState.userRole == UserRole.DRIVER) {
                        Routes.DRIVER_HOME
                    } else {
                        Routes.HOME
                    }
                    navController.navigate(targetDestination) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                    loginViewModel.consumeLoginSuccess()
                }
            }

            LoginScreen(
                onLoginClick = { email, password ->
                    loginViewModel.login(email, password)
                },
                onCreateAccount = {
                    navController.navigate(Routes.CREATE_ACCOUNT)
                },
                successMessage = successMessage,
                isLoading      = uiState.isLoading,
                errorMessage   = uiState.errorMessage,
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
                onVerifyAndContinue = { firstName, lastName, contact, email, password, _ ->
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
                        .savedStateHandle["success_message"] = "Account successfully created! Please sign in to continue."

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
                onCityDetected     = { city -> homeViewModel.updateDetectedCity(city) },
                pickupLocation     = selectedPickup,
                destination        = selectedDestination,
                onPickupClick      = { navController.navigate(Routes.PICKUP_LOCATION) },
                onDestinationClick = { navController.navigate(Routes.DESTINATION) },
                onContinue         = { vehicle, distance ->
                    bookingVM.selectedVehicle  = vehicle
                    bookingVM.vehicleTypeId     = vehicle.id
                    bookingVM.distanceKm       = distance
                    bookingVM.pickupName       = selectedPickup?.name ?: ""
                    bookingVM.destinationName  = selectedDestination?.name ?: ""
                    bookingVM.pickupLocationId  = selectedPickup?.id
                    bookingVM.pickupLat         = selectedPickup?.latitude
                    bookingVM.pickupLng         = selectedPickup?.longitude
                    bookingVM.dropoffLocationId = selectedDestination?.id
                    bookingVM.dropoffLat        = selectedDestination?.latitude
                    bookingVM.dropoffLng        = selectedDestination?.longitude
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
                onViewPOD = { shipmentId -> navController.navigate("pod/$shipmentId") },
                onTrack = { shipmentId -> navController.navigate("track_detail/$shipmentId") },
                onViewDetails = { shipmentId -> navController.navigate("history_detail/$shipmentId") },
                onRebook = { booking ->
                    if (!booking.vehicleTypeId.isNullOrBlank()) {
                        bookingVM.selectedVehicle = MockData.vehicles.firstOrNull { it.id == booking.vehicleTypeId }
                        bookingVM.vehicleTypeId = booking.vehicleTypeId
                    }
                    bookingVM.pickupName = booking.pickupName
                    bookingVM.destinationName = booking.destinationName
                    bookingVM.pickupLocationId = booking.pickupLocationId
                    bookingVM.dropoffLocationId = booking.dropoffLocationId
                    navController.navigate(Routes.ADD_DETAILS)
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

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        // ── Driver Routes ──────────────────────────────────────────────────
        composable(Routes.DRIVER_HOME) {
            DriverHomeScreen(
                onNavigateToRoute = { route ->
                    when {
                        route == "profile" -> navController.navigate(Routes.DRIVER_PROFILE)
                        route == "navigate" -> navController.navigate("${Routes.DRIVER_NAVIGATION}/active")
                        route == "trips" -> navController.navigate(Routes.MY_TRIPS)
                        route == "login" -> navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                        route.startsWith("trip_details/") -> {
                            val deliveryId = route.substringAfter("trip_details/")
                            navController.navigate("${Routes.DRIVER_NAVIGATION}/$deliveryId")
                        }
                        route.startsWith("driver_navigation/") -> navController.navigate(route)
                        else -> {
                            if (route.isNotBlank() && route != "home") {
                                navController.navigate(route)
                            }
                        }
                    }
                }
            )
        }

        composable(
            route = "${Routes.DRIVER_NAVIGATION}/{deliveryId}",
            arguments = listOf(navArgument("deliveryId") { defaultValue = "active" })
        ) {
            DriverNavigationScreen(
                onNavigateToSubmitPod = { deliveryId ->
                    navController.navigate("${Routes.SUBMIT_POD}/$deliveryId")
                },
                onNavigateToDeliveryComplete = { deliveryId ->
                    navController.navigate("${Routes.DELIVERY_COMPLETE}/$deliveryId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.SUBMIT_POD}/{deliveryId}",
            arguments = listOf(navArgument("deliveryId") { defaultValue = "active" })
        ) {
            SubmitPodScreen(
                onNavigateToDeliveryComplete = { deliveryId ->
                    navController.navigate("${Routes.DELIVERY_COMPLETE}/$deliveryId") {
                        popUpTo(Routes.DRIVER_HOME) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.DELIVERY_COMPLETE}/{deliveryId}",
            arguments = listOf(navArgument("deliveryId") { defaultValue = "active" })
        ) {
            DeliveryCompleteScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.DRIVER_HOME) {
                        popUpTo(Routes.DRIVER_HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MY_TRIPS) {
            MyTripsScreen(
                onNavigateToRoute = { route ->
                    when (route) {
                        "home" -> navController.navigate(Routes.DRIVER_HOME) { popUpTo(Routes.DRIVER_HOME) { inclusive = false } }
                        "navigate" -> navController.navigate("${Routes.DRIVER_NAVIGATION}/active")
                        "trips" -> {}
                        "profile" -> navController.navigate(Routes.DRIVER_PROFILE)
                    }
                },
                onActiveTripClick = { shipmentId ->
                    navController.navigate("${Routes.DRIVER_NAVIGATION}/$shipmentId")
                }
            )
        }

        composable(Routes.DRIVER_PROFILE) {
            DriverProfileScreen(
                onNavigateToRoute = { route ->
                    when (route) {
                        "home" -> navController.navigate(Routes.DRIVER_HOME) { popUpTo(Routes.DRIVER_HOME) { inclusive = false } }
                        "navigate" -> navController.navigate("${Routes.DRIVER_NAVIGATION}/active")
                        "trips" -> navController.navigate(Routes.MY_TRIPS)
                        "profile" -> {}
                    }
                },
                onLogoutComplete = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
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
                onMenuClick         = { _ -> }
            )
        }

        // ── Pickup Location Picker ─────────────────────────────────────────
        composable(Routes.PICKUP_LOCATION) {
            PickupLocationScreen(
                vehicleTypeId = bookingVM.selectedVehicle?.id,
                onConfirm = { location ->
                    selectedPickup = location
                    bookingVM.pickupLocationId = location.id
                    bookingVM.pickupLat = location.latitude
                    bookingVM.pickupLng = location.longitude
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Destination Picker ─────────────────────────────────────────────
        composable(Routes.DESTINATION) {
            DestinationScreen(
                vehicleTypeId = bookingVM.selectedVehicle?.id,
                origin    = selectedPickup,
                onConfirm = { location ->
                    selectedDestination = location
                    bookingVM.dropoffLocationId = location.id
                    bookingVM.dropoffLat = location.latitude
                    bookingVM.dropoffLng = location.longitude
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
            val bookingUiState by bookingVM.uiState.collectAsState()

            LaunchedEffect(bookingUiState) {
                if (bookingUiState is BookingUiState.Success) {
                    navController.navigate(Routes.BOOKING_SUCCESS)
                }
            }

            BookingSummaryScreen(
                vehicle         = vehicle,
                distanceKm      = bookingVM.distanceKm,
                pickupName      = bookingVM.pickupName,
                destinationName = bookingVM.destinationName,
                weightKg        = bookingVM.weightKg,
                handling        = bookingVM.handling,
                notes           = bookingVM.notes,
                totalFee        = bookingVM.totalFee,
                uiState         = bookingUiState,
                onBack          = { navController.popBackStack() },
                onConfirmBooking = { bookingVM.submit() },
                onErrorDismiss  = { bookingVM.consumeError() }
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
