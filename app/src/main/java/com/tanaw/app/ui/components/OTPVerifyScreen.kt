package com.tanaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * OtpVerifyContent
 *
 * Shared stateful OTP UI used by both VerifyEmailScreen and VerifyPhoneScreen.
 * Each screen owns its own top bar and icon — this composable handles everything
 * from the OTP boxes downward.
 *
 * @param target        The email or phone number the code was sent to
 * @param targetColor   Color for the target text (NavyPrimary for email, OrangeAccent for phone)
 * @param subLabel      "We've sent a 6-digit code to"
 * @param heading       "Verify your email" / "Verify your number"
 * @param isLoading     Shows spinner on the button while API call is in-flight
 * @param errorMessage  Inline error shown below the OTP boxes
 * @param onVerify      Called with the completed 6-digit OTP string
 * @param onResend      Called when Resend is tapped after countdown expires
 * @param topContent    Slot for each screen's own top bar + icon badge
 */
@Composable
fun OtpVerifyContent(
    target       : String,
    targetColor  : Color   = NavyPrimary,
    subLabel     : String  = "We've sent a 6-digit code to",
    heading      : String,
    isLoading    : Boolean = false,
    errorMessage : String? = null,
    onVerify     : (otp: String) -> Unit,
    onResend     : () -> Unit,
    topContent   : @Composable ColumnScope.() -> Unit
) {
    val otpLength = 6
    var otpValues by remember { mutableStateOf(List(otpLength) { "" }) }
    val focusRequesters = remember { List(otpLength) { FocusRequester() } }

    // ── Countdown ─────────────────────────────────────────────────────────
    var secondsLeft by remember { mutableIntStateOf(45) }
    var canResend   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        }
        canResend = true
    }

    fun formatTime(s: Int) = "%02d:%02d".format(s / 60, s % 60)

    val fullOtp   = otpValues.joinToString("")
    val canSubmit = fullOtp.length == otpLength && !isLoading

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(20.dp))

        // ── Each screen injects its own top bar + icon here ───────────────
        topContent()

        Spacer(modifier = Modifier.height(28.dp))

        // ── Heading ───────────────────────────────────────────────────────
        Text(
            text       = heading,
            fontSize   = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = NavyPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Sub-label + target ────────────────────────────────────────────
        Text(
            text      = subLabel,
            fontSize  = 13.sp,
            color     = HintGray,
            textAlign = TextAlign.Center
        )
        Text(
            text       = target,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = targetColor,
            textAlign  = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // ── OTP boxes ─────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.padding(horizontal = 24.dp)
        ) {
            repeat(otpLength) { index ->
                val isFocused = otpValues[index].isEmpty() &&
                        (index == 0 || otpValues[index - 1].isNotEmpty())

                BasicTextField(
                    value         = otpValues[index],
                    onValueChange = { newVal ->
                        val digit   = newVal.filter { it.isDigit() }.takeLast(1)
                        val updated = otpValues.toMutableList()
                        updated[index] = digit
                        otpValues = updated
                        if (digit.isNotEmpty() && index < otpLength - 1) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    },
                    modifier      = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isFocused) 2.dp else 1.5.dp,
                            color = when {
                                isFocused                     -> NavyPrimary
                                otpValues[index].isNotEmpty() -> NavyPrimary
                                else                          -> BorderGray
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .background(
                            if (otpValues[index].isNotEmpty())
                                NavyPrimary.copy(alpha = 0.05f)
                            else
                                InputGray
                        )
                        .focusRequester(focusRequesters[index]),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    textStyle       = TextStyle(
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = NavyPrimary,
                        textAlign  = TextAlign.Center
                    ),
                    decorationBox  = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            innerTextField()
                        }
                    }
                )
            }
        }

        // ── Error message ─────────────────────────────────────────────────
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text      = errorMessage,
                color     = MaterialTheme.colorScheme.error,
                fontSize  = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Resend row ────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text     = "Didn't receive the code? ",
                fontSize = 13.sp,
                color    = HintGray
            )
            Text(
                text      = "Resend",
                fontSize  = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color     = if (canResend) OrangeAccent else HintGray,
                modifier  = Modifier.clickable(enabled = canResend) {
                    secondsLeft = 45
                    canResend   = false
                    otpValues   = List(otpLength) { "" }
                    onResend()
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Countdown ─────────────────────────────────────────────────────
        if (!canResend) {
            Text(
                text       = formatTime(secondsLeft),
                fontSize   = 13.sp,
                color      = HintGray,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Verify & Continue button ──────────────────────────────────────
        Button(
            onClick  = { onVerify(fullOtp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
            shape  = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor         = NavyPrimary,
                disabledContainerColor = NavyPrimary.copy(alpha = 0.4f)
            ),
            enabled = canSubmit
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color       = Color.White,
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text       = "Verify & Continue",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}