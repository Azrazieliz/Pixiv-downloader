# Kuroha

Kuroha is an Android-first private Pixiv archiver for followed and explicitly watched artists.

## Current behavior

- In-app Pixiv WebView login; session credentials stay on-device.
- **Following Feed Live Sync** archives newest illustrations from accounts followed by the connected Pixiv account.
- **Watched Artists** provide per-artist Live toggles, selection, archive status, profile picture, display name and a strip of latest artworks.
- **Import Pixiv follows** can populate the watched-artist list from the connected account.
- **Archive Backfill** downloads historical works for selected watched artists.
- Automatic background Live checks use Android JobScheduler.
- Live checks that arrive while another sync is active are coalesced into one pending global Live pass.
- Backfill yields to queued Live checks and resumes afterward.
- Pause / Resume / Stop are cooperative and preserve completed work.
- Multi-page illustrations are saved page by page.
- Ugoira are currently skipped.
- A work is bookmarked on Pixiv only after every expected image page exists locally.
- Existing Pixiv bookmarks do not block re-downloading missing local files.
- Files are stored in **Downloads/Kuroha/**.
- Legacy **Downloads/PixiFlow/** files are recognized so an app update does not force a redownload.

## Identity

Application ID remains `com.azrael.pixivdumpsync` for update compatibility.

Current version: **0.5.1**.
