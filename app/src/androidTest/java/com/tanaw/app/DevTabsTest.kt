package com.tanaw.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class DevTabsTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun allFiveTabsReadActualDevData() {
  val args=InstrumentationRegistry.getArguments()
  assumeTrue(args.getString("allowDevAcceptance")=="true" && BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
  compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("EMAIL ADDRESS").fetchSemanticsNodes().isNotEmpty() }
  if(compose.onAllNodesWithText("EMAIL ADDRESS").fetchSemanticsNodes().isNotEmpty()) {
   compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(args.getString("customerEmail")!!)
   compose.onAllNodes(hasSetTextAction()).onLast().performTextInput(args.getString("customerPassword")!!)
   compose.onNodeWithText("Login",substring=true).performClick()
  }
  compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithText("Delivery overview").assertExists()
  compose.onNodeWithText("New shipment").performScrollTo().performClick()
  compose.onNodeWithText("Step 1 of 3",substring=true).assertExists()
  compose.onNodeWithText("Continue to cargo").performScrollTo().assertIsNotEnabled()
  compose.onNode(hasText("Pickup address") and hasSetTextAction()).performScrollTo().performTextReplacement("Home UI draft")
  compose.onNodeWithText("History").performClick()
  compose.onNodeWithText("Home").performClick()
  compose.onNode(hasText("Pickup address") and hasSetTextAction()).assertTextContains("Home UI draft")
  compose.onNodeWithText("Active").performClick()
  compose.onNodeWithText("Active shipments").assertExists()
  compose.onNodeWithText("Search reference or address").assertExists()
  compose.onNodeWithText("History").performClick()
  compose.onNodeWithText("Shipment history").assertExists()
  compose.onNodeWithText("Completed").assertExists()
  compose.onNodeWithText("Alerts").performClick()
  compose.onNodeWithText("Notifications").assertExists()
  compose.onNodeWithText("Profile").performClick()
  compose.onNodeWithText("Your profile").assertExists()
  compose.onNodeWithText("Home").performClick()
 }
}
