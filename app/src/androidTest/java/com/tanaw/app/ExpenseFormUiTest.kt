package com.tanaw.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.driver.components.AddTripExpenseSheet
import com.tanaw.app.feature.driver.components.ExpenseReceiptSelector
import com.tanaw.app.feature.workspace.DriverTheme
import com.tanaw.app.feature.workspace.ExpenseDraft
import com.tanaw.app.feature.workspace.ExpenseDraftStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class ExpenseFormUiTest {
    @get:Rule val compose=createComposeRule()
    private fun validPngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }.toByteArray()
    }
    @Test fun positiveAmountWithoutReceiptSubmitsAndRetryRetainsDraft() {
        var calls=0
        compose.setContent {DriverTheme {AddTripExpenseSheet("fixture","TEST",false,"Retry unchanged details",{},
            {category,amount,receipt->assertEquals("Parking",category);assertEquals(50.25,amount,0.0);assertNull(receipt);calls++})}}
        compose.onNodeWithTag("expense_category_input").performTextInput("Parking")
        compose.onNodeWithTag("expense_amount_input").performTextInput("0")
        compose.onNodeWithText("Submit Expense").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("expense_amount_input").performTextReplacement("50.25")
        compose.onNodeWithText("Submit Expense").performScrollTo().performClick()
        compose.onNodeWithText("Submit Expense").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(2,calls)}
    }
    @Test fun offlineExpenseCannotSubmit() {
        compose.setContent {DriverTheme {AddTripExpenseSheet("fixture","TEST",false,null,{},
            {_,_,_->fail("Offline submit")},offline=true)}}
        compose.onNodeWithTag("expense_category_input").performTextInput("Parking")
        compose.onNodeWithTag("expense_amount_input").performTextInput("50")
        compose.onNodeWithText("Submit Expense").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Reconnect and refresh",substring=true).performScrollTo().assertExists()
    }
    @Test fun receiptSelectorDisplaysAttachedPhotoPreviewAndHandlesCorruptBytes() {
        val valid = validPngBytes()
        compose.setContent {
            DriverTheme {
                ExpenseReceiptSelector(
                    receipt = valid,
                    preparingReceipt = false,
                    enabled = true,
                    onSelectClick = {}
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("receipt_photo_preview", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("receipt_photo_preview", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Receipt attached · choose another").assertIsDisplayed()
    }
    @Test fun receiptSelectorShowsPreviewErrorForCorruptBytes() {
        compose.setContent {
            DriverTheme {
                ExpenseReceiptSelector(
                    receipt = byteArrayOf(1, 2, 3, 4),
                    preparingReceipt = false,
                    enabled = true,
                    onSelectClick = {}
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Unable to display receipt preview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Unable to display receipt preview").assertIsDisplayed()
        compose.onNodeWithTag("receipt_photo_preview", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun restoredDraftDisplaysReceiptPreviewUntilRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = UUID.randomUUID().toString()
        val delivery = UUID.randomUUID().toString()
        val store = ExpenseDraftStore(context)
        val png = validPngBytes()
        assertTrue(store.save(owner, delivery, ExpenseDraft(category = "Toll", amount = "120", receipt = png)))
        try {
            compose.setContent {
                DriverTheme {
                    AddTripExpenseSheet(delivery, "TEST", false, null, {}, { _, _, _ -> }, owner = owner)
                }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("receipt_photo_preview", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("receipt_photo_preview", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Remove receipt").performScrollTo().performClick()
            compose.onNodeWithTag("receipt_photo_preview", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("Upload receipt photo").performScrollTo().assertIsDisplayed()
        } finally {
            store.clearOwner(owner)
        }
    }
}
