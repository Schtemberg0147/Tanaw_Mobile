package com.tanaw.app.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun WorkspaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}
@Composable fun StatusBadge(status:String) {
    val color=when(status) {
        "completed","approved" -> MaterialTheme.colorScheme.primary
        "cancelled","delayed","correction_requested" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(color=color.copy(alpha=.10f),shape=RoundedCornerShape(6.dp)) {
        Text(MobilePresentation.status(status),Modifier.padding(horizontal=10.dp,vertical=6.dp),color=color,
            style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
    }
}
@Composable fun SupportingText(text:String) { Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
