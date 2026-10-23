import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { summarizeReconciliationOverview } from "../../src/reconciliation-overview";

test("historical imports without human decisions are never shown as confirmed payouts", () => {
  const result = summarizeReconciliationOverview({
    totalCompleted: 283, reviewed: 0, confirmed: 0, noMatch: 0,
    withoutOccurrence: 234, futureOccurrence: 1,
  });
  assert.equal(result.status, "PENDENTE");
  assert.equal(result.unresolved, 283);
  assert.equal(result.confirmed, 0);
  assert.equal(result.coverage_pct, 0);
  assert.equal(result.missing_occurrence_time, 234);
  assert.equal(result.future_occurrence_time, 1);
  assert.equal(result.final_payment_verified, false);
  assert.ok(!("verified_payout" in result));
});

test("zero imports yields missing coverage rather than a fabricated 100 percent", () => {
  const empty = summarizeReconciliationOverview({
    totalCompleted: 0, reviewed: 0, confirmed: 0, noMatch: 0,
    withoutOccurrence: 0, futureOccurrence: 0,
  });
  assert.equal(empty.status, "SEM_HISTORICO");
  assert.equal(empty.coverage_pct, null);
  assert.equal(empty.final_payment_verified, false);
});

test("completed human reviews still do not certify an external payment", () => {
  const result = summarizeReconciliationOverview({
    totalCompleted: 5, reviewed: 5, confirmed: 3, noMatch: 2,
    withoutOccurrence: 1, futureOccurrence: 0,
  });
  assert.equal(result.status, "REVISADO");
  assert.equal(result.coverage_pct, 100);
  assert.equal(result.unresolved, 0);
  assert.equal(result.final_payment_verified, false);
});

test("summary-only API preserves auth and never calls any reconciliation write operation", () => {
  const api = readFileSync(new URL("../../app/api/v1/reconciliation/route.ts", import.meta.url), "utf8");
  const backend = readFileSync(new URL("../../src/reconciliation-v1.ts", import.meta.url), "utf8");
  const page = readFileSync(new URL("../../app/app/estatisticas/page.tsx", import.meta.url), "utf8");
  const getOnly = api.slice(api.indexOf("export async function GET"), api.indexOf("export async function POST"));
  const func = backend.slice(backend.indexOf("export async function reconciliationOverview"), backend.indexOf("export async function reconciliationSummary"));
  assert.match(getOnly, /authenticateBillingActor\(request\)/);
  assert.match(getOnly, /accessDeniedResponse\(auth\.access, "can_analytics"\)/);
  assert.match(getOnly, /summary_only/);
  assert.match(getOnly, /reconciliationOverview\(auth\.driverId\)/);
  assert.match(func, /count: "exact", head: true/);
  assert.doesNotMatch(func, /\.(?:insert|update|upsert|delete)\(/);
  assert.doesNotMatch(func, /pickup_label|destination_label|fare/);
  assert.match(page, /async function loadReconciliationOverview/);
  assert.match(page, /\/api\/v1\/reconciliation\?summary_only=1/);
  assert.match(page, /onClick=\{loadReconciliationOverview\}/);
  assert.doesNotMatch(page, /decision: "confirmed"|decision: "no_match"|fetch\([^\n]+method: "POST"/);
});
