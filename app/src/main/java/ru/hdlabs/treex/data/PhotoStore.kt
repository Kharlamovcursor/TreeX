package ru.hdlabs.treex.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

object PhotoStore {
    fun relativeName(id: String): String = "photos/$id.jpg"

    fun file(context: Context, relative: String): File = File(context.filesDir, relative)

    fun save(context: Context, personId: String, uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось прочитать фото" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1280)
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalArgumentException("Не удалось прочитать фото")
        val scaled = scale(bitmap, 1280)
        val relative = relativeName(safeId(personId))
        val target = file(context, relative)
        target.parentFile?.mkdirs()
        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return relative
    }

    fun delete(context: Context, relative: String) {
        if (relative.startsWith("photos/") && !relative.contains("..")) {
            file(context, relative).delete()
        }
    }

    private fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var size = 1
        while (width / size > maxSide * 2 || height / size > maxSide * 2) size *= 2
        return size
    }

    private fun scale(source: Bitmap, maxSide: Int): Bitmap {
        val max = maxOf(source.width, source.height)
        if (max <= maxSide) return source
        val ratio = maxSide.toFloat() / max
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
