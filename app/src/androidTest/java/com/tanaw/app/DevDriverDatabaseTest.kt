package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in read-only acceptance using the driver's existing session; no password or trip writes. */
class DevDriverDatabaseTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun driverScreensShowOwnDatabaseRecords() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowDriverAcceptance")=="true" &&
            BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
        val client=compose.activity.supabase
        compose.waitUntil(60000) {client.auth.currentUserOrNull()!=null}
        val data=runBlocking {client.postgrest.rpc("mobile_workspace").decodeAs<JsonObject>()}
        assertEquals("driver",data.obj("profile").value("role"))
        val uid=client.auth.currentUserOrNull()!!.id
        val driver=runBlocking {client.from("drivers").select {filter {eq("user_id",uid)}}.decodeAs<JsonArray>()}.single().jsonObject
        assertEquals(uid,driver.value("user_id"))
        val sections=runBlocking {DriverDatabaseData.load {table,id->
            client.from(table).select {when(table) {
                "drivers"->filter {eq("user_id",uid)}
                "trucks"->Unit
                else->filter {eq("driver_id",requireNotNull(id))}
            }}.decodeAs<JsonArray>()
        }}
        assertFalse("Driver database section failed",sections.containsKey("driver_read_error"))
        sections.rows("driver_deliveries").forEach {assertEquals(driver.value("id"),it.value("driver_id"))}
        sections.rows("compliance").forEach {assertEquals(driver.value("id"),it.value("driver_id"))}
        sections.rows("expenses").forEach {assertEquals(driver.value("id"),it.value("driver_id"))}
        val history=runBlocking {client.postgrest.rpc("mobile_driver_history",buildJsonObject {
            put("p_period","All records");put("p_offset",0);put("p_limit",50)
        }).decodeAs<JsonObject>()}
        assertTrue(history.value("revision").isNotBlank())
        assertTrue(history.value("total").toLong()>=history.rows("shipments").size)
        history.rows("driver_deliveries").forEach {assertEquals(driver.value("id"),it.value("driver_id"))}
        assertEquals(history.rows("shipments").map {it.value("id")}.toSet(),history.rows("driver_deliveries").map {it.value("shipment_id")}.toSet())
        history.rows("shipments").firstOrNull()?.let {shipment->
            val fuel=runBlocking {client.postgrest.rpc("mobile_driver_fuel_history",buildJsonObject {put("p_shipment",shipment.value("id"))}).decodeAs<JsonArray>()}
            fuel.forEach {assertEquals(driver.value("id"),it.jsonObject.value("driver_id"))}
        }
        compose.waitUntil(60000) {compose.onAllNodesWithText("PROFILE").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("PROFILE").performClick()
        compose.onNodeWithText(data.obj("profile").value("full_name")).assertExists()
        driver.value("license_number").takeIf(String::isNotBlank)?.let {compose.onNodeWithText(it).performScrollTo().assertExists()}
        compose.onNodeWithText("TRIPS").performClick()
        compose.onNodeWithText("My Trips").assertIsDisplayed()
        compose.onNodeWithText("HOME").performClick()
    }
}
