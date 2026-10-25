package com.tanaw.app

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import com.tanaw.app.feature.driver.components.AddTripExpenseSheet
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic, read-only UI fixtures: no login, remote mutations or personal records. */
class DriverFigmaUiTest {
    @get:Rule val compose=createComposeRule()
    private fun obj(vararg fields:Pair<String,String>)=buildJsonObject{fields.forEach{put(it.first,it.second)}}
    private fun fixture(active:Boolean=true)=buildJsonObject {
        put("profile",obj("id" to "ui-fixture","role" to "driver","full_name" to "Demo Driver","email" to "driver@example.invalid"))
        put("driver",obj("id" to "driver","status" to "available","license_number" to "SYN-UI-ONLY"))
        put("driver_trucks",buildJsonArray {add(obj("model" to "Wing Van","plate_number" to "UI-TEST","assigned_driver_id" to "driver"))})
        put("shipments",buildJsonArray {if(active)add(obj("id" to "shipment","tracking_number" to "UI-DRIVER-001","status" to "assigned",
            "cargo_description" to "Sample cargo","weight_kg" to "6000","pickup_address" to "Cabanatuan City Hub",
            "dropoff_address" to "Gapan City Distribution Centre","scheduled_departure" to "2026-10-26T00:00:00Z","created_at" to "2026-10-25T00:00:00Z",
            "distance_km" to "38","route_duration_minutes" to "75","driver_notes" to "Handle with care. Stack max 3 high."))})
        put("deliveries",buildJsonArray {if(active)add(obj("id" to "delivery","shipment_id" to "shipment","status" to "assigned","assigned_at" to "2026-10-25T00:00:00Z","driver_allowance" to "500"))})
    }
    private fun start(active:Boolean=true) {
        compose.setContent {DriverTheme {DriverWorkspaceScreen(MobileState(page="workspace"),fixture(active),{},{},{},{},{},
            {_,_,_->Text("Driver trip controls")},{_,_->},{_,_,_,_,_,_->})}}
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"driver-ui-$name.png")
        file.outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun driverTabsAndAssignmentUseDedicatedFlow() {
        start()
        compose.onNodeWithText("New Trip Assigned!").assertIsDisplayed();shot("home")
        compose.onNodeWithText("UI-DRIVER-001").performClick()
        compose.onNodeWithText("Ready to start route").assertIsDisplayed();shot("navigate")
        compose.onNodeWithText("TRIPS").performClick()
        compose.onNodeWithText("My Trips").assertIsDisplayed();shot("trips")
        compose.onNodeWithText("PROFILE").performClick()
        compose.onNodeWithText("Demo Driver").assertIsDisplayed()
        compose.onNodeWithText("No compliance documents are available.").assertIsDisplayed();shot("profile")
        compose.onNodeWithText("HOME").performClick()
        compose.onNodeWithText("New Trip Assigned!").assertIsDisplayed()
    }
    @Test fun idleDriverHasNoInventedAssignment() {
        start(false)
        compose.onNodeWithText("No trips assigned").assertIsDisplayed()
        compose.onNodeWithText("You are available for assignments",substring=true).assertExists();shot("idle")
    }
    @Test fun assignmentWithoutMapPinsOffersPickupAndDestinationDirections() {
        start()
        compose.onNodeWithText("UI-DRIVER-001").performClick()
        compose.onNodeWithText("This assignment has no saved map pins",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("Directions to pickup").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Directions to destination").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("View pickup-to-destination route").performScrollTo().assertIsEnabled()
    }
    @Test fun proofRequiresCaptureAndSignature() {
        compose.setContent {DriverTheme {DriverProofForm("fixture","fixture-owner","UI-DRIVER-001",MobileState(),{},{}) {_,_,_,_,_->}}}
        compose.onNodeWithText("COMPLETE DELIVERY").assertIsNotEnabled()
        compose.onNodeWithText("Enter full name").performTextInput("Demo Recipient")
        compose.onNodeWithText("COMPLETE DELIVERY").assertIsNotEnabled();shot("proof")
    }
    @Test fun incidentRetainsValidatedSubmission() {
        var reported=false
        compose.setContent {DriverTheme {DriverIncidentForm("fixture",MobileState(),{}) {_,category,description,minutes->reported=category=="traffic" && description=="Synthetic traffic delay" && minutes==0}}}
        compose.onNodeWithText("Submit Report").assertIsNotEnabled()
        compose.onNodeWithText("Describe the issue in detail…").performTextInput("Synthetic traffic delay")
        compose.onNodeWithText("Submit Report").assertIsEnabled();shot("incident")
        compose.onNodeWithText("Submit Report").performClick()
        compose.runOnIdle {org.junit.Assert.assertTrue(reported)}
    }
    @Test fun expenseRequiresDescriptionAndPositiveAmount() {
        compose.setContent {DriverTheme {AddTripExpenseSheet("fixture","UI-DRIVER-001",false,null,{},{_,_,_->})}}
        compose.onNodeWithText("Submit Expense").assertIsNotEnabled()
        compose.onNodeWithTag("expense_category_input").performTextInput("Parking")
        compose.onNodeWithTag("expense_amount_input").performTextInput("50")
        compose.onNodeWithText("Submit Expense").performScrollTo().assertIsEnabled();shot("expense")
    }
}
