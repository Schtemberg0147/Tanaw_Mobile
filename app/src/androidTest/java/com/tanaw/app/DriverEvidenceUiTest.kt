package com.tanaw.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.workspace.*
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DriverEvidenceUiTest {
    @get:Rule val compose=createComposeRule()
    private fun expense(id:String,status:String)=buildJsonObject {
        put("id",id);put("category",id);put("amount",50);put("status",status)
        put("receipt_path","delivery/receipt-$id.jpg");put("review_reason",if(status=="rejected")"Receipt unreadable" else "")
    }
    @Test fun expenseDecisionsAndPrivateFilesUseRecordIds() {
        val calls=mutableListOf<Triple<String,String,String>>()
        val details=buildJsonObject {
            put("expenses",JsonArray(listOf(expense("parking","pending"),expense("toll","approved"),expense("other","rejected"))))
            put("incidents",JsonArray(listOf(buildJsonObject {put("id","incident");put("category","traffic");put("description","Traffic photo")})))
            put("incident_evidence",JsonArray(listOf(buildJsonObject {put("id","photo");put("incident_id","incident");put("slot",1)})))
        }
        compose.setContent {DriverTheme {Column(Modifier.verticalScroll(rememberScrollState())){DriverEvidenceRecords("shipment",details,false){s,k,r->calls.add(Triple(s,k,r))}}}}
        compose.onNodeWithText("Pending office review").assertExists()
        compose.onNodeWithText("Approved").assertExists()
        compose.onNodeWithText("Rejected").assertExists()
        compose.onNodeWithText("Office feedback: Receipt unreadable").assertExists()
        compose.onNodeWithText("View receipt · parking").performScrollTo().performClick()
        compose.onNodeWithText("View incident photo 1").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(listOf(Triple("shipment","expense","parking"),Triple("shipment","incident","photo")),calls)}
    }
    @Test fun failedEvidenceDoesNotInventEmptyExpenses() {
        compose.setContent {DriverTheme {Column {DriverEvidenceRecords("s",buildJsonObject {put("evidence_error","Evidence unavailable")},false){_,_,_->}}}}
        compose.onNodeWithText("Evidence unavailable").assertExists()
        compose.onNodeWithText("No expenses submitted for this trip.").assertDoesNotExist()
    }
    @Test fun imagePreparationBoundsAndPhotoLimit() {
        val source=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val bytes=ByteArrayOutputStream().also {source.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();source.recycle()
        val jpeg=DriverEvidence.jpeg(bytes)
        assertEquals(0xff,jpeg[0].toInt() and 0xff);assertEquals(0xd8,jpeg[1].toInt() and 0xff)
        try {DriverEvidence.jpeg(ByteArray(DriverEvidence.MAX_BYTES+1));fail("Oversized accepted")}catch(_:IllegalArgumentException){}
        var remaining=3
        compose.setContent {DriverTheme {Column(Modifier.verticalScroll(rememberScrollState())){IncidentPhotoPicker(List(3){jpeg},false){remaining=it.size}}}}
        compose.onNodeWithText("Add incident photo").assertIsNotEnabled()
        compose.onNodeWithText("Remove incident photo 1").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(2,remaining)}
    }
    @Test fun offlineFileButtonsAreDisabled() {
        compose.setContent {DriverTheme {Column {DriverEvidenceRecords("s",buildJsonObject {put("expenses",JsonArray(listOf(expense("parking","pending"))))},true){_,_,_->}}}}
        compose.onNodeWithText("View receipt · parking").assertIsNotEnabled()
    }
}
