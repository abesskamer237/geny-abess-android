#!/usr/bin/env node
"use strict";

// Issue a signed Geny-Abess activation code for one Android device.
// Keep the private signing key offline and OUT of GitHub.
const fs = require("node:fs");
const crypto = require("node:crypto");
const path = require("node:path");

function fail(message) {
  console.error(message);
  process.exit(2);
}

const deviceId = (process.argv[2] || "").trim();
const validity = (process.argv[3] || "365").trim().toLowerCase();
const keyPath = process.argv[4] || path.join(__dirname, "..", "private", "geny-abess-private.pem");

if (!deviceId || deviceId.length > 256 || /[|\r\n]/.test(deviceId)) {
  fail("Usage: node tools/issue-activation.js <DEVICE_ID> [days|never] [PRIVATE_KEY_PATH]");
}
if (!fs.existsSync(keyPath)) {
  fail("Private key not found: " + keyPath + "\nPut geny-abess-private.pem in ./private/ or pass its path as argument 3.");
}

let expiresAt = 0;
if (validity !== "never") {
  const days = Number(validity);
  if (!Number.isInteger(days) || days < 1 || days > 3650) {
    fail("Validity must be an integer from 1 to 3650 days, or 'never'.");
  }
  expiresAt = Math.floor(Date.now() / 1000) + days * 86400;
}

const serial = crypto.randomBytes(12).toString("hex");
const payload = `GENY1|${deviceId}|${expiresAt}|${serial}`;
const privateKey = fs.readFileSync(keyPath, "utf8");
const signature = crypto.sign("RSA-SHA256", Buffer.from(payload, "utf8"), privateKey);
const b64url = (buffer) => buffer.toString("base64url");
const code = `${b64url(Buffer.from(payload, "utf8"))}.${b64url(signature)}`;

console.log("\nGENY-ABESS ACTIVATION CODE");
console.log(code);
console.log("\nDevice ID : " + deviceId);
console.log("Validité  : " + (expiresAt === 0 ? "illimitée" : new Date(expiresAt * 1000).toISOString()));
console.log("Serial    : " + serial);
console.log("\nCode généré localement. Ne partage jamais la clé privée.");
