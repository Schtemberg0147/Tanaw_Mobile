package com.tanaw.app.feature.workspace.driver.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tanaw.app.feature.workspace.driver.components.DriverTripActionsContent
import com.tanaw.app.feature.workspace.driver.rules.DriverGeofenceRules
import com.tanaw.app.feature.workspace.driver.rules.GeofenceCheckResult
import com.tanaw.app.feature.workspace.tracking.TrackingStatus
import com.tanaw.app.feature.workspace.state.MobileState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DriverGeofenceUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val pickupLat = 15.4865
    private val pickupLng = 120.9679
    private val dropoffLat = 14.6158
    private val dropoffLng = 120.9585

    private val shipmentWithPins = buildJsonObject {
        put("id", "shp-geo-1")
        put("pickup_lat", pickupLat)
        put("pickup_lng", pickupLng)
        put("dropoff_lat", dropoffLat)
        put("dropoff_lng", dropoffLng)
    }

    private val pickupDelivery = buildJsonObject {
        put("id", "del-geo-1")
        put("shipment_id", "shp-geo-1")
        put("status", "assigned")
        put("route_started_at", "2026-11-15T01:00:00Z")
        put("pickup_arrived_at", "")
    }

    private val inTransitDelivery = buildJsonObject {
        put("id", "del-geo-1")
        put("shipment_id", "shp-geo-1")
        put("status", "in_transit")
        put("route_started_at", "2026-11-15T01:00:00Z")
    }

    @Test
    fun pickupActionShows50mRequirementAndTriggersArriveCheck() {
        var requestedAction: String? = null
        var locating by mutableStateOf(false)
        compose.setContent {
            DriverTheme {
                Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
                    DriverTripActionsContent(
                        delivery = pickupDelivery,
                        shipment = shipmentWithPins,
                        state = MobileState(),
                        tracking = TrackingStatus(),
                        locating = locating,
                        activeAction = "arrive",
                        error = null,
                        noShowDue = false,
                        hasProofDraft = false,
                        onRequestLocationAction = { action ->
                            requestedAction = action
                            locating = true
                        },
                        onStopTracking = {},
                        onDriverAction = {},
                        onOpenProof = {},
                        onOpenIncident = {},
                        onRefreshTrip = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Must be within 50m of the pick-up location to confirm arrival.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Refresh trip coordinates").assertDoesNotExist()
        compose.onNodeWithText("I'VE ARRIVED AT PICK-UP").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("arrive", requestedAction) }
        compose.onNodeWithText("Checking pick-up location…")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun pickupGeofenceRejectionDisplays50mErrorMessage() {
        var arrivedConfirmed = false
        compose.setContent {
            var error by remember { mutableStateOf<String?>(null) }
            DriverTheme {
                Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
                    DriverTripActionsContent(
                        delivery = pickupDelivery,
                        shipment = shipmentWithPins,
                        state = MobileState(),
                        tracking = TrackingStatus(),
                        locating = false,
                        activeAction = "arrive",
                        error = error,
                        noShowDue = false,
                        hasProofDraft = false,
                        onRequestLocationAction = {
                            val check = DriverGeofenceRules.checkArrival(
                                driverLat = pickupLat + 0.001,
                                driverLng = pickupLng,
                                shipment = shipmentWithPins,
                                details = null,
                                isDestination = false,
                            )
                            if (check is GeofenceCheckResult.Allowed) {
                                arrivedConfirmed = true
                            } else {
                                error = DriverGeofenceRules.message(check, isDestination = false)
                            }
                        },
                        onStopTracking = {},
                        onDriverAction = {},
                        onOpenProof = {},
                        onOpenIncident = {},
                        onRefreshTrip = {},
                    )
                }
            }
        }

        compose.onNodeWithText("I'VE ARRIVED AT PICK-UP").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(!arrivedConfirmed) }
        compose.onNodeWithText("Move within 50m to confirm pick-up arrival.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun missingPickupCoordinatesShowsLoadingAndRefreshControls() {
        val shipmentWithoutPins = buildJsonObject { put("id", "shp-geo-1") }
        var detailLoading by mutableStateOf(true)
        var refreshClicks = 0
        compose.setContent {
            DriverTheme {
                Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
                    DriverTripActionsContent(
                        delivery = pickupDelivery,
                        shipment = shipmentWithoutPins,
                        state = MobileState(detailLoading = detailLoading),
                        tracking = TrackingStatus(),
                        locating = false,
                        activeAction = null,
                        error = null,
                        noShowDue = false,
                        hasProofDraft = false,
                        onRequestLocationAction = {},
                        onStopTracking = {},
                        onDriverAction = {},
                        onOpenProof = {},
                        onOpenIncident = {},
                        onRefreshTrip = { refreshClicks++ },
                    )
                }
            }
        }

        compose.onNodeWithText("Loading pick-up coordinates…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Refresh trip coordinates").assertDoesNotExist()

        compose.runOnIdle { detailLoading = false }
        compose.onNodeWithText(
            "Pick-up coordinates are missing for this trip. Refresh trip details or contact the office."
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Refresh trip coordinates").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshClicks) }
    }

    @Test
    fun destinationGeofenceShows50mRequirementAndDisplaysTooFarError() {
        var proofOpened = false
        compose.setContent {
            var error by remember { mutableStateOf<String?>(null) }
            DriverTheme {
                Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
                    DriverTripActionsContent(
                        delivery = inTransitDelivery,
                        shipment = shipmentWithPins,
                        state = MobileState(),
                        tracking = TrackingStatus(),
                        locating = false,
                        activeAction = "deliver",
                        error = error,
                        noShowDue = false,
                        hasProofDraft = false,
                        onRequestLocationAction = {
                            val check = DriverGeofenceRules.checkArrival(
                                driverLat = dropoffLat,
                                driverLng = dropoffLng + 0.001,
                                shipment = shipmentWithPins,
                                details = null,
                                isDestination = true,
                            )
                            if (check is GeofenceCheckResult.Allowed) {
                                proofOpened = true
                            } else {
                                error = DriverGeofenceRules.message(check, isDestination = true)
                            }
                        },
                        onStopTracking = {},
                        onDriverAction = {},
                        onOpenProof = { proofOpened = true },
                        onOpenIncident = {},
                        onRefreshTrip = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Must be within 50m of the destination to mark as delivered.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("MARK AS DELIVERED").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(!proofOpened) }
        compose.onNodeWithText("Move within 50m to mark as delivered.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
