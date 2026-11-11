package com.tanaw.app.feature.auth

import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

object GoogleSignIn {
    fun nonce(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    fun hashedNonce(nonce: String): String = MessageDigest.getInstance("SHA-256")
        .digest(nonce.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    suspend fun token(activityContext: Context, clientId: String, nonce: String): String {
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(hashedNonce(nonce)).build()
        val result = CredentialManager.create(activityContext).getCredential(
            context = MutableContextWrapper(activityContext),
            request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "GOOGLE_CREDENTIAL_INVALID"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
