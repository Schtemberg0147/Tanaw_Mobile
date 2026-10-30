package com.tanaw.app.feature.workspace

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ExpenseSubmissionTest {
    @Test fun receiptAndAccountIdentityMustMatchEvenAfterReview() {
        val row=buildJsonObject {put("delivery_id","d");put("driver_id","driver");put("description","Parking");put("amount","50.00");put("receipt_path","d/receipt-request.jpg");put("status","approved")}
        assertTrue(ExpenseSubmission.matches(row,"d","driver","Parking","50".toBigDecimal(),"d/receipt-request.jpg"))
        assertFalse(ExpenseSubmission.matches(row,"d","other","Parking","50".toBigDecimal(),"d/receipt-request.jpg"))
        assertFalse(ExpenseSubmission.matches(row,"different","driver","Parking","50".toBigDecimal(),"d/receipt-request.jpg"))
        assertFalse(ExpenseSubmission.matches(row,"d","driver","Parking","51".toBigDecimal(),"d/receipt-request.jpg"))
        assertFalse(ExpenseSubmission.matches(row,"d","driver","Parking","50".toBigDecimal(),null))
    }
    @Test fun lostInsertResponseFindsCommittedRecordWithoutAnotherInsert()=runTest {
        var saved:JsonObject?=null;var writes=0;var verified=0
        ExpenseSubmission.run({saved},{verified++},{},{writes++;saved=buildJsonObject{put("status","pending")};error("response lost")})
        assertEquals(1,writes);assertEquals(1,verified)
    }
    @Test fun reviewedReplayNeverUploadsOrOverwritesDecision()=runTest {
        val saved=buildJsonObject {put("status","rejected");put("review_reason","Unreadable")}
        ExpenseSubmission.run({saved},{assertEquals("rejected",it.value("status"))},{fail("reupload")},{fail("overwrite")})
    }
    @Test fun failedInsertWithoutCommittedRowRemainsFailure()=runTest {
        assertTrue(runCatching {ExpenseSubmission.run({null},{fail("not saved")},{},{error("denied")})}.isFailure)
    }
    @Test fun failedLookupAndChangedReplayNeverWrite()=runTest {
        assertTrue(runCatching {ExpenseSubmission.run({error("read denied")},{},{fail("upload")},{fail("insert")})}.isFailure)
        assertTrue(runCatching {ExpenseSubmission.run({buildJsonObject{}},{error("payload conflict")},{fail("upload")},{fail("insert")})}.isFailure)
    }
    @Test fun uploadedButLostResponseIsReconciledByExactBytes()=runTest {
        val bytes=byteArrayOf(1,2,3)
        ExpenseSubmission.upload(bytes,{error("response lost")},{bytes.copyOf()})
        assertTrue(runCatching {ExpenseSubmission.upload(bytes,{error("duplicate")},{byteArrayOf(9)})}.isFailure)
        assertTrue(runCatching {ExpenseSubmission.upload(bytes,{error("offline")},{error("unavailable")})}.isFailure)
    }
}
