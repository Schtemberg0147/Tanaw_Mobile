package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit opt-in only; reads the existing Dev test driver's screens, with no delivery actions. */
class DevDriverFigmaAcceptanceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun existingDriverCanOpenFigmaTabs() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowDriverAcceptance")=="true" && BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
        compose.waitUntil(60000) {
            listOf("TRIPS","PHONE NUMBER","EMAIL ADDRESS","Profile").any {compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()}
        }
        if(compose.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("TRIPS").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText("Profile").performClick()
            compose.onNodeWithText("Sign out").performScrollTo().performClick()
            compose.waitUntil(30000){compose.onAllNodesWithText("PHONE NUMBER").fetchSemanticsNodes().isNotEmpty()}
        }
        if(compose.onAllNodesWithText("TRIPS").fetchSemanticsNodes().isEmpty()) {
            if(compose.onAllNodesWithText("Use email address instead").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText("Use email address instead").performClick()
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(args.getString("driverEmail")!!)
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput(args.getString("driverPassword")!!)
            compose.onNodeWithText("Login",substring=true).performClick()
        }
        compose.waitUntil(60000){compose.onAllNodesWithText("TRIPS").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("TRIPS").performClick();compose.onNodeWithText("My Trips").assertIsDisplayed()
        compose.onNodeWithText("PROFILE").performClick();compose.onNodeWithText("Compliance Status").assertExists()
        compose.onNodeWithText("HOME").performClick()
    }
}
