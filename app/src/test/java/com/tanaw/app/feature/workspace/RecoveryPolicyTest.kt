package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    @Test fun challengeContainsOnlyBoundedContextAndExpires() {
        val data=RecoveryPolicy.challenge("recover_code","+639123456789",1000)
        assertEquals(setOf("page","target","created"),data.keys)
        assertTrue(RecoveryPolicy.valid(data,1001))
        assertFalse(RecoveryPolicy.valid(data,999))
        assertFalse(RecoveryPolicy.valid(data,1001+RecoveryPolicy.CHALLENGE_AGE))
        assertFalse(RecoveryPolicy.valid(buildJsonObject {put("page","login")},1001))
    }
    @Test fun cacheRejectsOtherAccountAndCustomerAndDropsUnneededSections() {
        fun data(role:String)=buildJsonObject {
            put("profile",buildJsonObject {put("id","driver-a");put("role",role)})
            put("shipments",buildJsonArray {})
            put("access_token","must-not-persist");put("proof","private-image");put("expenses",buildJsonArray {})
        }
        assertNull(RecoveryPolicy.workspace(data("driver"),"driver-b"))
        assertNull(RecoveryPolicy.workspace(data("customer"),"driver-a"))
        assertEquals(setOf("profile","shipments","expenses"),RecoveryPolicy.workspace(data("driver"),"driver-a")!!.keys)
    }
    @Test fun smsUsesIndependentSavedRowsAndDoesNotInventSuccessOnReadFailure() {
        val data=buildJsonObject {put("sms_preferences",buildJsonArray {
            add(buildJsonObject {put("type","shipment_status");put("enabled",true)})
            add(buildJsonObject {put("type","delay");put("enabled",false)})
        })}
        assertEquals("Enabled",RecoveryPolicy.sms(data,"shipment_status"))
        assertEquals("Disabled",RecoveryPolicy.sms(data,"delay"))
        assertTrue(RecoveryPolicy.sms(buildJsonObject {},"delay").contains("no opt-in"))
        assertTrue(RecoveryPolicy.sms(JsonObject(data+mapOf("sms_error" to JsonPrimitive("failed"))),"delay").startsWith("Unavailable"))
    }
}
