package com.tanaw.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.DriverEvidence
import com.tanaw.app.feature.workspace.ProofImage
import com.tanaw.app.feature.workspace.SignaturePoint
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ProofImageTest {
    @Test fun malformedOrOversizeProofDoesNotDecode() {
        listOf(byteArrayOf(),byteArrayOf(1,2,3,4),ByteArray(5*1024*1024+1)).forEach { bytes ->
            try { ProofImage.decode(bytes);fail("Invalid proof decoded") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun realPngIsDecodedWithBoundedDisplayDimensions() {
        val source=Bitmap.createBitmap(2400,1200,Bitmap.Config.ARGB_8888)
        val stream=ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG,100,stream)
        source.recycle()
        val decoded=ProofImage.decode(stream.toByteArray())
        assertTrue(decoded.width in 1..1600)
        assertTrue(decoded.height in 1..1600)
        assertEquals(2.0,decoded.width.toDouble()/decoded.height,0.01)
        decoded.recycle()
    }
    @Test fun decodeAsyncProducesValidBoundedBitmap() = runBlocking {
        val source=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
        val stream=ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG,90,stream)
        source.recycle()
        val decoded=ProofImage.decodeAsync(stream.toByteArray())
        assertEquals(800,decoded.width)
        assertEquals(600,decoded.height)
        decoded.recycle()
    }
    @Test fun renderSignaturePngProducesValidPngImage() = runBlocking {
        val strokes=listOf(
            listOf(SignaturePoint(0.1f,0.2f),SignaturePoint(0.5f,0.6f)),
            listOf(SignaturePoint(0.3f,0.4f),SignaturePoint(0.7f,0.8f))
        )
        val pngBytes=ProofImage.renderSignaturePng(strokes)
        assertTrue(pngBytes.isNotEmpty())
        val decoded=ProofImage.decode(pngBytes)
        assertEquals(1000,decoded.width)
        assertEquals(400,decoded.height)
        decoded.recycle()
    }
    @Test fun processCameraPhotoCompressesFileWithinAllowedBounds() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val tempFile=File(context.cacheDir,"test-camera-${java.util.UUID.randomUUID()}.jpg")
        try {
            val source=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
            val stream=ByteArrayOutputStream()
            source.compress(Bitmap.CompressFormat.JPEG,95,stream)
            source.recycle()
            tempFile.writeBytes(stream.toByteArray())

            val compressed=ProofImage.processCameraPhoto(tempFile)
            assertTrue(compressed.size in 1..5*1024*1024)
            val decoded=ProofImage.decode(compressed)
            assertTrue(decoded.width > 0 && decoded.height > 0)
            decoded.recycle()
        } finally {
            tempFile.delete()
        }
    }
    @Test fun processCameraPhotoRejectsOversizeOrEmptyFile() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val emptyFile=File(context.cacheDir,"test-empty-${java.util.UUID.randomUUID()}.jpg").apply { createNewFile() }
        try {
            assertTrue(runCatching { ProofImage.processCameraPhoto(emptyFile) }.isFailure)
        } finally {
            emptyFile.delete()
        }
    }
    @Test fun decodeAsyncRejectsMalformedBytes() = runBlocking {
        listOf(byteArrayOf(), byteArrayOf(1, 2, 3, 4), ByteArray(5 * 1024 * 1024 + 1)).forEach { bytes ->
            assertTrue(runCatching { ProofImage.decodeAsync(bytes) }.isFailure)
        }
    }
    @Test fun renderSignaturePngWithNoStrokesReturnsPureWhite1000x400Png() = runBlocking {
        val pngBytes = ProofImage.renderSignaturePng(emptyList())
        assertTrue(pngBytes.isNotEmpty())
        val decoded = ProofImage.decode(pngBytes)
        assertEquals(1000, decoded.width)
        assertEquals(400, decoded.height)
        assertEquals(android.graphics.Color.WHITE, decoded.getPixel(500, 200))
        decoded.recycle()
    }
    @Test fun processCameraPhotoRejectsCorruptFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val corruptFile = File(context.cacheDir, "test-corrupt-${java.util.UUID.randomUUID()}.jpg")
        try {
            corruptFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            assertTrue(runCatching { ProofImage.processCameraPhoto(corruptFile) }.isFailure)
        } finally {
            corruptFile.delete()
        }
    }
    @Test fun jpegAsyncRejectsOversize() = runBlocking {
        assertTrue(runCatching { DriverEvidence.jpegAsync(ByteArray(DriverEvidence.MAX_BYTES + 1)) }.isFailure)
    }
}
