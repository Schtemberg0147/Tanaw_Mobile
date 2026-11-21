package com.tanaw.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        modifier        = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
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
        keyboardActions      = keyboardActions,
        isError              = isError,
        supportingText       = supportingText,
        singleLine           = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor         = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor       = MaterialTheme.colorScheme.outline,
            focusedContainerColor      = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor    = MaterialTheme.colorScheme.surfaceContainerHigh,
            cursorColor                = MaterialTheme.colorScheme.primary,
            focusedTextColor           = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor         = MaterialTheme.colorScheme.onSurface,
            focusedLeadingIconColor    = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor  = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor   = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorBorderColor           = MaterialTheme.colorScheme.error,
            errorLeadingIconColor      = MaterialTheme.colorScheme.error,
            errorTrailingIconColor     = MaterialTheme.colorScheme.onSurfaceVariant,
            errorTextColor             = MaterialTheme.colorScheme.onSurface,
            errorContainerColor        = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorSupportingTextColor   = MaterialTheme.colorScheme.error,
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
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    Button(
        onClick  = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape   = shape,
        colors  = ButtonDefaults.buttonColors(
            containerColor         = MaterialTheme.colorScheme.primary,
            contentColor           = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color       = MaterialTheme.colorScheme.onPrimary,
                modifier    = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text  = text,
                style = MaterialTheme.typography.labelLarge
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
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape   = shape,
        border  = BorderStroke(
            1.5.dp,
            if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        ),
        colors  = ButtonDefaults.outlinedButtonColors(
            contentColor         = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        enabled = enabled
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ─── Text Link ────────────────────────────────────────────────────────────────
@Composable
fun TanawTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text       = text,
            color      = color,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = fontWeight
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
            text       = "  OR  ",
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            style      = MaterialTheme.typography.bodySmall,
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
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter            = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Dismiss",
                    tint               = extendedColors.onSuccessBannerText,
                    modifier           = Modifier.size(18.dp)
                )
            }
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
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = "Company policies",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onTermsClick,
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text       = "Terms of Service",
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text  = "and",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(
                onClick = onPrivacyClick,
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text       = "Privacy Policy",
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Tanaw Components")
@Composable
private fun TanawComponentsLightPreview() {
    TanawTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SuccessBanner(message = "Profile saved and verified.", onDismiss = {})
            TanawInputLabel(text = "CONTACT NUMBER")
            TanawTextField(
                value = "",
                onValueChange = {},
                placeholder = "0912 345 6789"
            )
            TanawTextLink(text = "Forgot Password?", onClick = {})
            TanawPrimaryButton(text = "Continue", onClick = {})
            TanawOutlinedButton(text = "Create Account", onClick = {})
        }
    }
}
