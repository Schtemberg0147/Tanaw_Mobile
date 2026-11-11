package com.tanaw.app.feature.workspace
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class ConversationRulesTest {
    @Test fun messageTimesSupportDatabaseOffsetsAndHonestMissingValues() {
        assertEquals("Oct 28 · 8:00 PM PHT",ConversationRules.timestamp("2026-10-28T12:00:00+00:00"))
        assertEquals("Oct 28 · 8:00 PM PHT",ConversationRules.timestamp("2026-10-28 12:00:00Z"))
        assertEquals("Time unavailable",ConversationRules.timestamp(""))
    }
    @Test fun textLimitCountsUnicodeCodePointsAndRejectsBlank() {
        assertFalse(ConversationRules.valid("  "));assertFalse(ConversationRules.valid("x".repeat(2001)))
        assertTrue(ConversationRules.valid("🚚".repeat(2000)))
    }
    @Test fun unreadMatchesExactDelivery() {
        val rows=listOf(buildJsonObject{put("delivery_id","other");put("unread",8)},buildJsonObject{put("delivery_id","selected");put("unread",3)})
        assertEquals(3,ConversationRules.unread(rows,"selected"));assertEquals(0,ConversationRules.unread(rows,"missing"))
    }
}
