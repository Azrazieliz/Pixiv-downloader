package com.azrael.galleryflow

import java.net.URI

/**
 * Reuses Kyora HTML pagination, detail pages and original-image extraction.
 * Preserves historical site IDs and FlatStoragePolicy (no album folders).
 */
class KyoraSiteAdapter(
    override val source:SourceId,
    private val core:GenericSiteAdapter
):SourceAdapter{
    override val enabled=true
    override val statusLabel="Kyora multi-method HTML source"
    private val hosts=when(source){
        SourceId.FOUR_K_HD->setOf("4khd.com")
        SourceId.BUONDUA->setOf("buondua.net","buondua.com")
        SourceId.COSPLAYTELE->setOf("cosplaytele.com")
        else->error("Unsupported Kyora source")
    }
    override fun matches(url:String):Boolean = runCatching{
        val host=URI(url).host.orEmpty().lowercase().removePrefix("www.")
        hosts.any{host==it || host.endsWith("."+it)}
    }.getOrDefault(false)
    override fun resolveEntity(inputUrl:String):SourceEntity{
        require(matches(inputUrl)){"Not a recognized "+source.wireName+" URL"}
        return core.resolveEntity(inputUrl).copy(source=source)
    }
    override fun enumerateGalleries(entity:SourceEntity):Sequence<GalleryRef> =
        core.enumerateGalleries(entity.copy(source=SourceId.GENERIC))
            .map{it.copy(source=source)}
    override fun fetchGallery(gallery:GalleryRef):Pair<GalleryMeta,List<MediaRef>>{
        val (meta,items)=core.fetchGallery(gallery.copy(source=SourceId.GENERIC))
        return meta.copy(source=source) to items.map{it.copy(source=source)}
    }
}
