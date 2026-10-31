package com.azrael.pixivdumpsync

import org.junit.Assert.*
import org.junit.Test

class SearchRankingTest {
    @Test fun configuredProviderList() {
        val list=SearchRanking.parseProviders(
            "searxng|https://search.example|General\n"+
            "html|https://media.example/search?q={query}|Mirror\n"+
            "searxng|http://searcher.onion|Index")
        assertEquals(3,list.size)
        assertEquals(SearchProviderKind.HTML,list[1].kind)
    }
    @Test fun rejectsInternalAddressesAndMalformedTemplates() {
        assertFalse(SearchRanking.validUrl("http://127.0.0.1/test"))
        assertFalse(SearchRanking.validUrl("http://localhost/test"))
        assertTrue(SearchRanking.validUrl("https://media.example/post/12"))
        assertThrows(IllegalArgumentException::class.java){
            SearchRanking.parseProviders("html|https://media.example/search")
        }
    }
    @Test fun removesTrackingOnlyFromDeduplicationKey() {
        val original="https://www.example.org/watch/a?utm_source=test&id=5"
        val plain="https://example.org/watch/a?id=5"
        val result=SearchRanking.deduplicate("creator",listOf(
            RankedHit("creator video",original,"index"),
            RankedHit("other video",plain,"mirror")))
        assertEquals(1,result.size)
        assertEquals(original,result.first().url)
    }
    @Test fun rejectsIrrelevantResultsAndNonvideoPages() {
        val result=SearchRanking.deduplicate("rare phrase",listOf(
            RankedHit("Other title","https://example.com/watch/2","mirror"),
            RankedHit("Rare Phrase recording","https://example.com/watch/1","index")))
        assertEquals(1,result.size)
        assertTrue(result[0].title.contains("Rare Phrase"))
        assertFalse(SearchRanking.isVideoUrl("https://example.com/category/rare-phrase"))
        assertTrue(SearchRanking.isVideoUrl("https://www.pornhub.com/view_video.php?viewkey=abc"))
        assertFalse(SearchRanking.isVideoUrl("https://www.pornhub.com/video/search?search=rare"))
    }
}
