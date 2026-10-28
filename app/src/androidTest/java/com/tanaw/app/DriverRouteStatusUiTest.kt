package com.tanaw.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.workspace.*
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test

class DriverRouteStatusUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun liveEtaShowsSourceGpsAndExternalGuidanceBoundary() {
        val now=Instant.now()
        val status=buildJsonObject {put("route_verified",true);put("leg","pickup");put("calculated_at",now.toString());put("position_at",now.minusSeconds(10).toString());put("expires_at",now.plusSeconds(100).toString());put("eta_at",now.plusSeconds(600).toString())}
        compose.setContent {DriverTheme {Column {DriverRouteStatusCard(status,false)}}}
        compose.onNodeWithText("ETA to pickup:",substring=true).assertExists()
        compose.onNodeWithText("OpenRouteService HGV",substring=true).assertExists()
        compose.onNodeWithText("Calculated",substring=true).assertExists()
        compose.onNodeWithText("Google Maps may recalculate",substring=true).assertExists()
    }
    @Test fun legacyRouteNeedsOfficeConfirmationRatherThanInventedEta() {
        compose.setContent {DriverTheme {Column {DriverRouteStatusCard(buildJsonObject{},false)}}}
        compose.onNodeWithText("Truck route awaiting office confirmation").assertExists()
        compose.onNodeWithText("ETA unavailable: office-confirmed truck route required").assertExists()
    }
    @Test fun expiredEtaIsNotPresentedAsLive() {
        val now=Instant.now()
        val status=buildJsonObject {put("route_verified",true);put("leg","destination");put("calculated_at",now.minusSeconds(180).toString());put("position_at",now.minusSeconds(190).toString());put("expires_at",now.minusSeconds(60).toString());put("eta_at",now.plusSeconds(600).toString())}
        compose.setContent {DriverTheme {Column {DriverRouteStatusCard(status,false)}}}
        compose.onNodeWithText("ETA expired",substring=true).assertExists()
        compose.onNodeWithText("ETA to destination:",substring=true).assertDoesNotExist()
    }
}
