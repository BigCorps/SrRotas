import { test } from "node:test";
import assert from "node:assert/strict";
import {
  CollectorRequestError,
  decideCollectorEventV1,
  materialChangesV1,
  parseOffsetDateTimeV1,
  validateCollectorBatchV1,
  validateCollectorEventV1,
  validateCollectorUrlV1,
} from "../../src/radar-collector-ingest-v1";

const collector = { allowed_sources: ["prefeitura_sp", "sympla"], max_batch: 200 };
const NOW = "2026-11-05T12:00:00.000Z";
const base = {
  source: "prefeitura_sp",
  external_id: "evt-123",
  event_type: "music",
  name: "Show na Praça",
  venue_name: "Praça Central",
  address: "Rua A, 100",
  city: "São Paulo",
  state: "SP",
  lat: -23.55,
  lng: -46.63,
  starts_at: "2026-11-20T20:00:00-03:00",
  expected_end_at: "2026-11-20T23:00:00-03:00",
  source_url: "https://agenda.exemplo.gov.br/evt-123",
  confidence: 0.9,
  status: "active",
};

function ok(input: Record<string, unknown>) {
  const v = validateCollectorEventV1(input, collector, NOW);
  assert.ok(v.ok, JSON.stringify(v));
  return (v as any).row;
}
function code(input: unknown) {
  const v = validateCollectorEventV1(input, collector, NOW);
  assert.equal(v.ok, false);
  return (v as any).code as string;
}

test("datetimes require ISO-8601 with an explicit offset", () => {
  assert.deepEqual(parseOffsetDateTimeV1("2026-11-20T20:00:00-03:00"), { ok: true, iso: "2026-11-20T23:00:00.000Z" });
  assert.equal(parseOffsetDateTimeV1("2026-11-20T23:00:00Z").ok, true);
  assert.equal(parseOffsetDateTimeV1("2026-11-20T20:00-03:00").ok, true);
  assert.deepEqual(parseOffsetDateTimeV1("2026-11-20T20:00:00"), { ok: false, reason: "offset_required" });
  assert.deepEqual(parseOffsetDateTimeV1("2026-11-20 20:00"), { ok: false, reason: "offset_required" });
  assert.deepEqual(parseOffsetDateTimeV1("20/11/2026 20:00"), { ok: false, reason: "invalid" });
  assert.deepEqual(parseOffsetDateTimeV1("2026-02-30T20:00:00-03:00"), { ok: false, reason: "invalid" });
  assert.deepEqual(parseOffsetDateTimeV1(1763679600000), { ok: false, reason: "invalid" });
  assert.equal(code({ ...base, starts_at: "2026-11-20T20:00:00" }), "starts_at_offset_required");
  assert.equal(code({ ...base, expected_end_at: "2026-11-20T23:00:00" }), "expected_end_at_offset_required");
  assert.equal(code({ ...base, egress_end_at: "amanhã" }), "egress_end_at_invalid");
  assert.equal(code({ ...base, starts_at: undefined }), "starts_at_required");
});

test("valid event normalizes and keeps source identity", () => {
  const row = ok(base);
  assert.equal(row.source, "prefeitura_sp");
  assert.equal(row.external_id, "evt-123");
  assert.equal(row.starts_at, "2026-11-20T23:00:00.000Z");
  assert.equal(row.end_time_source, "source");
  assert.equal(row.status, "active");
  assert.equal(row.event_type, "music");
});

test("required fields, unknown type/status rejected explicitly (no silent fallback)", () => {
  assert.equal(code({ ...base, external_id: "" }), "external_id_required");
  assert.equal(code({ ...base, external_id: undefined }), "external_id_required");
  assert.equal(code({ ...base, name: "  " }), "name_required");
  assert.equal(code({ ...base, lat: 91 }), "lat_invalid");
  assert.equal(code({ ...base, lng: "abc" }), "lng_invalid");
  assert.equal(code({ ...base, event_type: "rave" }), "event_type_unknown");
  assert.equal(code({ ...base, status: "postponed" }), "status_unknown");
  assert.equal(code({ ...base, status: "expired" }), "status_not_allowed");
  assert.equal(code({ ...base, confidence: 1.2 }), "confidence_invalid");
  assert.equal(code({ ...base, confidence: "0.9" }), "confidence_invalid");
  assert.equal(code({ ...base, expected_end_at: "2026-11-20T19:00:00-03:00" }), "expected_end_before_start");
  assert.equal(code({ ...base, metadata: { blob: "x".repeat(5000) } }), "metadata_too_large");
  assert.equal(code([]), "event_invalid");
  assert.equal(ok({ ...base, event_type: undefined }).event_type, "event");
});

test("source must be in the collector allowed_sources and never a reserved internal source", () => {
  assert.equal(code({ ...base, source: "outra_fonte" }), "source_not_allowed");
  assert.equal(code({ ...base, source: "ticketmaster" }), "source_not_allowed");
  assert.equal(code({ ...base, source: "" }), "source_invalid");
});

test("source_url must be http(s) without userinfo", () => {
  assert.equal(validateCollectorUrlV1("https://x.org/a"), "https://x.org/a");
  assert.equal(validateCollectorUrlV1(undefined), null);
  for (const bad of ["javascript:alert(1)", "ftp://x.org/a", "https://u:p@x.org/a", "https://u@x.org/", "not a url", "data:text/html,1"]) {
    assert.equal(validateCollectorUrlV1(bad), "invalid", bad);
    assert.equal(code({ ...base, source_url: bad }), "source_url_invalid", bad);
  }
});

test("batch: duplicates inside the batch are rejected, others accepted, per-item index kept", () => {
  const b = validateCollectorBatchV1({
    run_id: "run-2026-11-05-a",
    events: [base, { ...base, name: "Outro nome" }, { ...base, external_id: "evt-999" }, { ...base, external_id: "" }],
  }, collector, NOW);
  assert.equal(b.received, 4);
  assert.equal(b.runId, "run-2026-11-05-a");
  assert.deepEqual(b.rows.map((r) => r.index), [2]);
  const byIndex = new Map(b.results.map((r) => [r.index, r]));
  assert.equal(byIndex.get(0)?.code, "duplicate_in_batch");
  assert.equal(byIndex.get(1)?.code, "duplicate_in_batch");
  assert.equal(byIndex.get(3)?.code, "external_id_required");
  assert.ok(b.results.every((r) => r.status === "rejected"));
});

test("batch payload rules: object only, no snapshot, run_id format, size limit 413", () => {
  const expectErr = (body: unknown, status: number, c: string) => {
    assert.throws(() => validateCollectorBatchV1(body, collector, NOW), (e: unknown) => {
      assert.ok(e instanceof CollectorRequestError);
      assert.equal((e as CollectorRequestError).httpStatus, status);
      assert.equal((e as Error).message, c);
      return true;
    });
  };
  expectErr([base], 400, "payload_must_be_object");
  expectErr({ events: [base], snapshot_complete: true }, 400, "snapshot_not_allowed_for_collectors");
  expectErr({ events: [] }, 400, "events_required");
  expectErr({ events: [base], run_id: "x" }, 400, "run_id_invalid");
  expectErr({ events: [base], dry_run: "yes" }, 400, "dry_run_invalid");
  assert.throws(() => validateCollectorBatchV1({ events: Array.from({ length: 6 }, (_, i) => ({ ...base, external_id: `e${i}` })) }, { ...collector, max_batch: 5 }, NOW), (e: unknown) => (e as CollectorRequestError).httpStatus === 413);
  assert.throws(() => validateCollectorBatchV1({ events: Array.from({ length: 501 }, (_, i) => ({ ...base, external_id: `e${i}` })) }, { ...collector, max_batch: 9999 }, NOW), /batch_too_large/);
  assert.throws(() => validateCollectorBatchV1({ events: [base, base] }, { ...collector, max_batch: 1 }, NOW), /batch_too_large/);
  assert.equal(validateCollectorBatchV1({ events: [base], dry_run: true }, collector, NOW).dryRun, true);
});

// ----------------------------------------------------------------- decisions

const ctx = { collectorId: "c-1", runId: "run-12345678", now: NOW };
function stored(overrides: Record<string, unknown> = {}) {
  return { ...ok(base), id: "e-1", review_status: "approved", collector_id: "c-1", ...overrides } as any;
}

test("new key → insert pending with collector provenance", () => {
  const d = decideCollectorEventV1(null, ok(base), ctx);
  assert.equal(d.status, "accepted");
  assert.equal(d.code, "created_pending");
  assert.equal(d.write?.kind, "insert");
  const row = (d.write as any).row;
  assert.equal(row.review_status, "pending");
  assert.equal(row.collector_id, "c-1");
  assert.equal(row.ingest_run_id, "run-12345678");
});

test("sticky cancel: non-cancelled resend never reactivates a cancelled row", () => {
  const d = decideCollectorEventV1(stored({ status: "cancelled" }), ok({ ...base, name: "Renomeado" }), ctx);
  assert.equal(d.status, "unchanged");
  assert.equal(d.code, "cancelled_kept");
  assert.equal(d.write, null);
});

test("cancellation from the collector is applied immediately without touching review state", () => {
  const d = decideCollectorEventV1(stored(), ok({ ...base, status: "cancelled" }), ctx);
  assert.equal(d.status, "accepted");
  assert.equal(d.code, "cancelled_applied");
  const patch = (d.write as any).patch;
  assert.equal(patch.status, "cancelled");
  assert.ok(!("review_status" in patch));
  assert.equal(decideCollectorEventV1(stored({ status: "cancelled" }), ok({ ...base, status: "cancelled" }), ctx).code, "already_cancelled");
});

test("material change on an approved event → back to pending (requeued_for_review)", () => {
  for (const change of [
    { name: "Show na Praça (nova data)" },
    { starts_at: "2026-11-21T20:00:00-03:00", expected_end_at: "2026-11-21T23:00:00-03:00" },
    { expected_end_at: "2026-11-21T00:30:00-03:00" },
    { lat: -23.56 },
    { lng: -46.64 },
    { venue_name: "Outro Local" },
    { address: "Rua B, 1" },
  ]) {
    const d = decideCollectorEventV1(stored(), ok({ ...base, ...change }), ctx);
    assert.equal(d.code, "requeued_for_review", JSON.stringify(change));
    assert.equal(d.status, "accepted");
    assert.equal((d.write as any).patch.review_status, "pending");
    assert.equal((d.write as any).guardNotCancelled, true);
  }
});

test("non-material/no change on an approved event → unchanged (only freshness touched)", () => {
  for (const change of [{}, { source_url: "https://agenda.exemplo.gov.br/novo" }, { confidence: 0.8 }, { metadata: { a: 1 } }]) {
    const d = decideCollectorEventV1(stored(), ok({ ...base, ...change }), ctx);
    assert.equal(d.status, "unchanged", JSON.stringify(change));
    assert.equal(d.code, "no_material_change");
    const patch = (d.write as any).patch;
    assert.deepEqual(Object.keys(patch).sort(), ["ingest_run_id", "last_verified_at"]);
  }
  assert.deepEqual(materialChangesV1(stored(), ok({ ...base, lat: -23.5500000001 })), []);
});

test("pending/rejected/changes_requested rows take new content and (re)enter pending", () => {
  assert.equal(decideCollectorEventV1(stored({ review_status: "pending" }), ok({ ...base, confidence: 0.5 }), ctx).code, "updated_pending");
  assert.equal(decideCollectorEventV1(stored({ review_status: "pending" }), ok(base), ctx).code, "no_change");
  assert.equal(decideCollectorEventV1(stored({ review_status: "changes_requested" }), ok({ ...base, source_url: "https://x.org/fix" }), ctx).code, "resubmitted_for_review");
  assert.equal(decideCollectorEventV1(stored({ review_status: "rejected" }), ok({ ...base, name: "Corrigido" }), ctx).code, "resubmitted_for_review");
  assert.equal(decideCollectorEventV1(stored({ review_status: "rejected" }), ok(base), ctx).status, "unchanged");
});

test("expired rows are only reactivated through review after a material change", () => {
  assert.equal(decideCollectorEventV1(stored({ status: "expired" }), ok(base), ctx).code, "expired_kept");
  const d = decideCollectorEventV1(stored({ status: "expired" }), ok({ ...base, starts_at: "2026-12-01T20:00:00-03:00", expected_end_at: "2026-12-01T23:00:00-03:00" }), ctx);
  assert.equal(d.code, "requeued_for_review");
  assert.equal((d.write as any).patch.status, "active");
  assert.equal((d.write as any).patch.review_status, "pending");
});

test("rows owned by another ingest path are never modified by a collector", () => {
  assert.equal(decideCollectorEventV1(stored({ collector_id: null }), ok({ ...base, status: "cancelled" }), ctx).code, "owned_by_other_ingest");
  assert.equal(decideCollectorEventV1(stored({ collector_id: "c-2" }), ok(base), ctx).status, "rejected");
});
