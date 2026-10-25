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
        compose.onNodeWithText("New shipment").performScrollTo().performClick()
        fun input(label:String,value:String) { compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value) }
        input("Pickup address","Manila North Harbor, Manila")
        fun placePin(mode:String) {
            compose.onNodeWithText(mode).performScrollTo().performClick()
            compose.waitUntil(45000) {compose.onAllNodesWithText("Map ready",substring=true).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("Shipment map").performScrollTo().performTouchInput {click(androidx.compose.ui.geometry.Offset(width*.4f,height*.4f))}
        }
        placePin("Pin pickup")
        input("Destination address","Caloocan City Hall, Caloocan")
        placePin("Pin destination")
        compose.onNodeWithText("Choose date & time").performScrollTo().performClick()
        val chosen=java.time.LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(2)
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.DatePicker::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object:androidx.test.espresso.ViewAction {
            override fun getConstraints()=androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.DatePicker::class.java)
            override fun getDescription()="Choose a future departure date"
            override fun perform(ui:androidx.test.espresso.UiController,view:android.view.View) {(view as android.widget.DatePicker).updateDate(chosen.year,chosen.monthValue-1,chosen.dayOfMonth)}
        })
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click())
        compose.onNodeWithText("Set departure").performClick()
        compose.onNodeWithText("Continue to cargo").performScrollTo().assertIsEnabled().performClick()
        input("Cargo description and handling requirements","Dev workflow acceptance")
        input("Weight in kg","100")
        compose.onNodeWithText("Closed Van").performScrollTo().performClick()
        input("Contact number (09… or +639…)","+639000000001")
        input("Notes (optional)","shared-dev-e2e acceptance request")
        compose.onNodeWithText("Review request").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Send request").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Submit request").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Request ",substring=true).fetchSemanticsNodes().any { it.config.toString().contains("saved") } }
        compose.waitUntil(30000) { runCatching {compose.onNodeWithText("Active").assertIsEnabled();true}.getOrDefault(false) }
        compose.onNodeWithText("Active").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Active shipments").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Active shipments").assertExists()
        compose.onAllNodesWithText("Manila North Harbor, Manila",substring=true).onFirst().assertExists()
    }
}
