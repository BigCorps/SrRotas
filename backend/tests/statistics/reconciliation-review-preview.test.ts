import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  historicalTimeStatus, isValidCandidateChoice, pendingImportedRides, readableFare,
  type ReviewDetail,
} from "../../app/app/estatisticas/reconciliation-review-model";

const component = readFileSync(new URL("../../app/app/estatisticas/ReconciliationReview.tsx", import.meta.url), "utf8");
const page = readFileSync(new URL("../../app/app/estatisticas/page.tsx", import.meta.url), "utf8");

test("only unresolved completed imports can enter manual preview", () => {
  const rows = pendingImportedRides({
    unresolved: 2, total_completed_imports: 3,
    items: [
      { id: "a", ride_status: "completed", decision: null, captured_at: null, occurred_at: null, fare: null, service_type: null, pickup_label: null, destination_label: null },
      { id: "b", ride_status: "completed", decision: { decision: "confirmed", selected_ride_offer_id: 11 }, captured_at: null, occurred_at: null, fare: null, service_type: null, pickup_label: null, destination_label: null },
      { id: "c", ride_status: "cancelled", decision: null, captured_at: null, occurred_at: null, fare: null, service_type: null, pickup_label: null, destination_label: null },
    ],
  });
  assert.deepEqual(rows.map(r=>r.id), ["a"]);
});

test("choice can only target a candidate actually returned by the server", () => {
  const detail = {
    import: { id: "a", ride_status: "completed", decision: null },
    candidates: [{ id: 15 }],
  } as ReviewDetail;
  assert.equal(isValidCandidateChoice(detail, "15"), true);
  assert.equal(isValidCandidateChoice(detail, "16"), false);
  assert.equal(isValidCandidateChoice({ ...detail, decision: { decision: "no_match", selected_ride_offer_id: null } }, "15"), false);
  assert.equal(isValidCandidateChoice({ ...detail, import: { ...detail.import, decision: { decision: "no_match", selected_ride_offer_id: null } } }, "15"), false);
  assert.equal(isValidCandidateChoice(null, "15"), false);
});

test("incomplete or future import dates are explicitly visible", () => {
  const now = Date.parse("2026-10-20T12:00:00Z");
  assert.match(historicalTimeStatus(null, now)!, /não identificado/);
  assert.match(historicalTimeStatus("2027-01-01T10:00:00Z", now)!, /futura/);
  assert.match(historicalTimeStatus("not-a-date", now)!, /inválido/);
  assert.equal(historicalTimeStatus("2026-10-19T10:00:00Z", now), null);
  assert.equal(readableFare(null), "—");
  assert.equal(readableFare(""), "—");
  assert.equal(readableFare(0), "R$ 0,00");
});

test("preview uses only GET requests; draft selections do not write or promise approval", () => {
  assert.match(component, /\/api\/v1\/reconciliation\?limit=10/);
  assert.match(component, /\/api\/v1\/reconciliation\?import_id=/);
  assert.match(component, /Simular escolha/);
  assert.match(component, /Simular sem correspondência/);
  assert.match(component, /Nenhuma decisão foi enviada ao servidor/);
  assert.doesNotMatch(component, /\bmethod:\s*["']POST["']/);
  assert.doesNotMatch(component, /\/api\/v1\/reconciliation\?summary_only=1/);
  assert.match(page, /section === "corridas" \? <ReconciliationReview \/> : null/);
});
