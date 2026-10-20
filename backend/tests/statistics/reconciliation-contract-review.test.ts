import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const sql = readFileSync(new URL("../../../docs/contracts/srrotas-reconciliation-atomic-v2.review.sql", import.meta.url), "utf8");
const body = sql.slice(sql.indexOf("as $$"), sql.indexOf("end;\n$$;"));
const preflight = sql.slice(sql.indexOf("do $preflight$"), sql.indexOf("$preflight$;",sql.indexOf("do $preflight$")));

test("schema-qualified preflight fails closed for reused index name, duplicates and legacy overload", () => {
  assert.match(preflight, /to_regclass\('public\.sr_ride_reconciliation_unique_confirmed_offer_v2'\)/);
  assert.match(preflight, /reconciliation_index_exists_review_required/);
  assert.match(preflight, /having pg_catalog\.count\(\*\) > 1/);
  assert.match(preflight, /reconciliation_duplicate_confirmations_preflight/);
  assert.match(preflight, /to_regprocedure\('public\.sr_apply_reconciliation_atomic_v2\(uuid,uuid,text,bigint,numeric\)'\)/);
  assert.match(preflight, /reconciliation_legacy_overload_review_required/);
  assert.match(sql, /create unique index sr_ride_reconciliation_unique_confirmed_offer_v2/);
  assert.doesNotMatch(sql, /create unique index if not exists/i);
});

test("DB independently rejects invalid platform, missing timestamps and candidates outside inclusive 75-minute window", () => {
  assert.match(body, /v_offer\.platform is distinct from 'uber'/);
  assert.match(body, /v_offer\.observed_at is null or v_offer\.observed_at > pg_catalog\.now\(\)/);
  assert.match(body, /v_offer\.observed_at < v_import\.occurred_at - interval '75 minutes'/);
  assert.match(body, /v_offer\.observed_at > v_import\.occurred_at \+ interval '75 minutes'/);
  assert.match(body, /reconciliation_offer_outside_time_window/);
  assert.match(body, /reconciliation_offer_platform_mismatch/);
  assert.doesNotMatch(body, /coalesce\(v_offer\.observed_at|coalesce\(v_import\.occurred_at/);
});

test("review-only ledger still does not mutate source data or activate HTTP writes", () => {
  const route = readFileSync(new URL("../../app/api/v1/reconciliation/route.ts", import.meta.url), "utf8");
  assert.match(body, /insert into public\.sr_ride_reconciliations/);
  assert.doesNotMatch(body, /(?:insert into|update|delete from)\s+public\.(?:ride_outcomes|ride_offers|uber_completed_ride_imports)/i);
  assert.match(body, /'outcome_created', false/);
  assert.match(body, /'final_payment_verified', false/);
  assert.match(route, /reconciliation_writes_locked/);
  assert.match(route, /status: 423/);
  assert.doesNotMatch(route, /\.rpc\(/);
});
