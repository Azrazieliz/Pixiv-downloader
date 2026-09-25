package com.azrael.pixivdumpsync

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object FileStore {
    private val RELATIVE_DIR = "${Environment.DIRECTORY_PICTURES}/PixivDump/"

    fun exists(context: Context, filename: String): Boolean {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection =
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(filename, RELATIVE_DIR),
            null
        ).use { c -> return c != null && c.moveToFirst() }
    }

    fun saveImage(
        context: Context,
        imageUrl: String,
        filename: String,
        referer: String
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeFor(filename))
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert failed for $filename")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                val conn = URL(imageUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 90_000
                conn.setRequestProperty("User-Agent", PixivApi.USER_AGENT)
                conn.setRequestProperty("Referer", referer)
                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IOException("Image HTTP $code")
                }
                conn.inputStream.use { input -> input.copyTo(output, 128 * 1024) }
                conn.disconnect()
            } ?: throw IOException("Could not open output for $filename")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    fun extensionFromUrl(url: String): String {
        val clean = Uri.parse(url).lastPathSegment ?: return "jpg"
        val ext = clean.substringAfterLast('.', "jpg").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp" -> ext
            else -> "jpg"
        }
    }

    private fun mimeFor(filename: String): String =
        when (filename.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
}
