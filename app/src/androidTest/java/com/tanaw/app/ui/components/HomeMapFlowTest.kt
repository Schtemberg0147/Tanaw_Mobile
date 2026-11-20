package com.tanaw.app.ui.components

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class HomeMapFlowTest {
 @Test fun actualReverseLookupFindsPublicManilaAddress() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val address=kotlinx.coroutines.runBlocking {MobileGeocoding.reverse(context,LatLng(14.5995,120.9842))}
  assertFalse("Address provider and Android fallback unavailable",address.isNullOrBlank())
 }
}
