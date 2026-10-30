package com.tanaw.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DriverProfileUiTest {
    @get:Rule val compose=createComposeRule()
    private fun fixture(error:Boolean=false,published:Boolean=false)=buildJsonObject {
        put("profile",buildJsonObject {put("full_name","Profile Fixture");put("email","driver@example.invalid");put("phone","+639000000000")})
        put("driver",if(error)buildJsonObject{} else buildJsonObject {put("id","own-driver");put("license_number","UI-LICENSE");put("contact_number","+639000000000")})
        put("driver_trucks",JsonArray(emptyList()))
        put("compliance",JsonArray(emptyList()))
        put("driver_sections",buildJsonObject {
            put("driver_roster",if(error)"error" else "ready");put("driver_trucks",if(error)"error" else "empty")
            put("compliance",if(error)"error" else "empty");put("driver_settings",if(error)"error" else "ready")
        })
        if(!error)put("driver_settings",buildJsonArray {if(published)add(buildJsonObject {
            put("key","driver_app_content");put("value",buildJsonObject {
                put("terms_url","https://example.invalid/terms");put("privacy_url","https://example.invalid/privacy")
                put("support_email","office@example.invalid");put("support_phone","+639000000000")
            })
        })})
    }
    private fun start(data:JsonObject=fixture(),refresh:()->Unit={},recover:()->Unit={},messages:()->Unit={},open:(String)->Unit={}) {
        compose.setContent {CompositionLocalProvider(LocalUriHandler provides object:UriHandler {override fun openUri(uri:String){open(uri)}}) {
            DriverTheme {Column(Modifier.verticalScroll(rememberScrollState())) {DriverProfile(data,busy=false,offline=false,refresh=refresh,recover=recover,logout={},messages=messages)}}
        }}
    }
    @Test fun failedSectionsNeverClaimMissingRecordsAndHaveRetries() {
        var refreshes=0
        start(fixture(true),refresh={refreshes++})
        compose.onNodeWithText("driver@example.invalid").assertExists()
        compose.onNodeWithText("No compliance documents were returned for your account.").assertDoesNotExist()
        compose.onNodeWithText("No assigned vehicle").assertDoesNotExist()
        listOf("Retry driver profile","Retry assigned vehicle","Retry compliance records","Retry warning settings").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        assertEquals(4,refreshes)
    }
    @Test fun freshProfileShowsOwnRecordsAndAdministratorOwnership() {
        start()
        compose.onNodeWithText("UI-LICENSE").performScrollTo().assertExists()
        compose.onNodeWithText("No assigned vehicle").performScrollTo().assertExists()
        compose.onNodeWithText("No compliance documents were returned for your account.").performScrollTo().assertExists()
        compose.onNodeWithText("Contact your administrator to update compliance documents.").performScrollTo().assertExists()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir,"driver-profile.png").outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun realHelpOpensExistingOfficeMessageFlow() {
        var opened=0
        start(messages={opened++})
        compose.onNodeWithText("Help & Support").performScrollTo().performClick()
        compose.onNodeWithText("Open office messages").performScrollTo().performClick()
        assertEquals(1,opened)
        compose.onNodeWithText("The office has not published a support phone",substring=true).performScrollTo().assertExists()
    }
    @Test fun unpublishedPoliciesAreExplicitAndBackReturnsToProfile() {
        start()
        compose.onNodeWithText("Terms of Service").performScrollTo().performClick()
        compose.onNodeWithText("The office has not published an approved terms",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("Open terms of service").assertDoesNotExist()
        compose.onNodeWithText("← Back to profile").performScrollTo().performClick()
        compose.onNodeWithText("Privacy").performScrollTo().performClick()
        compose.onNodeWithText("The office has not published an approved privacy",substring=true).performScrollTo().assertExists()
    }
    @Test fun publishedPolicyAndSupportUseValidatedDestinations() {
        val opened=mutableListOf<String>()
        start(fixture(published=true),open={opened.add(it)})
        compose.onNodeWithText("Terms of Service").performScrollTo().performClick()
        compose.onNodeWithText("Open terms of service").performScrollTo().performClick()
        assertEquals("https://example.invalid/terms",opened.last())
        compose.onNodeWithText("← Back to profile").performScrollTo().performClick()
        compose.onNodeWithText("Help & Support").performScrollTo().performClick()
        compose.onNodeWithText("office@example.invalid").performScrollTo().performClick()
        assertEquals("mailto:office@example.invalid",opened.last())
    }
    @Test fun configuredCriticalWarningsRenderWithActualDocumentContext() {
        val data=fixture()
        val rows=buildJsonArray {add(buildJsonObject {
            put("key","compliance_thresholds");put("value",buildJsonObject {DriverProfileRules.types.forEach {key->
                put(key,buildJsonObject {put("first",45);put("critical",10)})
            }})
        })}
        val documents=buildJsonArray {add(buildJsonObject {
            put("doc_type","drivers_license");put("review_status","approved")
            put("expiry_date",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila")).plusDays(5).toString())
        })}
        start(JsonObject(data+mapOf("driver_settings" to rows,"compliance" to documents,
            "driver_sections" to JsonObject(data.obj("driver_sections")+mapOf("compliance" to JsonPrimitive("ready"))))))
        compose.onNodeWithText("CRITICAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Drivers license").assertExists()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir,"driver-profile-compliance.png").outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
