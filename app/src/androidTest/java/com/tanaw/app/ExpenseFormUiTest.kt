package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.driver.components.AddTripExpenseSheet
import com.tanaw.app.feature.workspace.DriverTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExpenseFormUiTest {
    @get:Rule val compose=createComposeRule()
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
}
