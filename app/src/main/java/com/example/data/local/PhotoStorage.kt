package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class PhotoStorage(private val context: Context) {
    private val photosDir: File by lazy {
        File(context.filesDir, "contact_photos").apply { if (!exists()) mkdirs() }
    }

    fun savePhotoBytes(bytes: ByteArray): String? {
        return try {
            val compressed = downsampleToJpeg(bytes, maxDim = 256) ?: return null
            val file = File(photosDir, "${UUID.randomUUID()}.jpg")
            file.writeBytes(compressed)
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    fun savePhotoFromUri(uri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            savePhotoBytes(bytes)
        } catch (_: Throwable) {
            null
        }
    }

    fun readPhotoBytes(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) file.readBytes() else null
        } catch (_: Throwable) {
            null
        }
    }

    fun deletePhoto(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (_: Throwable) {
        }
    }

    fun clearAllPhotos() {
        try {
            photosDir.listFiles()?.forEach { it.delete() }
        } catch (_: Throwable) {
        }
    }

    private fun downsampleToJpeg(raw: ByteArray, maxDim: Int): ByteArray? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var sampleSize = 1
        while (opts.outWidth / sampleSize > maxDim * 2 || opts.outHeight / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, decodeOpts) ?: return null

        val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * ratio).toInt().coerceAtLeast(1),
                (bmp.height * ratio).toInt().coerceAtLeast(1),
                true
            ).also { if (it != bmp) bmp.recycle() }
        } else {
            bmp
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
        scaled.recycle()
        return baos.toByteArray()
    }
}
