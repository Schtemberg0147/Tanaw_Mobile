package com.tanaw.app.feature.workspace

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.json.*
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object RecoveryPolicy {
    const val CHALLENGE_AGE = 15 * 60 * 1000L
    const val CACHE_AGE = 7 * 24 * 60 * 60 * 1000L
    fun challenge(page:String,target:String,now:Long):JsonObject {
        require(page in listOf("recover_code","verify_phone") && target.length in 3..254)
        return buildJsonObject {put("page",page);put("target",target);put("created",now)}
    }
    fun valid(data:JsonObject,now:Long):Boolean {
        val age=now-(data.value("created").toLongOrNull()?:return false)
        return age in 0..CHALLENGE_AGE && data.value("page") in listOf("recover_code","verify_phone") && data.value("target").length in 3..254
    }
    fun workspace(data:JsonObject,owner:String):JsonObject? {
        if(data.obj("profile").value("id")!=owner || data.obj("profile").value("role")!="driver")return null
        val keys=setOf("profile","driver","driver_trucks","trucks","shipments","deliveries","driver_deliveries","expenses","notifications","compliance","driver_read_error","driver_sections","driver_settings")
        return JsonObject(data.filterKeys {it in keys})
    }
    fun sms(data:JsonObject,type:String):String {
        if(data.value("sms_error").isNotBlank())return "Unavailable — refresh to retry"
        val row=data.rows("sms_preferences").firstOrNull {it.value("type")==type}
        return when(row?.get("enabled")) {JsonPrimitive(true)->"Enabled";JsonPrimitive(false)->"Disabled";null->"Disabled (no opt-in saved)";else->"Unavailable"}
    }
}

/** Private, backup-excluded ciphertext. AAD and filenames bind every record to project and owner. */
internal class WorkspaceRecoveryStore(context:Context,private val project:String) {
    private val root=File(context.noBackupFilesDir,"workspace-recovery").apply {mkdirs()}
    private fun name(scope:String)=MessageDigest.getInstance("SHA-256").digest("$project/$scope".toByteArray()).joinToString(""){"%02x".format(it)}
    private fun file(scope:String)=AtomicFile(File(root,name(scope)))
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return (store.getKey("workspace-recovery-v1",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("workspace-recovery-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generateKey()
        }
    }
    fun write(scope:String,data:JsonObject):Boolean = runCatching {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key());updateAAD(name(scope).toByteArray())}
        val bytes=cipher.iv+cipher.doFinal(data.toString().toByteArray())
        require(bytes.size<=2_000_000)
        val target=file(scope);val stream=target.startWrite()
        try {stream.write(bytes);target.finishWrite(stream)}catch(e:Exception){target.failWrite(stream);throw e}
        true
    }.getOrDefault(false)
    fun read(scope:String,maxAge:Long):JsonObject? = runCatching {
        val bytes=file(scope).openRead().use {require(it.available()<=2_000_000);it.readBytes()}
        require(bytes.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));updateAAD(name(scope).toByteArray())}
        val data=Json.parseToJsonElement(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)))).jsonObject
        val age=System.currentTimeMillis()-(data.value("created").toLongOrNull()?:error("missing date"))
        require(age in 0..maxAge);data
    }.getOrElse {clear(scope);null}
    fun clear(scope:String){file(scope).delete()}
}
