package com.azrael.galleryflow

/**
 * Kuroha is separate. Kyora preserves original site IDs, full gallery crawlers,
 * a flat media output policy, and generic user-added website support.
 */
object AdapterRegistry {
    private val http=HttpClient()
    private val generic=GenericSiteAdapter(http)
    private val kiutaku=KiutakuAdapter(http)
    private val hd4=KyoraSiteAdapter(SourceId.FOUR_K_HD,generic)
    private val buondua=KyoraSiteAdapter(SourceId.BUONDUA,generic)
    private val cosplayTele=KyoraSiteAdapter(SourceId.COSPLAYTELE,generic)
    val adapters:List<SourceAdapter>=listOf(kiutaku,hd4,buondua,cosplayTele,generic)

    fun available(source:SourceId):Boolean=source in setOf(
        SourceId.KIUTAKU,SourceId.FOUR_K_HD,SourceId.BUONDUA,
        SourceId.COSPLAYTELE,SourceId.GENERIC
    )
    fun forUrl(url:String):SourceAdapter {
        if(kiutaku.matches(url)&&Regex(
            "https?://[^/]+/(tag/\\d+|\\d+)(?:[/?#].*)?$",
            RegexOption.IGNORE_CASE).matches(url.trim()))return kiutaku
        return listOf(hd4,buondua,cosplayTele).firstOrNull{it.matches(url)}
            ?:generic.takeIf{it.matches(url)}
            ?:throw AdapterException("Enter an HTTP(S) cosplay or gallery website URL.")
    }
    fun forSource(source:SourceId):SourceAdapter=when(source){
        SourceId.KIUTAKU->kiutaku
        SourceId.FOUR_K_HD->hd4
        SourceId.BUONDUA->buondua
        SourceId.COSPLAYTELE->cosplayTele
        SourceId.GENERIC->generic
        else->throw AdapterException("Source requires an updated provider. Existing catalog is retained.")
    }
}
