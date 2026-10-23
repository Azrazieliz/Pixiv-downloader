package com.azrael.galleryflow

interface SourceAdapter {
    val source: SourceId
    val enabled: Boolean
    val statusLabel: String

    fun matches(url: String): Boolean
    fun resolveEntity(inputUrl: String): SourceEntity
    fun enumerateGalleries(entity: SourceEntity): Sequence<GalleryRef>
    fun fetchGallery(gallery: GalleryRef): Pair<GalleryMeta, List<MediaRef>>
}

/** Implements a resumable numeric page cursor, independent of Live scans. */
interface PagedBackfillAdapter : SourceAdapter {
    fun backfillFrom(entity: SourceEntity, page: Int): Sequence<GalleryRef>
    fun pageIndex(ref: GalleryRef): Int
}
