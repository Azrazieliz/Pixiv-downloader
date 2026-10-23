# Nocturne v0.9.1 — Backfill pagination and recovery

## Fixed premature termination

- Dedicated booru image sources no longer silently stop after 8 pages / 600 posts.
- Each tagged booru source has a persistent numeric backfill page in the v2 SQLite schema. The database is migrated in place; no catalog is deleted.
- Every fully processed result page commits the next page. Failed/partial pages do **not** advance the cursor.
- Running **Backfill** again resumes from that saved page without redownloading files already marked complete. Failed downloads remain eligible for retry.
- Each selected source can reset historical backfill to page one with an explicit confirmation; the downloaded files are preserved.
- Live checks still use a shallow recent window and do not reset the historical backfill cursor.
- Rate-limited and inaccessible pages, repeated API pages, and incomplete galleries are surfaced as errors instead of being mislabeled complete.
- The Nocturne Transfers screen shows the final error/completion status instead of replacing it with “No active transfer.”

## Gallery pagination

- Generic public HTML adapter follows same-listing next-page links and adjacent numeric pagination, constrained to the same site and listing path to avoid crossing between albums.
- Single galleries can include multiple linked pages (up to a clearly reported safety limit) with correct per-page media referer.
- Kiutaku gallery pagination now traverses additional links discovered on pages after the first rather than assuming the first page lists every page.

## Important limitations

- Protected, login-only, JavaScript-only, and unsupported pagination cannot be bypassed or guaranteed. Actual API behavior must be validated per site.
- HTML listing safety cap: 500 pages; HTML album cap: 200 pages; Kiutaku album cap: 600 pages. These limits report a failure instead of declaring a successful full archive.
- Booru safety cap: 10,000 result pages with explicit warning. Some sites also impose tighter server-side pagination limits. Source-specific end-of-list behavior and API keys remain external.
- CI debug signing may differ between workflows. Existing production Kuroha is not affected by the separate preview app, but updating an existing Nocturne preview could require installing with a matching signature or replacing the preview only.
