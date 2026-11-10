package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverDatabaseDataTest {
    @Test fun settingsFailureIsIndependentAndRetryClearsIt()=runTest {
        val failed=DriverDatabaseData.load(readSettings={error("denied")}) {table,_->if(table=="drivers")roster else buildJsonArray{}}
        assertEquals("error",failed.obj("driver_sections").value("driver_settings"))
        assertEquals("ready",failed.obj("driver_sections").value("driver_roster"))
        assertTrue(failed.containsKey("compliance"))
        assertNull(DriverProfileRules.settings(failed))
        val fresh=DriverDatabaseData.load(readSettings={JsonArray(emptyList())}) {table,_->if(table=="drivers")roster else buildJsonArray{}}
        assertEquals("ready",fresh.obj("driver_sections").value("driver_settings"))
        assertFalse(fresh.containsKey("driver_read_error"))
    }
    private val roster=buildJsonArray {add(buildJsonObject {put("id","own-driver")})}
    @Test fun failedComplianceDoesNotHideTripsOrExpenses()=runTest {
        val calls=mutableListOf<Pair<String,String?>>()
        val result=DriverDatabaseData.load {table,id->
            calls.add(table to id)
            when(table) {"drivers"->roster;"compliance_documents"->error("unavailable");else->buildJsonArray{}}
        }
        assertTrue(result.containsKey("driver_deliveries"))
        assertTrue(result.containsKey("expenses"))
        assertFalse(result.containsKey("compliance"))
        assertEquals("error",result.obj("driver_sections").value("compliance"))
        assertEquals("empty",result.obj("driver_sections").value("expenses"))
        assertTrue(result["driver_read_error"].toString().contains("compliance"))
        assertTrue(calls.drop(1).all {it.second=="own-driver"})
    }
    @Test fun missingRosterDoesNotQueryUnscopedDriverTables()=runTest {
        val calls=mutableListOf<String>()
        val result=DriverDatabaseData.load {table,_->calls.add(table);buildJsonArray{}}
        assertEquals(listOf("drivers"),calls)
        assertTrue(result["driver_read_error"].toString().contains("roster link"))
        assertEquals("unavailable",result.obj("driver_sections").value("compliance"))
    }
    @Test fun retryReturnsFreshDataAndClearsPreviousFailure()=runTest {
        val failed=DriverDatabaseData.load {_,_->error("offline")}
        assertTrue(failed.containsKey("driver_read_error"))
        val restored=DriverDatabaseData.load {table,_->if(table=="drivers")roster else buildJsonArray{}}
        assertFalse(restored.containsKey("driver_read_error"))
        assertTrue(restored.containsKey("expenses"))
    }
    @Test fun cancellationIsNeverReportedAsMissingData()=runTest {
        try {DriverDatabaseData.load {_,_->throw CancellationException("account changed")};fail("Cancellation swallowed")}
        catch(_:CancellationException) { }
    }
    @Test fun multipleFailedSectionsPreserveDeterministicErrorOrder()=runTest {
        val result=DriverDatabaseData.load(readSettings={error("denied")}) {table,_->
            when(table) {
                "drivers"->roster
                "compliance_documents"->error("compliance down")
                "delivery_expenses"->error("expenses down")
                "deliveries"->buildJsonArray{add(buildJsonObject{put("id","del-1")})}
                else->buildJsonArray{}
            }
        }
        assertEquals("error",result.obj("driver_sections").value("driver_settings"))
        assertEquals("error",result.obj("driver_sections").value("compliance"))
        assertEquals("error",result.obj("driver_sections").value("expenses"))
        assertEquals("ready",result.obj("driver_sections").value("driver_deliveries"))
        assertEquals("empty",result.obj("driver_sections").value("driver_trucks"))
        val err=result.value("driver_read_error")
        assertTrue(err.contains("profile settings, compliance, expenses"))
    }

    @Test fun stageOneAndStageTwoSectionsExecuteConcurrently()=runTest {
        val result=DriverDatabaseData.load(readSettings={
            delay(200)
            JsonArray(emptyList())
        }) {table,_->
            when(table) {
                "drivers" -> { delay(200); roster }
                "trucks", "compliance_documents", "deliveries", "delivery_expenses" -> {
                    delay(300)
                    buildJsonArray {}
                }
                else -> buildJsonArray {}
            }
        }
        assertEquals(500L, testScheduler.currentTime)
        assertEquals("ready", result.obj("driver_sections").value("driver_settings"))
        assertEquals("ready", result.obj("driver_sections").value("driver_roster"))
        assertEquals("empty", result.obj("driver_sections").value("driver_trucks"))
        assertEquals("empty", result.obj("driver_sections").value("compliance"))
        assertEquals("empty", result.obj("driver_sections").value("driver_deliveries"))
        assertEquals("empty", result.obj("driver_sections").value("expenses"))
    }

    @Test fun outOfOrderParallelFailuresStillPreserveDeterministicErrorMessage()=runTest {
        val result=DriverDatabaseData.load(readSettings={
            delay(150)
            error("settings error")
        }) {table,_->
            when(table) {
                "drivers" -> roster
                "delivery_expenses" -> { delay(10); error("expenses error") }
                "compliance_documents" -> { delay(50); error("compliance error") }
                "trucks" -> { delay(100); error("trucks error") }
                "deliveries" -> buildJsonArray { add(buildJsonObject { put("id", "del-1") }) }
                else -> buildJsonArray {}
            }
        }
        val err=result.value("driver_read_error")
        assertEquals("Could not load profile settings, assigned vehicle, compliance, expenses. Refresh to retry.", err)
    }

    @Test fun blankDriverIdInRosterMarksDependentSectionsUnavailableWithoutQueryingThem()=runTest {
        val calls=mutableListOf<String>()
        val blankIdRoster=buildJsonArray { add(buildJsonObject { put("id", "") }) }
        val result=DriverDatabaseData.load {table,_->
            calls.add(table)
            if(table=="drivers") blankIdRoster else buildJsonArray {}
        }
        assertEquals(listOf("drivers"), calls)
        assertEquals("unavailable", result.obj("driver_sections").value("driver_trucks"))
        assertEquals("unavailable", result.obj("driver_sections").value("compliance"))
        assertEquals("unavailable", result.obj("driver_sections").value("driver_deliveries"))
        assertEquals("unavailable", result.obj("driver_sections").value("expenses"))
        assertTrue(result.value("driver_read_error").contains("driver roster link (contact the office)"))
    }

    @Test fun cancellationInSettingsOrStageTwoSectionPropagatesImmediately()=runTest {
        try {
            DriverDatabaseData.load(readSettings={ throw CancellationException("settings cancelled") }) {table,_->
                if(table=="drivers") roster else buildJsonArray {}
            }
            fail("CancellationException in settings was swallowed")
        } catch(_:CancellationException) {}

        try {
            DriverDatabaseData.load {table,_->
                when(table) {
                    "drivers" -> roster
                    "deliveries" -> throw CancellationException("deliveries cancelled")
                    else -> buildJsonArray {}
                }
            }
            fail("CancellationException in Stage 2 deliveries was swallowed")
        } catch(_:CancellationException) {}
    }

    @Test fun retainDetailsForReloadPreservesSameShipmentAndClearsDifferentShipment() {
        val current=buildJsonObject {
            put("shipment_id", "ship-1")
            put("tracking_number", "TRK-001")
        }
        assertSame(current, DriverDatabaseData.retainDetailsForReload(current, "ship-1"))
        assertNull(DriverDatabaseData.retainDetailsForReload(current, "ship-2"))
        assertNull(DriverDatabaseData.retainDetailsForReload(null, "ship-1"))
        assertNull(DriverDatabaseData.retainDetailsForReload(current, ""))
    }

    @Test fun driverDetailsRunAllFourCallsInParallelAndEmitFastDbSectionsBeforeSlowRouting()=runTest {
        var fastDetailsTime: Long? = null
        var fastDetailsPayload: JsonObject? = null
        val priorRouting=buildJsonObject { put("route_verified", true); put("leg", "pickup") }
        val previous=buildJsonObject {
            put("shipment_id", "ship-1")
            put("routing", priorRouting)
        }

        val result=DriverDatabaseData.loadDetails(
            shipmentId = "ship-1",
            role = "driver",
            deliveryId = "del-1",
            previousDetails = previous,
            onFastDetailsReady = { interim ->
                fastDetailsTime = testScheduler.currentTime
                fastDetailsPayload = interim
            },
            readShipmentDetails = {
                delay(100)
                buildJsonObject { put("shipment_id", it); put("tracking_number", "TRK-001") }
            },
            readFuelHistory = {
                delay(100)
                buildJsonArray { add(buildJsonObject { put("id", "fuel-1") }) }
            },
            readEvidence = {
                delay(100)
                buildJsonObject { put("photos", buildJsonArray {}) }
            },
            readRouteStatus = {
                delay(5000)
                buildJsonObject { put("route_verified", true); put("leg", "destination") }
            }
        )

        assertEquals(100L, fastDetailsTime)
        assertNotNull(fastDetailsPayload)
        assertEquals("TRK-001", fastDetailsPayload!!.value("tracking_number"))
        assertEquals(1, fastDetailsPayload!!.rows("fuel_logs").size)
        assertEquals(priorRouting, fastDetailsPayload!!.obj("routing"))

        assertEquals(5000L, testScheduler.currentTime)
        assertEquals("destination", result.obj("routing").value("leg"))
    }

    @Test fun driverDetailsIsolateFuelEvidenceAndRoutingFailuresIndependently()=runTest {
        val result=DriverDatabaseData.loadDetails(
            shipmentId = "ship-1",
            role = "driver",
            deliveryId = "del-1",
            readShipmentDetails = { buildJsonObject { put("shipment_id", it) } },
            readFuelHistory = { error("fuel service down") },
            readEvidence = { error("evidence service down") },
            readRouteStatus = { error("routing network timeout") }
        )
        assertEquals("ship-1", result.value("shipment_id"))
        assertTrue(result.value("fuel_error").contains("Could not load fuel records"))
        assertTrue(result.value("evidence_error").contains("Could not load incident photos"))
        assertTrue(result.obj("routing").value("reason").contains("Truck routing provider unavailable"))
    }

    @Test fun customerDetailsRunShipmentDetailsAndActiveRoutingInParallel()=runTest {
        val result=DriverDatabaseData.loadDetails(
            shipmentId = "ship-cust-1",
            role = "customer",
            deliveryId = "del-cust-1",
            readShipmentDetails = {
                delay(200)
                buildJsonObject { put("shipment_id", it); put("status", "in_transit") }
            },
            readRouteStatus = {
                delay(400)
                buildJsonObject { put("route_verified", true); put("eta", "15 mins") }
            }
        )
        assertEquals(400L, testScheduler.currentTime)
        assertEquals("in_transit", result.value("status"))
        assertEquals("15 mins", result.obj("routing").value("eta"))
    }

    @Test fun customerDetailsSkipsRoutingWhenDeliveryIdIsBlank()=runTest {
        var routeStatusQueried = false
        val result=DriverDatabaseData.loadDetails(
            shipmentId = "ship-cust-2",
            role = "customer",
            deliveryId = null,
            readShipmentDetails = {
                delay(100)
                buildJsonObject { put("shipment_id", it); put("status", "pending") }
            },
            readRouteStatus = {
                routeStatusQueried = true
                buildJsonObject { put("eta", "none") }
            }
        )
        assertEquals(100L, testScheduler.currentTime)
        assertFalse(routeStatusQueried)
        assertFalse(result.containsKey("routing"))
        assertEquals("pending", result.value("status"))
    }
}
