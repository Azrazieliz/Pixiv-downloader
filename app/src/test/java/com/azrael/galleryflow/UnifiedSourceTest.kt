package com.azrael.galleryflow

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class UnifiedSourceTest {
    private val engine = GenericSiteAdapter(HttpClient())

    @Test fun siteHostAndLinkDiscovery() {
        val doc = Jsoup.parse("""
            <main><a href="/albums/123">Album one</a>
            <a href="/posts/456">Another post</a>
            <a href="https://other.example.com/gallery/77">Off-site</a></main>
        """.trimIndent(), "https://example.com/")
        val links = engine.discoverGalleryLinks(doc, "example.com")
        assertEquals(2, links.size)
        assertTrue(links[0].first.contains("/albums/123"))
    }

    @Test fun staticImageAndTagExtraction() {
        val doc = Jsoup.parse("""
          <html><head><meta name="keywords" content="Cosplay, Blue Archive"></head>
          <body><main><img data-src="/images/full.jpg" width="1200" height="900" />
          <img src="/assets/logo.png" width="80" height="80" /></main></body></html>
        """.trimIndent(), "https://example.com/")
        assertEquals(listOf("https://example.com/images/full.jpg"), engine.extractImageLinks(doc))
        assertEquals("Blue Archive", TagExclusions.firstBlocked(engine.extractTags(doc), "blue archive"))
    }

    @Test fun unsupportedSchemesAndPrivateHostsRejected() {
        assertFalse(engine.matches("file:///etc/passwd"))
        assertFalse(engine.matches("http://localhost/image"))
        assertFalse(engine.matches("http://192.168.0.1/gallery"))
        assertTrue(engine.matches("https://example.com/gallery/123"))
    }

    @Test fun flatStorageNamesPreserveUniqueGalleryId() {
        val one = MediaRef(SourceId.GENERIC, "x", "gallery_one", 0, "https://example.com/1.jpg", "https://example.com/1.jpg", "https://example.com")
        val two = one.copy(galleryStableId = "gallery_two")
        assertNotEquals(FlatStoragePolicy.filename(one, "jpg"), FlatStoragePolicy.filename(two, "jpg"))
    }
}
