# Kuroha: TeraBox alternatives

Kuroha has two optional cloud paths:

- **Official TeraBox Open Platform**: The existing encrypted OAuth token settings and persistent direct API upload queue are unchanged. TeraBox partner approval / application sandbox path and valid token are required. The official API integration does **not** accept a web NDUS cookie.
- **Round Sync**: Configure a Round Sync TeraBox remote and a COPY task from **/storage/emulated/0/Download/Kuroha** to the cloud destination. Enter the task's numeric ID under **Round Sync settings**. Kuroha requests the task after a successful run with newly downloaded images, or manually via **Run Round Sync backup**. This keeps authentication (including NDUS when using the appropriate fork) within Round Sync.

For TeraBox remote support, see the independent Round Sync rclone-extra fork https://github.com/gulp79/Round-Sync and its cookie configuration https://github.com/uiurin304/rclone-extra/blob/master/docs/content/terabox.md .

Do not configure a destructive MIRROR/SYNC task; choose COPY and keep local originals. Round Sync task launch is **not** proof of cloud transfer, and its request does not bypass local caching. The upload must be checked in Round Sync, and the destination configured inside Round Sync. Never publish the NDUS session or include it in source control or APK resources.

The current official-API path and Round Sync path are independent options; enabling both may duplicate uploads. Cloud end-to-end success and signed in-place installation need device-side verification.
