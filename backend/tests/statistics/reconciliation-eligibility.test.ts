import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { reconciliationCandidateEligibility, RECONCILIATION_MAX_DELTA_MINUTES } from "../../src/reconciliation-candidate-eligibility";
import { classifyReconciliationWriteError } from "../../src/reconciliation-write-error";

const base = Date.parse("2026-10-20T15:00:00Z");
const now = base + 180 * 60_000;
const imp = { driver_id:"driver1", ride_status:"completed", occurred_at:new Date(base).toISOString() };
const offer = { driver_id:"driver1", platform:"uber", observed_at:new Date(base).toISOString(), journey_id:"journey1", local_offer_id:"offer1" };
type Ride = Parameters<typeof reconciliationCandidateEligibility>[0];
type Candidate = Parameters<typeof reconciliationCandidateEligibility>[1];
const check = (o: Partial<Candidate>, i: Partial<Ride> = {}) =>
  reconciliationCandidateEligibility({ ...imp, ...i }, { ...offer, ...o }, now);

test("inclusive 75-minute gate blocks even one millisecond outside", () => {
  assert.equal(RECONCILIATION_MAX_DELTA_MINUTES, 75);
  assert.equal(check({}).eligible, true);
  assert.equal(check({observed_at:new Date(base-75*60_000).toISOString()}).eligible, true);
  assert.equal(check({observed_at:new Date(base+75*60_000).toISOString()}).eligible, true);
  assert.deepEqual(check({observed_at:new Date(base+75*60_000+1).toISOString()}),{eligible:false,reason:"outside_time_window"});
});

test("invalid times, unrelated apps, driver and incomplete offers are rejected", () => {
  assert.equal(check({platform:"99"}).reason,"platform_not_uber");
  assert.equal(check({platform:"other"}).reason,"platform_not_uber");
  assert.equal(check({driver_id:"different"}).reason,"driver_mismatch");
  assert.equal(check({observed_at:null}).reason,"offer_time_unverified");
  assert.equal(check({observed_at:"bad"}).reason,"offer_time_unverified");
  assert.equal(check({observed_at:new Date(now+1000).toISOString()}).reason,"offer_time_unverified");
  assert.equal(check({}, {occurred_at:null}).reason,"import_time_unverified");
  assert.equal(check({}, {occurred_at:"bad"}).reason,"import_time_unverified");
  assert.equal(check({}, {occurred_at:new Date(now+1000).toISOString()}).reason,"import_time_unverified");
  assert.equal(check({journey_id:null}).reason,"missing_journey");
  assert.equal(check({local_offer_id:null}).reason,"missing_journey");
  assert.equal(check({}, {ride_status:"cancelled"}).reason,"import_not_completed");
});

test("reader filters hints and already confirmed offers before ranking", () => {
  const src = readFileSync(new URL("../../src/reconciliation-v1.ts", import.meta.url),"utf8");
  assert.match(src,/reconciliationCandidateEligibility\(imported, offer, nowMs\)\.eligible/);
  assert.match(src,/\.eq\("platform", "uber"\)/);
  assert.match(src,/\.eq\("driver_id", driverId\)/);
  assert.match(src,/\.eq\("decision", "confirmed"\)/);
  assert.match(src,/usedIds\.has\(String\(offer\.id\)\)/);
  assert.match(src,/Number\.isFinite\(center\) && center <= Date\.now\(\)/);
  const contract = readFileSync(new URL("../../../docs/contracts/srrotas-reconciliation-atomic-v2.review.sql", import.meta.url),"utf8");
  assert.match(contract,/interval '75 minutes'/);
  assert.match(contract,/v_offer\.platform is distinct from 'uber'/);
});

test("future SQL 23505 is a safe 409 without leaking raw database errors", () => {
  assert.deepEqual(classifyReconciliationWriteError({code:"23505",message:"duplicate sensitive ride details"}),{status:409,error:"reconciliation_conflict"});
  assert.deepEqual(classifyReconciliationWriteError({message:"reconciliation_already_reviewed"}),{status:409,error:"reconciliation_conflict"});
  assert.deepEqual(classifyReconciliationWriteError({message:"reconciliation_offer_outside_time_window"}),{status:422,error:"reconciliation_candidate_invalid"});
  assert.deepEqual(classifyReconciliationWriteError({code:"42501",message:"secret table reference"}),{status:500,error:"reconciliation_write_failed"});
  assert.deepEqual(classifyReconciliationWriteError({message:"private SQL details"}),{status:500,error:"reconciliation_write_failed"});
  const locked = readFileSync(new URL("../../app/api/v1/reconciliation/route.ts", import.meta.url),"utf8");
  assert.match(locked,/reconciliation_writes_locked/);
  assert.match(locked,/status: 423/);
  assert.doesNotMatch(locked,/classifyReconciliationWriteError|\.rpc\(/);
});
