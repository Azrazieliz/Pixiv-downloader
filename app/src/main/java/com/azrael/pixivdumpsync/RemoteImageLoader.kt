package com.azrael.pixivdumpsync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

object RemoteImageLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private val memory = object : LruCache<String, Bitmap>(48) {}

    fun load(
        context: Context,
        imageView: ImageView,
        url: String?,
        referer: String = "https://www.pixiv.net/"
    ) {
        if (url.isNullOrBlank()) return
        imageView.tag = url

        memory.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }

        executor.execute {
            val bitmap = runCatching {
                val dir = File(context.cacheDir, "kuroha_images").apply { mkdirs() }
                val file = File(dir, sha256(url))
                if (file.exists() && file.length() > 0L) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    NetworkCookies.install(context)
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", PixivApi.USER_AGENT)
                    conn.setRequestProperty("Referer", referer)
                    conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        conn.disconnect()
                        null
                    } else {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        conn.disconnect()
                        if (bytes.isNotEmpty()) {
                            runCatching { file.writeBytes(bytes) }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } else {
                            null
                        }
                    }
                }
            }.getOrNull()

            if (bitmap != null) {
                memory.put(url, bitmap)
                imageView.post {
                    if (imageView.tag == url) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
