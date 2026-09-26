package com.azrael.pixivdumpsync

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object FileStore {
    private val RELATIVE_DIR =
        "${Environment.DIRECTORY_DOWNLOADS}/Kuroha/"

    private val LEGACY_RELATIVE_DIR =
        "${Environment.DIRECTORY_DOWNLOADS}/PixiFlow/"

    private val COLLECTION_URI =
        MediaStore.Downloads.EXTERNAL_CONTENT_URI

    fun exists(context: Context, filename: String): Boolean {
        val projection = arrayOf(BaseColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?"

        for (relativeDir in listOf(RELATIVE_DIR, LEGACY_RELATIVE_DIR)) {
            context.contentResolver.query(
                COLLECTION_URI,
                projection,
                selection,
                arrayOf(filename, relativeDir),
                null
            ).use { c ->
                if (c != null && c.moveToFirst()) return true
            }
        }
        return false
    }

    @Synchronized
    fun saveImage(
        context: Context,
        imageUrl: String,
        filename: String,
        referer: String
    ) {
        if (exists(context, filename)) return

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(filename))
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(COLLECTION_URI, values)
            ?: throw IOException("MediaStore insert failed for $filename")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                val conn = URL(imageUrl).openConnection() as HttpURLConnection
                NetworkRequestRegistry.register(conn)
                try {
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 12_000
                    conn.readTimeout = 45_000
                    conn.setRequestProperty("User-Agent", PixivApi.USER_AGENT)
                    conn.setRequestProperty("Referer", referer)

                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IOException("Image HTTP $code")
                    }

                    conn.inputStream.use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            if (SyncControl.isStopping()) throw SyncCancelledException()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                } catch (t: Throwable) {
                    if (SyncControl.isStopping()) throw SyncCancelledException()
                    throw t
                } finally {
                    NetworkRequestRegistry.unregister(conn)
                    runCatching { conn.disconnect() }
                }
            } ?: throw IOException("Could not open output for $filename")

            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
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
