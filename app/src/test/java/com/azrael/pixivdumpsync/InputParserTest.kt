package com.azrael.pixivdumpsync

import org.junit.Assert.assertEquals
import org.junit.Test

class InputParserTest {
    @Test
    fun parsesIdsAndUrls() {
        val parsed = InputParser.parseUserIds(
            "12345 https://www.pixiv.net/en/users/67890, https://www.pixiv.net/users/12345/"
        )
        assertEquals(listOf("12345", "67890"), parsed)
    }
}
