package com.azrael.galleryflow

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class BackfillPaginationTest {
    private val generic = GenericSiteAdapter(HttpClient())

    @Test fun followsNumberedNextListingPage() {
        val base = "https://example.org/albums?page=1"
        val doc = Jsoup.parse("""
            <a href="/gallery/x">gallery</a>
            <a class="next" href="/albums?page=2">Next</a>
        """, base)
        assertEquals("https://example.org/albums?page=2",
            generic.nextListingPage(doc, base, setOf(base)))
    }

    @Test fun doesNotJumpToUnrelatedNextGallery() {
        val url = "https://example.org/album/first"
        val doc = Jsoup.parse("""
            <a rel="next" href="/album/second">Next gallery</a>
            <a href="/album/first?page=2">2</a>
        """, url)
        assertEquals("https://example.org/album/first?page=2",
            generic.nextListingPage(doc, url, setOf(url)))
    }

    @Test fun followsPathPaginationAndStopsAtVisitedLink() {
        val page1 = "https://example.org/tags/artist"
        val page2 = "https://example.org/tags/artist/page/2"
        val doc = Jsoup.parse("""<a href="/tags/artist/page/2">2</a>""", page1)
        assertEquals(page2, generic.nextListingPage(doc, page1, setOf(page1)))
        assertNull(generic.nextListingPage(doc, page1, setOf(page1, page2)))
    }

    @Test fun noPaginationWithoutLegitimateLink() {
        val url = "https://example.org/posts?tags=artist"
        val doc = Jsoup.parse("""
            <a href="/posts/323">Post</a>
            <a href="https://outsider.test/posts?page=2" rel="next">Next</a>
        """, url)
        assertNull(generic.nextListingPage(doc, url, setOf(url)))
    }

    @Test fun numericBooruPagingIsResumableAndUncappedAtEight() {
        val adult = AdultBooruAdapter(SourceId.RULE34)
        val entity = adult.resolveEntity(
            "https://rule34.xxx/index.php?page=post&s=list&tags=artist_tag")
        assertEquals(0, adult.pageIndex(GalleryRef(SourceId.RULE34, "g", entity.stableId,
            entity.canonicalUrl, "Page one")))
        val next = entity.canonicalUrl.replace("pid=0", "pid=16")
        assertEquals(16, adult.pageIndex(GalleryRef(SourceId.RULE34, "g", entity.stableId,
            next, "Page seventeen")))
        assertTrue(AdultBooruAdapter.MAX_BACKFILL_PAGES > 8)
    }
}
