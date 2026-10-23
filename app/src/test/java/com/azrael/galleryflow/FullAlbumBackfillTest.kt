package com.azrael.galleryflow

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test

/** Offline regression tests for large listings and true album originals. */
class FullAlbumBackfillTest {
    private class FakeHttp(private val pages: Map<String, String>): HttpClient() {
        override fun document(url: String, referer: String?): Document =
            Jsoup.parse(pages[url] ?: throw IllegalArgumentException("Missing fake " + url), url)
    }

    @Test fun continuesPastFirst177AlbumPreviews() {
        val first = "https://example.org/albums?page=1"
        val second = "https://example.org/albums?page=2"
        val links1 = (1..177).joinToString("") {
            """<a href="/album/$it"><img src="/thumb/$it.jpg"></a>"""
        }
        val links2 = (178..354).joinToString("") {
            """<a href="/album/$it"><img src="/thumb/$it.jpg"></a>"""
        }
        val adapter = GenericSiteAdapter(FakeHttp(mapOf(
            first to (links1 + """<a rel="next" href="/albums?page=2">Next</a>"""),
            second to links2
        )))
        val entity = adapter.resolveEntity(first)
        val galleries = adapter.enumerateGalleries(entity).toList()
        assertEquals(354, galleries.size)
        assertEquals(354, galleries.map { it.canonicalUrl }.distinct().size)
        assertTrue(galleries.last().canonicalUrl.endsWith("/album/354"))
    }

    @Test fun fullPhotoDetailImagesReplaceAlbumPreviewsAndStayFlat() {
        val album = "https://photos.test/album/999"
        val pages = mapOf(
            album to """
                <h1>Sample Album</h1>
                <a href="/photo/10"><img src="/thumbs/10.jpg" width="230"></a>
                <a href="/photo/11"><img src="/thumbs/11.jpg" width="230"></a>
            """,
            "https://photos.test/photo/10" to """
                <h1>Photo 10</h1>
                <a download href="https://cdn.photos.test/original/10.jpg">
                  <img src="https://cdn.photos.test/thumbs/10.jpg">
                </a>
            """,
            "https://photos.test/photo/11" to """
                <img data-original="https://cdn.photos.test/original/11.png"
                     src="https://cdn.photos.test/thumbs/11.jpg">
            """
        )
        val adapter = GenericSiteAdapter(FakeHttp(pages))
        val entity = adapter.resolveEntity(album)
        val ref = adapter.enumerateGalleries(entity).single()
        val (meta, media) = adapter.fetchGallery(ref)
        assertEquals(2, media.size)
        assertTrue(meta.pageUrls.size >= 3)
        assertEquals("https://cdn.photos.test/original/10.jpg", media[0].url)
        assertEquals("https://cdn.photos.test/original/11.png", media[1].url)
        assertEquals("https://photos.test/photo/10", media[0].referer)
        assertFalse(media.any { it.url.contains("/thumbs/") })
        assertFalse(FlatStoragePolicy.filename(media[0], "jpg").contains("/"))
    }

    @Test fun choosesLargestResponsiveImageInsteadOfSmallPreview() {
        val doc = Jsoup.parse("""
            <picture>
              <source srcset="/img/large.webp 1200w, /img/small.webp 400w">
              <img src="/img/preview.jpg" width="600">
            </picture>
        """, "https://example.org/album/123")
        val items = AlbumImageExtractor.originals(doc, doc.baseUri())
        assertTrue(items.any { it.url.contains("/img/large.webp") })
    }

    @Test fun ignoresThumbnailWhenPhotoOriginalInMetadata() {
        val doc = Jsoup.parse("""
            <meta property="og:image" content="/uploads/original.jpg">
            <img src="/thumbs/preview.jpg" width="300">
        """, "https://example.org/photo/123")
        assertEquals(listOf("https://example.org/uploads/original.jpg"),
            AlbumImageExtractor.originals(doc, doc.baseUri()).map { it.url })
    }

    @Test fun photoPageLinksAreNotAlbumEntities() {
        val doc = Jsoup.parse("""
            <a href="/photo/11"><img src="/thumb/11.jpg"></a>
            <a href="/album/88"><img src="/thumb/album88.jpg"></a>
        """, "https://example.org/albums")
        val adapter = GenericSiteAdapter(HttpClient())
        val links = adapter.discoverGalleryLinks(doc, "example.org")
        assertEquals(1, links.size)
        assertEquals("https://example.org/album/88", links.single().first)
    }
}
