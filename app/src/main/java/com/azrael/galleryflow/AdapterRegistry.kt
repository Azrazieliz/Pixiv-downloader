package com.azrael.galleryflow

object AdapterRegistry {
    private val http = HttpClient()
    private val generic = GenericSiteAdapter(http)
    private val rule34 = AdultBooruAdapter(SourceId.RULE34)
    private val gelbooru = AdultBooruAdapter(SourceId.GELBOORU)
    private val danbooru = AdultBooruAdapter(SourceId.DANBOORU)
    private val e621 = AdultBooruAdapter(SourceId.E621)
    val adapters: List<SourceAdapter> = listOf(
        rule34, gelbooru, danbooru, e621,
        KiutakuAdapter(http),
        AuditedPlaceholderAdapter(
            SourceId.FOUR_K_HD,
            setOf("4khd.com", "www.4khd.com"),
            "Audited: /content/... galleries and /pages listings; implementation pending after Kiutaku validation."
        ),
        AuditedPlaceholderAdapter(
            SourceId.BUONDUA,
            setOf("buondua.net", "www.buondua.net"),
            "Audited: /tags/... entity pages, ?page=N pagination and /albums/... galleries; implementation pending."
        ),
        AuditedPlaceholderAdapter(
            SourceId.COSPLAYTELE,
            setOf("cosplaytele.com", "www.cosplaytele.com"),
            "Audited: WordPress-style gallery posts and creator/category archives; implementation pending."
        )
    ) + generic

    fun forUrl(url: String): SourceAdapter =
        adapters.firstOrNull {
            it.enabled && it.matches(url) &&
                (it.source != SourceId.KIUTAKU ||
                    Regex("https?://[^/]+/(tag/\\d+|\\d+)(?:[/?#].*)?$",
                        RegexOption.IGNORE_CASE).matches(url.trim()))
        }
            ?: generic.takeIf { it.matches(url) }
            ?: throw AdapterException("Enter a public HTTP or HTTPS image-site URL.")

    fun forSource(source: SourceId): SourceAdapter =
        if (source == SourceId.GENERIC) generic else adapters.first { it.source == source }
}

class AuditedPlaceholderAdapter(
    override val source: SourceId,
    private val hosts: Set<String>,
    override val statusLabel: String
) : SourceAdapter {
    override val enabled: Boolean = false
    override fun matches(url: String): Boolean = runCatching {
        val host = java.net.URI(url).host?.lowercase().orEmpty()
        hosts.any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)

    private fun unavailable(): Nothing = throw AdapterException(statusLabel)
    override fun resolveEntity(inputUrl: String): SourceEntity = unavailable()
    override fun enumerateGalleries(entity: SourceEntity): Sequence<GalleryRef> = unavailable()
    override fun fetchGallery(gallery: GalleryRef): Pair<GalleryMeta, List<MediaRef>> = unavailable()
}
