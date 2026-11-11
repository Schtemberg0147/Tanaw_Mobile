package com.tanaw.app.feature.auth

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.CustomerAddressRules
import com.tanaw.app.feature.workspace.MobileValidation
import com.tanaw.app.ui.components.*
import com.tanaw.app.ui.theme.TanawTheme

enum class PasswordStrength(val label: String) {
    EMPTY(""),
    WEAK("WEAK"),
    FAIR("FAIR"),
    STRONG("STRONG")
}

typealias PasswordStrengthLevel = PasswordStrength

@Composable
fun passwordStrengthColor(level: PasswordStrength): Color {
    val extendedColors = TanawTheme.extendedColors
    return when (level) {
        PasswordStrength.EMPTY  -> Color.Transparent
        PasswordStrength.WEAK   -> MaterialTheme.colorScheme.error
        PasswordStrength.FAIR   -> MaterialTheme.colorScheme.secondary
        PasswordStrength.STRONG -> extendedColors.onSuccessBannerText
    }
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
        password: String,
        address: String
    ) -> Unit,
    onSignIn: () -> Unit,
    onTermsClick: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onGoogleSignIn: (() -> Unit)? = null,
    phoneSignup: Boolean = false,
) {
    var firstName       by remember { mutableStateOf("") }
    var lastName        by remember { mutableStateOf("") }
    var contactNumber   by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var address         by remember { mutableStateOf("") }
    var streetAddress   by remember { mutableStateOf("") }
    var barangay        by remember { mutableStateOf("") }
    var city            by remember { mutableStateOf("") }
    var province        by remember { mutableStateOf(CustomerAddressRules.DEFAULT_PROVINCE_SUGGESTION) }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible        by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var agreedToTerms   by remember { mutableStateOf(false) }

    val passwordStrength = evaluatePasswordStrength(password)

    val passwordMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val addressValid = !phoneSignup || (streetAddress.isNotBlank() && city.isNotBlank() && CustomerAddressRules.isValidAddress(streetAddress, barangay, city, province))
    val canSubmit = firstName.isNotBlank()
            && lastName.isNotBlank()
            && MobileValidation.phone(contactNumber) != null
            && MobileValidation.email(email)
            && addressValid
            && password.isNotBlank()
            && confirmPassword.isNotEmpty()
            && password.length >= 8
            && !passwordMismatch
            && agreedToTerms
            && !isLoading

    val focusManager = LocalFocusManager.current

    fun submitForm() {
        val finalAddress = if (phoneSignup) {
            val (pAddress, pCity) = CustomerAddressRules.formatForRpc(
                street   = streetAddress.trim(),
                barangay = barangay.trim(),
                city     = city.trim(),
                province = province.trim()
            )
            if (pCity.isNotBlank()) "$pAddress, $pCity" else pAddress
        } else {
            address.trim()
        }
        onVerifyAndContinue(
            firstName.trim(),
            lastName.trim(),
            contactNumber.trim(),
            email.trim(),
            password,
            finalAddress
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter            = painterResource(
                    id = if (androidx.compose.foundation.isSystemInDarkTheme()) R.drawable.ic_tanaw_logo_dark else R.drawable.ic_tanaw_logo
                ),
                contentDescription = "TANAW logo",
                modifier           = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text  = "Create Account",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Google Sign In (Optional) ────────────────────────────────
            onGoogleSignIn?.let { GoogleSignInButton(!isLoading, it) }

            // ── First and Last Name ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TanawInputLabel(text = "FIRST NAME")
                    Spacer(modifier = Modifier.height(6.dp))
                    TanawTextField(
                        value           = firstName,
                        onValueChange   = { firstName = it },
                        placeholder     = "Juan",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    TanawInputLabel(text = "LAST NAME")
                    Spacer(modifier = Modifier.height(6.dp))
                    TanawTextField(
                        value           = lastName,
                        onValueChange   = { lastName = it },
                        placeholder     = "Dela Cruz",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Contact Number ───────────────────────────────────────────
            TanawInputLabel(text = "CONTACT NUMBER")
            Spacer(modifier = Modifier.height(6.dp))
            val contactInvalid = contactNumber.isNotBlank() && MobileValidation.phone(contactNumber) == null
            TanawTextField(
                value           = contactNumber,
                onValueChange   = { contactNumber = it },
                placeholder     = "0912 345 6789",
                leadingIcon     = {
                    Icon(
                        imageVector        = Icons.Outlined.Phone,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                isError         = contactInvalid,
                supportingText  = if (contactInvalid) {
                    {
                        Text(
                            text  = "Enter a Philippine mobile number, e.g. 0912 345 6789.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else null,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Email Address ────────────────────────────────────────────
            TanawInputLabel(text = "EMAIL ADDRESS")
            Spacer(modifier = Modifier.height(6.dp))
            val emailInvalid = email.isNotBlank() && !MobileValidation.email(email)
            TanawTextField(
                value           = email,
                onValueChange   = { email = it },
                placeholder     = "juandelacruz@gmail.com",
                leadingIcon     = {
                    Icon(
                        imageVector        = Icons.Outlined.Email,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                isError         = emailInvalid,
                supportingText  = if (emailInvalid) {
                    {
                        Text(
                            text  = "Enter a valid email address.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else null,
            )

            if (phoneSignup) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = "For your customer details only. Verification and account messages use SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                TanawInputLabel(text = "STREET / BUILDING ADDRESS")
                Spacer(modifier = Modifier.height(6.dp))
                val streetInvalid = streetAddress.isNotBlank() && !CustomerAddressRules.validateStreet(streetAddress)
                TanawTextField(
                    value           = streetAddress,
                    onValueChange   = { streetAddress = it },
                    placeholder     = "House/building number, street name",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    isError         = streetInvalid,
                    supportingText  = if (streetInvalid) {
                        { Text("Use 380 characters or fewer.", style = MaterialTheme.typography.bodySmall) }
                    } else null,
                )

                Spacer(modifier = Modifier.height(12.dp))
                TanawInputLabel(text = "BARANGAY")
                Spacer(modifier = Modifier.height(6.dp))
                val brgyInvalid = barangay.isNotBlank() && !CustomerAddressRules.validateBarangay(barangay)
                TanawTextField(
                    value           = barangay,
                    onValueChange   = { barangay = it },
                    placeholder     = "e.g. San Josef Sur",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    isError         = brgyInvalid,
                    supportingText  = if (brgyInvalid) {
                        { Text("Use 100 characters or fewer.", style = MaterialTheme.typography.bodySmall) }
                    } else null,
                )

                Spacer(modifier = Modifier.height(12.dp))
                TanawInputLabel(text = "CITY / MUNICIPALITY")
                Spacer(modifier = Modifier.height(6.dp))
                val cityInvalid = city.isNotBlank() && !CustomerAddressRules.validateCity(city)
                TanawTextField(
                    value           = city,
                    onValueChange   = { city = it },
                    placeholder     = "e.g. Cabanatuan City",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    isError         = cityInvalid,
                    supportingText  = if (cityInvalid) {
                        { Text("Use 60 characters or fewer.", style = MaterialTheme.typography.bodySmall) }
                    } else null,
                )

                Spacer(modifier = Modifier.height(12.dp))
                TanawInputLabel(text = "PROVINCE")
                Spacer(modifier = Modifier.height(6.dp))
                val provInvalid = province.isNotBlank() && !CustomerAddressRules.validateProvince(province)
                TanawTextField(
                    value           = province,
                    onValueChange   = { province = it },
                    placeholder     = "e.g. Nueva Ecija",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    isError         = provInvalid,
                    supportingText  = if (provInvalid) {
                        { Text("Use 35 characters or fewer.", style = MaterialTheme.typography.bodySmall) }
                    } else null,
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text     = "We will text a verification code to your contact number.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password ─────────────────────────────────────────────────
            TanawInputLabel(text = "PASSWORD")
            Spacer(modifier = Modifier.height(6.dp))
            val passwordTooShort = password.isNotEmpty() && password.length < 8
            TanawTextField(
                value           = password,
                onValueChange   = { password = it },
                placeholder     = "••••••••",
                leadingIcon     = {
                    Icon(
                        imageVector        = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp)
                    )
                },
                trailingIcon    = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector        = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                isError         = passwordTooShort,
                supportingText  = if (passwordTooShort) {
                    { Text("Password must be at least 8 characters.", style = MaterialTheme.typography.bodySmall) }
                } else null,
            )

            if (passwordStrength != PasswordStrengthLevel.EMPTY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = passwordStrength.label,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = passwordStrengthColor(passwordStrength)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Confirm Password ─────────────────────────────────────────
            TanawInputLabel(text = "CONFIRM PASSWORD")
            Spacer(modifier = Modifier.height(6.dp))
            TanawTextField(
                value           = confirmPassword,
                onValueChange   = { confirmPassword = it },
                placeholder     = "••••••••",
                leadingIcon     = {
                    Icon(
                        imageVector        = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp)
                    )
                },
                trailingIcon    = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector        = if (confirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (canSubmit) submitForm()
                }),
                isError        = passwordMismatch,
                supportingText = if (passwordMismatch) {
                    { Text("Passwords do not match", style = MaterialTheme.typography.bodySmall) }
                } else null,
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text     = errorMessage,
                    color    = MaterialTheme.colorScheme.error,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Terms and Conditions ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value         = agreedToTerms,
                        role          = Role.Checkbox,
                        onValueChange = { agreedToTerms = it }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked         = agreedToTerms,
                    onCheckedChange = null,
                    colors          = CheckboxDefaults.colors(
                        checkedColor   = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                Spacer(modifier = Modifier.width(8.dp))
                val termsText = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)) {
                        append("I agree to Tanaw's ")
                    }
                    pushStringAnnotation(tag = "TERMS", annotation = "terms")
                    withStyle(
                        SpanStyle(
                            color      = MaterialTheme.colorScheme.secondary,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append("Terms of Service")
                    }
                    pop()
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)) {
                        append(" and confirm all booking information I provide is accurate.")
                    }
                }
                @Suppress("DEPRECATION")
                ClickableText(
                    text     = termsText,
                    style    = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    modifier = Modifier.weight(1f),
                    onClick  = { offset ->
                        val clickedTerms = termsText.getStringAnnotations(tag = "TERMS", start = offset, end = offset).isNotEmpty()
                        if (clickedTerms) {
                            onTermsClick()
                        } else {
                            agreedToTerms = !agreedToTerms
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Primary Action Button ────────────────────────────────────
            TanawPrimaryButton(
                text      = "Verify & Continue  →",
                onClick   = ::submitForm,
                isLoading = isLoading,
                enabled   = canSubmit
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Footer ───────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TanawTextLink(
                    text       = "Sign-in",
                    onClick    = onSignIn,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────
@Preview(showBackground = true, name = "Create Account Light")
@Composable
fun CreateAccountScreenLightPreview() {
    TanawTheme(darkTheme = false) {
        CreateAccountScreen(
            onVerifyAndContinue = { _, _, _, _, _, _ -> },
            onSignIn            = {},
            onTermsClick        = {}
        )
    }
}

@Preview(showBackground = true, name = "Create Account Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CreateAccountScreenDarkPreview() {
    TanawTheme(darkTheme = true) {
        CreateAccountScreen(
            onVerifyAndContinue = { _, _, _, _, _, _ -> },
            onSignIn            = {},
            onTermsClick        = {}
        )
    }
}
