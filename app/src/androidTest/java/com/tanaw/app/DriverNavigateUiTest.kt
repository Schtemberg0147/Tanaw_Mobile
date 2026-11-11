package com.tanaw.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DriverNavigateUiTest {
    @get:Rule val compose=createComposeRule()
    private fun row(vararg values:Pair<String,String>)=buildJsonObject{values.forEach{put(it.first,it.second)}}
    private val shipment=row("id" to "fixture","tracking_number" to "UI-NAV-001","pickup_address" to "Pickup warehouse, Manila","dropoff_address" to "Delivery warehouse, Nueva Ecija","pickup_lat" to "14.6","pickup_lng" to "120.98","dropoff_lat" to "15.4","dropoff_lng" to "120.94")
    private val delivery=row("id" to "fixture-delivery","status" to "assigned","route_started_at" to "2026-10-29T01:00:00Z")
    private fun shot(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null),"navigate-$name.png").outputStream().use {instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun nextActionAndFuelAreAboveDirectionsWhileMapIsInitiallyHidden() {
        var arrived=false;var fuel=false
        compose.setContent {DriverTheme {Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
            DriverNavigateOverview(shipment,delivery,MobileState(),{_,_->Button(onClick={arrived=true}){Text("Confirm pickup arrival")}}, {}, {fuel=true})
        }}}
        compose.onNodeWithText("Heading to Pick-up").assertIsDisplayed()
        compose.onNodeWithText("Next step").assertIsDisplayed()
        compose.onNodeWithText("Confirm pickup arrival").assertIsDisplayed().performClick()
        compose.onNodeWithText("Record Fuel").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(arrived);assertTrue(fuel)}
        compose.onNodeWithTag("Shipment map").assertDoesNotExist()
        shot("next-step")
        compose.onNodeWithText("Show delivery steps").performScrollTo().performClick()
        compose.onNodeWithText("3. Arrived at pickup").performScrollTo().assertExists()
        compose.onNodeWithText("Hide delivery steps").performScrollTo().performClick()
        compose.onNodeWithText("3. Arrived at pickup").assertDoesNotExist()
    }
    @Test fun directionsRetainOfficeConfirmationAndTruckSafetyBoundary() {
        compose.setContent {DriverTheme {Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
            DriverNavigateOverview(shipment,delivery,MobileState(),{_,_->}, {})
        }}}
        compose.onNodeWithText("Directions to pickup").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Directions to destination").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Truck route awaiting office confirmation").performScrollTo().assertExists()
        compose.onNodeWithText("Google Maps may recalculate",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("Show route map").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("Shipment map").assertDoesNotExist()
        shot("route-details")
    }
    @Test fun savedFuelUsesReadableAmountAndLabelledQuantities() {
        compose.setContent {DriverTheme {Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
            DriverTripSection("Saved trip records") {DriverFuelRecords(listOf(row("total_cost" to "684.50","liters" to "10.25","price_per_liter" to "66.78","odometer_km" to "12000.1","station" to "Test station","logged_at" to "2026-10-29T01:00:00Z")))}
        }}}
        compose.onNodeWithText("₱684.50").assertExists()
        compose.onNodeWithText("Test station").assertExists()
        compose.onNodeWithText("Purchased:",substring=true).assertExists()
        compose.onNodeWithText("Odometer:",substring=true).assertExists()
        shot("fuel-records")
    }
    @Test fun roadPreviewRetriesWithoutClaimingOfficeConfirmationOrChangingAssignment() {
        var calls=0
        val geometry="""{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[120.98,14.6],[120.94,15.4]]}}]}"""
        compose.setContent {DriverTheme {Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
            DriverNavigateOverview(shipment,delivery,MobileState(),{_,_->},{},routePreview={id,_,_->
                assertEquals("fixture-delivery",id);calls++
                if(calls==1)error("Synthetic provider unavailable")
                RoutePreview(geometry,12.0,30)
            })
        }}}
        compose.onNodeWithText("Show route map").performScrollTo().performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("Retry road preview").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Unverified road preview").performScrollTo().assertExists()
        compose.onNodeWithText("Retry road preview").performScrollTo().performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("Preview road distance: 12.0 km").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Truck route awaiting office confirmation").performScrollTo().assertExists()
        compose.runOnIdle {assertEquals(2,calls);assertNull(shipment["route_geometry"])}
    }
    @Test fun offlineMapDoesNotRequestRoadPreview() {
        var calls=0
        compose.setContent {DriverTheme {Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
            DriverNavigateOverview(shipment,delivery,MobileState(offline=true),{_,_->},{},routePreview={_,_,_->calls++;error("Must not run")})
        }}}
        compose.onNodeWithText("Show route map").performScrollTo().performClick()
        compose.onNodeWithText("Road preview unavailable offline. Reconnect and retry.").performScrollTo().assertExists()
        compose.onNodeWithText("Retry road preview").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,calls)}
    }
}
