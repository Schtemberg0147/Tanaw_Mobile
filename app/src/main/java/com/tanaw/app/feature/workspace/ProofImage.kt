package com.tanaw.app.feature.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object ProofImage {
    fun decode(bytes: ByteArray, maxBytes: Int = 5 * 1024 * 1024): Bitmap {
        require(bytes.isNotEmpty() && bytes.size <= maxBytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("INVALID_PROOF_IMAGE")
    }

    suspend fun decodeAsync(bytes: ByteArray, maxBytes: Int = 5 * 1024 * 1024): Bitmap =
        withContext(Dispatchers.Default) {
            decode(bytes, maxBytes)
        }

    suspend fun processCameraPhoto(file: File): ByteArray = withContext(Dispatchers.IO) {
        require(file.length() in 1..20L * 1024 * 1024) { "Camera photo file exceeds allowed size (up to 20 MB)." }
        val rawBytes = file.readBytes()
        withContext(Dispatchers.Default) {
            val bitmap = decode(rawBytes, 20 * 1024 * 1024)
            val bytes = try {
                ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }.toByteArray()
            } finally {
                bitmap.recycle()
            }
            require(bytes.size in 1..5 * 1024 * 1024) { "Compressed photo exceeds 5 MB." }
            bytes
        }
    }

    internal suspend fun renderSignaturePng(strokes: List<List<SignaturePoint>>): ByteArray = withContext(Dispatchers.Default) {
        val signature = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(signature)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 4f
            isAntiAlias = true
        }
        strokes.forEach { line ->
            line.zipWithNext().forEach { (a, b) ->
                canvas.drawLine(a.x * 1000f, a.y * 400f, b.x * 1000f, b.y * 400f, paint)
            }
        }
        val bytes = try {
            ByteArrayOutputStream().also { signature.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            signature.recycle()
        }
        bytes
    }
}
