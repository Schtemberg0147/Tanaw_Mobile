package com.tanaw.app.feature.workspace.customer.rules

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Pure helper rules for customer profile photo encoding, center-square cropping,
 * and data-URI serialization (`data:image/jpeg;base64,...`).
 */
object CustomerAvatarRules {
    const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
    const val AVATAR_DIMENSION_PX = 256
    const val JPEG_QUALITY = 82
    const val DATA_URI_PREFIX = "data:image/jpeg;base64,"
    val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    fun isSupportedMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        return mimeType.trim().lowercase() in SUPPORTED_MIME_TYPES
    }

    fun isValidSourceSize(byteCount: Int): Boolean = byteCount in 1..MAX_SOURCE_BYTES

    fun toDataUri(jpegBytes: ByteArray): String {
        require(jpegBytes.isNotEmpty()) { "Avatar image bytes cannot be empty." }
        val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return "$DATA_URI_PREFIX$encoded"
    }

    fun fromDataUriOrBase64(raw: String?): ByteArray? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val payload = when {
            trimmed.startsWith("data:image/", ignoreCase = true) -> {
                val commaIndex = trimmed.indexOf(',')
                if (commaIndex < 0 || commaIndex + 1 >= trimmed.length) return null
                trimmed.substring(commaIndex + 1)
            }
            !trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> return null
        }
        return runCatching {
            Base64.decode(payload, Base64.DEFAULT).takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /**
     * Calculates the center-square crop rectangle (left, top, size) for a source image
     * of [width] x [height].
     */
    fun centerSquareBounds(width: Int, height: Int): Triple<Int, Int, Int> {
        require(width > 0 && height > 0) { "Dimensions must be positive." }
        val size = min(width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2
        return Triple(left, top, size)
    }

    /**
     * Decodes, center-crops to a square, scales to [AVATAR_DIMENSION_PX] x [AVATAR_DIMENSION_PX],
     * and compresses to JPEG bytes.
     */
    fun processToSquareAvatarJpeg(
        sourceBytes: ByteArray,
        targetSizePx: Int = AVATAR_DIMENSION_PX,
        jpegQuality: Int = JPEG_QUALITY,
    ): ByteArray {
        require(sourceBytes.isNotEmpty() && sourceBytes.size <= MAX_SOURCE_BYTES) {
            "Choose a photo up to 20 MB."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to decode selected image." }

        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSizePx) {
            sample *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            sourceBytes,
            0,
            sourceBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Unable to decode selected image.")

        val (cropX, cropY, cropSize) = centerSquareBounds(decoded.width, decoded.height)
        val cropped = if (cropX == 0 && cropY == 0 && decoded.width == decoded.height) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, cropX, cropY, cropSize, cropSize).also {
                if (it !== decoded) decoded.recycle()
            }
        }

        val scaled = if (cropped.width == targetSizePx && cropped.height == targetSizePx) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetSizePx, targetSizePx, true).also {
                if (it !== cropped) cropped.recycle()
            }
        }

        return try {
            ByteArrayOutputStream().use { out ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
                    "Unable to encode profile photo."
                }
                out.toByteArray()
            }
        } finally {
            scaled.recycle()
        }
    }

    suspend fun processToSquareAvatarJpegAsync(sourceBytes: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            processToSquareAvatarJpeg(sourceBytes)
        }

    suspend fun decodeAvatarBitmapAsync(rawAvatarUrl: String?, fallbackBytes: ByteArray?): Bitmap? =
        withContext(Dispatchers.Default) {
            val bytes = fromDataUriOrBase64(rawAvatarUrl) ?: fallbackBytes ?: return@withContext null
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
}
