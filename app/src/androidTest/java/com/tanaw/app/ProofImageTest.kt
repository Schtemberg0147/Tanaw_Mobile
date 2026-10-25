package com.tanaw.app

import android.graphics.Bitmap
import com.tanaw.app.feature.workspace.ProofImage
import java.io.ByteArrayOutputStream
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
}
