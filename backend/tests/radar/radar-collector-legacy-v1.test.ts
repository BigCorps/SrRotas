import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { poiCandidateFromEventV1 } from "../../src/radar-poi-resolver-v1";

const read = (rel: string) => readFileSync(new URL(`../../${rel}`, import.meta.url), "utf8");

test("reconcile creates POI candidates inactive + review pending (never published)", () => {
  const row = poiCandidateFromEventV1({
    id: "e-1", source: "prefeitura_sp", external_id: "evt-1", event_type: "music", name: "Show",
    venue_name: "Arena Central", address: "Rua A", city: "São Paulo", state: "SP",
    lat: -23.55, lng: -46.63, source_url: "https://x.org", confidence: 0.9, metadata: {},
  }, "2026-11-05T12:00:00.000Z");
  assert.equal(row.status, "inactive");
  assert.equal(row.metadata.review, "pending");
  assert.equal(row.metadata.created_from_event_id, "e-1");
  assert.equal(row.canonical_name, "Arena Central");
  const src = read("src/radar-poi-resolver-v1.ts");
  const create = src.split("async function createPoiFromEvent")[1].split("export async function")[0];
  assert.match(create, /poiCandidateFromEventV1\(event\)/);
  assert.match(create, /existingPoiCandidate/);
  assert.ok(!/status:"active"/.test(src), "resolver never writes active POIs");
});

test("legacy RADAR_INGEST_SECRET ingest path is unchanged (same handler, same errors)", () => {
  const route = read("app/api/v1/radar/ingest/route.ts");
  assert.match(route, /legacyRadarSecretAuthorized\(request\.headers\.get\("authorization"\), serverEnv\(\)\.radarIngestSecret\)/);
  const post = route.split("export async function POST")[1].split("export async function GET")[0];
  // legacy branch first, still accepting raw arrays and calling the legacy ingest
  const legacyIdx = post.indexOf("if (authorized(request))");
  const collectorIdx = post.indexOf("isCollectorBearer(");
  assert.ok(legacyIdx >= 0 && collectorIdx > legacyIdx, "legacy secret checked before the collector path");
  assert.match(post, /Array\.isArray\(body\) \? \{ events: body \} : body/);
  assert.match(post, /ingestRadarEvents0261\(payload\)/);
  assert.match(post, /radar_ingest_secret_missing/);
  assert.match(post, /\{ error: "unauthorized" \}, \{ status: 401 \}/);
  assert.match(post, /radar_ingest_failed/);
  assert.ok(!/===\s*`Bearer/.test(route), "no plain === secret compare");
  // legacy ingest module still supports snapshot_complete for the internal path
  const legacy = read("src/radar-ingest-0261.ts");
  assert.match(legacy, /if \(input\.snapshot_complete\)/);
  assert.match(legacy, /onConflict: "source,external_id"/);
});

test("internal admin Radar routes keep the secret (constant time) and never accept collector keys", () => {
  for (const rel of ["app/api/v1/radar/admin/route.ts", "app/api/v1/radar/poi/admin/route.ts", "app/api/v1/radar/poi/reconcile/route.ts"]) {
    const s = read(rel);
    assert.match(s, /legacyRadarSecretAuthorized\(/, rel);
    assert.ok(!/===\s*`Bearer/.test(s), `${rel}: plain === compare removed`);
    assert.ok(!/isCollectorBearer|authenticateCollector|collectorIngest/.test(s), `${rel}: no collector auth`);
  }
});

test("collector ingest never accepts snapshot_complete and never runs reconcile", () => {
  const ingest = read("src/radar-collector-ingest-v1.ts");
  assert.match(ingest, /snapshot_not_allowed_for_collectors/);
  assert.ok(!/reconcileUnlinkedRadarEventsV1|resolveRadarEventPoiV1/.test(ingest));
  assert.match(ingest, /review_status: "pending"/);
  const http = read("src/radar-collector-http-v1.ts");
  assert.ok(!/reconcile/.test(http));
});

test("migration is additive, guarded, server-only and keeps existing rows approved", () => {
  const sql = read("../supabase/migrations/20261105120000_radar_collectors_review_v1.sql");
  const body = sql.split("-- ROLLBACK")[0];
  assert.match(body, /add column if not exists review_status text not null default 'approved'/);
  assert.match(body, /create unique index if not exists sr_radar_collectors_key_hash_uidx/);
  assert.match(body, /sr_event_opportunities\(review_status, status\)/);
  for (const t of ["sr_radar_collectors", "sr_radar_collector_usage", "sr_radar_review_decisions"]) {
    assert.match(body, new RegExp(`create table if not exists public\\.${t}`));
    assert.match(body, new RegExp(`alter table public\\.${t} enable row level security`));
    assert.match(body, new RegExp(`revoke all on table public\\.${t} from public, anon, authenticated`));
  }
  assert.ok(!/\bdrop\b/i.test(body), "no drops outside the commented rollback");
  assert.ok(!/create policy/i.test(body));
  const rollback = sql.split("-- ROLLBACK")[1];
  assert.ok(rollback.split("\n").slice(1).filter((l) => l.trim()).every((l) => l.trim().startsWith("--")), "rollback is comments only");
});
