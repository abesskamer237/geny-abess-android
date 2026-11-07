#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATOR="$ROOT/tools/issue-activation.js"
KEY="$ROOT/private/geny-abess-private.pem"

printf '\n=====================================\n'
printf '       GENY-ABESS ACTIVATION MANAGER\n'
printf '=====================================\n\n'

if ! command -v node >/dev/null 2>&1; then
  echo "ERREUR : Node.js n'est pas installé."
  echo "Dans Termux : pkg install nodejs-lts"
  exit 1
fi

if [[ ! -f "$GENERATOR" ]]; then
  echo "ERREUR : générateur introuvable : $GENERATOR"
  exit 1
fi

if [[ ! -f "$KEY" ]]; then
  echo "ERREUR : clé privée originale introuvable."
  echo "Fichier attendu : $KEY"
  echo "Récupère la clé privée originale. N'en génère pas une nouvelle."
  exit 1
fi

read -r -p "Identifiant DEVICE_ID du client : " DEVICE_ID
if [[ -z "${DEVICE_ID// /}" ]]; then
  echo "ERREUR : DEVICE_ID obligatoire."
  exit 1
fi

echo
echo "Durée de validité :"
echo "  1) 7 jours"
echo "  2) 30 jours"
echo "  3) 90 jours"
echo "  4) 365 jours"
echo "  5) Personnalisée"
echo "  6) Illimitée"
read -r -p "Ton choix [2] : " CHOICE
CHOICE="${CHOICE:-2}"

case "$CHOICE" in
  1) DAYS=7 ;;
  2) DAYS=30 ;;
  3) DAYS=90 ;;
  4) DAYS=365 ;;
  5)
    read -r -p "Nombre de jours (1 à 3650) : " DAYS
    if ! [[ "$DAYS" =~ ^[0-9]+$ ]] || (( DAYS < 1 || DAYS > 3650 )); then
      echo "ERREUR : entre un nombre entier de 1 à 3650."
      exit 1
    fi
    ;;
  6) DAYS=never ;;
  *) echo "Choix invalide."; exit 1 ;;
esac

echo
echo "Génération du code..."
node "$GENERATOR" "$DEVICE_ID" "$DAYS" "$KEY"
