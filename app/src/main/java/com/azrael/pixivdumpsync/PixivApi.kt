package com.azrael.pixivdumpsync

import android.content.Context
import java.io.IOException

class PixivApi(private val context: Context) {
    companion object {
        const val USER_AGENT = "PixivDumpSync/0.1 (Android; personal-use client)"
    }

    data class ArtworkDetail(
        val id: String,
        val userId: String,
        val title: String,
        val pageCount: Int,
        val illustType: Int
    )

    private fun unavailable(): Nothing =
        throw IOException("Pixiv transport adapter is not enabled in this repository build.")

    fun userArtworkIds(userId: String): List<String> = unavailable()
    fun artworkDetail(id: String): ArtworkDetail = unavailable()
    fun pageOriginalUrls(id: String): List<String> = unavailable()
    fun like(id: String) = unavailable()
}

class HttpStatusException(val code: Int, message: String) : IOException(message)
