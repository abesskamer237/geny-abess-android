import Fastify from "fastify";
import rateLimit from "@fastify/rate-limit";
import Database from "better-sqlite3";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || "0.0.0.0";
const ADMIN_KEY = process.env.ADMIN_KEY || "";
const DB_PATH = process.env.DB_PATH || "/data/activation.sqlite";
const CONTACT = process.env.DEV_WHATSAPP || "+237695140419";

if (ADMIN_KEY.length < 32) {
  console.error("ADMIN_KEY must be a random secret of at least 32 characters.");
  process.exit(1);
}
fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

const db = new Database(DB_PATH);
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");
db.exec(`
CREATE TABLE IF NOT EXISTS access_codes (
  id TEXT PRIMARY KEY,
  code_hash TEXT NOT NULL UNIQUE,
  label TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  expires_at INTEGER,
  device_id TEXT,
  activated_at INTEGER,
  revoked_at INTEGER
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  code_id TEXT NOT NULL REFERENCES access_codes(id),
  device_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL,
  revoked_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_sessions_code ON sessions(code_id);
`);

const app = Fastify({ logger: true, bodyLimit: 16 * 1024 });
await app.register(rateLimit, { global: true, max: 90, timeWindow: "1 minute" });

const now = () => Math.floor(Date.now() / 1000);
const hash = (s) => crypto.createHash("sha256").update(s, "utf8").digest("hex");
const randomSecret = (bytes = 32) => crypto.randomBytes(bytes).toString("base64url");
const cleanId = (s) => typeof s === "string" && s.length >= 4 && s.length <= 256 && !/[\r\n|]/.test(s);

function adminOnly(req, reply) {
  const supplied = req.headers["x-admin-key"];
  if (typeof supplied !== "string" ||
      supplied.length !== ADMIN_KEY.length ||
      !crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(ADMIN_KEY))) {
    reply.code(401).send({ error: "unauthorized" });
    return false;
  }
  return true;
}

app.get("/health", async () => ({ ok: true, service: "geny-abess-activation-api" }));

app.post("/v1/activate", async (req, reply) => {
  const code = typeof req.body?.code === "string" ? req.body.code.trim() : "";
  const deviceId = req.body?.deviceId;
  if (code.length < 16 || code.length > 512 || !cleanId(deviceId)) {
    return reply.code(400).send({ error: "invalid_request" });
  }

  const record = db.prepare("SELECT * FROM access_codes WHERE code_hash = ?").get(hash(code));
  if (!record || record.revoked_at || (record.expires_at && record.expires_at < now())) {
    return reply.code(403).send({ error: "invalid_or_expired_code" });
  }
  if (record.device_id && record.device_id !== deviceId) {
    return reply.code(409).send({ error: "code_already_bound_to_another_device" });
  }

  const token = randomSecret(32);
  const ts = now();
  const transaction = db.transaction(() => {
    if (!record.device_id) {
      db.prepare("UPDATE access_codes SET device_id = ?, activated_at = ? WHERE id = ? AND device_id IS NULL")
        .run(deviceId, ts, record.id);
    }
    db.prepare("UPDATE sessions SET revoked_at = ? WHERE code_id = ? AND revoked_at IS NULL")
      .run(ts, record.id);
    db.prepare("INSERT INTO sessions(token_hash, code_id, device_id, created_at, last_seen_at) VALUES(?,?,?,?,?)")
      .run(hash(token), record.id, deviceId, ts, ts);
  });
  transaction();

  return {
    accessToken: token,
    expiresAt: record.expires_at,
    deviceBound: true,
    developerWhatsApp: CONTACT
  };
});

app.get("/v1/status", async (req, reply) => {
  const auth = req.headers.authorization || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  const deviceId = req.headers["x-device-id"];
  if (token.length < 20 || !cleanId(deviceId)) {
    return reply.code(401).send({ active: false, error: "unauthorized" });
  }

  const session = db.prepare(`
    SELECT s.*, c.expires_at, c.revoked_at AS code_revoked_at, c.device_id AS bound_device
    FROM sessions s JOIN access_codes c ON c.id = s.code_id
    WHERE s.token_hash = ?
  `).get(hash(token));

  if (!session || session.revoked_at || session.code_revoked_at ||
      session.device_id !== deviceId || session.bound_device !== deviceId ||
      (session.expires_at && session.expires_at < now())) {
    return reply.code(403).send({ active: false, error: "access_revoked_or_invalid" });
  }
  db.prepare("UPDATE sessions SET last_seen_at = ? WHERE token_hash = ?").run(now(), hash(token));
  return { active: true, expiresAt: session.expires_at, deviceBound: true };
});

app.post("/v1/admin/codes", async (req, reply) => {
  if (!adminOnly(req, reply)) return;
  const label = typeof req.body?.label === "string" ? req.body.label.slice(0, 120) : "";
  const days = req.body?.days === undefined ? 365 : Number(req.body.days);
  if (!Number.isInteger(days) || days < 1 || days > 3650) {
    return reply.code(400).send({ error: "days_must_be_1_to_3650" });
  }
  const code = "GENY-" + randomSecret(24);
  const id = crypto.randomUUID();
  const created = now();
  const expires = created + days * 86400;
  db.prepare("INSERT INTO access_codes(id,code_hash,label,created_at,expires_at) VALUES(?,?,?,?,?)")
    .run(id, hash(code), label, created, expires);
  // The plaintext code is returned once and never stored in the database.
  return reply.code(201).send({ id, code, label, createdAt: created, expiresAt: expires });
});

app.get("/v1/admin/codes", async (req, reply) => {
  if (!adminOnly(req, reply)) return;
  const rows = db.prepare(`
    SELECT id, label, created_at AS createdAt, expires_at AS expiresAt,
           device_id AS deviceId, activated_at AS activatedAt, revoked_at AS revokedAt
    FROM access_codes ORDER BY created_at DESC LIMIT 500
  `).all();
  return { codes: rows };
});

app.post("/v1/admin/codes/:id/revoke", async (req, reply) => {
  if (!adminOnly(req, reply)) return;
  const ts = now();
  const transaction = db.transaction(() => {
    const result = db.prepare("UPDATE access_codes SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL")
      .run(ts, req.params.id);
    db.prepare("UPDATE sessions SET revoked_at = ? WHERE code_id = ? AND revoked_at IS NULL")
      .run(ts, req.params.id);
    return result.changes;
  });
  const changed = transaction();
  if (!changed) return reply.code(404).send({ error: "active_code_not_found" });
  return { revoked: true };
});

app.setNotFoundHandler((req, reply) => reply.code(404).send({ error: "not_found" }));
app.setErrorHandler((err, req, reply) => {
  req.log.error(err);
  reply.code(500).send({ error: "internal_error" });
});

try {
  await app.listen({ port: PORT, host: HOST });
} catch (err) {
  app.log.error(err);
  process.exit(1);
}
