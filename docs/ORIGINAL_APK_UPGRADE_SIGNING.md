# Updating Kuroha without uninstalling: original signing certificate

## Forensic verification — user-provided v0.6.1 APK

Verified from the APK Signing Block (APK Signature Scheme v2):

- Installed Kuroha v0.6.1 SHA-256 **certificate fingerprint**:
  `540E540F3C8FBC0C8BB1DAD514F48799E80A74FACD0A3F51371478D18143DEC9`
- Previously built Kuroha v0.7.0 debug APK:
  `2486D9F181A6BC6ED43DD0A30974BCE565E1B5E1C5B633BEDCE196A99D862A6C`
- Result: the 0.7.0 debug APK **cannot** update the installed 0.6.1 package in place.
- Both certificate subjects are `CN=Android Debug,O=Android,C=US`; these
  subjects are NOT keys and are insufficient for replacement.
- The uploaded APK contains the **public certificate** and cryptographic
  signatures only, not the private key. Re-signing with the original identity
  requires locating the original `.keystore` / `.jks` private-key file.

## No-destructive-upgrade policy

**Do not uninstall v0.6.1.** Kuroha has
`android:allowBackup="false"` and its SQLite database, saved preferences,
artist lists and Pixiv session may not survive removal. Images in
`Downloads/Kuroha` are stored separately in MediaStore, but that does
not replace app-private settings and DB data.

## When the original keystore becomes available

In repository Settings → Secrets and variables → Actions → New repository secret,
set these four secrets (never commit the keystore or plaintext passwords):

- `KUROHA_ORIGINAL_KEYSTORE_BASE64`: base64-encoded bytes of the ORIGINAL
  debug/release keystore file whose certificate matches v0.6.1
- `KUROHA_STORE_PASSWORD`: password for that keystore
- `KUROHA_KEY_ALIAS`: name of the signing key within the keystore
- `KUROHA_KEY_PASSWORD`: password for that private key

For a *standard* Android debug keystore, the alias is usually
`androiddebugkey` and the default keystore/key passwords are `android`.
**These defaults do not reconstruct the original private key.**

Then run **Actions → Kuroha Original-Signature Update APK → Run workflow**.
The workflow:
1. Decodes the keystore to a temporary file on the ephemeral runner.
2. Checks that the exported certificate's SHA-256 fingerprint is EXACTLY
   `540e540f3c8fbc0c8bb1dad514f48799e80a74facd0a3f51371478d18143dec9`.
3. Builds the signed Android release artifact without changing the package ID.
4. Verifies the signed APK itself with `apksigner` and confirms the fingerprint.
5. Publishes an installable upgrade APK **only after both checks succeed**.

If the original key was created by an ephemeral GitHub Actions runner
and was not exported or persisted at creation, it might no longer exist.
Neither the APK nor the public certificate can recreate it.

## Current status

The guarded workflow is implemented. It cannot publish a signature-compatible
APK until the original private key is provided. The ordinary debug CI build
remains separate and must never be mislabeled as upgrade-compatible.
