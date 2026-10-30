package com.azrael.pixivdumpsync

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ChapterScraperTest {
    @Test fun sortsFractionalChaptersAndPreservesOriginalOrdering(){
        val original=listOf(
            ChapterLink("https://comics.example/serie/book/chapter-10/","Chapter 10"),
            ChapterLink("https://comics.example/serie/book/chapter-2_5/","Chapter 2.5"),
            ChapterLink("https://comics.example/serie/book/chapter-2/","Chapter 2"),
            ChapterLink("https://comics.example/serie/book/chapter-1/","Chapter 1"))
        val sorted=ChapterScraper.sort(original)
        assertEquals(listOf("Chapter 1","Chapter 2","Chapter 2.5","Chapter 10"),
            sorted.map{it.title})
    }
    @Test fun detectsWholeSeriesVsSingleChapter(){
        assertFalse(ChapterScraper.isSingleChapter(
            "https://toonily.com/serie/moby-dick-17bba228/"))
        assertTrue(ChapterScraper.isSingleChapter(
            "https://toonily.com/serie/moby-dick-17bba228/chapter-12_1/"))
    }
    @Test fun selectsOriginalLazyImagesAndExcludesLogos(){
        val html="""<div class="reading-content">
          <img src="/loading.gif" data-src="/chapter/page-01.webp" />
          <img src="/chapter/page-02.webp" />
          <img src="/images/logo.png" />
        </div>"""
        val doc=Jsoup.parse(html,"https://reader.example/serie/test/chapter-1/")
        assertEquals(listOf("https://reader.example/chapter/page-01.webp",
            "https://reader.example/chapter/page-02.webp"),
            ChapterScraper.parsePageImages(doc))
    }
    @Test fun doesNotConfuseSeriesSlugWithChapterNumber(){
        assertEquals(12.5,ChapterScraper.naturalKey("Chapter 12.5"),0.001)
        assertEquals(Double.MAX_VALUE,ChapterScraper.naturalKey("Moby Dick 17bba228"),0.001)
    }
}
