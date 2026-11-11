package com.tanaw.app.feature.workspace

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class EvidenceRulesTest {
    @Test fun photoMimeTypesAllowJpgPngAndWebpOnly() {
        listOf("image/jpeg", "image/png", "image/webp").forEach { mime ->
            EvidenceRules.validatePhotoMime(mime)
        }
        listOf("image/gif", "application/pdf", "image/bmp", null, "", "text/plain").forEach { mime ->
            assertTrue("Expected rejection for $mime", runCatching { EvidenceRules.validatePhotoMime(mime) }.isFailure)
        }
    }

    @Test fun receiptMimeTypesAllowJpgAndPngOnly() {
        listOf("image/jpeg", "image/png").forEach { mime ->
            EvidenceRules.validateReceiptMime(mime)
        }
        listOf("image/webp", "image/gif", "application/pdf", null, "").forEach { mime ->
            assertTrue("Expected rejection for $mime", runCatching { EvidenceRules.validateReceiptMime(mime) }.isFailure)
        }
    }

    @Test fun readStreamWithinLimitReadsExactBytes() {
        val original = byteArrayOf(10, 20, 30, 40, 50)
        val stream = ByteArrayInputStream(original)
        val result = EvidenceRules.readStreamWithinLimit(stream, 1024, "Too large")
        assertArrayEquals(original, result)
    }

    @Test fun readStreamWithinLimitRejectsEmptyStream() {
        val emptyStream = ByteArrayInputStream(byteArrayOf())
        val error = runCatching {
            EvidenceRules.readStreamWithinLimit(emptyStream, 1024, "Too large")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("File is empty.", error?.message)
    }

    @Test fun readStreamWithinLimitRejectsOversizeStreamPromptly() {
        val limit = 100
        val oversizedData = ByteArray(limit + 50) { 1 }
        val stream = ByteArrayInputStream(oversizedData)
        val error = runCatching {
            EvidenceRules.readStreamWithinLimit(stream, limit, "File exceeds 100 bytes.")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("File exceeds 100 bytes.", error?.message)
    }
}
