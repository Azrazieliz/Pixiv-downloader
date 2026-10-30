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


## Federated source discovery extension (2026-10-30)

- Multiple persisted sources can now be configured in Asterion Downloader > Settings
  using one source definition per line:
  - `searxng|https://search.example` for SearXNG JSON index APIs
  - `html|https://site.example/search?q={query}` for conventional site search result pages
  - `searxng|http://example.onion` with a separately running local Tor-compatible SOCKS proxy
- Source definitions do not blacklist a site for being adult-oriented, unlicensed,
  a mirror, or reachable via Tor. The user chooses configured sources.
- Query text is sent to each provider unmodified apart from URL encoding; source
  metadata, original result URL, relevance score and search history are persisted
  separately from completed download records in `asterion_search_results.sqlite`.
- Discovery does not equate to a playable or legal download. Generic HTML sites
  may change markup or require browser login; password/DRM bypass is not implemented.
  Search indexes never guarantee exhaustive or accurate results.
- Per-source failures are saved to each creator watch and do not make the other
  configured sources fail. A low relevance result remains reviewable rather than
  being automatically downloaded. Duplicate tracking-link variants are collapsed
  for display; the original source URL remains unchanged for downloads.
- If a local SOCKS proxy is enabled, configured search requests use that proxy
  and do not silently fall back to a direct connection. The proxy must be running.
- The existing Pixiv, photo-only X, chapter ZIP, video, signing and data
  preservation contracts remain in force. Confirm live operation and original
  certificate update separately from Android CI.


## Android on-device installation and saved configuration — 2026-10-30

- CI run `41139595378` succeeded and published the direct-download preview:
  `https://github.com/Azrazieliz/Pixiv-downloader/releases/tag/asterion-downloader-v1.3.0-preview-20261030`.
  CI artifact SHA-256:
  `d5c1b5262dd908e5093f607bde466d0449c9e0f22ffb1381c6a93f83c49841bf`.
- Installed on the connected Android 17 phone via ADB:
  `com.azrael.pixivdumpsync.nocturne`, versionCode 29,
  versionName `1.3.0-preview`. Verified launcher activity
  `com.azrael.pixivdumpsync.AsterionHomeActivity` opened successfully.
- Preserved existing production package `com.azrael.pixivdumpsync`,
  versionCode 15, versionName `0.6.1`. No uninstall, data clear or
  production signing replacement.
- For repeatable in-place preview updates the released APK was re-signed
  with the PC's existing Android debug keystore before ADB installation.
  Installed preview signing certificate SHA-256:
  `9bc439712a0a460cc4703ebf254aaaf9bf3cc6e67b0ced6fc12162ed3ca3b9ad`.
  Future preview builds need the SAME key to install over this package without
  removing app-private preferences. The publicly published CI APK was
  signed by a different CI debug key and is not directly upgrade-compatible.
  Do not commit or expose the PC's private signing key.
- Saved persistent `asterion_keyword_sources.xml` SharedPreferences for the
  installed preview (without navigating or editing the user's other apps):
  - `html|https://search.brave.com/search?q={query}&safesearch=off|Brave Search`
  - `html|https://www.pornhub.com/video/search?search={query}|Pornhub`
  - `html|https://www.xvideos.com/?k={query}|XVideos`
  - `html|https://xhamster.com/search/{query}|xHamster`
  - `html|https://www.bing.com/videos/search?q={query}&adlt=off|Bing Videos`
  Verified that the XML exists in the preview app's own sandbox.
  Desktop HTTP reachability was checked; this is NOT proof of working
  Android-side scraping, source availability or successful media downloads.
- Installed official `org.torproject.android` Orbot v17.9.7-RC-1
  (arm64) from Guardian Project GitHub releases. Verified downloaded APK
  SHA-256 `97d3a0a5181394e99fbabdda9fefb73027657cac4c7400c64ebf146a7778860b`.
  The local SOCKS listener appeared on port 9050 but Tor connections did
  NOT pass the external `check.torproject.org/api/ip` check (timeouts).
  Orbot was stopped after testing; `tor_proxy` remains disabled so ordinary
  Asterion search remains usable. Do not claim .onion access works yet.
- Remaining gates: validate actual provider result parsing and downloads
  on device, verify the Toonily chapter ZIP, X photos, post-reboot persistence,
  actual SOCKS/Tor connection, provider error reporting and safe incremental
  upgrades. PR #17 remains separate from production until verified.
