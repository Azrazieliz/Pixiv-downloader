# Nocturne / Kuroha + Kyora core

Nocturne is a separately installable test variant of the existing merged Android
downloader. It does NOT replace production Kuroha, which retains its original
package/signing workflow.

## Exactly two core workspaces

- **Kuroha / Pixiv:** existing Pixiv account, following feed, artist watchlists,
  art downloads, Pixiv tag exclusions, its own sync and storage.
- **Kyora / Cosplay:** dedicated Kiutaku adapter, cosplay galleries, flat downloads,
  per-site/global excluded tags, gallery backfill/retries, Round Sync COPY handoff.

There are NO bundled image-site catalogs, booru provider APIs, or nonfunctional
site-specific placeholder adapters. Only the Kiutaku adapter and a best-effort
public-HTML adapter remain in the Kyora runtime.

## Add websites when needed

Inside Kyora, tap Add Source, enter an HTTPS website, profile, or gallery URL,
then tap Add Website. You may try a site such as PornPics by entering its URL.
That does not mean a site-specific scraper already exists: static HTML links,
pagination and public albums are best-effort; protected, JavaScript-only, or
otherwise incompatible sites require a dedicated adapter.

Each manually added source has its own gallery history, selected/live sync
settings, and tag exclusions. From the source page, Remove Website hides it
from the library and stops its scheduled downloads. Existing files remain intact;
re-adding the same URL can restore the saved record where supported.

## Upgrade safeguards

- The SQLite v4 upgrade disables and hides records belonging to previously
  bundled noncore providers. **It does not delete the underlying historical
  source, gallery or image records or any downloaded files.**
- User-added generic URL sources stay visible. They can be removed individually
  with the in-app Remove Website action. The app never silently deletes photos.
- The Kuroha production application ID remains com.azrael.pixivdumpsync.
- The co-installable Nocturne preview is
  com.azrael.pixivdumpsync.nocturne, with separate application preferences,
  databases and download directories.
- Nocturne Pixiv files: Downloads/NocturnePixiv/
- Nocturne gallery files: Downloads/NocturneGallery/ (all albums flat)
- Round Sync COPY requests are not confirmation that the cloud received files.
- Existing Pixiv account authorization is NOT shared between the two apps.
- CI debug certificates can change between builds; preview upgrades may require
  the same signing certificate to install in place.

See ALBUM_FULL_BACKFILL_092.md for pagination/full-photo extraction limitations.
