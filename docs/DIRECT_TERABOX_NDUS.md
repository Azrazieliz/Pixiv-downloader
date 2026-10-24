# Nocturne Direct TeraBox upload using NDUS

This is an **unofficial** TeraBox web-API upload method, no Round Sync required.

## Configure (Android phone)
1. Open Nocturne -> Kuroha -> Configure direct TeraBox, or
   Nocturne -> Kyora -> Settings -> Configure TeraBox / NDUS.
2. Paste ONLY the `ndus` value from your TeraBox web session, or
   paste `ndus=...`. The dialog also accepts a full Cookie header
   and extracts NDUS. Do not share it in screenshots, logs, or chats.
3. Select independent remote folders (defaults:
   `/Nocturne/Kuroha`, `/Nocturne/Kyora`).
4. Tap Test TeraBox login. If needed, select nephobox.com for region.
5. Enable direct uploads and save.
6. Download more images, or select Include existing downloads to enqueue
   the Pixiv and cosplay files already owned by Nocturne.
7. Upload pending images. The Android notification shows progress.
8. **Check the TeraBox destination** to confirm actual transfer.

Files are transferred in 4 MiB chunks directly from Android MediaStore.
No duplicate local working archive is made. A SQLite outbox tracks per-file
successes and failures and backs off on retry. No originals are deleted.

## Security and reliability
NDUS is a bearer session cookie granting access to your TeraBox account.
It is encrypted at rest with Android Keystore AES-256/GCM (device-local,
no backup). HTTPS requests are restricted to verified terabox.com or
nephobox.com hostnames. Cookies are not stored in logs or export JSON.
The upload endpoint hostname reported by TeraBox is validated before use.
Disconnect removes the local saved NDUS key ciphertext (not the cloud files).

This uses the independent reverse-engineered web endpoints in OpenList's
TeraBox driver (precreate -> locateupload -> chunked superfile2 -> create)
rather than the official Open Platform API, which requires partner credentials:
https://github.com/OpenListTeam/OpenList/tree/main/drivers/terabox
https://github.com/uiurin304/rclone-extra/blob/master/docs/content/terabox.md

NDUS is subject to expiration, regional restrictions, rate limits, temporary
failures, and changes in unofficial web endpoints. Full reliability cannot be
guaranteed without a real NDUS account to perform an authenticated upload test.

Foreground upload processes bounded batches for up to 12 minutes, while a persisted Android network-only job attempts queued/retry files approximately every 15 minutes; additional pending
files resume in subsequent runs. Initial existing-file discovery streams MediaStore rows, avoiding enormous filename arrays. No Round Sync task is needed.
Historical app data remains intact on an in-place APK upgrade where signing
certificates match. GitHub debug build keys are not reliable for update-in-place.
