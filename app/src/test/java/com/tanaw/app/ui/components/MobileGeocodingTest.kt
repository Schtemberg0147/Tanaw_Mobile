package com.tanaw.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class MobileGeocodingTest {

    @Test
    fun readBoundedUtf8_returnsTextWhenWithinLimit() {
        val text = """{"features":[{"place_name":"Manila, Philippines","center":[120.9842,14.5995]}]}"""
        val result = MobileGeocoding.readBoundedUtf8(
            ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
            maxBytes = 1024
        )
        assertEquals(text, result)
    }

    @Test
    fun readBoundedUtf8_rejectsPayloadExceedingLimit() {
        val oversized = ByteArray(2049) { 'a'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            MobileGeocoding.readBoundedUtf8(
                ByteArrayInputStream(oversized),
                maxBytes = 2048
            )
        }
    }
}
