import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  collectorBearer,
  collectorSourceAllowed,
  effectiveMaxBatch,
  generateCollectorKey,
  hashCollectorKey,
  isCollectorBearer,
  isMissingSchemaError,
  legacyRadarSecretAuthorized,
  normalizeCollectorConfigV1,
  parseCollectorKey,
  publicCollectorV1,
  timingSafeSecretEqual,
  verifyCollectorKey,
} from "../../src/radar-collectors-v1";

const read = (rel: string) => readFileSync(new URL(`../../${rel}`, import.meta.url), "utf8");

test("collector key format: srrk_<8 hex>_<base64url ≥ 32 bytes>, unique, hashed", () => {
  const a = generateCollectorKey();
  const b = generateCollectorKey();
  assert.match(a.key, /^srrk_[0-9a-f]{8}_[A-Za-z0-9_-]{43}$/);
  assert.notEqual(a.key, b.key);
  assert.equal(a.displayPrefix, `srrk_${a.prefix}`);
  assert.match(a.hash, /^[0-9a-f]{64}$/);
  assert.equal(a.hash, hashCollectorKey(a.key));
  assert.ok(!a.hash.includes(a.key.slice(14)), "hash must not contain the secret");
  // 43 base64url chars = 32 random bytes
  assert.equal(Buffer.from(a.key.split("_").slice(2).join("_"), "base64url").length, 32);
});

test("collector key verification is exact and constant-time based", () => {
  const { key, hash } = generateCollectorKey();
  assert.equal(verifyCollectorKey(key, hash), true);
  assert.equal(verifyCollectorKey(key + "x", hash), false);
  assert.equal(verifyCollectorKey(key, "0".repeat(64)), false);
  assert.equal(verifyCollectorKey(key, ""), false);
  assert.ok(parseCollectorKey(key));
  for (const bad of ["", "srrk_", "srrk_ABCDEF12_" + "a".repeat(43), "srrk_abcdef12_short", "Bearer " + key, "xxrk_abcdef12_" + "a".repeat(43)]) {
    assert.equal(parseCollectorKey(bad), null, bad);
  }
  const store = read("src/radar-collectors-v1.ts");
  assert.match(store, /timingSafeEqual/);
  assert.match(store, /randomBytes\(32\)\.toString\("base64url"\)/);
});

test("bearer parsing distinguishes collector keys from the legacy secret", () => {
  const { key } = generateCollectorKey();
  assert.equal(collectorBearer(`Bearer ${key}`), key);
  assert.equal(isCollectorBearer(`Bearer ${key}`), true);
  assert.equal(isCollectorBearer("Bearer legacy-secret"), false);
  assert.equal(isCollectorBearer(null), false);
});

test("legacy RADAR_INGEST_SECRET compare keeps accept/deny semantics with timing-safe compare", () => {
  assert.equal(legacyRadarSecretAuthorized("Bearer s3cret", "s3cret"), true);
  assert.equal(legacyRadarSecretAuthorized("Bearer s3cret ", "s3cret"), false);
  assert.equal(legacyRadarSecretAuthorized("bearer s3cret", "s3cret"), false);
  assert.equal(legacyRadarSecretAuthorized("Bearer other", "s3cret"), false);
  assert.equal(legacyRadarSecretAuthorized(null, "s3cret"), false);
  assert.equal(legacyRadarSecretAuthorized("Bearer ", ""), false, "missing secret never authorizes");
  assert.equal(timingSafeSecretEqual("a", "a"), true);
  assert.equal(timingSafeSecretEqual("a", "ab"), false);
});

test("collector config: scopes subset, sources slugs (no reserved), batch/rate bounds", () => {
  const ok = normalizeCollectorConfigV1({ name: "Coletor SP", allowed_sources: ["Prefeitura_SP", "sympla", "sympla"], scopes: ["events:ingest"] });
  assert.ok(Date.parse(ok.expires_at!)>Date.now());
  const {expires_at:_expiry,...config}=ok;
  assert.deepEqual(config, { name: "Coletor SP", allowed_sources: ["prefeitura_sp", "sympla"], scopes: ["events:ingest"], max_batch: 200, rate_limit_per_hour: 30 });
  const bads: Record<string, unknown>[] = [
    { name: "x", allowed_sources: ["a1"], scopes: ["events:ingest"] },
    { name: "Coletor", allowed_sources: [], scopes: ["events:ingest"] },
    { name: "Coletor", allowed_sources: ["ticketmaster"], scopes: ["events:ingest"] },
    { name: "Coletor", allowed_sources: ["manual"], scopes: ["events:ingest"] },
    { name: "Coletor", allowed_sources: ["bad source"], scopes: ["events:ingest"] },
    { name: "Coletor", allowed_sources: ["src"], scopes: ["admin"] },
    { name: "Coletor", allowed_sources: ["src"], scopes: [] },
    { name: "Coletor", allowed_sources: ["src"], scopes: ["events:ingest"], max_batch: 501 },
    { name: "Coletor", allowed_sources: ["src"], scopes: ["events:ingest"], max_batch: 0 },
    { name: "Coletor", allowed_sources: ["src"], scopes: ["events:ingest"], rate_limit_per_hour: 1001 },
  ];
  for (const bad of bads) assert.throws(() => normalizeCollectorConfigV1(bad), JSON.stringify(bad));
  assert.deepEqual(normalizeCollectorConfigV1({ max_batch: 50 }, true), { max_batch: 50 });
  assert.equal(effectiveMaxBatch({ max_batch: 9999 }), 500);
  assert.equal(effectiveMaxBatch({ max_batch: 0 }), 200);
});

test("collector privileges: allowed_sources only, reserved internal sources always denied", () => {
  const c = { allowed_sources: ["prefeitura_sp", "ticketmaster"] };
  assert.equal(collectorSourceAllowed(c, "prefeitura_sp"), true);
  assert.equal(collectorSourceAllowed(c, "sympla"), false);
  assert.equal(collectorSourceAllowed(c, "ticketmaster"), false);
  assert.equal(collectorSourceAllowed(c, null), false);
});

test("public collector view never leaks key_hash", () => {
  const pub = publicCollectorV1({ id: "x", name: "n", key_hash: "a".repeat(64), key_prefix: "srrk_abcd1234", scopes: [], allowed_sources: [] });
  assert.ok(!("key_hash" in pub));
  assert.ok(!JSON.stringify(pub).includes("a".repeat(64)));
});

test("schema-missing detection covers PostgREST/Postgres codes and messages", () => {
  assert.equal(isMissingSchemaError({ code: "42703", message: "column sr_event_opportunities.review_status does not exist" }), true);
  assert.equal(isMissingSchemaError({ code: "42P01", message: 'relation "public.sr_radar_collectors" does not exist' }), true);
  assert.equal(isMissingSchemaError({ code: "PGRST205", message: "Could not find the table 'public.sr_radar_collectors' in the schema cache" }), true);
  assert.equal(isMissingSchemaError({ code: "PGRST204", message: "Could not find the 'review_status' column of 'sr_event_opportunities' in the schema cache" }), true);
  assert.equal(isMissingSchemaError({ code: "23505", message: "duplicate key value violates unique constraint" }), false);
  assert.equal(isMissingSchemaError(null), false);
});

test("collector store degrades to 503 collector_store_unavailable and stores only the hash", () => {
  const store = read("src/radar-collector-store-v1.ts");
  assert.match(store, /collector_store_unavailable/);
  assert.match(store, /hashCollectorKey\(parsed.key\)/);
  assert.match(store, /verifyCollectorKey/);
  assert.match(store, /collector_rate_limited/);
  assert.match(store, /key_hash: generated\.hash/);
  assert.ok(!/key: generated\.key,\s*\n?\s*key_hash/.test(store));
  assert.ok(!/insert\(\{[^}]*\bkey: /.test(store), "plain key is never inserted");
  const lib = read("src/radar-collectors-v1.ts");
  assert.match(lib, /\{ error: "collector_store_unavailable" \}/);
  assert.match(lib, /status: 503/);
});
