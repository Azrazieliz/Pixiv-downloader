package com.azrael.galleryflow

import org.junit.Assert.*
import org.junit.Test

/**
 * The shipping Nocturne catalog contains only Kuroha (outside this registry)
 * and Kyora (the Kiutaku cosplay adapter plus user-added generic websites).
 */
class CoreOnlyRegistryTest {
    @Test fun onlyCoreGalleryAdaptersShip() {
        assertEquals(setOf(SourceId.KIUTAKU, SourceId.GENERIC),
            AdapterRegistry.adapters.map { it.source }.toSet())
        assertEquals(2, AdapterRegistry.adapters.size)
    }

    @Test fun manualWebsitesAreNotPreinstalledProviders() {
        assertEquals(SourceId.GENERIC,
            AdapterRegistry.forUrl("https://www.pornpics.com/galleries/sample/").source)
        assertEquals(SourceId.GENERIC,
            AdapterRegistry.forUrl("https://example.com/cosplay/artist/gallery").source)
        assertEquals(SourceId.KIUTAKU,
            AdapterRegistry.forUrl("https://kiutaku.com/tag/123").source)
    }

    @Test fun retiredWireIdsRemainReadableButCannotRun() {
        assertEquals(SourceId.RULE34, SourceId.fromWire("rule34"))
        assertFalse(AdapterRegistry.available(SourceId.RULE34))
        assertFalse(AdapterRegistry.available(SourceId.DANBOORU))
        assertFalse(AdapterRegistry.available(SourceId.FOUR_K_HD))
        assertTrue(AdapterRegistry.available(SourceId.GENERIC))
        try {
            AdapterRegistry.forSource(SourceId.E621)
            fail("Retired source unexpectedly enabled")
        } catch (expected: AdapterException) {
            assertTrue(expected.message.orEmpty().contains("Retired"))
        }
    }
}
