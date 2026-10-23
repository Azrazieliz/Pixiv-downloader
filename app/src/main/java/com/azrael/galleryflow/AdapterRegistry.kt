package com.azrael.galleryflow

/**
 * Core-only baseline:
 *   - Kuroha / Pixiv has its own engine outside this registry.
 *   - Kyora / cosplay uses Kiutaku plus a generic public HTML site importer
 *     for websites the user adds manually.
 *
 * Removed providers have no registered adapters, scheduled jobs, or bundled
 * selection buttons. Their old DB wire values remain parseable for migration.
 */
object AdapterRegistry {
    private val http = HttpClient()
    private val generic = GenericSiteAdapter(http)
    private val kiutaku = KiutakuAdapter(http)
    val adapters: List<SourceAdapter> = listOf(kiutaku, generic)

    fun available(source: SourceId): Boolean =
        source == SourceId.KIUTAKU || source == SourceId.GENERIC

    fun forUrl(url: String): SourceAdapter =
        if (kiutaku.matches(url) &&
            Regex("https?://[^/]+/(tag/\\d+|\\d+)(?:[/?#].*)?$",
                RegexOption.IGNORE_CASE).matches(url.trim()))
            kiutaku
        else generic.takeIf { it.matches(url) }
            ?: throw AdapterException("Enter a public HTTP or HTTPS image website URL.")

    fun forSource(source: SourceId): SourceAdapter = when (source) {
        SourceId.KIUTAKU -> kiutaku
        SourceId.GENERIC -> generic
        else -> throw AdapterException("Retired provider. Add this website manually " +
            "in Kyora if its public galleries are supported.")
    }
}
