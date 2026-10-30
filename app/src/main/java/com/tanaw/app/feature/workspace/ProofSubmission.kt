package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Identity includes recipient and BOTH immutable objects, including historical correction attempts. */
internal object ProofSubmission {
    fun validate(delivery:String,attempt:String,recipient:String,photo:ByteArray,signature:ByteArray) {
        require(UUID.fromString(delivery).toString()==delivery && UUID.fromString(attempt).toString()==attempt)
        require(recipient.trim().length in 2..100 && photo.size in 1..5*1024*1024 && signature.size in 1..5*1024*1024)
    }
    fun recorded(rows:List<JsonObject>,delivery:String,owner:String,recipient:String,photo:String,signature:String):Boolean {
        val pair=rows.filter {it.value("storage_path") in listOf(photo,signature)}
        if(pair.isEmpty())return false
        check(pair.size==2 && pair.map{it.value("storage_path")}.toSet()==setOf(photo,signature) &&
            pair.all {it.value("delivery_id")==delivery && it.value("uploaded_by")==owner && it.value("recipient_name")==recipient.trim()} &&
            pair.map{it.value("proof_attempt")}.distinct().singleOrNull()?.toIntOrNull()?.let{it>0}==true &&
            pair.single{it.value("storage_path")==photo}.value("kind")=="proof_of_delivery" &&
            pair.single{it.value("storage_path")==signature}.value("kind")=="signature") {
            "The saved proof differs from this attempt. Refresh and contact the office before changing it."
        }
        return true
    }
    suspend fun run(recorded:suspend ()->Boolean,verifyFiles:suspend ()->Unit,upload:suspend ()->Unit,commit:suspend ()->Unit) {
        if(recorded()){verifyFiles();return}
        upload()
        try {commit()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception) {
            val saved=try {recorded()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){throw failure}
            if(!saved)throw failure
            verifyFiles()
        }
    }
}
