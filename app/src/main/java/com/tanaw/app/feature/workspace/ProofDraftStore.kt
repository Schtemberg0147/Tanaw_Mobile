package com.tanaw.app.feature.workspace

import android.content.Context
import android.util.Base64
import com.tanaw.app.BuildConfig
import kotlinx.serialization.json.*
import java.util.UUID

internal data class SignaturePoint(val x:Float,val y:Float)
internal data class ProofDraft(
    val attempt:String=UUID.randomUUID().toString(),val recipient:String="",val photo:ByteArray?=null,
    val strokes:List<List<SignaturePoint>> = emptyList(),val signature:ByteArray?=null,val frozen:Boolean=false
) {
    fun changed()=copy(attempt=UUID.randomUUID().toString(),signature=null)
    override fun equals(other:Any?):Boolean = this===other || (other is ProofDraft &&
        attempt==other.attempt && recipient==other.recipient && photo.contentEquals(other.photo) &&
        strokes==other.strokes && signature.contentEquals(other.signature) && frozen==other.frozen)
    override fun hashCode():Int {
        var result=attempt.hashCode()
        result=31*result+recipient.hashCode()
        result=31*result+(photo?.contentHashCode()?:0)
        result=31*result+strokes.hashCode()
        result=31*result+(signature?.contentHashCode()?:0)
        return 31*result+frozen.hashCode()
    }
}

/** Encrypted, atomic, backup-excluded draft, scoped by backend/account/delivery. No tokens or shared paths. */
internal class ProofDraftStore(context:Context,project:String=BuildConfig.SUPABASE_URL) {
    private val store=WorkspaceRecoveryStore(context,"$project/proof-drafts-v1",16_000_000,preserveUnreadable=true)
    private var lastPhotoBytes:ByteArray?=null
    private var lastPhotoEncoded:String?=null
    private var lastSignatureBytes:ByteArray?=null
    private var lastSignatureEncoded:String?=null
    private fun scope(owner:String,delivery:String)="proof/$owner/$delivery"
    fun exists(owner:String,delivery:String)=store.contains(scope(owner,delivery))
    private fun encodePhoto(bytes:ByteArray):String {
        if(bytes===lastPhotoBytes && lastPhotoEncoded!=null)return lastPhotoEncoded!!
        return Base64.encodeToString(bytes,Base64.NO_WRAP).also {lastPhotoBytes=bytes;lastPhotoEncoded=it}
    }
    private fun encodeSignature(bytes:ByteArray):String {
        if(bytes===lastSignatureBytes && lastSignatureEncoded!=null)return lastSignatureEncoded!!
        return Base64.encodeToString(bytes,Base64.NO_WRAP).also {lastSignatureBytes=bytes;lastSignatureEncoded=it}
    }
    fun save(owner:String,delivery:String,draft:ProofDraft):Boolean {
        require(owner.isNotBlank() && delivery.isNotBlank())
        val existing=store.read("index/$owner",Long.MAX_VALUE)?.get("deliveries")?.jsonArray.orEmpty().map{it.jsonPrimitive.content}.toSet()
        val indexSaved=if(delivery in existing) true else store.write("index/$owner",buildJsonObject {put("created",System.currentTimeMillis());put("deliveries",JsonArray((existing+delivery).map(::JsonPrimitive)))})
        return indexSaved &&
            store.write(scope(owner,delivery),buildJsonObject {
            put("created",System.currentTimeMillis());put("owner",owner);put("delivery",delivery)
            put("attempt",draft.attempt);put("recipient",draft.recipient);put("frozen",draft.frozen)
            draft.photo?.let {put("photo",encodePhoto(it))}
            draft.signature?.let {put("signature",encodeSignature(it))}
            put("strokes",buildJsonArray {draft.strokes.forEach {line->add(buildJsonArray {line.forEach {p->add(buildJsonArray {add(p.x);add(p.y)})}})}})
        })
    }
    fun read(owner:String,delivery:String):ProofDraft? {
        val row=store.read(scope(owner,delivery),Long.MAX_VALUE)?:run {
            check(!exists(owner,delivery)){"The saved proof draft cannot be read. Contact the office before submitting another attempt."};return null
        }
        return try {
            require(row.value("owner")==owner && row.value("delivery")==delivery)
            UUID.fromString(row.value("attempt"))
            fun bytes(key:String)=row.value(key).takeIf{it.isNotBlank()}?.let{Base64.decode(it,Base64.NO_WRAP).also {b->require(b.size in 1..5*1024*1024)}}
            val strokes=row["strokes"]!!.jsonArray.map {line->line.jsonArray.map {p->
                val a=p.jsonArray;SignaturePoint(a[0].jsonPrimitive.float,a[1].jsonPrimitive.float).also {require(it.x in 0f..1f && it.y in 0f..1f)}
            }}
            ProofDraft(row.value("attempt"),row.value("recipient"),bytes("photo"),strokes,bytes("signature"),row.value("frozen")=="true").also {
                if(it.frozen)require(it.photo!=null && it.signature!=null && it.recipient.trim().length in 2..100 && it.strokes.isNotEmpty())
            }
        }catch(failure:Exception){throw IllegalStateException("The saved proof draft cannot be read. Contact the office before submitting another attempt.",failure)}
    }
    fun clear(owner:String,delivery:String)=store.clear(scope(owner,delivery))
    fun clearOwner(owner:String) {
        store.read("index/$owner",Long.MAX_VALUE)?.get("deliveries")?.jsonArray?.forEach {clear(owner,it.jsonPrimitive.content)}
        store.clear("index/$owner")
    }
}
