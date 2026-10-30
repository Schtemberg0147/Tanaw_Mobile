package com.tanaw.app
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.action.ViewActions.click
import com.tanaw.app.feature.workspace.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class BookingScheduleTest {
 @get:Rule val compose=createComposeRule()
 @Test fun datePickerBlocksPastDatesAndTimeMenusExcludePastTimes() {
  var chosen=""
  compose.setContent {var value by remember {mutableStateOf("")};MaterialTheme {BookingSchedule(value,true) {value=it;chosen=it}}}
  compose.onNodeWithText("Choose date & time").performClick()
  onView(isAssignableFrom(android.widget.DatePicker::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,_ ->
   val picker=view as android.widget.DatePicker
   val minimum=java.time.Instant.ofEpochMilli(picker.minDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
   assertEquals(MobileBookingRules.earliest().toLocalDate(),minimum)
  }
  onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
  val earliest=MobileBookingRules.earliest()
  compose.onNodeWithText("Hour: ${earliest.hour.toString().padStart(2,'0')}").performClick()
  if(earliest.hour>0)compose.onNodeWithText((earliest.hour-1).toString().padStart(2,'0')).assertDoesNotExist()
  compose.onNodeWithText(earliest.hour.toString().padStart(2,'0')).performClick()
  compose.onNodeWithText("Minute: ${earliest.minute.toString().padStart(2,'0')}").performClick()
  if(earliest.minute>0)compose.onNodeWithText((earliest.minute-1).toString().padStart(2,'0')).assertDoesNotExist()
  compose.onNodeWithText(earliest.minute.toString().padStart(2,'0')).performClick()
  compose.onNodeWithText("Set departure").assertIsEnabled().performClick()
  assertNotNull(MobileBookingRules.departure(chosen))
 }
}
