package com.azrael.pixivdumpsync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivWebLoginResultTest {
    @Test
    fun acceptsJavascriptTrue() {
        assertTrue(PixivWebLoginResult.isAuthenticated("true"))
    }

    @Test
    fun rejectsJavascriptFalseOrNull() {
        assertFalse(PixivWebLoginResult.isAuthenticated("false"))
        assertFalse(PixivWebLoginResult.isAuthenticated("null"))
        assertFalse(PixivWebLoginResult.isAuthenticated(null))
    }
}
