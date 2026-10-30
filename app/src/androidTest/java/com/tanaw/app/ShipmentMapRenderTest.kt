package com.tanaw.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.DriverTheme
import com.tanaw.app.feature.workspace.ShipmentMap
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.io.File

/** Synthetic geometry and local style: native rendering, no account/provider/business writes. */
class ShipmentMapRenderTest {
    @get:Rule val compose=createComposeRule()
    private val points=listOf("Pickup" to LatLng(14.60,120.98),"Destination" to LatLng(14.65,121.01))
    private val style="""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#f0f4f8"}}]}"""
    private val road="""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[[120.98,14.60],[120.99,14.60],[120.99,14.63],[121.01,14.63],[121.01,14.65]]}}]}"""
    private fun waitForLine() {compose.waitUntil(30000){compose.onAllNodesWithText("Route line displayed",substring=true).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun initialGeometryDrawsAfterAsyncStyleLoadWithoutAnotherUiChange() {
        compose.setContent {DriverTheme {Column {ShipmentMap(points,road,styleJson=style)}}}
        waitForLine()
        compose.onNodeWithText("Route line displayed · 2 saved stops").assertExists()
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var rendered:android.graphics.Bitmap?=null
        // Compose becoming idle does not mean the native GL frame has been drawn.
        // Require route-blue pixels away from both endpoint markers before capturing proof.
        val bounds=compose.onNodeWithTag("Shipment map").fetchSemanticsNode().boundsInRoot
        compose.waitUntil(30000) {
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            var blue=0
            var redPin=0
            for(y in (bounds.top+bounds.height*.2f).toInt() until (bounds.top+bounds.height*.8f).toInt() step 2)
                for(x in (bounds.left+bounds.width*.2f).toInt() until (bounds.left+bounds.width*.8f).toInt() step 2) {
                    val color=bitmap.getPixel(x.coerceIn(0,bitmap.width-1),y.coerceIn(0,bitmap.height-1))
                    if(android.graphics.Color.blue(color)>180 && android.graphics.Color.red(color)<80 && android.graphics.Color.green(color) in 50..150)blue++
                }
            for(y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height) step 2)
                for(x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width) step 2) {
                    val color=bitmap.getPixel(x,y)
                    if(android.graphics.Color.red(color)>180 && android.graphics.Color.green(color)<100 && android.graphics.Color.blue(color)<100)redPin++
                }
            if(blue>30 && redPin>20){rendered=bitmap;true}else{bitmap.recycle();false}
        }
        File(instrumentation.targetContext.getExternalFilesDir(null),"map-render-synthetic.png").outputStream().use{requireNotNull(rendered).compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        rendered?.recycle()
    }
    @Test fun geometryArrivingLaterAndRemovalUpdateTheExistingNativeMap() {
        var geometry by mutableStateOf<String?>(null)
        compose.setContent {DriverTheme {Column {ShipmentMap(points,geometry,styleJson=style)}}}
        compose.waitUntil(30000){compose.onAllNodesWithText("Saved stops displayed",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {geometry=road}
        waitForLine()
        compose.runOnIdle {geometry=null}
        compose.waitUntil(5000){compose.onAllNodesWithText("Saved stops displayed · no road route loaded").fetchSemanticsNodes().isNotEmpty()}
    }
}
