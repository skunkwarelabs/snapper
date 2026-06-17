package com.skunk.snapper.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Handles photo files: temp camera output, permanent copies, and decoding for upload. */
object ImageStorage {

    /** A FileProvider Uri the camera app can write a full-res shot into (in cacheDir). */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** True if the Uri points at a readable, non-empty image (i.e. the camera actually wrote it). */
    fun hasContent(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.read() != -1 } ?: false
    }.getOrDefault(false)

    /** Copies any image Uri (camera or gallery) into permanent internal storage; returns the path. */
    fun copyToInternal(context: Context, source: Uri): String {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val dest = File(dir, "catch_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Could not open image stream" }
            dest.outputStream().use { input.copyTo(it) }
        }
        return dest.absolutePath
    }

    /** Decodes a downsampled, correctly-rotated bitmap suitable for sending to Gemini. */
    fun decodeForUpload(path: String, maxDim: Int = 1024): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2

        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Could not decode image at $path")

        return rotate(bitmap, degreesFor(ExifInterface(path)))
    }

    /** Like [decodeForUpload] but reads straight from a Uri — used by quick-identify,
     *  which never copies the photo to permanent storage. */
    fun decodeForUpload(context: Context, uri: Uri, maxDim: Int = 1024): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2

        val bitmap = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not decode image")

        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        return rotate(bitmap, exif?.let { degreesFor(it) } ?: 0f)
    }

    private fun degreesFor(exif: ExifInterface): Float =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
