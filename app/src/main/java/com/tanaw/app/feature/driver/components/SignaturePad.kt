package com.tanaw.app.feature.driver.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.ui.theme.NavyBanner
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import java.io.ByteArrayOutputStream

data class PathPoint(val offset: Offset, val isStart: Boolean = false)

@Composable
fun SignaturePad(
    onSignatureChanged: (hasSignature: Boolean, captureBytes: () -> ByteArray?) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = remember { mutableStateListOf<PathPoint>() }

    fun clearSignature() {
        points.clear()
        onSignatureChanged(false) { null }
    }

    fun exportSignatureToPngBytes(width: Int = 500, height: Int = 250): ByteArray? {
        if (points.isEmpty()) return null
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint().apply {
            color = "#0F172A".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val nativePath = android.graphics.Path()
        var currentStart = false

        for (pt in points) {
            if (pt.isStart || !currentStart) {
                nativePath.moveTo(pt.offset.x, pt.offset.y)
                currentStart = true
            } else {
                nativePath.lineTo(pt.offset.x, pt.offset.y)
            }
        }

        canvas.drawPath(nativePath, paint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recipient Signature *",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NavyBanner,
                modifier = Modifier.weight(1f)
            )

            if (points.isNotEmpty()) {
                OutlinedButton(
                    onClick = { clearSignature() },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("clear_signature_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clear", fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                .background(Color.White)
                .testTag("signature_canvas_container")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                points.add(PathPoint(offset, isStart = true))
                                onSignatureChanged(true) { exportSignatureToPngBytes() }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                points.add(PathPoint(change.position))
                                onSignatureChanged(true) { exportSignatureToPngBytes() }
                            }
                        )
                    }
                    .testTag("signature_canvas")
            ) {
                if (points.isNotEmpty()) {
                    val composePath = Path()
                    var isStart = true

                    for (p in points) {
                        if (p.isStart || isStart) {
                            composePath.moveTo(p.offset.x, p.offset.y)
                            isStart = false
                        } else {
                            composePath.lineTo(p.offset.x, p.offset.y)
                        }
                    }

                    drawPath(
                        path = composePath,
                        color = Color(0xFF0F172A),
                        style = Stroke(width = 5f)
                    )
                }
            }

            if (points.isEmpty()) {
                Text(
                    text = "Sign inside this box using your finger",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("signature_hint_text")
                )
            }
        }
    }
}
