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


## Accès privé : codes d’activation liés à un téléphone

L'application demande maintenant un code d'activation signé avant d'ouvrir le site. Chaque code contient l'identifiant Android de l'appareil et peut être limité dans le temps. Un code créé pour un téléphone ne doit pas fonctionner sur un autre.

### Émettre un code
1. Sur ton ordinateur privé, crée un dossier `private/` à la racine du dépôt local et place-y `geny-abess-private.pem`. **Ne pousse jamais ce fichier sur GitHub.**
2. Installe Node.js 18 ou plus récent.
3. Depuis la racine du dépôt, exécute :

```bash
node tools/issue-activation.js IDENTIFIANT_DU_TELEPHONE 365
```

Remplace `IDENTIFIANT_DU_TELEPHONE` par l'identifiant affiché dans l'écran de verrouillage. Le second argument est la durée en jours; utilise `never` pour une durée illimitée. Tu peux aussi fournir le chemin de la clé privée en troisième argument :

```bash
node tools/issue-activation.js IDENTIFIANT_DU_TELEPHONE 365 /chemin/vers/geny-abess-private.pem
```

Envoie le code généré au client de manière privée. Le client doit saisir le code dans l'application. Le bouton WhatsApp préremplit un message destiné à ABESS-OPS (+237 695 140 419).

### Important — limites sans backend
- La vérification de signature se fait dans l'application avec une clé publique; la clé privée de signature ne doit jamais être incluse dans l'APK ou le dépôt.
- Le code est lié à l'identifiant Android fourni et peut expirer, mais l'application ne peut pas savoir si un code a été révoqué à distance ou si un même code a déjà été délivré à quelqu'un d'autre.
- Une réinstallation, une réinitialisation du téléphone ou un identifiant Android modifié peut nécessiter une nouvelle activation.
- Ce système n'est pas un remplacement d'une validation serveur. Pour une restriction robuste à un seul appareil et une révocation immédiate, il faudra plus tard un service d'activation avec base de données.

### Protection de la clé privée
Garde une copie chiffrée et sauvegardée hors du dépôt. Si la clé privée est perdue, les futures versions de l'application nécessiteront une nouvelle clé publique intégrée et un nouveau build. Ne partage jamais la clé privée dans un ticket, un chat public ou un commit.
