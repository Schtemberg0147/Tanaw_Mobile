package com.tanaw.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.ui.theme.*

// ─── Verify Phone Screen ──────────────────────────────────────────────────────
/**
 * @param phoneNumber   The phone number the code was sent to
 * @param onVerify      Called with the 6-digit OTP string when user taps Verify
 * @param onResend      Called when user taps Resend after countdown expires
 * @param onBack        Navigate back
 * @param isLoading     Show loading indicator on the button
 * @param errorMessage  Non-null shows an inline error below the OTP boxes
 */
@Composable
fun VerifyPhoneScreen(
    phoneNumber  : String  = "+63 912 345 6789",
    onVerify     : (otp: String) -> Unit,
    onResend     : () -> Unit,
    onBack       : () -> Unit,
    isLoading    : Boolean = false,
    errorMessage : String? = null,
) {
    OtpVerifyContent(
        target       = phoneNumber,
        targetColor  = OrangeAccent,      // phone number shown in orange, matching your mockup
        heading      = "Verify your number",
        isLoading    = isLoading,
        errorMessage = errorMessage,
        onVerify     = onVerify,
        onResend     = onResend
    ) {
        // ── Top bar — no back arrow on phone screen per mockup ────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "Verify Number",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = NavyPrimary
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // ── Icon badge — same visual style as email screen ────────────────
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = NavyPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter            = painterResource(id = R.drawable.ic_mail_check),
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(OrangeAccent, shape = RoundedCornerShape(50))
                    .offset(x = 4.dp, y = (-4).dp)
            )
        }
    }
}