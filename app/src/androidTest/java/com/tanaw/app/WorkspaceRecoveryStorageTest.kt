package com.tanaw.app

import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceRecoveryStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun freshStoreRestoresContextWithoutSecretsAndClearsExplicitly() {
        val project="recovery-test-${java.util.UUID.randomUUID()}"
        val store=WorkspaceRecoveryStore(context,project)
        try {
            val context=RecoveryPolicy.challenge("recover_code","driver@example.invalid",System.currentTimeMillis())
            assertTrue(store.write("challenge",context))
            val restored=WorkspaceRecoveryStore(this.context,project).read("challenge",RecoveryPolicy.CHALLENGE_AGE)!!
            assertEquals(context,restored)
            assertEquals(setOf("page","target","created"),restored.keys)
            store.clear("challenge")
            assertNull(store.read("challenge",RecoveryPolicy.CHALLENGE_AGE))
        }finally{store.clear("challenge")}
    }
    @Test fun workspaceSurvivesRecreationButCannotCrossOwnerProjectOrExpiry() {
        val project="offline-test-${java.util.UUID.randomUUID()}"
        val store=WorkspaceRecoveryStore(context,project)
        val data=buildJsonObject {put("created",System.currentTimeMillis());put("data",buildJsonObject {
            put("profile",buildJsonObject {put("id","driver-a");put("role","driver")})
            put("shipments",buildJsonArray {add(buildJsonObject {put("id","trip-a")})})
        })}
        try {
            assertTrue(store.write("driver-a",data))
            assertEquals(data,WorkspaceRecoveryStore(context,project).read("driver-a",RecoveryPolicy.CACHE_AGE))
            assertNull(store.read("driver-b",RecoveryPolicy.CACHE_AGE))
            assertNull(WorkspaceRecoveryStore(context,"$project-other").read("driver-a",RecoveryPolicy.CACHE_AGE))
            val expired=JsonObject(data+mapOf("created" to JsonPrimitive(System.currentTimeMillis()-RecoveryPolicy.CACHE_AGE-1000)))
            assertTrue(store.write("driver-a",expired))
            assertNull(store.read("driver-a",RecoveryPolicy.CACHE_AGE))
        }finally{store.clear("driver-a")}
    }
    @Test fun ciphertextCannotBeReadAfterOwnerSwapOrTampering() {
        val project="tamper-test-${java.util.UUID.randomUUID()}"
        val store=WorkspaceRecoveryStore(context,project)
        fun file(owner:String):java.io.File {
            val name=java.security.MessageDigest.getInstance("SHA-256").digest("$project/$owner".toByteArray()).joinToString(""){"%02x".format(it)}
            return java.io.File(context.noBackupFilesDir,"workspace-recovery/$name")
        }
        try {
            assertTrue(store.write("driver-a",RecoveryPolicy.challenge("recover_code","private-driver@example.invalid",System.currentTimeMillis())))
            val bytes=file("driver-a").readBytes()
            assertFalse(String(bytes,Charsets.ISO_8859_1).contains("private-driver@example.invalid"))
            file("driver-b").writeBytes(bytes)
            assertNull(store.read("driver-b",RecoveryPolicy.CHALLENGE_AGE))
            bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
            file("driver-a").writeBytes(bytes)
            assertNull(store.read("driver-a",RecoveryPolicy.CHALLENGE_AGE))
        }finally{store.clear("driver-a");store.clear("driver-b")}
    }
}
