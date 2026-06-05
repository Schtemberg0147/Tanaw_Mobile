package com.tanaw.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.tanaw.app.R
import com.tanaw.app.ui.theme.*

// ─── Forgot Password Screen ───────────────────────────────────────────────────
/**
 * ForgotPasswordScreen
 *
 * @param onSendResetLink   Called with the entered email when user taps Send Reset Link
 * @param onBackToSignIn    Navigate back to LoginScreen
 * @param isLoading         Show loading indicator on the Send button
 * @param errorMessage      Non-null shows an inline error below the input
 */
@Composable
fun ForgotPasswordScreen(
    onSendResetLink: (email: String) -> Unit,
    onBackToSignIn: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var email by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray),  // light blue-gray background
        contentAlignment = Alignment.Center
    ) {

        // ── White card ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Icon with orange notification dot ────────────────────────
                Box(contentAlignment = Alignment.TopEnd) {
                    // Lock/refresh icon background
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color = Color(0xFFEEF2FF),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_forgotpass),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    // Orange notification dot
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = OrangeAccent,
                                shape = RoundedCornerShape(50)
                            )
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Title ────────────────────────────────────────────────────
                Text(
                    text = "Forgot Password?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NavyPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── Subtitle ─────────────────────────────────────────────────
                Text(
                    text = "Enter your email address and we'll send you a link to reset your password.",
                    fontSize = 13.sp,
                    color = HintGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ── Email input ──────────────────────────────────────────────
                TanawTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "your@gmail.com",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = null,
                            tint = HintGray,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )

                // ── Inline error ─────────────────────────────────────────────
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Send Reset Link button ───────────────────────────────────
                Button(
                    onClick = { onSendResetLink(email) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "SEND RESET LINK",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Back to Sign In outline button ───────────────────────────
                OutlinedButton(
                    onClick = onBackToSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.5.dp, NavyPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary)
                ) {
                    Text(
                        text = "BACK TO SIGN IN",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // ── Orange bottom accent bar ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(OrangeAccent)
                .align(Alignment.BottomCenter)
        )
    }
}