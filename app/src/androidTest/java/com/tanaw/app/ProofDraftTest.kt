package com.tanaw.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class ProofDraftTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun photo():ByteArray {
        val bitmap=Bitmap.createBitmap(30,20,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
        return ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.JPEG,85,it);bitmap.recycle()}.toByteArray()
    }
    private fun draft()=ProofDraft(recipient="Synthetic Recipient",photo=photo(),strokes=listOf(listOf(SignaturePoint(.1f,.2f),SignaturePoint(.8f,.7f))))
    @Test fun newStoreRestoresCompleteDraftAndSeparatesAccountDeliveryAndBackend() {
        val owner=UUID.randomUUID().toString();val delivery=UUID.randomUUID().toString();val original=draft()
        val store=ProofDraftStore(context)
        try {
            assertTrue(store.save(owner,delivery,original))
            val restored=ProofDraftStore(context).read(owner,delivery)!!
            assertEquals(original.attempt,restored.attempt);assertEquals(original.recipient,restored.recipient)
            assertEquals(original.strokes,restored.strokes);assertArrayEquals(original.photo,restored.photo)
            assertNull(store.read("other",delivery));assertNull(store.read(owner,"other"))
            assertNull(ProofDraftStore(context,"other-backend").read(owner,delivery))
            store.clearOwner(owner);assertNull(store.read(owner,delivery))
        }finally{store.clearOwner(owner)}
    }
    @Test fun restoredDraftSurvivesClosingFormAndSubmissionFreezesSameBytesAcrossRetry() {
        val owner=UUID.randomUUID().toString();val delivery=UUID.randomUUID().toString();val initial=draft();val store=ProofDraftStore(context)
        assertTrue(store.save(owner,delivery,initial))
        var visible by mutableStateOf(true);var calls=0;var signature:ByteArray?=null
        try {
            compose.setContent {if(visible)DriverTheme {DriverProofForm(delivery,owner,"SYNTHETIC",MobileState(),{},{visible=false}) {recipient,photo,sign,attempt,_->
                calls++;assertEquals(initial.recipient,recipient);assertArrayEquals(initial.photo,photo);assertEquals(initial.attempt,attempt)
                if(signature==null)signature=sign else assertArrayEquals(signature,sign)
            }}}
            compose.onNodeWithText("Synthetic Recipient").assertExists()
            compose.onNodeWithText("COMPLETE DELIVERY").assertIsEnabled().performClick()
            compose.onNodeWithText("RETRY / CHECK SAVED PROOF").assertIsEnabled()
            compose.onNodeWithText("Clear signature").performScrollTo().assertIsNotEnabled()
            compose.runOnIdle{visible=false};compose.waitForIdle();compose.runOnIdle{visible=true}
            compose.onNodeWithText("RETRY / CHECK SAVED PROOF").performClick()
            compose.runOnIdle{assertEquals(2,calls);assertTrue(store.read(owner,delivery)!!.frozen)}
        }finally{store.clearOwner(owner)}
    }
    @Test fun offlineDraftCannotSubmitAndDiscardRequiresExplicitConfirmation() {
        val owner=UUID.randomUUID().toString();val delivery=UUID.randomUUID().toString();val store=ProofDraftStore(context)
        store.save(owner,delivery,draft())
        try {
            compose.setContent {DriverTheme {DriverProofForm(delivery,owner,"SYNTHETIC",MobileState(offline=true),{},{}) {_,_,_,_,_->fail("offline submit")}}}
            compose.onNodeWithText("COMPLETE DELIVERY").assertIsNotEnabled()
            compose.onNodeWithText("Discard saved draft").performScrollTo().performClick()
            compose.onNodeWithText("Keep draft").performClick()
            assertNotNull(store.read(owner,delivery))
            compose.onNodeWithText("Discard saved draft").performScrollTo().performClick()
            compose.onNodeWithText("Discard draft",useUnmergedTree=true).performClick()
            assertNull(store.read(owner,delivery))
        }finally{store.clearOwner(owner)}
    }
    @Test fun actualCameraContractFileAndDrawnSignatureAreDurableAndCancelledRetakePreservesThem() {
        val owner=UUID.randomUUID().toString();val delivery=UUID.randomUUID().toString();val store=ProofDraftStore(context)
        var captured=false
        val registry=object:ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
                val uri=input as Uri
                if(!captured)context.contentResolver.openOutputStream(uri)!!.use{it.write(photo())}
                val success=!captured;captured=true
                android.os.Handler(android.os.Looper.getMainLooper()).post {dispatchResult(requestCode,success)}
            }
        }
        val registryOwner=object:ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        try {
            compose.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                DriverTheme {DriverProofForm(delivery,owner,"SYNTHETIC",MobileState(),{},{}){_,_,_,_,_->}}
            }}
            compose.onNodeWithText("TAKE PHOTO").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Captured delivery photo").assertExists()
            compose.onNodeWithText("Enter full name").performTextInput("Drawn Recipient")
            compose.onNodeWithTag("proof_signature_canvas").performScrollTo().performTouchInput {swipe(centerLeft,centerRight,500)}
            compose.onNodeWithText("COMPLETE DELIVERY").assertIsEnabled()
            val before=store.read(owner,delivery)!!
            assertTrue(before.strokes.isNotEmpty());assertNotNull(before.photo)
            compose.onNodeWithText("RETAKE PHOTO").performScrollTo().performClick()
            compose.onNodeWithText("Photo capture cancelled.",substring=true).performScrollTo().assertExists()
            val restored=ProofDraftStore(context).read(owner,delivery)!!
            assertEquals(before.attempt,restored.attempt);assertEquals(before.strokes,restored.strokes)
            assertArrayEquals(before.photo,restored.photo)
        }finally{store.clearOwner(owner)}
    }
    @Test fun encryptedDraftCorruptionBlocksReplacementAndLeavesEvidenceIntact() {
        val owner=UUID.randomUUID().toString();val delivery=UUID.randomUUID().toString();val store=ProofDraftStore(context)
        store.save(owner,delivery,draft())
        val identity="${BuildConfig.SUPABASE_URL}/proof-drafts-v1/proof/$owner/$delivery"
        val name=java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString(""){"%02x".format(it)}
        val file=java.io.File(context.noBackupFilesDir,"workspace-recovery/$name")
        try {
            assertFalse(String(file.readBytes()).contains("Synthetic Recipient"))
            file.writeBytes(byteArrayOf(1,2,3))
            assertTrue(runCatching{store.read(owner,delivery)}.isFailure);assertTrue(store.exists(owner,delivery))
            compose.setContent {DriverTheme {DriverProofForm(delivery,owner,"SYNTHETIC",MobileState(),{},{}){_,_,_,_,_->fail("corrupt draft submit")}}}
            compose.onNodeWithText("COMPLETE DELIVERY").assertIsNotEnabled()
            compose.onNodeWithText("The saved proof draft cannot be read.",substring=true).performScrollTo().assertExists()
            assertTrue(file.exists())
        }finally{store.clearOwner(owner)}
    }
}
