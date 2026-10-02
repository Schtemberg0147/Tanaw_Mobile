package com.tanaw.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.ui.theme.TanawTheme

// ─── Input Label ──────────────────────────────────────────────────────────────
@Composable
fun TanawInputLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    )
}

// ─── Text Field ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanawTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        modifier        = modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(12.dp),
        placeholder     = {
            Text(
                text  = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon          = leadingIcon,
        trailingIcon         = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions      = keyboardOptions,
        singleLine           = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            cursorColor             = MaterialTheme.colorScheme.primary,
            focusedTextColor        = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
        )
    )
}

// ─── Primary Button ───────────────────────────────────────────────────────────
@Composable
fun TanawPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick  = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape   = RoundedCornerShape(28.dp),
        colors  = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary
        ),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color       = MaterialTheme.colorScheme.onPrimary,
                modifier    = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text  = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ─── Outlined Button ──────────────────────────────────────────────────────────
@Composable
fun TanawOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape   = RoundedCornerShape(28.dp),
        border  = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        colors  = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        enabled = enabled
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ─── Divider ──────────────────────────────────────────────────────────────────
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
        Text(
            text  = "  OR  ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
    }
}

// ─── Success Banner ───────────────────────────────────────────────────────────
@Composable
fun SuccessBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = TanawTheme.extendedColors

    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(
            containerColor = extendedColors.successBannerBackground
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter            = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                colorFilter        = ColorFilter.tint(extendedColors.onSuccessBannerText)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text     = message,
                color    = extendedColors.onSuccessBannerText,
                style    = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter            = painterResource(id = R.drawable.ic_close),
                contentDescription = "Dismiss",
                modifier           = Modifier
                    .size(16.dp)
                    .clickable { onDismiss() },
                colorFilter        = ColorFilter.tint(extendedColors.onSuccessBannerText)
            )
        }
    }
}

// ─── Terms Footer ─────────────────────────────────────────────────────────────
@Composable
fun TermsFooter(
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mutedColor  = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.secondary

    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = mutedColor, fontSize = 11.sp)) {
            append("By signing in you agree to our\n")
        }
        withStyle(SpanStyle(color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) {
            append("Terms of Service")
        }
        withStyle(SpanStyle(color = mutedColor, fontSize = 11.sp)) {
            append(" and ")
        }
        withStyle(SpanStyle(color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) {
            append("Privacy Policy")
        }
    }

    Text(
        text       = annotated,
        textAlign  = TextAlign.Center,
        lineHeight = 18.sp,
        modifier   = modifier
    )
}
