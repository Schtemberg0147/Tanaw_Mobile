package com.tanaw.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.ui.components.*
import com.tanaw.app.ui.theme.*

enum class PasswordStrength(val label: String, val color: Color) {
    EMPTY("", Color.Transparent),
    WEAK("WEAK", Color(0xFFE53935)),
    FAIR("FAIR", Color(0xFFFB8C00)),
    STRONG("STRONG", Color(0xFF43A047)),
}

fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.EMPTY
    var score = 0
    if (password.length >= 8) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> PasswordStrength.WEAK
        score <= 2 -> PasswordStrength.FAIR
        else       -> PasswordStrength.STRONG
    }
}

@Composable
fun CreateAccountScreen(
    onVerifyAndContinue: (
        firstName: String,
        lastName: String,
        contactNumber: String,
        email: String,
        password: String
    ) -> Unit,
    onSignIn: () -> Unit,
    onTermsClick: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var firstName       by remember { mutableStateOf("") }
    var lastName        by remember { mutableStateOf("") }
    var contactNumber   by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible        by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var agreedToTerms   by remember { mutableStateOf(false) }

    val passwordStrength = evaluatePasswordStrength(password)

    val passwordMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val canSubmit = firstName.isNotBlank()
            && lastName.isNotBlank()
            && contactNumber.isNotBlank()
            && email.isNotBlank()
            && password.isNotBlank()
            && !passwordMismatch
            && agreedToTerms
            && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Spacer(modifier = Modifier.height(48.dp))

        Image(
            painter = painterResource(id = R.drawable.ic_tanaw_logo),
            contentDescription = "TANAW logo",
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create Account",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NavyPrimary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TanawInputLabel(text = "FIRST NAME")
                Spacer(modifier = Modifier.height(6.dp))
                TanawTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    placeholder = "Juan",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                TanawInputLabel(text = "LAST NAME")
                Spacer(modifier = Modifier.height(6.dp))
                TanawTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    placeholder = "Dela Cruz",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TanawInputLabel(text = "CONTACT NUMBER")
        Spacer(modifier = Modifier.height(6.dp))
        TanawTextField(
            value = contactNumber,
            onValueChange = { contactNumber = it },
            placeholder = "0912 345 6789",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = HintGray,
                    modifier = Modifier.size(18.dp)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )

        Spacer(modifier = Modifier.height(16.dp))

        TanawInputLabel(text = "EMAIL ADDRESS")
        Spacer(modifier = Modifier.height(6.dp))
        TanawTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "juandelacruz@gmail.com",
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

        Spacer(modifier = Modifier.height(16.dp))

        TanawInputLabel(text = "PASSWORD")
        Spacer(modifier = Modifier.height(6.dp))
        TanawTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "••••••••",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = HintGray,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible)
                            Icons.Outlined.Visibility
                        else
                            Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = HintGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            visualTransformation = if (passwordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        if (passwordStrength != PasswordStrength.EMPTY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = passwordStrength.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = passwordStrength.color
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TanawInputLabel(text = "CONFIRM PASSWORD")
        Spacer(modifier = Modifier.height(6.dp))
        TanawTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = "••••••••",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = HintGray,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector = if (confirmPasswordVisible)
                            Icons.Outlined.Visibility
                        else
                            Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = HintGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            visualTransformation = if (confirmPasswordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        if (passwordMismatch) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Passwords do not match",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = agreedToTerms,
                onCheckedChange = { agreedToTerms = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = NavyPrimary,
                    uncheckedColor = BorderGray
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            val termsText = buildAnnotatedString {
                withStyle(SpanStyle(color = TextDark, fontSize = 12.sp)) {
                    append("I agree to Tanaw's ")
                }
                withStyle(SpanStyle(color = OrangeAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)) {
                    append("Terms of Service")
                }
                withStyle(SpanStyle(color = TextDark, fontSize = 12.sp)) {
                    append(" and confirm all booking information I provide is accurate.")
                }
            }
            Text(
                text = termsText,
                lineHeight = 18.sp,
                modifier = Modifier.clickable { onTermsClick() }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onVerifyAndContinue(
                    firstName, lastName, contactNumber, email, password
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NavyPrimary,
                disabledContainerColor = NavyPrimary.copy(alpha = 0.4f)
            ),
            enabled = canSubmit
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Verify & Continue  →",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val footerText = buildAnnotatedString {
            withStyle(SpanStyle(color = HintGray, fontSize = 13.sp)) {
                append("Already have an account? ")
            }
            withStyle(SpanStyle(color = OrangeAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) {
                append("Sign-in")
            }
        }
        Text(
            text = footerText,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { onSignIn() }
        )

        Spacer(modifier = Modifier.height(36.dp))
    }
}
