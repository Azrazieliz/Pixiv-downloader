package com.azrael.pixivdumpsync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class PixivApi(private val context: Context) {
    companion object {
        const val USER_AGENT = "PixiFlow/0.4.0 (Android; personal-use client)"
        private const val BASE = "https://www.pixiv.net"
    }

    data class UserProfile(
        val userId: String,
        val name: String,
        val imageUrl: String?
    )

    data class ArtworkDetail(
        val id: String,
        val userId: String,
        val title: String,
        val pageCount: Int,
        val illustType: Int,
        val isBookmarked: Boolean
    )

    private var csrfToken: String? = null

    init {
        if (!SessionStore.isLoggedIn(context)) {
            throw IOException("Pixiv session is not connected.")
        }
        NetworkCookies.install(context)
    }

    fun userProfile(userId: String): UserProfile {
        val root = getJson("$BASE/ajax/user/$userId?full=1&lang=en")
        val body = root.getJSONObject("body")
        return UserProfile(
            userId = body.optString("userId", userId),
            name = body.optString("name", "Pixiv user $userId"),
            imageUrl = body.optString("imageBig", body.optString("image", ""))
                .takeIf { it.isNotBlank() }
        )
    }

    fun userArtworkIds(userId: String): List<String> {
        val root = getJson("$BASE/ajax/user/$userId/profile/all?lang=en")
        val body = root.getJSONObject("body")
        val ids = linkedSetOf<String>()
        for (key in listOf("illusts", "manga")) {
            val obj = body.optJSONObject(key) ?: continue
            val iterator = obj.keys()
            while (iterator.hasNext()) ids += iterator.next()
        }
        return ids.sortedByDescending { it.toLongOrNull() ?: 0L }
    }

    fun artworkDetail(id: String): ArtworkDetail {
        val root = getJson("$BASE/ajax/illust/$id?lang=en")
        val body = root.getJSONObject("body")
        return ArtworkDetail(
            id = body.getString("id"),
            userId = body.getString("userId"),
            title = body.optString("title", ""),
            pageCount = body.optInt("pageCount", 1),
            illustType = body.optInt("illustType", 0),
            isBookmarked = !body.isNull("bookmarkData")
        )
    }

    fun pageOriginalUrls(id: String): List<String> {
        val root = getJson("$BASE/ajax/illust/$id/pages?lang=en")
        val body: JSONArray = root.getJSONArray("body")
        return buildList {
            for (i in 0 until body.length()) {
                add(body.getJSONObject(i).getJSONObject("urls").getString("original"))
            }
        }
    }

    fun bookmark(id: String) {
        val token = csrfToken ?: fetchCsrfToken().also { csrfToken = it }
        val payload = JSONObject()
            .put("illust_id", id)
            .put("restrict", 0)
            .put("comment", "")
            .put("tags", JSONArray())
            .toString()

        try {
            postJson("$BASE/ajax/illusts/bookmarks/add", payload, token)
        } catch (e: HttpStatusException) {
            if (e.code == 403) {
                val fresh = fetchCsrfToken().also { csrfToken = it }
                postJson("$BASE/ajax/illusts/bookmarks/add", payload, fresh)
            } else {
                throw e
            }
        }
    }

    private fun fetchCsrfToken(): String {
        val html = requestText("$BASE/", "GET", null, null)
        return PixivCsrf.extract(html)
            ?: throw IOException("Could not read Pixiv CSRF token. Reconnect Pixiv and try again.")
    }

    private fun getJson(url: String): JSONObject {
        val root = JSONObject(requestText(url, "GET", null, null))
        if (root.optBoolean("error", false)) {
            throw IOException(root.optString("message", "Pixiv returned an error"))
        }
        return root
    }

    private fun postJson(url: String, body: String, csrf: String): JSONObject {
        val root = JSONObject(requestText(url, "POST", body, csrf))
        if (root.optBoolean("error", false)) {
            throw IOException(root.optString("message", "Pixiv returned an error"))
        }
        return root
    }

    private fun requestText(
        urlString: String,
        method: String,
        body: String?,
        csrf: String?
    ): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 45_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.8")
        conn.setRequestProperty("Referer", "$BASE/")
        if (!csrf.isNullOrBlank()) conn.setRequestProperty("X-CSRF-TOKEN", csrf)

        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()

        if (code !in 200..299) {
            throw HttpStatusException(code, "HTTP $code from Pixiv: ${responseText.take(240)}")
        }
        return responseText
    }
}

class HttpStatusException(val code: Int, message: String) : IOException(message)
