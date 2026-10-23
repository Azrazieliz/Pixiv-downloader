# Nocturne v0.9.2 — complete albums and uninterrupted backfill

## Investigation and fixes
- The generic scraper was capped at 180 album links per listing page and could mistake thumbnails for full downloads. It now discovers up to 5,000 album links per listing page, follows listing pagination, opens individual image detail pages found within each album, and prefers original/high-resolution media URLs over previews.
- Multi-page albums are scanned before marking an album complete. All resulting files are still written flat to the existing NocturneGallery download directory with collision-resistant filenames, never one folder per album.
- The previous backfill stopped at the first album with a failed download. Incomplete galleries now enter a persistent SQLite v3 retry queue; the scanner continues to remaining albums. Retrying an incomplete gallery does not erase prior downloaded files.
- HTTP 429/5xx responses and interrupted transfers are retried with bounded backoff, and non-image or truncated responses are rejected.
- Some tagged-image APIs return fewer than the requested 75 images even when further pages exist. A short page is no longer interpreted as the end; the scanner stops on an empty page or a repeated page signature.
- Nocturne's source detail screen shows pending failed albums and continues to report errors.
- Tests use a 354-album offline listing (177 entries per page), as well as albums whose thumbnails lead to separate original-size image pages.

## Limits and scope
- Static public HTML and supported API sources only. Authentication-protected media, JavaScript-rendered galleries, infinite-scroll-only APIs, and site-specific hidden image endpoints still require dedicated integrations.
- Safety limits remain high but finite (5,000 links per listing page, 1,500 album pagination pages, 10,000 photo pages per album). Reaching a cap produces an explicit error; it does not claim full success.
- A site may rate-limit requests or serve temporary errors. Failed albums are retained for later retry and reported rather than dropping from history.
- This is not a completed live-site verification for every adult gallery website.

## Test APK
- Nocturne remains `com.azrael.pixivdumpsync.nocturne`, installed independently of original Kuroha.
- Existing app private data is migrated in place to SQLite schema v3 where Android signing allows an update. CI's ephemeral debug signing may prevent in-place updates between builds.
