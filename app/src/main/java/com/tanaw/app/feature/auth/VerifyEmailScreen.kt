package com.tanaw.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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

@Composable
fun VerifyEmailScreen(
    email                : String  = "maria.santos@gmail.com",
    onVerify             : (otp: String) -> Unit,
    onResend             : () -> Unit,
    onBack               : () -> Unit,
    isLoading            : Boolean = false,
    errorMessage         : String? = null,
    resendSuccessMessage : String? = null,
) {
    OtpVerifyContent(
        target = email,
        targetColor = NavyPrimary,
        heading = "Verify your email",
        isLoading = isLoading,
        errorMessage = errorMessage,
        resendSuccessMessage = resendSuccessMessage,
        onVerify = onVerify,
        onResend = onResend
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = NavyPrimary
                )
            }
            Text(
                text = "Verify Email",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

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
                    painter = painterResource(id = R.drawable.ic_mail_check),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
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
