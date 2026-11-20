package com.tanaw.app.feature.workspace.customer.rules

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CustomerNoShowRulesTest {

    private val baseArrival = Instant.parse("2026-11-24T02:00:00Z") // 10:00 AM PHT

    private fun waitingShipment(status: String = "assigned") = buildJsonObject {
        put("id", "shp-1")
        put("tracking_number", "SHP-NE-8042")
        put("status", status)
        put("pickup_address", "Cabanatuan City Warehouse, Nueva Ecija")
    }

    private fun waitingDelivery(
        status: String = "assigned",
        arrivedAt: Instant = baseArrival,
        graceEndsAt: Instant = baseArrival.plusSeconds(15 * 60),
        autoCancelAt: Instant = baseArrival.plusSeconds(30 * 60),
        customerShowedAt: String = "",
        customerOnWayAt: String = ""
    ) = buildJsonObject {
        put("id", "del-1")
        put("shipment_id", "shp-1")
        put("status", status)
        put("pickup_arrived_at", arrivedAt.toString())
        put("grace_ends_at", graceEndsAt.toString())
        put("auto_cancel_at", autoCancelAt.toString())
        if (customerShowedAt.isNotBlank()) put("customer_showed_at", customerShowedAt)
        if (customerOnWayAt.isNotBlank()) put("customer_on_way_at", customerOnWayAt)
    }

    @Test
    fun evaluatePickupWait_distinguishesWithinGrace_GraceEnded_AtRisk_andExpired() {
        val shipment = waitingShipment()
        val delivery = waitingDelivery()

        // 10 min after arrival => WITHIN_GRACE
        val within = CustomerNoShowRules.evaluatePickupWait(
            shipment, delivery, baseArrival.plusSeconds(10 * 60)
        )
        assertEquals(PickupWaitPhase.WITHIN_GRACE, within.phase)
        assertFalse(within.isUrgentBannerVisible)

        // 15m 09s after arrival => GRACE_ENDED (00:09 over grace)
        val graceEnded = CustomerNoShowRules.evaluatePickupWait(
            shipment, delivery, baseArrival.plusSeconds(15 * 60 + 9)
        )
        assertEquals(PickupWaitPhase.GRACE_ENDED, graceEnded.phase)
        assertTrue(graceEnded.isUrgentBannerVisible)
        assertEquals(9L, graceEnded.elapsedOverGraceSeconds)
        assertEquals("00:09", CustomerNoShowRules.formatMmSs(graceEnded.elapsedOverGraceSeconds))

        // 25m 04s after arrival => AT_RISK (04:56 remaining before autoCancelAt)
        val atRisk = CustomerNoShowRules.evaluatePickupWait(
            shipment, delivery, baseArrival.plusSeconds(25 * 60 + 4)
        )
        assertEquals(PickupWaitPhase.AT_RISK, atRisk.phase)
        assertTrue(atRisk.isUrgentBannerVisible)
        assertEquals(296L, atRisk.remainingBeforeCancelSeconds)
        assertEquals("04:56", CustomerNoShowRules.formatMmSs(atRisk.remainingBeforeCancelSeconds))

        // 30m 05s after arrival => EXPIRED_WAITING_CONFIRMATION
        val expired = CustomerNoShowRules.evaluatePickupWait(
            shipment, delivery, baseArrival.plusSeconds(30 * 60 + 5)
        )
        assertEquals(PickupWaitPhase.EXPIRED_WAITING_CONFIRMATION, expired.phase)
        assertEquals(0L, expired.remainingBeforeCancelSeconds)
    }

    @Test
    fun evaluatePickupWait_returnsNoneOnceCustomerShowedOrCancelled() {
        val showed = CustomerNoShowRules.evaluatePickupWait(
            waitingShipment(),
            waitingDelivery(customerShowedAt = baseArrival.plusSeconds(600).toString()),
            baseArrival.plusSeconds(16 * 60)
        )
        assertEquals(PickupWaitPhase.NONE, showed.phase)

        val cancelled = CustomerNoShowRules.evaluatePickupWait(
            waitingShipment(status = "cancelled"),
            waitingDelivery(status = "cancelled"),
            baseArrival.plusSeconds(26 * 60)
        )
        assertEquals(PickupWaitPhase.NONE, cancelled.phase)
    }

    @Test
    fun evaluateStanding_handlesStrike1Cooldown_Strike2FinalWarning_andStrike3Suspension() {
        val now = Instant.parse("2026-11-24T04:00:00Z")

        val strike1Data = buildJsonObject {
            put("customer", buildJsonObject {
                put("no_show_strike_count", 1)
                put("standing_status", "active")
                put("booking_cooldown_until", now.plusSeconds(48 * 3600).toString())
            })
            put("customer_strikes", buildJsonArray {
                add(buildJsonObject {
                    put("id", "st-1")
                    put("strike_number", 1)
                    put("shipment_id", "shp-1")
                    put("confirmed_at", "2026-11-24T02:30:00Z")
                })
            })
        }
        val standing1 = CustomerNoShowRules.evaluateStanding(strike1Data, now)
        assertEquals(CustomerStandingLevel.COOLDOWN, standing1.level)
        assertTrue(standing1.isInCooldown)
        assertTrue(standing1.isBookingBlocked)
        assertFalse(standing1.isSuspended)
        assertEquals(48, standing1.cooldownHoursHint)
        assertEquals("48h 0m remaining", standing1.cooldownRemainingText)

        // Cooldown expired => GOOD standing
        val afterCooldown = CustomerNoShowRules.evaluateStanding(strike1Data, now.plusSeconds(49 * 3600))
        assertEquals(CustomerStandingLevel.GOOD, afterCooldown.level)
        assertFalse(afterCooldown.isBookingBlocked)

        // Strike 3 => SUSPENDED with strike history
        val strike3Data = buildJsonObject {
            put("customer", buildJsonObject {
                put("no_show_strike_count", 3)
                put("standing_status", "suspended")
                put("suspended_at", "2026-11-24T02:30:00Z")
                put("suspension_reason", "3 confirmed no-shows")
            })
            put("customer_strikes", buildJsonArray {
                add(buildJsonObject { put("id", "st-1"); put("strike_number", 1); put("shipment_id", "s1"); put("confirmed_at", "2026-10-10T02:30:00Z") })
                add(buildJsonObject { put("id", "st-2"); put("strike_number", 2); put("shipment_id", "s2"); put("confirmed_at", "2026-11-01T02:30:00Z") })
                add(buildJsonObject { put("id", "st-3"); put("strike_number", 3); put("shipment_id", "s3"); put("confirmed_at", "2026-11-24T02:30:00Z") })
            })
        }
        val standing3 = CustomerNoShowRules.evaluateStanding(strike3Data, now)
        assertEquals(CustomerStandingLevel.SUSPENDED, standing3.level)
        assertTrue(standing3.isSuspended)
        assertEquals(3, standing3.strikes.size)
        assertEquals("3 confirmed no-shows", standing3.suspensionReason)
        assertEquals("Nov 24, 2026", standing3.formattedSuspendedDate)
    }

    @Test
    fun evaluateStanding_reinstatedCustomerKeepsStrikeCountWithoutSuspensionGate() {
        val data = buildJsonObject {
            put("customer", buildJsonObject {
                put("no_show_strike_count", 3)
                put("standing_status", "reinstated")
                put("reinstated_at", "2026-11-28T02:00:00Z")
            })
        }
        val standing = CustomerNoShowRules.evaluateStanding(data, Instant.parse("2026-11-29T02:00:00Z"))
        assertEquals(CustomerStandingLevel.GOOD, standing.level)
        assertTrue(standing.isReinstated)
        assertFalse(standing.isSuspended)
        assertEquals(3, standing.strikeCount)
    }

    @Test
    fun isNoShowCancellation_detectsByTypeOrReasonOrStrikeRecord() {
        val byType = buildJsonObject {
            put("id", "shp-a")
            put("status", "cancelled")
            put("cancellation_type", "no_show")
        }
        assertTrue(CustomerNoShowRules.isNoShowCancellation(byType, null))

        val normalCancel = buildJsonObject {
            put("id", "shp-b")
            put("status", "cancelled")
            put("cancellation_type", "customer")
            put("cancelled_reason", "Change of schedule")
        }
        assertFalse(CustomerNoShowRules.isNoShowCancellation(normalCancel, null))

        val byReason = buildJsonObject {
            put("id", "shp-c")
            put("status", "cancelled")
            put("cancelled_reason", "Confirmed customer no-show after pickup wait period")
        }
        assertTrue(CustomerNoShowRules.isNoShowCancellation(byReason, null))
    }

    @Test
    fun buildNoShowTimeline_buildsFourOrderedStepsWithPhtTimes() {
        val shipment = buildJsonObject {
            put("id", "shp-1")
            put("status", "cancelled")
            put("pickup_address", "Cabanatuan City Warehouse, Nueva Ecija")
            put("cancelled_at", "2026-11-24T02:30:00Z")
        }
        val delivery = waitingDelivery(status = "cancelled")
        val steps = CustomerNoShowRules.buildNoShowTimeline(shipment, delivery)

        assertEquals(4, steps.size)
        assertTrue(steps[0].title.contains("10:00 AM"))
        assertTrue(steps[1].title.contains("10:15 AM"))
        assertEquals(NoShowTimelineNodeTone.WARNING, steps[2].tone)
        assertTrue(steps[3].title.contains("10:30 AM"))
        assertEquals(NoShowTimelineNodeTone.DANGER, steps[3].tone)
    }

    @Test
    fun validateAppealReason_enforcesMinAndMaxBounds() {
        assertFalse(CustomerNoShowRules.validateAppealReason("too short"))
        assertTrue(CustomerNoShowRules.validateAppealReason("Our warehouse gate was blocked by roadwork."))
        assertFalse(CustomerNoShowRules.validateAppealReason("a".repeat(CustomerNoShowRules.MAX_APPEAL_REASON_LENGTH + 1)))
    }

    @Test
    fun optimisticHelpers_updateDeliveryAndAppealsImmediately() {
        val initial = buildJsonObject {
            put("deliveries", buildJsonArray { add(waitingDelivery()) })
            put("customer_appeals", buildJsonArray {})
        }
        val marked = CustomerNoShowRules.optimisticMarkCustomerOnWay(initial, "del-1", "2026-11-24T02:18:00Z")
        val wait = CustomerNoShowRules.evaluatePickupWait(
            waitingShipment(),
            marked?.get("deliveries")?.let { (it as kotlinx.serialization.json.JsonArray).first() as kotlinx.serialization.json.JsonObject },
            baseArrival.plusSeconds(18 * 60)
        )
        assertTrue(wait.hasNotifiedOnWay)

        val appealed = CustomerNoShowRules.optimisticRecordAppeal(initial, "app-1", "Valid appeal explanation text", "2026-11-24T04:00:00Z")
        val standing = CustomerNoShowRules.evaluateStanding(appealed)
        assertEquals("app-1", standing.latestAppeal?.id)
        assertTrue(standing.latestAppeal?.isPending == true)
    }
}
