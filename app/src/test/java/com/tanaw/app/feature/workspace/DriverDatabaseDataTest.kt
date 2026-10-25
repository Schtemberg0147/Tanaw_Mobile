package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverDatabaseDataTest {
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
        assertTrue(result["driver_read_error"].toString().contains("compliance"))
        assertTrue(calls.drop(1).all {it.second=="own-driver"})
    }
    @Test fun missingRosterDoesNotQueryUnscopedDriverTables()=runTest {
        val calls=mutableListOf<String>()
        val result=DriverDatabaseData.load {table,_->calls.add(table);buildJsonArray{}}
        assertEquals(listOf("drivers"),calls)
        assertTrue(result["driver_read_error"].toString().contains("roster link"))
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
}
