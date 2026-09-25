package com.azrael.pixivdumpsync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivLoginVerifierTest {
    @Test
    fun acceptsAuthenticatedAjaxResponse() {
        assertTrue(
            PixivLoginVerifier.isAuthenticatedResponse(
                """{"error":false,"body":{"userId":"123"}}"""
            )
        )
    }

    @Test
    fun rejectsPixivErrorResponse() {
        assertFalse(
            PixivLoginVerifier.isAuthenticatedResponse(
                """{"error":true,"message":"Login required"}"""
            )
        )
    }

    @Test
    fun rejectsMalformedResponse() {
        assertFalse(PixivLoginVerifier.isAuthenticatedResponse("not-json"))
    }
}
