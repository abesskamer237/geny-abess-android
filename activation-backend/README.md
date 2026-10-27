# Geny-Abess Activation API

This service provides server-side activation codes bound to the first Android device that activates them. It uses Fastify, SQLite, Docker Compose and Caddy-managed HTTPS.

## Prerequisites

- A Linux VPS with Docker Engine and Docker Compose plugin installed.
- A DNS name such as `activation.example.com` with an A record pointing to the VPS IPv4 address (and AAAA only if IPv6 is configured correctly).
- Ports TCP 80 and 443 open in the VPS firewall/provider firewall.
- A strong private admin key. Never commit `.env`.

## Deploy

On the VPS, clone the repository or upload this `activation-backend` folder. Then:

```bash
cd activation-backend
cp .env.example .env
openssl rand -hex 32
nano .env
```

Put your actual hostname in `ACTIVATION_DOMAIN` and the random 64-character hex output in `ADMIN_KEY`. Keep the WhatsApp number as provided or change it. Then start:

```bash
docker compose -f compose.yml up -d --build
docker compose -f compose.yml ps
docker compose -f compose.yml logs -f activation-api caddy
```

Check the API:

```bash
curl -fsS https://activation.example.com/health
```

Expected response: `{"ok":true,"service":"geny-abess-activation-api"}`.

## Create a code

Run this from the VPS shell; replace the example hostname and admin key with the values in your private `.env` file. Do not paste the real key into chats or tickets.

```bash
read -rsp 'Admin key: ' ADMIN_KEY; echo
curl -fsS -X POST 'https://activation.example.com/v1/admin/codes' \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"label":"Client 001","days":365}'
unset ADMIN_KEY
```

The returned `code` is shown only once. Send it privately to the client. It is stored as a SHA-256 hash, not plaintext.

## API contract

- `GET /health`: health check.
- `POST /v1/activate`: JSON `{"code":"...","deviceId":"Android ID"}`; binds the code to the first device and returns a bearer token. A different device gets HTTP 409.
- `GET /v1/status`: requires `Authorization: Bearer <accessToken>` and `X-Device-Id: <Android ID>`; checks validity and revocation.
- `POST /v1/admin/codes`: requires `X-Admin-Key`; creates a code. JSON body `{"label":"Client 001","days":365}`.
- `GET /v1/admin/codes`: lists code metadata, device IDs, expiration and revocation.
- `POST /v1/admin/codes/:id/revoke`: revokes the code and all associated sessions.

## Security notes

- Keep `.env` and the SQLite volume private. Back up the Docker volume regularly.
- Admin endpoints are protected by a high-entropy key and rate limits. Rotate the key if exposed.
- HTTPS is handled by Caddy. DNS must resolve to this VPS and ports 80/443 must be reachable for certificate issuance.
- The API code is prepared, but the Android client must be configured to call the actual HTTPS hostname before production use. Do not distribute a build that has not been tested against the deployed API.
- Android ID is an app-provided identifier, not cryptographic proof of physical hardware. A modified client can spoof it; stronger attestation (for example, Play Integrity) can raise the bar but requires additional setup.
- A revocation takes effect the next time the app checks `/v1/status`; the Android client should check at launch and periodically while in use.
