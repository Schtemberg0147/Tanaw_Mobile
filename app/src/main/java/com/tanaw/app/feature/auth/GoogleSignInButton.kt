package com.tanaw.app.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GoogleSignInButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Continue with Google")
    }
    Spacer(Modifier.height(12.dp))
    Text("Or continue with email", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(16.dp))
}
