package com.tanaw.app.feature.workspace.customer.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerAvatarRulesTest {

    @Test
    fun `isSupportedMimeType accepts jpeg png and webp case insensitively`() {
        assertTrue(CustomerAvatarRules.isSupportedMimeType("image/jpeg"))
        assertTrue(CustomerAvatarRules.isSupportedMimeType("IMAGE/PNG"))
        assertTrue(CustomerAvatarRules.isSupportedMimeType(" image/webp "))
        assertFalse(CustomerAvatarRules.isSupportedMimeType("application/pdf"))
        assertFalse(CustomerAvatarRules.isSupportedMimeType(""))
        assertFalse(CustomerAvatarRules.isSupportedMimeType(null))
    }

    @Test
    fun `isValidSourceSize enforces non-empty and max 20MB limit`() {
        assertFalse(CustomerAvatarRules.isValidSourceSize(0))
        assertFalse(CustomerAvatarRules.isValidSourceSize(-10))
        assertTrue(CustomerAvatarRules.isValidSourceSize(1024))
        assertTrue(CustomerAvatarRules.isValidSourceSize(CustomerAvatarRules.MAX_SOURCE_BYTES))
        assertFalse(CustomerAvatarRules.isValidSourceSize(CustomerAvatarRules.MAX_SOURCE_BYTES + 1))
    }

    @Test
    fun `centerSquareBounds computes centered crop for landscape portrait and square`() {
        // Landscape 1000 x 600 -> crop size 600 at (200, 0)
        assertEquals(Triple(200, 0, 600), CustomerAvatarRules.centerSquareBounds(1000, 600))
        // Portrait 600 x 1000 -> crop size 600 at (0, 200)
        assertEquals(Triple(0, 200, 600), CustomerAvatarRules.centerSquareBounds(600, 1000))
        // Square 512 x 512 -> crop size 512 at (0, 0)
        assertEquals(Triple(0, 0, 512), CustomerAvatarRules.centerSquareBounds(512, 512))
    }

    @Test
    fun `readSourceWithinLimit reads valid stream and rejects empty or oversized stream`() {
        val valid = ByteArray(1024) { 0x42 }
        val read = CustomerAvatarRules.readSourceWithinLimit(
            java.io.ByteArrayInputStream(valid),
            maxBytes = 2048
        )
        assertEquals(1024, read.size)

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CustomerAvatarRules.readSourceWithinLimit(
                java.io.ByteArrayInputStream(ByteArray(0)),
                maxBytes = 2048
            )
        }

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CustomerAvatarRules.readSourceWithinLimit(
                java.io.ByteArrayInputStream(ByteArray(2049)),
                maxBytes = 2048
            )
        }
    }
}
