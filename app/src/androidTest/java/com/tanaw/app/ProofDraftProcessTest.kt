package com.tanaw.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Run seed and restore in separate instrumentation invocations with an intervening force-stop. */
class ProofDraftProcessTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner="proof-process-fixture";private val delivery="proof-process-delivery"
    @Test fun seedForProcessDeath() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("proofProcessPhase")=="seed")
        val bitmap=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
        val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.JPEG,85,it);bitmap.recycle()}.toByteArray()
        val draft=ProofDraft(attempt="a15b84b4-0a2d-4df0-94d3-e6760ebfd9d1",recipient="Process Fixture",photo=bytes,
            strokes=listOf(listOf(SignaturePoint(.1f,.3f),SignaturePoint(.8f,.6f))),signature=bytes,frozen=true)
        assertTrue(ProofDraftStore(context).save(owner,delivery,draft))
    }
    @Test fun restoreAfterProcessDeath() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("proofProcessPhase")=="restore")
        val store=ProofDraftStore(context)
        try {
            val draft=store.read(owner,delivery)!!
            assertEquals("a15b84b4-0a2d-4df0-94d3-e6760ebfd9d1",draft.attempt);assertEquals("Process Fixture",draft.recipient)
            assertTrue(draft.frozen);assertArrayEquals(draft.photo,draft.signature)
            assertEquals(listOf(listOf(SignaturePoint(.1f,.3f),SignaturePoint(.8f,.6f))),draft.strokes)
            ProofImage.decode(draft.photo!!).recycle()
        }finally{store.clearOwner(owner)}
    }
}
