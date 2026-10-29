package com.tanaw.app

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.workspace.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DriverRecordPresentationUiTest {
    @get:Rule val compose=createComposeRule()
    private fun obj(vararg fields:Pair<String,String>)=buildJsonObject {fields.forEach {put(it.first,it.second)}}
    private fun data(vararg ids:String,status:String="assigned")=buildJsonObject {
        put("profile",obj("role" to "driver","full_name" to "Fixture Driver"))
        put("driver",obj("id" to "driver","status" to "available"))
        put("driver_trucks",JsonArray(emptyList()))
        put("compliance",JsonArray(emptyList()))
        put("shipments",JsonArray(ids.map {obj("id" to it,"tracking_number" to "REF-$it","status" to status,"created_at" to "2026-10-28T00:00:00Z")}))
        put("driver_deliveries",JsonArray(ids.map {obj("id" to "delivery-$it","shipment_id" to it,"status" to if(status=="completed")"delivered" else status)}))
    }
    @Composable private fun RecordScreen(data:JsonObject,state:MobileState=MobileState(),details:(String)->Unit={},refresh:()->Unit={}) {
        DriverTheme {DriverWorkspaceScreen(state,data,refresh,{},{},{},details,
            {s,_,_->Text("Controls for ${s.value("id")}")},{_,_->},{_,_,_,_,_,_->})}
    }
    @Test fun disappearingSelectedTripNeverOpensAnotherTripEvenWithStaleHistory() {
        var records by mutableStateOf(data("A","B"))
        val loads=mutableListOf<String>()
        val history=data("A")
        compose.setContent {RecordScreen(records,MobileState(history=history),{loads.add(it)})}
        compose.onNodeWithText("REF-A").performScrollTo().performClick()
        compose.onNodeWithText("Controls for A").performScrollTo().assertExists()
        compose.runOnIdle {records=data("B")}
        compose.onNodeWithText("Selected trip unavailable").assertExists()
        compose.onNodeWithText("Controls for B").assertDoesNotExist()
        compose.runOnIdle {assertFalse(loads.contains("B"))}
        compose.onNodeWithText("Choose another trip").performScrollTo().performClick()
        compose.onNodeWithText("REF-B").performScrollTo().performClick()
        compose.onNodeWithText("Controls for B").performScrollTo().assertExists()
    }
    @Test fun navigateRequiresExplicitTripChoice() {
        val loads=mutableListOf<String>()
        compose.setContent {RecordScreen(data("A","B"),details={loads.add(it)})}
        compose.onNodeWithText("NAVIGATE").performClick()
        compose.onNodeWithText("Choose a trip to navigate").assertExists()
        compose.runOnIdle {assertTrue(loads.isEmpty())}
        compose.onNodeWithText("REF-B").performScrollTo().performClick()
        compose.onNodeWithText("Controls for B").performScrollTo().assertExists()
    }
    @Test fun expenseFormDoesNotTransferWhenSelectedAssignmentDisappears() {
        var records by mutableStateOf(data("A","B",status="in_transit"))
        compose.setContent {RecordScreen(records)}
        compose.onNodeWithText("REF-A").performScrollTo().performClick()
        compose.onNodeWithText("Add Trip Expense").performScrollTo().performClick()
        compose.onNodeWithTag("add_trip_expense_sheet").assertExists()
        compose.runOnIdle {records=data("B",status="in_transit")}
        compose.onNodeWithTag("add_trip_expense_sheet").assertDoesNotExist()
        compose.onNodeWithText("Selected trip unavailable").assertExists()
    }
    @Test fun complianceFailureIsUnavailableAndRetryCanConfirmEmpty() {
        var records by mutableStateOf(JsonObject(data()+mapOf("driver_sections" to obj("compliance" to "error"))))
        compose.setContent {RecordScreen(records,refresh={records=data()})}
        compose.onNodeWithText("PROFILE").performClick()
        compose.onNodeWithText("Compliance records unavailable.",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("No compliance documents were returned for your account.").assertDoesNotExist()
        compose.onNodeWithText("Refresh driver profile").performScrollTo().performClick()
        compose.onNodeWithText("No compliance documents were returned for your account.").performScrollTo().assertExists()
        compose.onNodeWithText("Compliance records unavailable.",substring=true).assertDoesNotExist()
    }
    @Test fun completeSummaryDoesNotConvertUnavailableExpensesToZero() {
        compose.setContent {RecordScreen(data("C",status="completed"))}
        compose.onNodeWithText("TRIPS").performClick()
        compose.onNodeWithText("REF-C").performScrollTo().performClick()
        compose.onNodeWithText("Other expenses submitted: Unavailable",substring=true).performScrollTo().assertExists()
    }
    @Test fun missingNonNavigateSelectionHasExplicitRecovery() {
        var records by mutableStateOf(data("C",status="completed"))
        compose.setContent {RecordScreen(records)}
        compose.onNodeWithText("TRIPS").performClick()
        compose.onNodeWithText("REF-C").performScrollTo().performClick()
        compose.runOnIdle {records=data()}
        compose.onNodeWithText("Selected trip unavailable").assertExists()
        compose.onNodeWithText("Choose another trip").performScrollTo().assertIsEnabled()
    }
    @Test fun rpcTimelineRendersStatusNoteAndMilestone() {
        val details=buildJsonObject {
            put("shipment_id","A")
            put("history",JsonArray(listOf(obj("from_status" to "assigned","to_status" to "loading","created_at" to "2026-10-28T01:00:00Z","note" to "Customer present"))))
            put("events",JsonArray(listOf(obj("event_type" to "route_started","occurred_at" to "2026-10-28T00:00:00Z"))))
        }
        compose.setContent {RecordScreen(data("A"),MobileState(details=details))}
        compose.onNodeWithText("REF-A").performScrollTo().performClick()
        compose.onNodeWithText("Show timeline (2 updates)").performScrollTo().performClick()
        compose.onNodeWithText("Assigned → Loading · Customer present",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("Route started",substring=true).performScrollTo().assertExists()
    }
}
