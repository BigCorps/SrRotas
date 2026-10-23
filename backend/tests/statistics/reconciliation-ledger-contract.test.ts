import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const sql = readFileSync(new URL("../../../docs/contracts/srrotas-reconciliation-atomic-v2.review.sql", import.meta.url), "utf8");
const route = readFileSync(new URL("../../app/api/v1/reconciliation/route.ts", import.meta.url), "utf8");
const begin = sql.indexOf("as $$");
const end = sql.indexOf("end;\n$$;", begin);
const body = sql.slice(begin, end);

test("one-ledger-write draft changes no original observations or outcomes", () => {
  assert.match(sql, /REVIEW ONLY \/ DO NOT APPLY/);
  assert.match(body, /insert into public\.sr_ride_reconciliations/);
  assert.doesNotMatch(body, /(?:insert into|update|delete from)\s+public\.(?:ride_outcomes|ride_offers|uber_completed_ride_imports)/);
  assert.match(body, /'outcome_created', false/);
  assert.match(body, /'final_payment_verified', false/);
  assert.doesNotMatch(sql, /p_score|security definer/i);
  assert.match(body, /null, 'journey-reconciliation-ledger-v2'/);
});

test("duplicate selected offers and same-import retries have independent DB guards", () => {
  assert.match(sql, /create unique index sr_ride_reconciliation_unique_confirmed_offer_v2/);
  assert.doesNotMatch(sql, /create unique index if not exists sr_ride_reconciliation_unique_confirmed_offer_v2/i);
  assert.match(sql, /on public\.sr_ride_reconciliations \(driver_id, selected_ride_offer_id\)/);
  assert.match(sql, /where decision = 'confirmed' and selected_ride_offer_id is not null/);
  assert.match(body, /where id = p_import_id and driver_id = p_driver_id\s+for update/);
  assert.match(body, /where id = p_offer_id and driver_id = p_driver_id\s+for update/);
  assert.match(body, /reconciliation_already_reviewed/);
  assert.match(body, /reconciliation_offer_already_used/);
});

test("cross-driver and invalid import/offer states are fail-closed in the draft", () => {
  for (const msg of [
    "reconciliation_actor_or_import_required","reconciliation_decision_invalid",
    "reconciliation_candidate_invalid","reconciliation_import_not_found",
    "reconciliation_only_completed_rides","reconciliation_already_reviewed",
    "reconciliation_import_time_unverified","reconciliation_offer_not_found",
    "reconciliation_offer_without_owned_journey",
    "reconciliation_offer_time_unverified",
    "reconciliation_offer_outside_time_window",
    "reconciliation_offer_platform_mismatch",
  ]) assert.ok(body.includes(msg), msg);
  assert.match(body, /where id = v_offer\.journey_id and driver_id = p_driver_id/);
  assert.match(body, /v_import\.occurred_at is null or v_import\.occurred_at > pg_catalog\.now\(\)/);
  assert.match(body, /v_offer\.observed_at is null or v_offer\.observed_at > pg_catalog\.now\(\)/);
  assert.match(body, /v_offer\.observed_at < v_import\.occurred_at - interval '75 minutes'/);
  assert.match(body, /v_offer\.observed_at > v_import\.occurred_at \+ interval '75 minutes'/);
  assert.match(body, /v_offer\.platform is distinct from 'uber'/);
  assert.doesNotMatch(body, /coalesce\(v_import\.occurred_at|:= v_import\.captured_at/);
});

test("future RPC is service-role only; current HTTP POST remains locked", () => {
  assert.match(sql, /security invoker/);
  assert.match(sql, /set search_path = ''/);
  assert.match(sql, /revoke all on function public\.sr_apply_reconciliation_atomic_v2\(uuid,uuid,text,bigint\)\s+from public, anon, authenticated/);
  assert.match(sql, /grant execute on function public\.sr_apply_reconciliation_atomic_v2\(uuid,uuid,text,bigint\)\s+to service_role/);
  assert.match(route, /reconciliation_writes_locked/);
  assert.match(route, /status: 423/);
  assert.doesNotMatch(route, /sr_apply_reconciliation_atomic_v2/);
});
