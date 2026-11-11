package com.tanaw.app

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.workspace.*
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IncidentSubmissionUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun textOnlyRetryRetainsRequestAndEditingCreatesNewRequest() {
        val requests=mutableListOf<String>()
        compose.setContent {DriverTheme {DriverIncidentForm("fixture",MobileState(error="Upload unavailable. Retry."),{}) {request,category,text,delay,photos->
            requests.add(request);assertEquals("traffic",category);assertTrue(text.length>=5);assertEquals(0,delay);assertTrue(photos.isEmpty())
        }}}
        compose.onNodeWithText("Submit Report").assertIsNotEnabled()
        compose.onNodeWithText("Describe the issue in detail…").performTextInput("Synthetic traffic delay")
        compose.onNodeWithText("Submit Report").performClick()
        compose.onNodeWithText("Traffic/Delay").performScrollTo().performClick()
        compose.onNodeWithText("Submit Report").performClick()
        compose.runOnIdle {assertEquals(requests[0],requests[1])}
        compose.onNodeWithText("Synthetic traffic delay").performScrollTo().performTextInput(" updated")
        compose.onNodeWithText("Submit Report").performClick()
        compose.runOnIdle {assertNotEquals(requests[1],requests[2])}
    }
    @Test fun offlineIncidentCannotSubmitOrAddPhoto() {
        compose.setContent {DriverTheme {DriverIncidentForm("fixture",MobileState(offline=true),{}) {_,_,_,_,_->fail("Offline submit")}}}
        compose.onNodeWithText("Describe the issue in detail…").performTextInput("Synthetic incident")
        compose.onNodeWithText("Submit Report").assertIsNotEnabled()
        compose.onNodeWithText("Add incident photo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Reconnect and refresh",substring=true).performScrollTo().assertExists()
    }
    @Test fun confirmedIncidentClosesBeforeFailedRefreshAndKeepsSavedAcknowledgment() {
        var closed by mutableStateOf(false)
        var message by mutableStateOf("")
        var saves=0
        compose.setContent {
            val scope=rememberCoroutineScope()
            DriverTheme {
                if(closed)Text(message)
                else DriverIncidentForm("fixture",MobileState(),{}) {_,_,_,_,_->scope.launch {
                    ConfirmedMutation.run({saves++},{true},{closed=true;message="Incident reported to the office."},
                        {assertTrue(closed);error("refresh failed")},{message="Incident saved. Refresh to reload your records."})
                }}
            }
        }
        compose.onNodeWithText("Describe the issue in detail…").performTextInput("Synthetic incident")
        compose.onNodeWithText("Submit Report").performClick()
        compose.onNodeWithText("Incident saved.",substring=true).assertExists()
        compose.onNodeWithText("Submit Report").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,saves)}
    }
}
