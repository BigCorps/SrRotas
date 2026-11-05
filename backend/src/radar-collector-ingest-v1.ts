import { adminSupabase } from "./supabase";
import { EVENT_TYPES, normalizeRadarEvent0261 } from "./radar-ingest-0261";
import {
  CollectorRecordV1,
  CollectorStoreUnavailableError,
  collectorSourceAllowed,
  effectiveMaxBatch,
  isMissingSchemaError,
  normalizeCollectorSource,
} from "./radar-collectors-v1";

/**
 * External collector event ingest (vc110).
 * Pure validation + decision functions are exported for tests; the store function at the end
 * performs the writes. Every new/materially changed collector event is stored `pending` and is
 * invisible to the app until an admin approves it.
 */

export const COLLECTOR_CONTRACT_V1 = "srrotas-radar-collector-api-v1";
export const COLLECTOR_STATUSES = new Set(["active", "cancelled"]);
export const MATERIAL_FIELDS = [
  "name", "starts_at", "expected_end_at", "egress_start_at", "egress_end_at",
  "lat", "lng", "venue_name", "address",
] as const;
const CONTENT_FIELDS = ["event_type", "source_url", "confidence", "city", "state", "country_code", "metadata"] as const;
const MAX_METADATA_BYTES = 4096;
const RUN_ID_RE = /^[A-Za-z0-9._:-]{8,100}$/;
const OFFSET_ISO_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})$/;

export type CollectorItemStatusV1 = "accepted" | "unchanged" | "rejected";
export type CollectorItemResultV1 = {
  index: number;
  external_id: string | null;
  source: string | null;
  status: CollectorItemStatusV1;
  code: string;
  event_id?: string;
};

export class CollectorRequestError extends Error {
  constructor(public readonly httpStatus: number, code: string, public readonly extra: Record<string, unknown> = {}) {
    super(code);
  }
}

export type NormalizedCollectorEventV1 = ReturnType<typeof normalizeRadarEvent0261>;

/** ISO-8601 with an explicit offset (`Z` or `±hh:mm`). Strings without offset are rejected. */
export function parseOffsetDateTimeV1(value: unknown): { ok: true; iso: string } | { ok: false; reason: "required" | "offset_required" | "invalid" } {
  if (value === undefined || value === null || value === "") return { ok: false, reason: "required" };
  if (typeof value !== "string") return { ok: false, reason: "invalid" };
  const raw = value.trim();
  const date = new Date(raw);
  if (!OFFSET_ISO_RE.test(raw)) {
    return Number.isFinite(date.getTime()) || /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(raw)
      ? { ok: false, reason: "offset_required" }
      : { ok: false, reason: "invalid" };
  }
  if (!Number.isFinite(date.getTime())) return { ok: false, reason: "invalid" };
  // Reject calendar overflow (e.g. 2026-02-30) that some engines silently roll over.
  const [y, m, d] = raw.slice(0, 10).split("-").map(Number);
  const probe = new Date(Date.UTC(y, m - 1, d));
  if (probe.getUTCFullYear() !== y || probe.getUTCMonth() !== m - 1 || probe.getUTCDate() !== d) {
    return { ok: false, reason: "invalid" };
  }
  return { ok: true, iso: date.toISOString() };
}

export function validateCollectorUrlV1(value: unknown): string | null | "invalid" {
  if (value === undefined || value === null || value === "") return null;
  if (typeof value !== "string" || value.length > 1200) return "invalid";
  try {
    const url = new URL(value.trim());
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || !url.hostname) return "invalid";
    return url.toString();
  } catch {
    return "invalid";
  }
}

function finiteCoord(value: unknown, min: number, max: number): number | null {
  if (value === null || value === undefined || value === "" || typeof value === "boolean") return null;
  const n = typeof value === "number" ? value : Number(String(value).trim());
  return Number.isFinite(n) && n >= min && n <= max ? n : null;
}

export type CollectorValidationV1 =
  | { ok: true; row: NormalizedCollectorEventV1 }
  | { ok: false; code: string; source: string | null; external_id: string | null };

export function validateCollectorEventV1(
  input: unknown,
  collector: Pick<CollectorRecordV1, "allowed_sources">,
  verifiedAt = new Date().toISOString(),
): CollectorValidationV1 {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return { ok: false, code: "event_invalid", source: null, external_id: null };
  }
  const raw = input as Record<string, unknown>;
  const rawExternal = typeof raw.external_id === "string" || typeof raw.external_id === "number"
    ? String(raw.external_id).trim() : "";
  const externalId = rawExternal || null;
  const source = normalizeCollectorSource(raw.source);
  const fail = (code: string): CollectorValidationV1 => ({ ok: false, code, source, external_id: externalId });

  if (!source) return fail("source_invalid");
  if (!collectorSourceAllowed(collector, source)) return fail("source_not_allowed");
  if (!externalId) return fail("external_id_required");
  if (externalId.length > 160 || /[\u0000-\u001f]/.test(externalId)) return fail("external_id_invalid");

  const name = typeof raw.name === "string" ? raw.name.trim() : "";
  if (!name) return fail("name_required");
  if (name.length > 240) return fail("name_too_long");

  if (finiteCoord(raw.lat, -90, 90) === null) return fail("lat_invalid");
  if (finiteCoord(raw.lng, -180, 180) === null) return fail("lng_invalid");

  const times: Record<string, string | undefined> = {};
  for (const field of ["starts_at", "expected_end_at", "egress_start_at", "egress_end_at"] as const) {
    const parsed = parseOffsetDateTimeV1(raw[field]);
    if (parsed.ok) { times[field] = parsed.iso; continue; }
    if (parsed.reason === "required" && field !== "starts_at") continue;
    return fail(`${field}_${parsed.reason}`);
  }

  let eventType = "event";
  if (raw.event_type !== undefined && raw.event_type !== null && raw.event_type !== "") {
    const t = typeof raw.event_type === "string" ? raw.event_type.trim().toLowerCase() : "";
    if (!EVENT_TYPES.has(t)) return fail("event_type_unknown");
    eventType = t;
  }
  let status = "active";
  if (raw.status !== undefined && raw.status !== null && raw.status !== "") {
    const s = typeof raw.status === "string" ? raw.status.trim().toLowerCase() : "";
    if (s === "expired") return fail("status_not_allowed");
    if (!COLLECTOR_STATUSES.has(s)) return fail("status_unknown");
    status = s;
  }
  const sourceUrl = validateCollectorUrlV1(raw.source_url);
  if (sourceUrl === "invalid") return fail("source_url_invalid");

  if (raw.confidence !== undefined && raw.confidence !== null && raw.confidence !== "") {
    const c = typeof raw.confidence === "number" ? raw.confidence : NaN;
    if (!Number.isFinite(c) || c < 0 || c > 1) return fail("confidence_invalid");
  }
  if (raw.country_code !== undefined && raw.country_code !== null && raw.country_code !== "") {
    if (typeof raw.country_code !== "string" || !/^[A-Za-z]{2}$/.test(raw.country_code.trim())) return fail("country_code_invalid");
  }
  if (raw.metadata !== undefined && raw.metadata !== null) {
    if (typeof raw.metadata !== "object" || Array.isArray(raw.metadata)) return fail("metadata_invalid");
    let size = 0;
    try { size = Buffer.byteLength(JSON.stringify(raw.metadata), "utf8"); } catch { return fail("metadata_invalid"); }
    if (size > MAX_METADATA_BYTES) return fail("metadata_too_large");
  }

  try {
    const row = normalizeRadarEvent0261({
      ...raw,
      source,
      external_id: externalId,
      name,
      event_type: eventType,
      status,
      source_url: sourceUrl,
      ...times,
    }, verifiedAt);
    return { ok: true, row };
  } catch (error) {
    const message = error instanceof Error ? error.message : "event_invalid";
    return fail(/^[a-z_]+$/.test(message) ? message : "event_invalid");
  }
}

export type ValidatedCollectorBatchV1 = {
  runId: string | null;
  dryRun: boolean;
  received: number;
  results: CollectorItemResultV1[];
  rows: Array<{ index: number; row: NormalizedCollectorEventV1 }>;
};

/** Payload-level checks throw CollectorRequestError; item-level problems become per-item results. */
export function validateCollectorBatchV1(
  body: unknown,
  collector: Pick<CollectorRecordV1, "allowed_sources" | "max_batch">,
  verifiedAt = new Date().toISOString(),
): ValidatedCollectorBatchV1 {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    throw new CollectorRequestError(400, "payload_must_be_object");
  }
  const payload = body as Record<string, unknown>;
  if (payload.snapshot_complete !== undefined && payload.snapshot_complete !== false) {
    throw new CollectorRequestError(400, "snapshot_not_allowed_for_collectors");
  }
  if (payload.dry_run !== undefined && typeof payload.dry_run !== "boolean") {
    throw new CollectorRequestError(400, "dry_run_invalid");
  }
  let runId: string | null = null;
  if (payload.run_id !== undefined && payload.run_id !== null) {
    if (typeof payload.run_id !== "string" || !RUN_ID_RE.test(payload.run_id)) {
      throw new CollectorRequestError(400, "run_id_invalid");
    }
    runId = payload.run_id;
  }
  if (!Array.isArray(payload.events) || payload.events.length === 0) {
    throw new CollectorRequestError(400, "events_required");
  }
  const maxBatch = effectiveMaxBatch(collector);
  if (payload.events.length > maxBatch) {
    throw new CollectorRequestError(413, "batch_too_large", { max_batch: maxBatch, received: payload.events.length });
  }

  const validated = payload.events.map((event, index) => ({ index, v: validateCollectorEventV1(event, collector, verifiedAt) }));
  const keyCount = new Map<string, number>();
  for (const { v } of validated) {
    if (v.ok) {
      const key = `${v.row.source}|${v.row.external_id}`;
      keyCount.set(key, (keyCount.get(key) ?? 0) + 1);
    }
  }
  const results: CollectorItemResultV1[] = [];
  const rows: Array<{ index: number; row: NormalizedCollectorEventV1 }> = [];
  for (const { index, v } of validated) {
    if (!v.ok) {
      results.push({ index, external_id: v.external_id, source: v.source, status: "rejected", code: v.code });
      continue;
    }
    if ((keyCount.get(`${v.row.source}|${v.row.external_id}`) ?? 0) > 1) {
      results.push({ index, external_id: v.row.external_id, source: v.row.source, status: "rejected", code: "duplicate_in_batch" });
      continue;
    }
    rows.push({ index, row: v.row });
  }
  return { runId, dryRun: payload.dry_run === true, received: payload.events.length, results, rows };
}

// ------------------------------------------------------------------ decision (sticky + requeue)

export type ExistingCollectorEventV1 = {
  id: string;
  status: string;
  review_status: string | null;
  collector_id: string | null;
  [key: string]: unknown;
};

export type CollectorWriteV1 =
  | { kind: "insert"; row: Record<string, unknown> }
  | { kind: "update"; id: string; patch: Record<string, unknown>; guardNotCancelled: boolean };

export type CollectorDecisionV1 = {
  status: CollectorItemStatusV1;
  code: string;
  write: CollectorWriteV1 | null;
};

function sameValue(field: string, a: unknown, b: unknown): boolean {
  const empty = (v: unknown) => v === null || v === undefined || (typeof v === "string" && v.trim() === "");
  if (empty(a) && empty(b)) return true;
  if (empty(a) || empty(b)) return false;
  if (field === "lat" || field === "lng") return Math.abs(Number(a) - Number(b)) < 1e-6;
  if (field === "confidence") return Math.abs(Number(a) - Number(b)) < 1e-4;
  if (field.endsWith("_at")) return new Date(String(a)).getTime() === new Date(String(b)).getTime();
  if (field === "metadata") return stableJson(a) === stableJson(b);
  return String(a).trim() === String(b).trim();
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value as object).sort().map((k) => `${JSON.stringify(k)}:${stableJson((value as any)[k])}`).join(",")}}`;
  }
  return JSON.stringify(value ?? null);
}

export function materialChangesV1(existing: Record<string, unknown>, incoming: Record<string, unknown>): string[] {
  return MATERIAL_FIELDS.filter((f) => !sameValue(f, existing[f], incoming[f]));
}

export function contentChangesV1(existing: Record<string, unknown>, incoming: Record<string, unknown>): string[] {
  return [...MATERIAL_FIELDS, ...CONTENT_FIELDS].filter((f) => !sameValue(f, existing[f], incoming[f]));
}

function contentPatch(incoming: NormalizedCollectorEventV1) {
  const { source: _s, external_id: _e, ...rest } = incoming;
  return rest;
}

/**
 * Decides what a collector item does to the stored row.
 *  - new key → insert as `pending`;
 *  - a row owned by another ingest path (legacy/admin/other collector) is never touched;
 *  - incoming `cancelled` is applied immediately (hiding is safe), review state untouched;
 *  - stored `cancelled` is sticky: a non-cancelled resend never reactivates it (`cancelled_kept`);
 *  - material change on an approved/expired row → back to `pending` (`requeued_for_review`);
 *  - no material change on an approved row → `unchanged` (only last_verified_at is refreshed);
 *  - pending/rejected/changes_requested rows take the new content and (re)enter `pending`.
 */
export function decideCollectorEventV1(
  existing: ExistingCollectorEventV1 | null,
  incoming: NormalizedCollectorEventV1,
  ctx: { collectorId: string; runId: string | null; now: string },
): CollectorDecisionV1 {
  if (!existing) {
    return {
      status: "accepted",
      code: incoming.status === "cancelled" ? "created_cancelled" : "created_pending",
      write: {
        kind: "insert",
        row: {
          ...incoming,
          review_status: "pending",
          collector_id: ctx.collectorId,
          ingest_run_id: ctx.runId,
          first_seen_at: ctx.now,
        },
      },
    };
  }
  if (existing.collector_id !== ctx.collectorId) {
    return { status: "rejected", code: "owned_by_other_ingest", write: null };
  }
  const touch = { last_verified_at: ctx.now, ingest_run_id: ctx.runId };

  if (incoming.status === "cancelled") {
    if (existing.status === "cancelled") return { status: "unchanged", code: "already_cancelled", write: null };
    return {
      status: "accepted",
      code: "cancelled_applied",
      write: { kind: "update", id: existing.id, patch: { status: "cancelled", updated_at: ctx.now, ...touch }, guardNotCancelled: false },
    };
  }
  if (existing.status === "cancelled") return { status: "unchanged", code: "cancelled_kept", write: null };

  const material = materialChangesV1(existing, incoming);
  const content = contentChangesV1(existing, incoming);
  const review = String(existing.review_status ?? "approved");
  const requeue = (code: string): CollectorDecisionV1 => ({
    status: "accepted",
    code,
    write: {
      kind: "update",
      id: existing.id,
      patch: { ...contentPatch(incoming), review_status: "pending", collector_id: ctx.collectorId, ingest_run_id: ctx.runId },
      guardNotCancelled: true,
    },
  });
  const touchOnly = (code: string): CollectorDecisionV1 => ({
    status: "unchanged",
    code,
    write: { kind: "update", id: existing.id, patch: touch, guardNotCancelled: true },
  });

  if (existing.status === "expired") {
    if (!material.length) return touchOnly("expired_kept");
    return requeue(review === "approved" ? "requeued_for_review" : "resubmitted_for_review");
  }
  if (review === "approved") {
    return material.length ? requeue("requeued_for_review") : touchOnly("no_material_change");
  }
  if (!content.length) return touchOnly("no_change");
  return requeue(review === "pending" ? "updated_pending" : "resubmitted_for_review");
}

// ------------------------------------------------------------------------------ store

const EXISTING_COLUMNS =
  "id,source,external_id,status,review_status,collector_id,name,venue_name,address,city,state,country_code,lat,lng,starts_at,expected_end_at,egress_start_at,egress_end_at,event_type,source_url,confidence,metadata";

function storeError(error: any): never {
  if (isMissingSchemaError(error)) throw new CollectorStoreUnavailableError();
  throw new Error(String(error?.message || "collector_ingest_failed"));
}

async function existingRows(rows: NormalizedCollectorEventV1[]) {
  const bySource = new Map<string, string[]>();
  for (const r of rows) bySource.set(r.source, [...(bySource.get(r.source) ?? []), r.external_id]);
  const found = new Map<string, ExistingCollectorEventV1>();
  const queries: Array<PromiseLike<any>> = [];
  for (const [source, ids] of bySource) {
    for (let i = 0; i < ids.length; i += 100) {
      queries.push(adminSupabase().from("sr_event_opportunities").select(EXISTING_COLUMNS)
        .eq("source", source).in("external_id", ids.slice(i, i + 100)));
    }
  }
  for (const q of await Promise.all(queries)) {
    if (q.error) storeError(q.error);
    for (const row of q.data ?? []) found.set(`${row.source}|${row.external_id}`, row);
  }
  return found;
}

async function previousRun(collectorId: string, runId: string) {
  const q = await adminSupabase().from("sr_radar_source_runs").select("summary,created_at")
    .eq("collector_id", collectorId).eq("run_id", runId).limit(1).maybeSingle();
  if (q.error) storeError(q.error);
  return q.data ?? null;
}

function summarize(results: CollectorItemResultV1[]) {
  const sorted = [...results].sort((a, b) => a.index - b.index);
  return {
    accepted: sorted.filter((r) => r.status === "accepted").length,
    unchanged: sorted.filter((r) => r.status === "unchanged").length,
    rejected: sorted.filter((r) => r.status === "rejected").length,
    pending_review: sorted.filter((r) => r.status === "accepted" && r.code !== "cancelled_applied" && r.code !== "created_cancelled").length,
    items: sorted,
  };
}

async function runUpdate(write: Extract<CollectorWriteV1, { kind: "update" }>) {
  let q = adminSupabase().from("sr_event_opportunities").update(write.patch).eq("id", write.id);
  if (write.guardNotCancelled) q = q.neq("status", "cancelled");
  return q.select("id").maybeSingle();
}

export async function ingestCollectorEventsV1(collector: CollectorRecordV1, body: unknown) {
  const now = new Date().toISOString();
  const batch = validateCollectorBatchV1(body, collector, now);

  if (batch.runId && !batch.dryRun) {
    const previous = await previousRun(collector.id, batch.runId);
    if (previous?.summary) {
      return { ...(previous.summary as Record<string, unknown>), idempotent_replay: true, replayed_from: previous.created_at };
    }
  }

  const results = [...batch.results];
  const existing = batch.rows.length ? await existingRows(batch.rows.map((r) => r.row)) : new Map();
  const planned = batch.rows.map(({ index, row }) => ({
    index,
    row,
    decision: decideCollectorEventV1(existing.get(`${row.source}|${row.external_id}`) ?? null, row, {
      collectorId: collector.id, runId: batch.runId, now,
    }),
  }));

  if (batch.dryRun) {
    for (const p of planned) {
      results.push({ index: p.index, external_id: p.row.external_id, source: p.row.source, status: p.decision.status, code: p.decision.code });
    }
    return {
      ok: true, contract: COLLECTOR_CONTRACT_V1, dry_run: true, run_id: batch.runId,
      received: batch.received, expired: 0, ...summarize(results),
    };
  }

  // Inserts (one call; per-row fallback isolates a conflicting/invalid item).
  const inserts = planned.filter((p) => p.decision.write?.kind === "insert");
  const insertIds = new Map<number, string>();
  const failed = new Map<number, string>();
  if (inserts.length) {
    const rows = inserts.map((p) => (p.decision.write as Extract<CollectorWriteV1, { kind: "insert" }>).row);
    const q = await adminSupabase().from("sr_event_opportunities").insert(rows).select("id,source,external_id");
    if (q.error) {
      if (isMissingSchemaError(q.error)) storeError(q.error);
      for (const p of inserts) {
        const one = await adminSupabase().from("sr_event_opportunities")
          .insert((p.decision.write as Extract<CollectorWriteV1, { kind: "insert" }>).row).select("id").single();
        if (one.error) failed.set(p.index, one.error.code === "23505" ? "write_conflict" : "write_failed");
        else insertIds.set(p.index, String(one.data.id));
      }
    } else {
      const byKey = new Map<string, string>(
        (q.data ?? []).map((r: any) => [`${r.source}|${r.external_id}`, String(r.id)] as [string, string]),
      );
      for (const p of inserts) {
        const id = byKey.get(`${p.row.source}|${p.row.external_id}`);
        if (id) insertIds.set(p.index, id);
      }
    }
  }

  // Updates (bounded parallelism).
  const updates = planned.filter((p) => p.decision.write?.kind === "update");
  for (let i = 0; i < updates.length; i += 10) {
    const chunk = updates.slice(i, i + 10);
    const out = await Promise.all(chunk.map((p) => runUpdate(p.decision.write as Extract<CollectorWriteV1, { kind: "update" }>)));
    out.forEach((q, j) => {
      const p = chunk[j];
      if (q.error) {
        if (isMissingSchemaError(q.error)) storeError(q.error);
        failed.set(p.index, "write_failed");
      } else if (!q.data) {
        // Guard hit: the row became cancelled concurrently; cancellation stays sticky.
        failed.set(p.index, "cancelled_kept");
      }
    });
  }

  for (const p of planned) {
    const failure = failed.get(p.index);
    const id = insertIds.get(p.index) ?? (p.decision.write?.kind === "update" ? p.decision.write.id : undefined);
    if (failure === "cancelled_kept") {
      results.push({ index: p.index, external_id: p.row.external_id, source: p.row.source, status: "unchanged", code: failure, event_id: id });
    } else if (failure) {
      results.push({ index: p.index, external_id: p.row.external_id, source: p.row.source, status: "rejected", code: failure });
    } else {
      results.push({ index: p.index, external_id: p.row.external_id, source: p.row.source, status: p.decision.status, code: p.decision.code, ...(id ? { event_id: id } : {}) });
    }
  }

  // Same temporal hygiene as the legacy ingest (kept; never reactivates anything).
  const temporal = await adminSupabase().from("sr_event_opportunities")
    .update({ status: "expired", updated_at: now })
    .eq("status", "active")
    .lt("egress_end_at", new Date(Date.now() - 2 * 60 * 60_000).toISOString())
    .select("id");
  const expired = temporal.error ? 0 : temporal.data?.length ?? 0;

  const summary = {
    ok: true, contract: COLLECTOR_CONTRACT_V1, dry_run: false, run_id: batch.runId,
    received: batch.received, expired, ...summarize(results),
  };
  const sources = [...new Set(batch.rows.map((r) => r.row.source))];
  const run = await adminSupabase().from("sr_radar_source_runs").insert({
    source: sources.length === 1 ? sources[0] : "collector_multi",
    status: failed.size && failed.size === planned.length ? "error" : "ok",
    centers_checked: 0,
    fetched_count: batch.received,
    saved_count: summary.accepted,
    rejected_count: summary.rejected,
    expired_count: expired,
    error_message: failed.size ? `write_failures:${failed.size}` : null,
    collector_id: collector.id,
    run_id: batch.runId,
    summary,
  });
  if (run.error && isMissingSchemaError(run.error)) storeError(run.error);
  return summary;
}
