import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  applyApprovedReviewFilterV1,
  decisionToReviewStatusV1,
  googleMapsSearchUrlV1,
  inconsistencyFlagsV1,
  isLikelyDuplicateV1,
  reviewFlagsV1,
  validateReviewDecisionV1,
  withApprovedReviewFallbackV1,
} from "../../src/radar-review-v1";

const read = (rel: string) => readFileSync(new URL(`../../${rel}`, import.meta.url), "utf8");
const NOW = Date.parse("2026-11-05T12:00:00Z");
const ev = (o: Record<string, unknown> = {}) => ({
  id: "a", name: "Show na Praça", venue_name: "Praça Central", lat: -23.55, lng: -46.63,
  starts_at: "2026-11-20T23:00:00Z", expected_end_at: "2026-11-21T02:00:00Z",
  source_url: "https://x.org/e", confidence: 0.9, review_status: "pending", status: "active", ...o,
}) as any;

test("likely duplicate: same normalized name, start within 3h, ≤ 300 m", () => {
  assert.equal(isLikelyDuplicateV1(ev(), ev({ id: "b", name: "SHOW na praca!", starts_at: "2026-11-21T01:30:00Z", lat: -23.551, venue_name: "x" })), true);
  assert.equal(isLikelyDuplicateV1(ev(), ev({ id: "b", starts_at: "2026-11-21T03:30:00Z", venue_name: "x" })), false, "> 3h");
  assert.equal(isLikelyDuplicateV1(ev(), ev({ id: "b", lat: -23.56, venue_name: "x" })), false, "> 300 m");
  assert.equal(isLikelyDuplicateV1(ev(), ev()), false, "same id");
});

test("likely duplicate: same venue + same start (±15 min) even with a different name", () => {
  assert.equal(isLikelyDuplicateV1(ev(), ev({ id: "b", name: "Outro", lat: -23.6, starts_at: "2026-11-20T23:10:00Z" })), true);
  assert.equal(isLikelyDuplicateV1(ev(), ev({ id: "b", name: "Outro", lat: -23.6, starts_at: "2026-11-21T00:00:00Z" })), false);
});

test("inconsistency flags", () => {
  const codes = (o: Record<string, unknown>) => inconsistencyFlagsV1(ev(o), NOW).map((f) => f.code);
  assert.deepEqual(codes({}), []);
  assert.ok(codes({ expected_end_at: "2026-11-20T22:00:00Z" }).includes("end_before_start"));
  assert.ok(codes({ starts_at: "2026-11-01T22:00:00Z", expected_end_at: "2026-11-01T23:00:00Z" }).includes("start_in_past"));
  assert.ok(!codes({ starts_at: "2026-11-01T22:00:00Z", expected_end_at: "2026-11-01T23:00:00Z", review_status: "approved" }).includes("start_in_past"));
  assert.ok(codes({ lat: 40.7, lng: -74.0 }).includes("outside_brazil"));
  assert.ok(codes({ source_url: null }).includes("missing_source_url"));
  assert.ok(codes({ confidence: 0.3 }).includes("low_confidence"));
  assert.ok(codes({ confidence: null }).includes("low_confidence"));
});

test("reviewFlagsV1 compares listed rows with context rows", () => {
  const flags = reviewFlagsV1([ev()], [ev({ id: "ctx", starts_at: "2026-11-20T23:30:00Z" })], NOW);
  const f = flags.get("a")!;
  assert.equal(f[0].code, "likely_duplicate");
  assert.deepEqual(f[0].related_ids, ["ctx"]);
});

test("Google Maps link uses the universal external search URL", () => {
  assert.equal(googleMapsSearchUrlV1(-23.55, -46.63), "https://www.google.com/maps/search/?api=1&query=-23.550000,-46.630000");
  assert.equal(googleMapsSearchUrlV1("x", 1), null);
});

test("review decisions map to statuses; reject/request_changes need a note", () => {
  assert.equal(decisionToReviewStatusV1("approve"), "approved");
  assert.equal(decisionToReviewStatusV1("reject"), "rejected");
  assert.equal(decisionToReviewStatusV1("request_changes"), "changes_requested");
  const id = "123e4567-e89b-12d3-a456-426614174000";
  assert.deepEqual(validateReviewDecisionV1({ id, decision: "approve",note:"Reviewed",expected_version:1 }), { id, decision: "approve", note: "Reviewed",version:1 });
  assert.throws(() => validateReviewDecisionV1({ id, decision: "reject", note: "" }), /note_required/);
  assert.throws(() => validateReviewDecisionV1({ id, decision: "publish" }), /decision_invalid/);
  assert.throws(() => validateReviewDecisionV1({ id: "1", decision: "approve" }), /id_invalid/);
});

// ------------------------------------------------------------- consumer filter

function fakeQuery(log: string[]) {
  const q: any = { eq(c: string, v: string) { log.push(`${c}=${v}`); return q; } };
  return q;
}

test("consumer approved filter builder adds review_status=approved only when requested", () => {
  const a: string[] = [];
  applyApprovedReviewFilterV1(fakeQuery(a).eq("status", "active"), true);
  assert.deepEqual(a, ["status=active", "review_status=approved"]);
  const b: string[] = [];
  applyApprovedReviewFilterV1(fakeQuery(b).eq("status", "active"), false);
  assert.deepEqual(b, ["status=active"]);
});

test("consumer fails closed when review_status is missing (42703)", async () => {
  const calls: boolean[] = [];
  await assert.rejects(withApprovedReviewFallbackV1(async (approvedOnly)=>{calls.push(approvedOnly);return {data:null,error:{code:'42703',message:'review column missing'}}}),/collector_store_unavailable/);
  assert.deepEqual(calls,[true]);

  const calls2: boolean[] = [];
  const ok = await withApprovedReviewFallbackV1(async (approvedOnly) => { calls2.push(approvedOnly); return { data: [], error: null }; });
  assert.deepEqual(calls2, [true]);
  assert.equal(ok.review_filter, "approved");

  const other = await withApprovedReviewFallbackV1(async () => ({ data: null, error: { code: "57014", message: "timeout" } }));
  assert.equal(other.error.code, "57014", "non-schema errors are not masked");
});

test("all app-facing event readers and reconcile use the approved filter with fallback", () => {
  for (const file of ["src/event-radar-026.ts", "src/radar-poi-v1.ts", "src/radar-opportunities-service-v1.ts", "src/radar-poi-resolver-v1.ts"]) {
    const s = read(file);
    assert.match(s, /withApprovedReviewFallbackV1\(/, file);
    assert.match(s, /applyApprovedReviewFilterV1\(/, file);
  }
  const radar = read("src/event-radar-026.ts");
  const nearby = radar.split("export async function nearbyEventRadar026")[1];
  assert.match(nearby, /withApprovedReviewFallbackV1/);
  assert.match(nearby, /\.eq\("status", "active"\)/);
});

test("admin radar routes require the admin session and refuse collector keys", () => {
  const http = read("src/radar-admin-http-v1.ts");
  assert.match(http, /importActor\(request\)/);
  assert.match(http, /canAccessAdminOps\(actor\.email\)/);
  assert.match(http, /collector_key_not_allowed/);
  assert.match(http, /"Cache-Control": "no-store"/);
  for (const route of ["events", "pois", "decisions", "collectors", "runs"]) {
    const s = read(`app/api/v1/admin/radar/${route}/route.ts`);
    const handlers = s.split("export async function ").slice(1);
    assert.ok(handlers.length > 0, route);
    for (const h of handlers) assert.match(h, /requireRadarAdminV1\(request\)/, `${route}:${h.slice(0, 6)}`);
    assert.ok(!/radarIngestSecret|srrk_/.test(s), `${route} must not accept secrets/keys`);
  }
});
