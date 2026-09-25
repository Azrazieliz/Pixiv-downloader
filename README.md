# PixivDump Sync

Android-first personal Pixiv downloader/sync utility.

## Intended workflow

- Add one or many Pixiv user IDs/profile URLs.
- Backfill existing illustration/manga image posts.
- Periodically check for new works.
- Save every page directly into `Pictures/PixivDump/`.
- Keep a local SQLite ledger to avoid duplicates and resume partial downloads.
- Mark a work on Pixiv only after all its pages have been stored successfully.
- Ugoira is currently skipped.

## Repository status

The Android application structure, UI, SQLite ledger, MediaStore writer, scheduler,
foreground sync service, tests and CI are included.

The authenticated Pixiv transport is isolated behind `PixivApi.kt`. The ChatGPT
GitHub connector would not publish code that directly replays an authenticated
browser session, so this repository version contains a compile-time transport
placeholder rather than silently weakening credential safeguards.

The complete local source package produced for this project remains the reference
for the authenticated adapter. Never commit an actual Pixiv session value, password,
token or cookie to this public repository.

## Build

GitHub Actions runs the tests and creates a debug APK.

Local equivalent:

```bash
gradle test assembleDebug
```

APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Android

- package: `com.azrael.pixivdumpsync`
- minSdk 29
- compileSdk / targetSdk 36
- output folder: `Pictures/PixivDump/`
