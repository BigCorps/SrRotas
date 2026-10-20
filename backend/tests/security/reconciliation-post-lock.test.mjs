import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const route = readFileSync(new URL("../../app/api/v1/reconciliation/route.ts", import.meta.url), "utf8");
const get = route.slice(route.indexOf("export async function GET"), route.indexOf("export async function POST"));
const post = route.slice(route.indexOf("export async function POST"));

test("GET keeps review available", () => {
  assert.match(get, /reconciliationDetail\(auth\.driverId, importId\)/);
  assert.match(get, /reconciliationSummary\(auth\.driverId/);
});

test("POST requires authentication and entitlement, then refuses all writes", () => {
  assert.match(post, /authenticateBillingActor\(request\)/);
  assert.match(post, /accessDeniedResponse\(auth\.access, "can_analytics"\)/);
  assert.match(post, /reconciliation_writes_locked/);
  assert.match(post, /status: 423/);
  assert.match(post, /"Cache-Control": "no-store"/);
  assert.doesNotMatch(post, /request\.json|decideReconciliation|\.rpc\(|\.insert\(|\.update\(|\.upsert\(|\.delete\(/);
  assert.doesNotMatch(route, /import \{[^}]*decideReconciliation/);
});
