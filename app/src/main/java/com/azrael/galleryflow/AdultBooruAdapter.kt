package com.azrael.galleryflow

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/** Bounded public booru API reader. Exact tags are evaluated per post. */
class AdultBooruAdapter(override val source: SourceId) : PagedBackfillAdapter {
    override val enabled = true
    override val statusLabel = "Tagged-image API"
    private val gate = Any()
    private var lastRequestMs = 0L
    private val host: String = when(source) {
        SourceId.RULE34 -> "rule34.xxx"
        SourceId.GELBOORU -> "gelbooru.com"
        SourceId.DANBOORU -> "danbooru.donmai.us"
        SourceId.E621 -> "e621.net"
        else -> error("Unsupported booru source")
    }

    override fun matches(url: String): Boolean = runCatching {
        val domain = URI(withScheme(url)).host?.lowercase(Locale.ROOT).orEmpty().removePrefix("www.")
        domain == host || (source == SourceId.DANBOORU && domain == "shima.donmai.us")
    }.getOrDefault(false)

    override fun resolveEntity(inputUrl: String): SourceEntity {
        val uri = URI(withScheme(inputUrl))
        require(matches(inputUrl)) { "Unexpected image site" }
        val params = queryParameters(uri)
        val fromPostPage = if (source == SourceId.RULE34 || source == SourceId.GELBOORU) {
            if (params["s"]?.firstOrNull() == "view") params["id"]?.firstOrNull()
            else null
        } else Regex("^/posts/(\\d+)").find(uri.path)?.groupValues?.get(1)
        val search = fromPostPage?.takeIf { it.all(Char::isDigit) }?.let { "id:$it" }
            ?: searchTags(uri)
        return SourceEntity(source, "query:" + digest(host + "|" + search).take(30),
            (if (search.isBlank()) "$host / recent" else "$host / $search").take(96),
            apiUrl(search, 0), "booru")
    }

    // Live inspects just the newest window. Backfill has its own saved cursor.
    override fun enumerateGalleries(entity: SourceEntity): Sequence<GalleryRef> =
        backfillFrom(entity, 0).take(LIVE_PAGES)

    override fun backfillFrom(entity: SourceEntity, page: Int): Sequence<GalleryRef> = sequence {
        val query = searchTags(URI(entity.canonicalUrl))
        var current = page.coerceAtLeast(0)
        val seenSignatures = hashSetOf<String>()
        while (current < MAX_BACKFILL_PAGES) {
            if (!SyncControl.checkpoint()) return@sequence
            val found = posts(query, current)
            if (found.isEmpty()) return@sequence
            val latest = found.first().optLong("id", -1L)
            val oldest = found.last().optLong("id", -1L)
            // A broken pagination API sometimes silently returns page one forever.
            val signature = "$latest:$oldest"
            if (!seenSignatures.add(signature)) {
                throw AdapterException("The image API repeated page data at page " +
                    (current + 1) + ". Backfill paused; retry or change search tags.")
            }
            val key = digest(entity.stableId + "/" + current + "/" + latest).take(28)
            val title = host + " / " + (if (query.isBlank()) "recent" else query) +
                " / page " + (current + 1)
            yield(GalleryRef(source, key, entity.stableId, apiUrl(query, current), title))
            current++
            if (found.size < PAGE_SIZE) return@sequence
        }
        throw AdapterException("Backfill safety limit reached at page " + current +
            ". Page cursor was saved; resume is available.")
    }

    override fun pageIndex(ref: GalleryRef): Int {
        val uri = URI(ref.canonicalUrl)
        val params = queryParameters(uri)
        return params["pid"]?.firstOrNull()?.toIntOrNull()
            ?: params["page"]?.firstOrNull()?.toIntOrNull()?.minus(1) ?: 0
    }

    override fun fetchGallery(gallery: GalleryRef): Pair<GalleryMeta,List<MediaRef>> {
        val uri = URI(gallery.canonicalUrl)
        val query = searchTags(uri)
        val params = queryParameters(uri)
        val page = params["pid"]?.firstOrNull()?.toIntOrNull()
            ?: params["page"]?.firstOrNull()?.toIntOrNull()?.minus(1) ?: 0
        val posts = posts(query, page.coerceAtLeast(0))
        val media = posts.mapIndexedNotNull { index, post ->
            val file = if (source == SourceId.E621) {
                post.optJSONObject("file")?.optString("url")?.takeIf(String::isNotBlank)
                    ?: post.optJSONObject("sample")?.optString("url")
            } else post.optString("file_url").takeIf(String::isNotBlank)
                ?: post.optString("sample_url")
            val url = file?.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
            val resolved = if (url.startsWith("//")) "https:$url" else url
            if (!resolved.startsWith("https://", true) && !resolved.startsWith("http://", true))
                return@mapIndexedNotNull null
            val ext = runCatching {
                URI(resolved).path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            }.getOrDefault("")
            if (ext !in setOf("jpg", "jpeg", "png", "webp", "gif", "avif"))
                return@mapIndexedNotNull null
            val id = post.optLong("id", -1L)
            if (id <= 0) return@mapIndexedNotNull null
            MediaRef(source, id.toString(), gallery.stableId, index, resolved,
                resolved.substringBefore('?'), "https://$host/",
                when(ext) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "avif" -> "image/avif"
                    else -> null
                }, postTags(post))
        }
        if (posts.isNotEmpty() && media.isEmpty()) {
            throw AdapterException("Posts have no available image files (video results are omitted).")
        }
        val meta = GalleryMeta(source, gallery.stableId, gallery.entityStableId,
            gallery.canonicalUrl, gallery.title,
            query.split(' ').filter(String::isNotBlank), null, listOf(gallery.canonicalUrl))
        return meta to media
    }

    internal fun postTags(post: JSONObject): List<String> = when(source) {
        SourceId.E621 -> {
            val tags = post.optJSONObject("tags") ?: JSONObject()
            val result = ArrayList<String>()
            val keys = tags.keys()
            while (keys.hasNext()) {
                val array = tags.optJSONArray(keys.next()) ?: continue
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf(String::isNotBlank)?.let(result::add)
                }
            }
            result.distinct()
        }
        SourceId.DANBOORU -> post.optString("tag_string")
            .split(' ').filter(String::isNotBlank)
        else -> post.optString("tags").split(' ').filter(String::isNotBlank)
    }

    private fun posts(query: String, page: Int): List<JSONObject> {
        if (!SyncControl.checkpoint()) throw SyncCancelledException()
        val payload = fetchJson(apiUrl(query, page))
        val array = when(payload) {
            is JSONArray -> payload
            is JSONObject -> payload.optJSONArray("post")
                ?: payload.optJSONArray("posts") ?: JSONArray()
            else -> JSONArray()
        }
        val result = ArrayList<JSONObject>()
        for (i in 0 until array.length()) array.optJSONObject(i)?.let(result::add)
        return result
    }

    private fun fetchJson(url: String): Any {
        synchronized(gate) {
            val diff = System.currentTimeMillis() - lastRequestMs
            val interval = if (source == SourceId.E621) 1200L else 400L
            if (diff < interval) Thread.sleep(interval - diff)
            lastRequestMs = System.currentTimeMillis()
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        NetworkRequestRegistry.register(conn)
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent",
                "AsterionDownloader/0.8.1 (+https://github.com/Azrazieliz/Pixiv-downloader)")
            val status = conn.responseCode
            if (status !in 200..299)
                throw HttpStatusException(status, "API HTTP $status from $host")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8)
                .use { it.readText().take(MAX_RESPONSE_CHARS) }.trimStart()
            return if (body.startsWith("[")) JSONArray(body) else JSONObject(body)
        } finally {
            NetworkRequestRegistry.unregister(conn)
            conn.disconnect()
        }
    }

    internal fun searchTags(uri: URI): String = queryParameters(uri)["tags"]
        ?.firstOrNull()?.trim()?.take(200).orEmpty()

    private fun apiUrl(tags: String, page: Int): String {
        val encoded = URLEncoder.encode(tags, "UTF-8")
        return when(source) {
            SourceId.RULE34, SourceId.GELBOORU ->
                "https://$host/index.php?page=dapi&s=post&q=index&json=1&limit=$PAGE_SIZE&pid=$page&tags=$encoded"
            SourceId.DANBOORU, SourceId.E621 ->
                "https://$host/posts.json?limit=$PAGE_SIZE&page=" + (page + 1) + "&tags=$encoded"
            else -> error("Unsupported source")
        }
    }

    private fun queryParameters(uri: URI): Map<String,List<String>> {
        val result = linkedMapOf<String,MutableList<String>>()
        for (piece in uri.rawQuery.orEmpty().split('&')) {
            if (piece.isBlank()) continue
            val k = URLDecoder.decode(piece.substringBefore('='), "UTF-8")
            val v = URLDecoder.decode(piece.substringAfter('=', ""), "UTF-8")
            result.getOrPut(k) { mutableListOf() }.add(v)
        }
        return result
    }

    private fun withScheme(value: String) = if (
        value.startsWith("http://", true) || value.startsWith("https://", true)) value
        else "https://$value"

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val PAGE_SIZE = 75
        const val LIVE_PAGES = 2
        const val MAX_BACKFILL_PAGES = 10000
        private const val MAX_RESPONSE_CHARS = 4_000_000
    }
}
