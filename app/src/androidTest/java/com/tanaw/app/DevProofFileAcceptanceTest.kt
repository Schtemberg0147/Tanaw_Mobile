package com.tanaw.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Opt-in ONLY: provision an isolated fixture first. Never uses or replaces the app's saved session. */
class DevProofFileAcceptanceTest {
    @Test fun cleanupUnlinkedFixtureFiles()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowProofFixtureCleanup")=="true" && BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
        val fixture=requireNotNull(args.getString("proofFixture"));require(fixture.startsWith("proof-acceptance-"))
        val delivery=requireNotNull(args.getString("proofDelivery"));UUID.fromString(delivery)
        val client=client()
        try {
            client.auth.signInWith(Email){email="$fixture-driver@example.invalid";password=requireNotNull(args.getString("proofPassword"))}
            val docs=client.from("delivery_documents").select {filter{eq("delivery_id",delivery)}}.decodeAs<JsonArray>()
            check(docs.isEmpty()){"Remove ONLY this fixture's metadata before Storage API cleanup."}
            val bucket=client.storage.from("delivery-documents")
            val files=bucket.list(delivery).map {it.name}
            check(files.all{(it.startsWith("pod-photo-") || it.startsWith("signature-")) && !it.contains('/')})
            if(files.isNotEmpty())bucket.delete(files.map{"$delivery/$it"})
            check(bucket.list(delivery).isEmpty())
        }finally{client.close()}
    }
    private fun client()=createSupabaseClient(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_ANON_KEY) {
        install(Auth){autoLoadFromStorage=false;autoSaveToStorage=false;alwaysAutoRefresh=false}
        install(Postgrest);install(Storage)
    }
    private fun image(signature:Boolean):ByteArray {
        val bitmap=Bitmap.createBitmap(400,160,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(if(signature)Color.WHITE else Color.BLUE)
        val paint=Paint().apply{color=Color.BLACK;strokeWidth=5f;isAntiAlias=true}
        canvas.drawLine(20f,100f,170f,30f,paint);canvas.drawLine(170f,30f,350f,120f,paint)
        return ByteArrayOutputStream().also {bitmap.compress(if(signature)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,85,it);bitmap.recycle()}.toByteArray()
    }
    @Test fun actualPrivateFilesLostResponsesCorrectionAndCompletion()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowProofFixtureWrites")=="true" && BuildConfig.SUPABASE_URL.contains("wrcrxsvppehuaqennrla"))
        val fixture=requireNotNull(args.getString("proofFixture"));require(fixture.startsWith("proof-acceptance-"))
        val delivery=requireNotNull(args.getString("proofDelivery"));UUID.fromString(delivery)
        val password=requireNotNull(args.getString("proofPassword"))
        val driver=client();val office=client();val outsider=client();val anonymous=client()
        suspend fun signIn(client:SupabaseClient,role:String) {client.auth.signInWith(Email){email="$fixture-$role@example.invalid";this.password=password}}
        suspend fun records()=driver.from("delivery_documents").select {filter{eq("delivery_id",delivery)}}.decodeAs<JsonArray>().map{it.jsonObject}
        suspend fun delivery(client:SupabaseClient)=client.from("deliveries").select {filter{eq("id",delivery)}}.decodeAs<JsonArray>().single().jsonObject
        val allPaths=mutableListOf<String>()
        try {
            signIn(driver,"driver");signIn(office,"office");signIn(outsider,"outsider")
            val owner=driver.auth.currentUserOrNull()!!.id
            val photo=image(false);val signature=image(true)
            var attempt=UUID.randomUUID().toString()
            suspend fun submit(lostResponse:Boolean) {
                val p="$delivery/pod-photo-$attempt.jpg";val s="$delivery/signature-$attempt.png";allPaths.addAll(listOf(p,s))
                suspend fun saved()=ProofSubmission.recorded(records(),delivery,owner,"Synthetic Recipient",p,s)
                suspend fun verify(){assertArrayEquals(photo,driver.storage.from("delivery-documents").downloadAuthenticated(p));assertArrayEquals(signature,driver.storage.from("delivery-documents").downloadAuthenticated(s))}
                ProofSubmission.run(::saved,::verify,{
                    listOf(Triple(p,photo,ContentType.Image.JPEG),Triple(s,signature,ContentType.Image.PNG)).forEach {(path,bytes,type)->
                        ExpenseSubmission.upload(bytes,{
                            driver.storage.from("delivery-documents").upload(path,bytes){upsert=false;contentType=type}
                            if(lostResponse)error("Simulated lost upload response after actual file saved")
                        },{driver.storage.from("delivery-documents").downloadAuthenticated(path)})
                    }
                },{
                    driver.postgrest.rpc("driver_submit_pod",buildJsonObject{put("p_delivery_id",delivery);put("p_recipient_name","Synthetic Recipient");put("p_photo_path",p);put("p_signature_path",s)})
                    if(lostResponse)error("Simulated lost POD response after commit")
                })
                verify()
            }
            submit(true);assertEquals(2,records().size);assertEquals("pending_review",delivery(driver).value("proof_review_status"))
            val firstAttempt=attempt
            // Replaying must only read; it cannot replace evidence or reset a later office decision.
            submit(false);assertEquals(2,records().size)
            val firstPhoto=allPaths.first();val firstSignature=allPaths[1]
            assertArrayEquals(photo,office.storage.from("delivery-documents").downloadAuthenticated(firstPhoto))
            assertArrayEquals(signature,office.storage.from("delivery-documents").downloadAuthenticated(firstSignature))
            assertTrue(runCatching{outsider.storage.from("delivery-documents").downloadAuthenticated(firstPhoto)}.isFailure)
            assertTrue(runCatching{anonymous.storage.from("delivery-documents").downloadAuthenticated(firstPhoto)}.isFailure)
            runCatching{driver.storage.from("delivery-documents").delete(listOf(firstPhoto,firstSignature))}
            assertArrayEquals(photo,driver.storage.from("delivery-documents").downloadAuthenticated(firstPhoto))
            assertArrayEquals(signature,driver.storage.from("delivery-documents").downloadAuthenticated(firstSignature))
            suspend fun review(action:String) {
                val current=delivery(office)
                office.postgrest.rpc("office_delivery_action",buildJsonObject {put("p_delivery",delivery);put("p_expected",current.value("updated_at"));put("p_action",action);put("p_reason","Synthetic acceptance: clearer proof requested")})
            }
            review("request_correction");assertEquals("correction_requested",delivery(driver).value("proof_review_status"))
            submit(false);assertEquals("correction_requested",delivery(driver).value("proof_review_status"))
            attempt=UUID.randomUUID().toString();assertNotEquals(firstAttempt,attempt)
            submit(true);assertEquals(4,records().size);assertEquals("2",delivery(driver).value("proof_attempt"))
            review("complete");assertEquals("approved",delivery(driver).value("proof_review_status"))
            val shipment=delivery(driver).value("shipment_id")
            assertEquals("completed",driver.from("shipments").select {filter{eq("id",shipment)}}.decodeAs<JsonArray>().single().jsonObject.value("status"))
            submit(false);assertEquals(4,records().size);assertEquals("approved",delivery(driver).value("proof_review_status"))
            ProofImage.decode(driver.storage.from("delivery-documents").downloadAuthenticated(firstPhoto)).recycle()
            ProofImage.decode(driver.storage.from("delivery-documents").downloadAuthenticated(firstSignature)).recycle()
        }finally {
            // Server protects recorded proof. The external fixture cleanup removes metadata then uses Storage API.
            listOf(driver,office,outsider,anonymous).forEach {it.close()}
        }
    }
}
