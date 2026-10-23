package com.azrael.galleryflow

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class AdultBooruAdapterTest {
    @Test fun recognizesOnlyItsOwnPublicSite() {
        val rule34 = AdultBooruAdapter(SourceId.RULE34)
        val gelbooru = AdultBooruAdapter(SourceId.GELBOORU)
        val danbooru = AdultBooruAdapter(SourceId.DANBOORU)
        val e621 = AdultBooruAdapter(SourceId.E621)
        assertTrue(rule34.matches("https://rule34.xxx/index.php?page=post&s=list&tags=art"))
        assertFalse(rule34.matches("https://notrule34.xxx.example.com/"))
        assertTrue(gelbooru.matches("https://gelbooru.com/index.php?page=post&s=list&tags=foo"))
        assertTrue(danbooru.matches("https://danbooru.donmai.us/posts?tags=foo"))
        assertTrue(e621.matches("https://e621.net/posts?tags=foo"))
        assertFalse(e621.matches("http://localhost/posts"))
    }

    @Test fun acceptsTaggedSearchAndDirectPostURLs() {
        val rule34 = AdultBooruAdapter(SourceId.RULE34)
        val tags = URI("https://rule34.xxx/index.php?page=post&s=list&tags=blue_archive+solo")
        val entity = rule34.resolveEntity(tags.toString())
        assertTrue(entity.displayName.contains("blue archive solo"))
        assertTrue(entity.canonicalUrl.contains("pid=0"))
        val post = rule34.resolveEntity("https://rule34.xxx/index.php?page=post&s=view&id=123456")
        assertTrue(post.displayName.contains("id:123456"))
        assertEquals(SourceId.RULE34, post.source)
    }

    @Test fun extractApiTagsForDifferentBoorus() {
        val rule34 = AdultBooruAdapter(SourceId.RULE34)
        val danbooru = AdultBooruAdapter(SourceId.DANBOORU)
        val e621 = AdultBooruAdapter(SourceId.E621)
        assertEquals(listOf("foo", "bar"),
            rule34.postTags(JSONObject("""{"tags":"foo bar"}""")))
        assertEquals(listOf("artist:foo","sample"),
            danbooru.postTags(JSONObject("""{"tag_string":"artist:foo sample"}""")))
        val post = JSONObject("""{"tags":{"general":["foo"],"artist":["bar"],"species":["foo"]}}""")
        assertEquals(listOf("foo","bar"),e621.postTags(post))
    }

    @Test fun adapterRegistryRoutesSupportedImageSites() {
        assertEquals(SourceId.RULE34,
            AdapterRegistry.forUrl("https://rule34.xxx/").source)
        assertEquals(SourceId.GELBOORU,
            AdapterRegistry.forUrl("https://gelbooru.com/").source)
        assertEquals(SourceId.DANBOORU,
            AdapterRegistry.forUrl("https://danbooru.donmai.us/posts").source)
        assertEquals(SourceId.E621,
            AdapterRegistry.forUrl("https://e621.net/posts").source)
        assertEquals(SourceId.GENERIC,
            AdapterRegistry.forUrl("https://www.imagefap.com/gallery/123456").source)
    }

    @Test fun blockedPictureTagsUseExactMatch() {
        assertEquals("foo",
            TagExclusions.firstBlocked(listOf("foo","bar"), "FOO"))
        assertNull(TagExclusions.firstBlocked(listOf("foobar"), "foo"))
    }
}
