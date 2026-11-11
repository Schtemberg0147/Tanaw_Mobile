package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Opt-in real Dev acceptance. Credentials are instrumentation arguments, never source. */
class DevCustomerWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun customerSubmitsRequestVisibleInActive() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowDevWrites")=="true" && BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
        compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("EMAIL ADDRESS").fetchSemanticsNodes().isNotEmpty() }
        if(compose.onAllNodesWithText("EMAIL ADDRESS").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(args.getString("customerEmail")!!)
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput(args.getString("customerPassword")!!)
            compose.onNodeWithText("Login",substring=true).performClick()
        }
        compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithTag("PickupLocationField").performClick()
        compose.onNodeWithTag("TopFloatingAddressCard").performClick()
        compose.onNodeWithTag("LocationSearchInput").performTextReplacement("Manila North Harbor, Manila")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Manila North Harbor",substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Manila North Harbor",substring=true).onFirst().performClick()
        compose.onNodeWithTag("ConfirmLocationButton").performClick()
        compose.onNodeWithTag("DestinationLocationField").performClick()
        compose.onNodeWithTag("TopFloatingAddressCard").performClick()
        compose.onNodeWithTag("LocationSearchInput").performTextReplacement("Caloocan City Hall, Caloocan")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Caloocan",substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Caloocan",substring=true).onFirst().performClick()
        compose.onNodeWithTag("ConfirmLocationButton").performClick()
        compose.onNodeWithText("Closed Van").performScrollTo().performClick()
        compose.onNodeWithTag("BookingContinueButton").performClick()
        compose.onNodeWithTag("OrderContactInput").performScrollTo().performTextReplacement("+639000000001")
        compose.onNodeWithTag("CargoDescriptionInput").performScrollTo().performTextReplacement("Dev workflow acceptance")
        compose.onNodeWithTag("EstWeightInput").performScrollTo().performTextReplacement("100")
        compose.onNodeWithTag("NotesToDriverInput").performScrollTo().performTextReplacement("shared-dev-e2e acceptance request")
        compose.onNodeWithTag("ConfirmDetailsButton").assertIsEnabled().performClick()
        compose.onNodeWithTag("ConfirmBookingButton").assertIsEnabled().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Booking Submitted!").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("TrackShipmentButton").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Manila North Harbor",substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Manila North Harbor",substring=true).onFirst().assertExists()
    }
}
