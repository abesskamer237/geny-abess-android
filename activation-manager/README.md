# Geny-Abess — Gestionnaire local d'activations

Petit assistant interactif pour générer un code d'activation à partir du DEVICE_ID d'un client. Fonctionne dans Termux (Android) ou dans un environnement Bash avec Node.js.

## Utilisation dans Termux

Depuis la racine du dépôt :

```bash
pkg install -y nodejs-lts
bash activation-manager/generate.sh
```

Le programme demande :
1. le DEVICE_ID affiché dans l'application du client ;
2. la durée (7, 30, 90, 365 jours, personnalisée ou illimitée).

Il affiche ensuite le code signé à transmettre au client.

## Clé privée requise

Le programme utilise exclusivement la clé privée existante :

```
private/geny-abess-private.pem
```

Cette clé doit correspondre à la clé publique intégrée dans l'APK actuel. **Ne génère pas une nouvelle paire de clés** et ne partage jamais la clé privée.

Si le fichier est absent, le programme s'arrête sans générer de code. Il ne télécharge jamais la clé depuis GitHub et ne l'intègre pas au dépôt.

Protection recommandée :

```bash
chmod 700 private
chmod 600 private/geny-abess-private.pem
git check-ignore private/geny-abess-private.pem
```

Le générateur de signature existant reste la source de vérité : `tools/issue-activation.js`. Ce dossier ne modifie ni le format des codes, ni la clé publique, ni l'application Android.
