package com.tanaw.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic forms/history. No account, business-record or remote writes. */
class DriverOperationalUiTest {
    @get:Rule val compose=createComposeRule()
    private fun shot(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null),"operations-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun fuelRequiresQuantitiesAndUsesCalculatedCost() {
        var saved=false
        compose.setContent {DriverTheme {DriverFuelForm("fixture",MobileState(),{}) {_,entry,_->saved=entry.total.toPlainString()=="684.50"}}}
        compose.onNodeWithText("Save Fuel Record").assertIsNotEnabled()
        compose.onNodeWithText("Liters").performTextInput("10.25")
        compose.onNodeWithText("Price per liter (PHP)").performTextInput("66.78")
        compose.onNodeWithText("Save Fuel Record").assertIsNotEnabled()
        compose.onNodeWithText("Odometer (km)").performTextInput("12000.1")
        compose.onNodeWithText("Save Fuel Record").assertIsEnabled();shot("fuel")
        compose.onNodeWithText("Save Fuel Record").performClick()
        compose.runOnIdle {assertTrue(saved)}
    }
    @Test fun uncertainFuelSaveLocksInputsAndRetriesSameRequestAndBody() {
        val sent=mutableListOf<Pair<String,FuelEntry>>()
        var closed=false
        compose.setContent {DriverTheme {DriverFuelForm("fixture",MobileState(),{closed=true}) {request,entry,saved->
            sent.add(request to entry)
            if(sent.size==2)saved()
        }}}
        compose.onNodeWithText("Liters").performTextInput("10.25")
        compose.onNodeWithText("Price per liter (PHP)").performTextInput("66.78")
        compose.onNodeWithText("Odometer (km)").performTextInput("12000.1")
        compose.onNodeWithText("Save Fuel Record").performClick()
        compose.onNodeWithText("Liters").assertIsNotEnabled()
        compose.onNodeWithText("Price per liter (PHP)").assertIsNotEnabled()
        compose.onNodeWithText("Odometer (km)").assertIsNotEnabled()
        compose.onNodeWithText("Station (optional)").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Close unconfirmed fuel record?").assertIsDisplayed()
        compose.runOnIdle {assertFalse(closed)}
        compose.onNodeWithText("Keep retrying").performClick()
        compose.onNodeWithText("Retry Fuel Record").performClick()
        compose.runOnIdle {assertEquals(2,sent.size);assertEquals(sent[0],sent[1]);assertTrue(closed)}
    }
    @Test fun offlineFuelFormCannotSubmit() {
        var calls=0
        compose.setContent {DriverTheme {DriverFuelForm("fixture",MobileState(offline=true),{}) {_,_,_->calls++}}}
        compose.onNodeWithText("Liters").performTextInput("10")
        compose.onNodeWithText("Price per liter (PHP)").performTextInput("60")
        compose.onNodeWithText("Odometer (km)").performTextInput("1000")
        compose.onNodeWithText("Save Fuel Record").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,calls)}
    }
    private fun data()=buildJsonObject {
        put("profile",buildJsonObject {put("role","driver");put("full_name","Fixture Driver")})
        put("shipments",JsonArray(emptyList()))
    }
    @Test fun historyUsesFullPeriodTotalsAndRequestsNextPage() {
        val calls=mutableListOf<Pair<String,Boolean>>()
        val history=buildJsonObject {
            put("period","This month");put("offset",0);put("total",225);put("completed",225)
            put("allowance_missing",0);put("allowance_total",22500);put("on_time_known",225);put("on_time_count",225)
            put("shipments",JsonArray(emptyList()));put("driver_deliveries",JsonArray(emptyList()))
        }
        compose.setContent {DriverTheme {DriverWorkspaceScreen(MobileState(history=history),data(),{},{},{},{},{},
            {_,_,_->Text("Trip controls")},{_,_->},{_,_,_,_,_,_->},onHistory={period,more->calls.add(period to more)})}}
        compose.onNodeWithText("TRIPS").performClick()
        compose.onNodeWithText("225 Completed").assertExists();shot("history")
        compose.onNodeWithText("Load more trips").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(calls.contains("This month" to true))}
        compose.onNodeWithContentDescription("Filter trips").performScrollTo().performClick()
        compose.onNodeWithText("Last month").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(calls.contains("Last month" to false))}
    }
    @Test fun failedHistoryHasRetryAndDoesNotClaimZeroTotals() {
        val calls=mutableListOf<Pair<String,Boolean>>()
        compose.setContent {DriverTheme {DriverWorkspaceScreen(MobileState(historyError="History unavailable"),data(),{},{},{},{},{},
            {_,_,_->Text("Trip controls")},{_,_->},{_,_,_,_,_,_->},onHistory={period,more->calls.add(period to more)})}}
        compose.onNodeWithText("TRIPS").performClick()
        compose.onNodeWithText("History unavailable").assertExists()
        compose.onNodeWithText("0 Completed").assertDoesNotExist()
        compose.onNodeWithText("Refresh trip history").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(2,calls.size)}
    }
}
