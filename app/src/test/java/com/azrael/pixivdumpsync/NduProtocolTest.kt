package com.azrael.pixivdumpsync

import org.junit.Assert.*
import org.junit.Test

class NduProtocolTest {
    @Test fun ndaInputCanBeRawOrBrowserCookie() {
        assertEquals("abcdef0123456789",NduSettings.parseNdus("abcdef0123456789"))
        assertEquals("abcdef0123456789",NduSettings.parseNdus(
            "Cookie: other=foo; ndus=abcdef0123456789; lang=en"))
        assertEquals("abcdef0123456789",NduSettings.parseNdus(
            "ndus=abcdef0123456789"))
    }
    @Test fun badCredentialsAreRejected() {
        listOf("abc","foo=zz; bar=aa","ndus=\r\nsecret","ndus=abc; oth=foo").forEach { input ->
            try { NduSettings.parseNdus(input); fail("Unsafe NDUS accepted") }
            catch (expected:IllegalArgumentException) { }
        }
    }
    @Test fun cloudPathsCannotEscapeTarget() {
        assertEquals("/Nocturne/Kyora",NduSettings.validateDirectory("/Nocturne/Kyora/"))
        listOf("../test","/../x","/x\\y","/x?yy","/x//y","/","/x\nb").forEach {
            try { NduSettings.validateDirectory(it); fail("Unsafe directory $it") }
            catch (expected:IllegalArgumentException) { }
        }
    }
    @Test fun chunkHostCannotSendNdusToOtherServer() {
        assertEquals("jp-data.terabox.com",
            NduTeraboxClient.checkedUploadHost("jp-data.terabox.com"))
        listOf("evil.test","evil.terabox.com.evil.test","http://terabox.com",
            "terabox.com").forEach {
            try { NduTeraboxClient.checkedUploadHost(it); fail("Untrusted host allowed") }
            catch (expected:IllegalArgumentException) { }
        }
    }
    @Test fun stablePartMd5AndEscaping() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72",
            NduTeraboxClient.partHashes("abc".toByteArray()))
        assertEquals("path=%2FNocturne%2FKyora%2Fimage+1.png",
            NduTeraboxClient.formEncode(mapOf("path" to "/Nocturne/Kyora/image 1.png")))
    }
}
