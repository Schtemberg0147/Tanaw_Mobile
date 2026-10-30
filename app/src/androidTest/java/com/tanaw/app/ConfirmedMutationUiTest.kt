package com.tanaw.app

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.workspace.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConfirmedMutationUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun confirmedStartAndFailedRefreshShowSavedStateAndBlockAnotherCommand() {
        var state by mutableStateOf(MobileState(page="workspace"))
        var starts=0
        var gps=0
        fun obj(vararg fields:Pair<String,String>)=buildJsonObject {fields.forEach {put(it.first,it.second)}}
        val data=buildJsonObject {
            put("profile",obj("role" to "driver","full_name" to "Fixture"))
            put("driver",obj("id" to "driver","status" to "available"))
            put("driver_trucks",JsonArray(emptyList()))
            put("shipments",buildJsonArray {add(obj("id" to "s","tracking_number" to "TEST-RT08","status" to "assigned"))})
            put("driver_deliveries",buildJsonArray {add(obj("id" to "d","shipment_id" to "s","status" to "assigned"))})
        }
        compose.setContent {
            val scope=rememberCoroutineScope()
            DriverTheme {DriverWorkspaceScreen(state,data,{},{},{},{},{}, {_,_,_->
                Button(enabled=!state.offline,onClick={scope.launch {
                    ConfirmedMutation.run({starts++},{true},{gps++;state=state.copy(message="Route started.")},
                        {error("read unavailable")},{state=state.copy(offline=true,message="Route started. Saved successfully. Refresh to reload current trip status.")})
                }}) {Text("Start fixture route")}
            },{_,_->},{_,_,_,_,_,_->})}
        }
        compose.onNodeWithText("TEST-RT08").performScrollTo().performClick()
        compose.onNodeWithText("Start fixture route").performScrollTo().performClick()
        compose.onNodeWithText("Route started. Saved successfully.",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("Retry connection").performScrollTo().assertExists()
        compose.onNodeWithText("Start fixture route").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {assertEquals(1,starts);assertEquals(1,gps);assertNull(state.error)}
    }
}
