# Asterion Downloader v1.3.0 — Continuation and verification record (2026-10-30)

## Canonical continuation
- Repository: `Azrazieliz/Pixiv-downloader`.
- Working branch: `feature/asterion-unified-downloads-20261030`; PR #17 targets main.
- Keep the original Pixiv, cosplay, video databases, downloads, sessions, filters, scheduling, cloud settings, signing identity, and original folder contents.
- One Android app/launcher: `AsterionHomeActivity` with Add, Queue, Library, Sources, Settings. Older Kuroha/Kyora/VideoFlow classes remain internal implementations; do not produce separate app installations.
- Preserve the production package `com.azrael.pixivdumpsync` and the guarded original-key upgrade workflow. Debug and preview signatures are not original production signatures; do not uninstall the installed original to update it. Preview `.nocturne` may coexist separately.

## Implemented in GitHub source
1. `SourceRouter.kt`: routes Pixiv profiles, X posts and profiles, video URLs, comics/Toonily series, and generic image galleries.
2. `AsterionHomeActivity.kt`: one dashboard, intake, queue, local media library, source watches and settings.
3. `XPhotoQueue.kt`, `XPhotoEngine.kt`, `XPhotoScheduler.kt`: X images only, using documented FxTwitter API v2, with deduplication, persisted profile cursors, full-resolution photo URLs, app-private retries and MediaStore files in `Downloads/AsterionDownloader/X/`. Do not route X to video download.
4. `ChapterScraper.kt`, `ChapterQueue.kt`, `ChapterArchives.kt`, `ChapterRecovery.kt`: series/chapter discovery, numeric chapter ordering including decimals, per-page images, local cached pages, ZIP directories and app-level queue persistence. Completed ZIP saved to `Downloads/AsterionDownloader/Comics/`. Any failed page must leave a visible error, not a false complete archive.
5. `NameDiscovery.kt`, `KeywordSources.kt`: saved creator/title watches, Internet Archive advanced search plus optional configured SearXNG JSON index, optional user-defined search URLs with name substitution. Query words are not silently removed. Actual result URLs are retained. Search success never guarantees global completeness.
6. Video transfer durability: SQLite schema v2 with retry_attempts/retry_after, bounded exponential retry queue, original yt-dlp continuation, staged file/publish verification, optional SOCKS5 transport. Optional local proxy must be running before private-network URL access.
7. Existing Pixiv and image-gallery engines, cloud integrations and app-private persistence remain in the repository.

## User example and requirements
- Test source: `https://toonily.com/serie/moby-dick-17bba228/`.
- Live visible series index on 2026-10-30 lists Chapter 1 through Chapter 109, with fractional chapters (31.5, 39.5, 47.5, 58.5, 75.5, 102.5). Source scraping and CDN image access must be tested on device, not assumed.
- Adult-oriented sources and terms should not be silently excluded from configured search. The engine may support lawful adult media, with user-supplied access, but no claim that unavailable, non-indexed or protected works can be retrieved.
- Deep/alternative-network support depends on an externally active compatible local SOCKS proxy and explicitly configured index/source. No guaranteed crawler across undiscoverable websites exists.
- Avoid pretending that unfinished cloud backup confirmation or universal scraper compatibility is done.

## Verification
- The CI for intermediate commit `d3d4b8d1d6829ebcf0d3d23d48e090b4c8948cc6` passed and produced an installable v1.3.0 preview APK.
- After allowing proxy-routed hidden-service URLs, an older unit test expecting that syntax to be rejected failed. The obsolete assertion was corrected in `WebDiscoveryTest.kt` at commit `4e6f2e4`; do not misreport this as a runtime failure.
- Android emulator API 36 smoke tests were unable to install the APK due to ADB broken pipe, not a demonstrated app exception. Updated smoke workflow at `8ce18acd` switches to API 35 and a single preview install.
- Newer CI APK, on-device download of a Toonily chapter, complete X account archive, actual SOCKS operation and keyword discovery were NOT yet verified as of this record.
- Download result and errors must always be distinguishable, with retries accessible. Do not claim zero errors or universal discoverability.
