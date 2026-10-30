package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProofSubmissionTest {
    @Test fun restoredDraftWithCopiedImageBytesHasSameValueAndHash() {
        val draft=ProofDraft(recipient="Recipient",photo=byteArrayOf(1,2),signature=byteArrayOf(3,4))
        val restored=draft.copy(photo=draft.photo!!.copyOf(),signature=draft.signature!!.copyOf())
        assertEquals(draft,restored)
        assertEquals(draft.hashCode(),restored.hashCode())
        assertTrue(hashSetOf(draft).contains(restored))
        assertNotEquals(draft,draft.copy(photo=byteArrayOf(9)))
        assertNotEquals(draft,draft.copy(signature=byteArrayOf(9)))
    }
    @Test fun absentImagesAreEqualAndDistinctFromEmptyImages() {
        val draft=ProofDraft()
        assertEquals(draft,draft.copy())
        assertEquals(draft.hashCode(),draft.copy().hashCode())
        assertNotEquals(draft,draft.copy(photo=byteArrayOf()))
        assertNotEquals(draft,draft.copy(signature=byteArrayOf()))
    }
    private fun rows()=listOf("proof_of_delivery" to "d/photo","signature" to "d/signature").map {(kind,path)->buildJsonObject {
        put("delivery_id","d");put("uploaded_by","u");put("recipient_name","Recipient");put("proof_attempt",1);put("kind",kind);put("storage_path",path)
    }}
    @Test fun historicalPairIsRecognizedAndChangedRecipientOrOwnerIsRejected() {
        assertTrue(ProofSubmission.recorded(rows(),"d","u"," Recipient ","d/photo","d/signature"))
        assertFalse(ProofSubmission.recorded(rows(),"d","u","Recipient","d/new-photo","d/new-signature"))
        listOf("other" to "Recipient","u" to "Changed").forEach {(owner,name)->
            assertTrue(runCatching{ProofSubmission.recorded(rows(),"d",owner,name,"d/photo","d/signature")}.isFailure)
        }
        assertTrue(runCatching{ProofSubmission.recorded(rows().take(1),"d","u","Recipient","d/photo","d/signature")}.isFailure)
    }
    @Test fun lostRpcResponseReconcilesWithoutAnotherCommit()=runTest {
        var saved=false;var writes=0;var verified=0
        ProofSubmission.run({saved},{verified++},{},{writes++;saved=true;error("response lost")})
        assertEquals(1,writes);assertEquals(1,verified)
    }
    @Test fun replayAfterCorrectionOrCompletionDoesNotChangeReviewOrUpload()=runTest {
        var verified=false
        ProofSubmission.run({true},{verified=true},{fail("must not upload")},{fail("must not resubmit reviewed proof")})
        assertTrue(verified)
    }
    @Test fun interruptedUploadAndRpcRetainFailureForSameAttemptRetry()=runTest {
        assertTrue(runCatching {ProofSubmission.run({false},{fail()},{error("upload lost and unreadable")},{fail()})}.isFailure)
        assertTrue(runCatching {ProofSubmission.run({false},{fail()},{},{error("not committed")})}.isFailure)
        assertTrue(runCatching {ProofSubmission.run({error("lookup denied")},{fail()},{fail()},{fail()})}.isFailure)
    }
    @Test fun cancellationPropagatesWithoutReconciliationOrCleanup()=runTest {
        var reads=0
        val result=runCatching {ProofSubmission.run({reads++;false},{fail()},{},{throw CancellationException()})}
        assertTrue(result.exceptionOrNull() is CancellationException);assertEquals(1,reads)
    }
    @Test fun badIdentityIsRejectedBeforeUpload() {
        assertTrue(runCatching {ProofSubmission.validate("bad","bad","Recipient",byteArrayOf(1),byteArrayOf(2))}.isFailure)
    }
}
