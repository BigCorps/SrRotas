import { radarRpcV2 } from "./radar-hardening-v2";
import { adminSupabase } from "./supabase";
import {
  CollectorStoreUnavailableError,
  isMissingSchemaError,
} from "./radar-collectors-v1";

/** Human review over the vc110 tables. V2 keeps every reader approved-only;
 * unavailable review schema fails closed instead of removing the approval filter. */

export const REVIEW_STATUSES = [
  "pending",
  "approved",
  "rejected",
  "changes_requested",
  "unpublished",
] as const;
export type ReviewStatusV1 = (typeof REVIEW_STATUSES)[number];
export const REVIEW_DECISIONS = [
  "approve",
  "reject",
  "request_changes",
  "unpublish",
] as const;
export type ReviewDecisionV1 = (typeof REVIEW_DECISIONS)[number];

export function decisionToReviewStatusV1(
  decision: ReviewDecisionV1,
): ReviewStatusV1 {
  return decision === "approve"
    ? "approved"
    : decision === "reject"
      ? "rejected"
      : decision === "unpublish"
        ? "unpublished"
        : "changes_requested";
}

// ----------------------------------------------------------------- consumer filter + fallback

type Filterable<Q> = { eq: (column: string, value: string) => Q };

export function applyApprovedReviewFilterV1<Q extends Filterable<Q>>(
  query: Q,
  approvedOnly: boolean,
): Q {
  return approvedOnly ? query.eq("review_status", "approved") : query;
}

/** Historical export name retained for consumer compatibility; no unfiltered fallback. */
export async function withApprovedReviewFallbackV1<T = any>(
  run: (approvedOnly: boolean) => PromiseLike<T>,
): Promise<T & { review_filter: "approved" }> {
  const first = await run(true);
  const firstError = (first as { error?: unknown } | null)?.error;
  if (firstError && isMissingSchemaError(firstError))
    throw new CollectorStoreUnavailableError();
  return { ...(first as any), review_filter: "approved" } as T & {
    review_filter: "approved";
  };
}

// ------------------------------------------------------------------------------ pure flags

export type ReviewFlagRowV1 = {
  id: string;
  name: string | null;
  venue_name?: string | null;
  lat: number | null;
  lng: number | null;
  starts_at: string | null;
  expected_end_at?: string | null;
  source_url?: string | null;
  confidence?: number | null;
  review_status?: string | null;
  status?: string | null;
};

export type ReviewFlagV1 = {
  code: string;
  label: string;
  related_ids?: string[];
};

export const BRAZIL_BBOX = {
  minLat: -34.0,
  maxLat: 5.5,
  minLng: -74.5,
  maxLng: -28.5,
} as const;
export const LOW_CONFIDENCE = 0.5;
const DUP_WINDOW_MS = 3 * 60 * 60_000;
const SAME_START_MS = 15 * 60_000;
const DUP_DISTANCE_M = 300;

export function normalizeReviewTextV1(value: unknown): string {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/&/g, " e ")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

export function distanceMetersV1(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
): number {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad,
    dLng = (lng2 - lng1) * rad;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function ms(value: unknown): number | null {
  if (!value) return null;
  const t = new Date(String(value)).getTime();
  return Number.isFinite(t) ? t : null;
}

export function isLikelyDuplicateV1(
  a: ReviewFlagRowV1,
  b: ReviewFlagRowV1,
): boolean {
  if (a.id === b.id) return false;
  const ta = ms(a.starts_at),
    tb = ms(b.starts_at);
  if (ta === null || tb === null) return false;
  const dt = Math.abs(ta - tb);
  const na = normalizeReviewTextV1(a.name),
    nb = normalizeReviewTextV1(b.name);
  if (
    na &&
    na === nb &&
    dt <= DUP_WINDOW_MS &&
    Number.isFinite(Number(a.lat)) &&
    Number.isFinite(Number(b.lat)) &&
    distanceMetersV1(
      Number(a.lat),
      Number(a.lng),
      Number(b.lat),
      Number(b.lng),
    ) <= DUP_DISTANCE_M
  ) {
    return true;
  }
  const va = normalizeReviewTextV1(a.venue_name),
    vb = normalizeReviewTextV1(b.venue_name);
  return Boolean(va) && va === vb && dt <= SAME_START_MS;
}

export function inconsistencyFlagsV1(
  row: ReviewFlagRowV1,
  nowMs = Date.now(),
): ReviewFlagV1[] {
  const flags: ReviewFlagV1[] = [];
  const start = ms(row.starts_at),
    end = ms(row.expected_end_at);
  if (start !== null && end !== null && end < start)
    flags.push({ code: "end_before_start", label: "Término antes do início" });
  if (
    start !== null &&
    start < nowMs &&
    (row.review_status ?? "pending") === "pending"
  ) {
    flags.push({ code: "start_in_past", label: "Início já passou (pendente)" });
  }
  const lat = Number(row.lat),
    lng = Number(row.lng);
  if (
    !Number.isFinite(lat) ||
    !Number.isFinite(lng) ||
    lat < BRAZIL_BBOX.minLat ||
    lat > BRAZIL_BBOX.maxLat ||
    lng < BRAZIL_BBOX.minLng ||
    lng > BRAZIL_BBOX.maxLng
  ) {
    flags.push({ code: "outside_brazil", label: "Coordenadas fora do Brasil" });
  }
  if (!row.source_url)
    flags.push({ code: "missing_source_url", label: "Sem link da fonte" });
  const c = Number(row.confidence);
  if (
    row.confidence === null ||
    row.confidence === undefined ||
    !Number.isFinite(c) ||
    c < LOW_CONFIDENCE
  ) {
    flags.push({ code: "low_confidence", label: "Confiança baixa" });
  }
  return flags;
}

/** Flags for every listed row, comparing against listed rows + extra context rows. */
export function reviewFlagsV1(
  rows: ReviewFlagRowV1[],
  context: ReviewFlagRowV1[] = [],
  nowMs = Date.now(),
) {
  const pool = new Map<string, ReviewFlagRowV1>();
  for (const r of [...rows, ...context]) pool.set(r.id, r);
  const all = [...pool.values()];
  const out = new Map<string, ReviewFlagV1[]>();
  for (const row of rows) {
    const flags = inconsistencyFlagsV1(row, nowMs);
    const related = all
      .filter((o) => isLikelyDuplicateV1(row, o))
      .map((o) => o.id);
    if (related.length)
      flags.unshift({
        code: "likely_duplicate",
        label: "Possível duplicado",
        related_ids: related.slice(0, 10),
      });
    out.set(row.id, flags);
  }
  return out;
}

export function googleMapsSearchUrlV1(
  lat: unknown,
  lng: unknown,
): string | null {
  const a = Number(lat),
    b = Number(lng);
  if (
    !Number.isFinite(a) ||
    !Number.isFinite(b) ||
    a < -90 ||
    a > 90 ||
    b < -180 ||
    b > 180
  )
    return null;
  return `https://www.google.com/maps/search/?api=1&query=${a.toFixed(6)},${b.toFixed(6)}`;
}

export class ReviewInputError extends Error {}

export function validateReviewDecisionV1(input: Record<string, unknown>) {
  const id = String(input.id ?? "").trim();
  if (!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(id))
    throw new ReviewInputError("id_invalid");
  const decision = String(input.decision ?? "") as ReviewDecisionV1;
  if (!(REVIEW_DECISIONS as readonly string[]).includes(decision))
    throw new ReviewInputError("decision_invalid");
  const note = String(input.note ?? "")
    .trim()
    .slice(0, 1000);
  if (note.length < 3) throw new ReviewInputError("note_required");
  const version = Number(input.expected_version);
  if (!Number.isSafeInteger(version) || version < 1)
    throw new ReviewInputError("expected_version_required");
  return { id, decision, note: note || null, version };
}

// ------------------------------------------------------------------------------ admin store

function check(error: any) {
  if (!error) return;
  if (isMissingSchemaError(error)) throw new CollectorStoreUnavailableError();
  throw new Error(String(error.message || "radar_review_failed"));
}

const EVENT_ADMIN_COLUMNS =
  "row_version,id,source,external_id,event_type,name,venue_name,address,city,state,lat,lng,starts_at,expected_end_at,egress_start_at,egress_end_at,source_url,confidence,end_time_source,status,review_status,review_note,reviewed_at,reviewed_by,collector_id,ingest_run_id,first_seen_at,last_verified_at,updated_at";

export type ReviewListFiltersV1 = {
  origin?: "collectors" | "manual" | "all";
  collectorId?: string | null;
  source?: string | null;
  reviewStatus?: string | null;
  status?: string | null;
  from?: string | null;
  to?: string | null;
  limit?: number;
};

export async function listReviewEventsV1(filters: ReviewListFiltersV1) {
  const limit = Math.max(10, Math.min(Number(filters.limit) || 200, 500));
  let q = adminSupabase()
    .from("sr_event_opportunities")
    .select(EVENT_ADMIN_COLUMNS)
    .order("first_seen_at", { ascending: false })
    .limit(limit);
  if (filters.origin === "manual") q = q.eq("source", "admin_manual");
  else if (filters.origin !== "all") q = q.not("collector_id", "is", null);
  if (filters.collectorId) q = q.eq("collector_id", filters.collectorId);
  if (filters.source) q = q.eq("source", filters.source);
  if (
    filters.reviewStatus &&
    (REVIEW_STATUSES as readonly string[]).includes(filters.reviewStatus)
  )
    q = q.eq("review_status", filters.reviewStatus);
  if (
    filters.status &&
    ["active", "expired", "cancelled"].includes(filters.status)
  )
    q = q.eq("status", filters.status);
  if (filters.from && ms(filters.from) !== null)
    q = q.gte("starts_at", new Date(filters.from).toISOString());
  if (filters.to && ms(filters.to) !== null)
    q = q.lte("starts_at", new Date(filters.to).toISOString());
  const listed = await q;
  check(listed.error);
  const rows = (listed.data ?? []) as any[];

  // Context for duplicate detection: other non-cancelled events around the listed time range.
  let context: any[] = [];
  const starts = rows
    .map((r) => ms(r.starts_at))
    .filter((t): t is number => t !== null);
  if (starts.length) {
    const ctx = await adminSupabase()
      .from("sr_event_opportunities")
      .select(
        "id,name,venue_name,lat,lng,starts_at,source,review_status,status",
      )
      .neq("status", "cancelled")
      .gte(
        "starts_at",
        new Date(Math.min(...starts) - DUP_WINDOW_MS).toISOString(),
      )
      .lte(
        "starts_at",
        new Date(Math.max(...starts) + DUP_WINDOW_MS).toISOString(),
      )
      .limit(1500);
    if (!ctx.error) context = ctx.data ?? [];
  }
  const flags = reviewFlagsV1(rows, context);
  return rows.map((r) => ({
    ...r,
    maps_url: googleMapsSearchUrlV1(r.lat, r.lng),
    flags: flags.get(r.id) ?? [],
  }));
}

async function logDecision(row: {
  target_type: "event" | "poi";
  target_id: string;
  decision: ReviewDecisionV1;
  note: string | null;
  actor_email: string;
  previous_status: string | null;
}) {
  const q = await adminSupabase()
    .from("sr_radar_review_decisions")
    .insert(row)
    .select("id,created_at")
    .single();
  check(q.error);
  return q.data;
}

export async function reviewEventV1(
  input: Record<string, unknown>,
  actorEmail: string,
) {
  const v = validateReviewDecisionV1(input);
  return radarRpcV2("sr_radar_review_v2", {
    p_kind: "event",
    p_id: v.id,
    p_version: v.version,
    p_decision: v.decision,
    p_note: v.note,
    p_actor: actorEmail,
  });
}

export async function listReviewPoisV1(limit = 200) {
  const q = await adminSupabase()
    .from("sr_radar_pois")
    .select(
      "row_version,id,canonical_name,poi_type,address,city,state,lat,lng,source,source_external_id,source_url,source_confidence,status,metadata,updated_at",
    )
    .order("updated_at", { ascending: false })
    .limit(Math.max(10, Math.min(limit, 300)));
  check(q.error);
  return (q.data ?? []).map((p: any) => ({
    ...p,
    review: String(p?.metadata?.review ?? "pending"),
    maps_url: googleMapsSearchUrlV1(p.lat, p.lng),
  }));
}

export async function reviewPoiV1(
  input: Record<string, unknown>,
  actorEmail: string,
) {
  const v = validateReviewDecisionV1(input);
  return radarRpcV2("sr_radar_review_v2", {
    p_kind: "poi",
    p_id: v.id,
    p_version: v.version,
    p_decision: v.decision,
    p_note: v.note,
    p_actor: actorEmail,
  });
}

export async function reviewHistoryV2(id: string, kind = "event") {
  const q = await adminSupabase()
    .from("sr_radar_revision_history")
    .select("*")
    .eq(
      "entity_type",
      kind === "event" ? "sr_event_opportunities" : "sr_radar_pois",
    )
    .eq("entity_id", id)
    .order("id", { ascending: false })
    .limit(100);
  check(q.error);
  return q.data ?? [];
}

export async function listReviewDecisionsV1(limit = 200) {
  const q = await adminSupabase()
    .from("sr_radar_review_decisions")
    .select(
      "id,target_type,target_id,decision,note,actor_email,previous_status,created_at",
    )
    .order("created_at", { ascending: false })
    .limit(Math.max(10, Math.min(limit, 500)));
  check(q.error);
  const rows = q.data ?? [];
  const eventIds = rows
    .filter((r: any) => r.target_type === "event")
    .map((r: any) => r.target_id);
  const poiIds = rows
    .filter((r: any) => r.target_type === "poi")
    .map((r: any) => r.target_id);
  const [events, pois] = await Promise.all([
    eventIds.length
      ? adminSupabase()
          .from("sr_event_opportunities")
          .select("id,name,source")
          .in("id", [...new Set(eventIds)])
      : { data: [] },
    poiIds.length
      ? adminSupabase()
          .from("sr_radar_pois")
          .select("id,canonical_name,source")
          .in("id", [...new Set(poiIds)])
      : { data: [] },
  ]);
  const names = new Map<string, string>();
  for (const e of (events as any).data ?? [])
    names.set(e.id, `${e.name} · ${e.source}`);
  for (const p of (pois as any).data ?? [])
    names.set(p.id, `${p.canonical_name} · ${p.source}`);
  return rows.map((r: any) => ({
    ...r,
    target_label: names.get(r.target_id) ?? null,
  }));
}
