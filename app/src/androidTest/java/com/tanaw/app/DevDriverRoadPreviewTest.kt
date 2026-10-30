package com.tanaw.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in existing Dev session/pins only. No account, fixture, route-plan or trip mutation. */
class DevDriverRoadPreviewTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun assignedDriverReceivesProviderRoadGeometryForExistingSavedPins() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowDriverPreviewAcceptance")=="true" && BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
        val client=compose.activity.supabase
        compose.waitUntil(60000){client.auth.currentUserOrNull()!=null}
        val workspace=runBlocking {client.postgrest.rpc("mobile_workspace").decodeAs<JsonObject>()}
        assertEquals("driver",workspace.obj("profile").value("role"))
        val deliveries=workspace.rows("driver_deliveries").ifEmpty{workspace.rows("deliveries")}
        val target=InstrumentationRegistry.getArguments().getString("targetDeliveryId")
        val assignment=deliveries.firstOrNull {d->(target==null || d.value("id")==target) && d.value("status") in listOf("assigned","loading","in_transit") && workspace.rows("shipments").any{s->s.value("id")==d.value("shipment_id") && driverStopPins(s).size==2}}
        assertNotNull("An existing assigned Dev trip with saved pins is required",assignment)
        val shipment=workspace.rows("shipments").first{it.value("id")==assignment!!.value("shipment_id")}
        val pins=driverStopPins(shipment)
        val vm=ViewModelProvider(compose.activity)[MobileWorkspaceViewModel::class.java]
        val result=runBlocking {vm.previewDriverRoute(assignment!!.value("id"),pins[0].second,pins[1].second)}
        assertTrue("Provider distance must be positive",result.distanceKm>0)
        assertTrue("Provider geometry must contain a line",Json.parseToJsonElement(result.geometry).jsonObject["features"]!!.jsonArray.isNotEmpty())
    }
}
