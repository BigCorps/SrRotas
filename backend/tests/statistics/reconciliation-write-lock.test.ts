import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const route = readFileSync(new URL("../../app/api/v1/reconciliation/route.ts", import.meta.url), "utf8");
const backend = readFileSync(new URL("../../src/reconciliation-v1.ts", import.meta.url), "utf8");
const sql = readFileSync(new URL("../../../docs/contracts/srrotas-reconciliation-atomic-v2.review.sql", import.meta.url), "utf8");

test("reconciliation POST fails closed after checking identity and subscription", () => {
  const post = route.slice(route.indexOf("export async function POST"));
  assert.match(post, /authenticateBillingActor\(request\)/);
  assert.match(post, /accessDeniedResponse\(auth\.access, "can_analytics"\)/);
  assert.match(post, /reconciliation_writes_locked/);
  assert.match(post, /status: 423/);
  assert.doesNotMatch(post, /request\.json|decideReconciliation|\.rpc\(|\.upsert\(|\.insert\(|\.update\(|\.delete\(/);
  assert.doesNotMatch(backend, /export async function decideReconciliation/);
  assert.doesNotMatch(backend, /removeLegacyOutcomeForOffer/);
});

test("manual review GET remains available and read-only", () => {
  const get = route.slice(route.indexOf("export async function GET"), route.indexOf("export async function POST"));
  assert.match(get, /reconciliationDetail\(auth\.driverId, importId\)/);
  assert.match(get, /reconciliationOverview\(auth\.driverId\)/);
  assert.match(get, /reconciliationSummary\(auth\.driverId/);
  assert.doesNotMatch(get, /\.insert\(|\.update\(|\.upsert\(|\.delete\(/);
});

test("atomic SQL is a review-only contract, not a migration or active endpoint", () => {
  assert.match(sql, /REVIEW ONLY \/ DO NOT APPLY/);
  assert.match(sql, /create unique index if not exists sr_ride_reconciliation_unique_confirmed_offer_v2/);
  assert.match(sql, /on public\.sr_ride_reconciliations \(driver_id, selected_ride_offer_id\)/);
  assert.match(sql, /where decision = 'confirmed'/);
  assert.match(sql, /for update/g);
  assert.match(sql, /on conflict \(driver_id, local_offer_id\)/);
  assert.match(sql, /where public\.ride_outcomes\.source = 'offer_engine'/);
  assert.match(sql, /grant execute on function public\.sr_apply_reconciliation_atomic_v2/);
  assert.match(sql, /to service_role/);
  assert.match(sql, /final_payment_verified', false/);
  assert.doesNotMatch(route, /sr_apply_reconciliation_atomic_v2/);
});
