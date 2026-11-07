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

### Générer un code depuis Android avec Termux

Le dépôt contient déjà le générateur `tools/issue-activation.js`. Il utilise Node.js et la clé privée RSA correspondante à la clé publique intégrée à l'application. **Il ne faut pas créer une nouvelle paire de clés pour cette procédure** : une autre clé produirait des codes refusés par l'APK actuel.

#### 1. Installer les outils dans Termux

Ouvre Termux et exécute :

```bash
pkg update && pkg upgrade -y
pkg install -y git nodejs-lts
node --version
git --version
```

Node.js 18 ou supérieur est requis.

#### 2. Télécharger le dépôt

```bash
cd ~
git clone https://github.com/abesskamer237/geny-abess-android.git
cd geny-abess-android
```

Si le dossier existe déjà, utilise plutôt :

```bash
cd ~/geny-abess-android
git pull
```

#### 3. Installer la clé privée en toute sécurité

Le générateur attend par défaut le fichier :

```
~/geny-abess-android/private/geny-abess-private.pem
```

Crée le dossier privé et protège ses permissions :

```bash
mkdir -p private
chmod 700 private
```

Transfère **ta clé privée existante** depuis ton ordinateur vers ce dossier par un moyen privé et sécurisé (par exemple une connexion SSH/SCP sur ton propre réseau). Ne la colle jamais dans un dépôt GitHub, un groupe WhatsApp, un ticket ou une conversation. Puis :

```bash
chmod 600 private/geny-abess-private.pem
```

Le dépôt ignore déjà `/private/` et les fichiers `*.pem`. Vérifie quand même avant toute synchronisation :

```bash
git check-ignore private/geny-abess-private.pem
git status --short
```

La première commande doit afficher le chemin de la clé comme ignoré. Si ce n'est pas le cas, **n'exécute aucun `git add` ni `git push`**.

> Si tu ne possèdes pas déjà la clé privée correspondant exactement à la clé publique intégrée dans `MainActivity.kt`, arrête-toi ici : il faut retrouver la clé d'origine. Générer une autre clé ne permettrait pas de créer des codes compatibles avec l'application déjà distribuée.

#### 4. Récupérer l'identifiant du téléphone

Sur le téléphone du client, ouvre Geny-Abess. Sur l'écran « Accès privé », copie l'**identifiant de cet appareil**. C'est cet identifiant exact qu'il faut utiliser, sans espace ajouté.

#### 5. Générer le code

Depuis la racine du dépôt dans Termux :

```bash
node tools/issue-activation.js "IDENTIFIANT_DU_TELEPHONE" 30
```

Remplace :
- `IDENTIFIANT_DU_TELEPHONE` par l'identifiant copié depuis l'application ;
- `30` par la durée voulue en jours (entier de 1 à 3650).

Exemples :

```bash
# Code valable 30 jours
node tools/issue-activation.js "IDENTIFIANT_DU_TELEPHONE" 30

# Code valable 365 jours
node tools/issue-activation.js "IDENTIFIANT_DU_TELEPHONE" 365

# Code sans date d'expiration
node tools/issue-activation.js "IDENTIFIANT_DU_TELEPHONE" never
```

Le terminal affiche un code d'activation signé. Envoie uniquement ce code au client par message privé. Le client le saisit dans l'application.

#### 6. Utiliser une clé située ailleurs (facultatif)

Si le fichier de clé n'est pas dans `private/`, passe son chemin en troisième argument :

```bash
node tools/issue-activation.js "IDENTIFIANT_DU_TELEPHONE" 30 "/chemin/prive/geny-abess-private.pem"
```

#### 7. Comprendre les erreurs courantes

- `Private key not found` : le fichier n'est pas au chemin attendu ; vérifie son nom et son emplacement.
- `Validity must be...` : la durée doit être un entier de 1 à 3650, ou `never`.
- Code refusé par l'application : vérifie l'identifiant caractère par caractère et surtout que la clé privée est bien la clé d'origine associée à la clé publique de l'APK.
- Après changement de clé publique dans l'application, il faut reconstruire et redistribuer l'APK ; les anciennes clés privées ne signeront pas les nouveaux codes.

**Sécurité :** la clé privée permet d'émettre des activations. Garde-la uniquement sur un appareil de confiance, protège-la avec un mot de passe/stockage chiffré et conserve une sauvegarde sécurisée. Ne la place jamais dans l'APK ni dans GitHub.

### Important — limites sans backend
- La vérification de signature se fait dans l'application avec une clé publique; la clé privée de signature ne doit jamais être incluse dans l'APK ou le dépôt.
- Le code est lié à l'identifiant Android fourni et peut expirer, mais l'application ne peut pas savoir si un code a été révoqué à distance ou si un même code a déjà été délivré à quelqu'un d'autre.
- Une réinstallation, une réinitialisation du téléphone ou un identifiant Android modifié peut nécessiter une nouvelle activation.
- Ce système n'est pas un remplacement d'une validation serveur. Pour une restriction robuste à un seul appareil et une révocation immédiate, il faudra plus tard un service d'activation avec base de données.

### Protection de la clé privée
Garde une copie chiffrée et sauvegardée hors du dépôt. Si la clé privée est perdue, les futures versions de l'application nécessiteront une nouvelle clé publique intégrée et un nouveau build. Ne partage jamais la clé privée dans un ticket, un chat public ou un commit.
