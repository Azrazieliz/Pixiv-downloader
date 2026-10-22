package com.azrael.pixivdumpsync

import org.junit.Assert.*
import org.junit.Test

class TagExclusionPolicyTest {
    @Test fun exactCaseInsensitiveAndUnicode() {
        val blocked = TagExclusionPolicy.parse("R-18\nＦｏｏ\nlandscape\n")
        assertEquals(3, blocked.size)
        assertEquals("r-18", TagExclusionPolicy.firstBlockedTag(listOf("art", "r-18"), blocked))
        assertEquals("Foo", TagExclusionPolicy.firstBlockedTag(listOf("Foo"), blocked))
        assertNull(TagExclusionPolicy.firstBlockedTag(listOf("landscapes", "R-18G"), blocked))
    }

    @Test fun noTagsOrEmptyRules() {
        assertNull(TagExclusionPolicy.firstBlockedTag(listOf("example"), emptySet()))
        assertNull(TagExclusionPolicy.firstBlockedTag(emptyList(), TagExclusionPolicy.parse("example")))
    }
}
