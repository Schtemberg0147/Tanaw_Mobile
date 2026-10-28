package com.tanaw.app.feature.workspace
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class ConversationRulesTest {
    @Test fun textLimitCountsUnicodeCodePointsAndRejectsBlank() {
        assertFalse(ConversationRules.valid("  "));assertFalse(ConversationRules.valid("x".repeat(2001)))
        assertTrue(ConversationRules.valid("🚚".repeat(2000)))
    }
    @Test fun unreadMatchesExactDelivery() {
        val rows=listOf(buildJsonObject{put("delivery_id","other");put("unread",8)},buildJsonObject{put("delivery_id","selected");put("unread",3)})
        assertEquals(3,ConversationRules.unread(rows,"selected"));assertEquals(0,ConversationRules.unread(rows,"missing"))
    }
}
