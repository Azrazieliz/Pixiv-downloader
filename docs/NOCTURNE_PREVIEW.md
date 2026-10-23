# Nocturne Preview - co-installable test build

Nocturne Preview is a separate Android application for trying the merged
Kuroha + Kyora functionality without uninstalling or replacing Kuroha.

- Original package: com.azrael.pixivdumpsync
- Preview package: com.azrael.pixivdumpsync.nocturne
- Build command: gradle assemblePreview
- Produced artifact: app/build/outputs/apk/preview/app-preview.apk
- App label: Nocturne Preview; separate cyan-violet aperture launcher icon.
- Internal SQLite databases, settings, Pixiv sessions, job schedulers and caches
  are isolated by Android application UID/package.
- Gallery images: Downloads/NocturneGallery/ (flat).
- Pixiv images: Downloads/NocturnePixiv/.
- Any Round Sync COPY task for preview must use these preview directories.
- No gallery/Pixiv files from Kuroha, Kyora or old PixiFlow are migrated or
  discovered automatically by the preview.
- Official Pixiv / external TeraBox login is new to the preview, if configured.
- The normal debug and original-key release application IDs do not change.

Test installations should not be confused with the original-signed production APK.
A debug key is enough to install Nocturne Preview alongside
production-signed Kuroha because the application IDs differ. The same Nocturne
debug key is not guaranteed across CI runs, so a future Nocturne debug rebuild
may itself require uninstalling the Nocturne preview only. This does not
affect Kuroha.
