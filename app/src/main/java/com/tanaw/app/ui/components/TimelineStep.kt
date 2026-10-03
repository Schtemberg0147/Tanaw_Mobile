package com.tanaw.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tanaw.app.R

@Composable
fun TimelineStepWithLine(label: String, completed: Boolean, showTimer: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (completed) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color(0xFF0A8A3A),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFE6E6EE), shape = CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.width(2.dp).height(36.dp).background(Color(0xFFE6E6EE)))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = if (completed) Color.Black else Color.Gray)
            if (showTimer) {
                Text(text = "Waiting: 15:00", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB36B00))
            }
        }
    }
}
