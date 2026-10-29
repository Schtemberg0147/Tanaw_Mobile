package com.tanaw.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.auth.LoginScreen
import com.tanaw.app.ui.theme.TanawTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DriverLoginFormTest {
    @get:Rule val compose=createComposeRule()
    @Test fun phoneAndEmailFormsSubmitTheirCurrentIdentifierAndPassword() {
        val submissions=mutableListOf<Pair<String,String>>()
        compose.setContent {
            TanawTheme {
                LoginScreen(
                    onLoginClick = { identifier, password -> submissions.add(identifier to password) },
                    onForgotPassword = {},
                    onCreateAccount = {},
                    allowPhone = true,
                )
            }
        }
        compose.onNodeWithText("PHONE NUMBER").assertExists()
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        val screenshot=compose.onRoot().captureToImage().asAndroidBitmap()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"driver-login-public.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("09000000000")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("synthetic-form-only")
        compose.onNodeWithText("Login  →").performScrollTo().performClick()
        assertEquals("09000000000" to "synthetic-form-only",submissions.single())
        compose.onNodeWithText("Use email address instead").performScrollTo().performClick()
        compose.onNodeWithText("EMAIL ADDRESS").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("driver@example.invalid")
        compose.onNodeWithText("Login  →").performScrollTo().performClick()
        assertEquals("driver@example.invalid" to "synthetic-form-only",submissions.last())
    }
}
