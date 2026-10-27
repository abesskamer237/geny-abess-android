# Geny-Abess Android

Android WebView application for **https://geny.payool.net/**.

## Included
- Native Geny-Abess splash screen and branded colors.
- WebView loading indicator, pull-to-refresh, Back navigation and state restoration.
- Network monitoring with a retry screen when offline.
- Android file picker for website uploads.
- HTTPS-only downloads through Android DownloadManager, with completion notifications.
- External payment/provider links opened in the browser; the app never treats a browser return as proof of payment.
- TLS certificate errors are cancelled, mixed content is blocked, and file access is disabled for WebView.
- GitHub Actions builds a debug APK on every push and can build a signed release when secrets are configured.

## Download a test APK
1. Open **Actions** in this repository.
2. Open the latest successful **Build Geny-Abess APK** run.
3. Download the `Geny-Abess-debug-APK` artifact and extract `app-debug.apk`.

## Configure production signing (recommended before public distribution)
Create a dedicated Android release keystore and keep a secure offline backup. Never commit the keystore or passwords to the repository.

In **Settings → Secrets and variables → Actions**, add:
- `GENY_KEYSTORE_BASE64`: base64 encoding of the keystore file.
- `GENY_KEYSTORE_PASSWORD`: keystore password.
- `GENY_KEY_ALIAS`: key alias.
- `GENY_KEY_PASSWORD`: key password.

Example to encode an existing keystore locally:
```bash
base64 -w 0 geny-release.jks
```
Copy the output into the secret `GENY_KEYSTORE_BASE64`. Do not paste keystore data or passwords into chat or commit them to GitHub. After all four secrets are set, the workflow additionally produces `Geny-Abess-production-APK`.

**Important:** if you lose the release keystore, you may be unable to publish seamless updates using the same app signing identity. The debug APK is signed with a development key and is intended for testing only.

## Payment safety
The Android wrapper does not process or confirm payments itself. Payment status must be validated by the website/backend using the payment provider's trusted server-side confirmation. External payment pages are opened outside the trusted WebView to reduce the risk of exposing the app's web session to unrelated sites.

## Build locally
Requires JDK 17, Android SDK Platform 35 and Gradle 8.11.1:
```bash
gradle --no-daemon :app:assembleDebug
```
