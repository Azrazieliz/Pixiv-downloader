# Asterion Downloader — Kuroha + Kyora

The installation identity is com.azrael.pixivdumpsync: original Kuroha data and signing-key update path are preserved. The launcher contains separate screens and download engines.

- Kuroha: Pixiv login, followed feed, artists, bookmarks, exclusion tags, TeraBox official API uploader and Round Sync handoff.
- Kyora: Kiutaku dedicated adapter, gallery catalog, Live/Backfill, exact gallery tag exclusions, flat Downloads/GalleryFlow files, Round Sync handoff.
- Add Site: public HTML URLs; generic discovery bounded to 80 same-site gallery/post links and 500 images per gallery. Unsupported JS/login/complex paging needs a dedicated adapter. 4KHD, BuonDua, CosplayTele were placeholders; URLs now use the generic public-HTML adapter instead.
- Separate databases, tag filters, Round Sync settings and Android scheduled jobs.
- Signed install-over-update requires the original Kuroha signing key. CI debug APK cannot update production signed Kuroha.
- Existing standalone Kyora app uses a different Android application ID and its historical private catalog cannot be automatically read by this app. Existing saved images are not deleted, and new unified Kyora retains the Downloads/GalleryFlow folder. In the old Kyora app use Export JSON, then in the unified Kyora screen use Import sites from old Kyora JSON to transfer saved source records and selected/live toggles. Media histories and existing file URI ownership are not transferred. Keep the old app during migration.
- Round Sync task requests are not cloud completion receipts. Keep credentials in Round Sync and use COPY, never destructive mirror.

Per-site screens list captured galleries with a local thumbnail, and open a paged, scrollable two-column image preview. The archive retains full-size files.

