package com.tanaw.app

import com.tanaw.app.feature.auth.GoogleSignIn
import org.junit.Assert.*
import org.junit.Test

class GoogleSignInTest {
    @Test fun nonceIsRandomAndSha256MatchesTheKnownVector() {
        val first = GoogleSignIn.nonce()
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(first, GoogleSignIn.nonce())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            GoogleSignIn.hashedNonce("abc"))
        assertNotEquals(first, GoogleSignIn.hashedNonce(first))
    }
}
