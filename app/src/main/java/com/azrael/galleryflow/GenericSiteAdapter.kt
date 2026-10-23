package com.azrael.galleryflow

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/** Bounded, public-HTML image scraper. JavaScript or protected pages need dedicated adapters. */
class GenericSiteAdapter(private val http: HttpClient) : SourceAdapter {
    override val source = SourceId.GENERIC
    override val enabled = true
    override val statusLabel = "Public HTML • image and gallery discovery"

    override fun matches(url: String): Boolean = runCatching { validUri(url); true }.getOrDefault(false)

    override fun resolveEntity(inputUrl: String): SourceEntity {
        val uri = validUri(inputUrl)
        val url = canonical(uri)
        val doc = http.document(url)
        val title = doc.selectFirst("meta[property=og:site_name]")?.attr("content")
            ?.takeIf(String::isNotBlank)
            ?: doc.title().substringBefore("|").trim().takeIf(String::isNotBlank)
            ?: uri.host
        val galleryOnly = looksGalleryPage(uri.path) || uri.path.endsWith(".html", ignoreCase = true)
        return SourceEntity(source, "url:" + digest(url).take(32),
            title.take(90), url, if (galleryOnly) "gallery_only" else "site")
    }

    override fun enumerateGalleries(entity: SourceEntity): Sequence<GalleryRef> = sequence {
        val url = canonical(validUri(entity.canonicalUrl))
        if (entity.kind == "gallery_only") {
            yield(toGallery(entity, url, entity.displayName))
            return@sequence
        }
        val doc = http.document(url)
        val host = URI(url).host.lowercase(Locale.ROOT)
        val candidates = discoverGalleryLinks(doc, host).take(MAX_GALLERIES)
        if (candidates.isEmpty()) {
            yield(toGallery(entity, url, doc.title().ifBlank { entity.displayName }))
        } else {
            // Each site remains a separate tracked source; only the discovered links are crawled.
            for ((link, title) in candidates) yield(toGallery(entity, link, title))
        }
    }

    override fun fetchGallery(gallery: GalleryRef): Pair<GalleryMeta,List<MediaRef>> {
        val url = canonical(validUri(gallery.canonicalUrl))
        val doc = http.document(url)
        val links = extractImageLinks(doc).take(MAX_IMAGES)
        if (links.isEmpty()) throw AdapterException(
            "No static gallery images detected; this site may require JavaScript or an adapter.")
        val meta = GalleryMeta(source, gallery.stableId, gallery.entityStableId, url,
            doc.selectFirst("h1")?.text()?.takeIf(String::isNotBlank)
                ?: doc.title().ifBlank { gallery.title },
            extractTags(doc), null, listOf(url))
        val media = links.mapIndexed { index, link ->
            val normalized = URI(link).let {
                URI(it.scheme, it.authority, it.path, null, null).toString()
            }
            MediaRef(source, digest(url + "|" + normalized).take(32),
                gallery.stableId, index, link, normalized, url, mime(link))
        }
        return meta to media
    }

    internal fun extractTags(doc: Document): List<String> {
        val meta = doc.select("meta[name=keywords],meta[property=article:tag]")
            .flatMap { e -> e.attr("content").split(',') }
        val anchors = doc.select("a[rel=tag],a[href*=/tag/],a[href*=/tags/]")
            .map { it.text() }
        return (meta + anchors).map { it.trim() }.filter(String::isNotEmpty).distinct().take(100)
    }

    internal fun extractImageLinks(doc: Document): List<String> {
        val out = linkedSetOf<String>()
        val source = doc.baseUri()
        val nodes = doc.select("article img,main img,.gallery img,.entry-content img,.post-content img,figure img")
            .ifEmpty { doc.select("img") }
        for (img in nodes) {
            val raw = listOf("data-original", "data-full", "data-src", "data-lazy-src", "src")
                .firstNotNullOfOrNull { attribute -> img.attr(attribute).takeIf(String::isNotBlank) }
                ?: img.attr("srcset").substringAfterLast(',').trim().substringBefore(' ')
            if (raw.isBlank()) continue
            val url = absolute(source, raw) ?: continue
            val lower = url.lowercase(Locale.ROOT)
            if (listOf("favicon", "avatar", "logo", "emoji", "tracking", "pixel", "icon", "banner", "ads/").any { it in lower }) continue
            val w = img.attr("width").toIntOrNull()
            val h = img.attr("height").toIntOrNull()
            if ((w != null && w < 180) || (h != null && h < 180)) continue
            out.add(url)
            if (out.size >= MAX_IMAGES) break
        }
        doc.select("a[href]").forEach { a ->
            if (out.size >= MAX_IMAGES) return@forEach
            val url = absolute(source, a.attr("href")) ?: return@forEach
            if (Regex("\\.(jpe?g|png|gif|webp|avif)(?:\\?.*)?$", RegexOption.IGNORE_CASE)
                    .containsMatchIn(url)) out.add(url)
        }
        if (out.isEmpty()) {
            doc.select("meta[property=og:image]") .forEach { meta ->
                absolute(source, meta.attr("content"))?.let { out.add(it) }
            }
        }
        return out.toList()
    }

    internal fun discoverGalleryLinks(doc: Document, host: String): List<Pair<String,String>> {
        val found = linkedMapOf<String,String>()
        for (a in doc.select("a[href]")) {
            val link = absolute(doc.baseUri(), a.attr("href")) ?: continue
            val uri = runCatching { URI(link) }.getOrNull() ?: continue
            if (uri.host?.lowercase(Locale.ROOT) != host) continue
            if (!looksGalleryPage(uri.path)) continue
            found.putIfAbsent(canonical(uri), a.text().ifBlank { uri.path })
            if (found.size >= MAX_GALLERIES) break
        }
        return found.toList()
    }

    private fun toGallery(entity: SourceEntity, url: String, title: String): GalleryRef =
        GalleryRef(source, digest(url).take(32), entity.stableId, url, title.take(160))

    private fun looksGalleryPage(path: String): Boolean =
        Regex("/(gallery|galleries|album|albums|post|posts|photo|photos|cosplay|sets|images|p)/[^/?#]+",
            RegexOption.IGNORE_CASE).containsMatchIn(path)

    private fun validUri(url: String): URI {
        val text = url.trim().let {
            if (it.startsWith("https://", true) || it.startsWith("http://", true)) it
            else "https://" + it
        }
        val uri = URI(text)
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
        val host = uri.host?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Invalid site hostname")
        require(host.contains('.') && host != "localhost" &&
            !host.endsWith(".local") && !host.endsWith(".internal") &&
            !host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) &&
            !host.startsWith("127.") && !host.startsWith("10.")) {
            "Use a public image-site URL."
        }
        require(uri.userInfo == null) { "Credentials must not be placed inside site URLs." }
        return uri
    }

    private fun canonical(uri: URI) =
        URI(uri.scheme.lowercase(Locale.ROOT), uri.authority,
            uri.path.ifBlank { "/" }, uri.query, null).toString()

    private fun absolute(base: String, raw: String): String? = runCatching {
        val value = URI(base).resolve(raw.trim())
        if (!value.scheme.equals("http", true) && !value.scheme.equals("https", true))
            null else value.toString()
    }.getOrNull()

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun mime(url: String): String? = when (
        URI(url).path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        else -> null
    }

    companion object { const val MAX_GALLERIES = 80; const val MAX_IMAGES = 500 }
}
