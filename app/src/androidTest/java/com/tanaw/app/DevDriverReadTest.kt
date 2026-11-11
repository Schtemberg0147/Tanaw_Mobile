package com.tanaw.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class DevDriverReadTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun assignedDriverCanReadTripAndMilestoneControls() {
  val args=InstrumentationRegistry.getArguments()
  assumeTrue(args.getString("allowDriverAcceptance")=="true" && BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
  compose.waitUntil(30000){compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("EMAIL ADDRESS").fetchSemanticsNodes().isNotEmpty()}
  if(compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty()) {
   compose.waitUntil(30000){runCatching{compose.onNodeWithText("Profile").assertIsEnabled();true}.getOrDefault(false)}
   compose.onNodeWithText("Profile").performClick();compose.onNodeWithText("Sign out").performScrollTo().performClick()
   compose.waitUntil(30000){compose.onAllNodesWithText("EMAIL ADDRESS").fetchSemanticsNodes().isNotEmpty()}
  }
  compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(args.getString("driverEmail")!!)
  compose.onAllNodes(hasSetTextAction()).onLast().performTextInput(args.getString("driverPassword")!!)
  compose.onNodeWithText("Login",substring=true).performClick()
  compose.waitUntil(30000){compose.onAllNodesWithText("Driver workspace").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText(args.getString("trackingReference")!!).performScrollTo().performClick()
  compose.onNodeWithText("Trip actions").performScrollTo().assertExists()
  compose.onNodeWithText("Start route and share location").performScrollTo().assertExists()
 }
}
