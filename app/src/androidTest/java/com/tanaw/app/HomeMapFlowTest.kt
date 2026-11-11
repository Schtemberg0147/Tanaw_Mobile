package com.tanaw.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.BookingRouteMap
import com.tanaw.app.feature.workspace.RoutePreview
import com.tanaw.app.feature.workspace.MobileGeocoding
import org.maplibre.android.geometry.LatLng
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual native map taps, no account, backend writes or coordinate-entry fields. */
class HomeMapFlowTest {
 @get:Rule val compose=createComposeRule()
 @Test fun inlineMapPlacesAndMovesBothPinsWithoutCoordinateEntry() {
  var pickup="";var destination=""
  var pickupPoint:LatLng?=null;var destinationPoint:LatLng?=null
  compose.setContent {
   var p by remember {mutableStateOf("")};var d by remember {mutableStateOf("")}
   var pp by remember {mutableStateOf<LatLng?>(null)};var dp by remember {mutableStateOf<LatLng?>(null)}
   MaterialTheme {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
     BookingRouteMap(p,d,pp,dp,onPickupText={p=it},onDestinationText={d=it},
      onPickup={p=it.label;pp=LatLng(it.latitude,it.longitude);pickup=p;pickupPoint=pp},
      onDestination={d=it.label;dp=LatLng(it.latitude,it.longitude);destination=d;destinationPoint=dp},
      addressLoader={point -> kotlinx.coroutines.delay(100);"Resolved street ${point.latitude}"},
      routeLoader={a,b -> kotlinx.coroutines.delay(150);RoutePreview("""{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[${a.longitude},${a.latitude}],[${b.longitude},${b.latitude}]]}}]}""",2.5,7)})
    }
   }
  }
  compose.onNodeWithText("Confirmed latitude").assertDoesNotExist()
  compose.onNodeWithText("Confirmed longitude").assertDoesNotExist()
  compose.onNodeWithText("Back to Home").assertDoesNotExist()
  compose.waitUntil(60000) {compose.onAllNodesWithText("Map ready",substring=true).fetchSemanticsNodes().isNotEmpty()}
  fun tap(x:Float,y:Float) {compose.onNodeWithTag("Shipment map").performScrollTo().performTouchInput {click(Offset(width*x,height*y))}}
  tap(.35f,.35f)
  compose.waitUntil(5000) {pickupPoint!=null}
  compose.waitUntil(5000) {pickup.startsWith("Resolved street")}
  val originalPickup=pickupPoint
  compose.onNodeWithText("Pin destination").performScrollTo().performClick()
  tap(.7f,.65f)
  compose.waitUntil(5000) {destinationPoint!=null}
  assertEquals(originalPickup,pickupPoint)
  compose.waitUntil(5000) {destination.startsWith("Resolved street")}
  val originalDestination=destinationPoint
  compose.onNodeWithText("Pin pickup").performScrollTo().performClick()
  tap(.2f,.7f)
  compose.waitUntil(5000) {pickupPoint!=originalPickup}
  assertEquals(originalDestination,destinationPoint)
  compose.waitUntil(5000) {compose.onAllNodesWithText("Road route · 2.5 km",substring=true).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Red: pickup · Blue: destination").assertExists()
  compose.onNodeWithText("Pickup: pin placed · Destination: pin placed").assertExists()
  Thread.sleep(1500) // Let the native camera animation settle before visual capture.
  val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  val resolver=InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
  val values=android.content.ContentValues().apply {
   put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"tanaw-map-flow.png")
   put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
   put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Tanaw")
  }
  val uri=resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
  resolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
 }
 @Test fun actualReverseLookupFindsPublicManilaAddress() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val address=kotlinx.coroutines.runBlocking {MobileGeocoding.reverse(context,LatLng(14.5995,120.9842))}
  assertFalse("Address provider and Android fallback unavailable",address.isNullOrBlank())
 }
}
