import { adminSupabase } from "./supabase";
import { allowSecurityAction } from "./security-rate-limit";
import {
  CollectorRecordV1,
  CollectorStoreUnavailableError,
  collectorBearer,
  collectorStoreUnavailableResponse,
  generateCollectorKey,
  hashCollectorKey,
  isMissingSchemaError,
  normalizeCollectorConfigV1,
  parseCollectorKey,
  publicCollectorV1,
  requestIpHash,
  verifyCollectorKey,
} from "./radar-collectors-v1";

/** Supabase-backed collector store. All callers are server-only (service_role). */

const COLLECTOR_COLUMNS =
  "id,name,status,allowed_sources,scopes,key_prefix,key_hash,max_batch,rate_limit_per_hour,created_at,created_by,rotated_at,revoked_at,revoked_by,last_used_at";

function noStore(status: number, body: Record<string, unknown>, extra: Record<string, string> = {}) {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store", ...extra } });
}

function throwIfError(error: any) {
  if (!error) return;
  if (isMissingSchemaError(error)) throw new CollectorStoreUnavailableError();
  throw new Error(String(error.message || "collector_store_failed"));
}

export type CollectorAuthResultV1 =
  | { collector: CollectorRecordV1; response: null }
  | { collector: null; response: Response };

async function failedAuth(request: Request, error: string, status = 401): Promise<CollectorAuthResultV1> {
  // Brute-force guard on invalid keys only (never counts successful calls).
  const allowed = await allowSecurityAction(request, "radar-collector-auth-fail", "collector", 20, 900).catch(() => true);
  if (!allowed) return { collector: null, response: noStore(429, { error: "collector_auth_rate_limited" }, { "Retry-After": "900" }) };
  return { collector: null, response: noStore(status, { error }) };
}

/**
 * Authenticates `Authorization: Bearer srrk_...` and enforces the per-collector hourly limit.
 * Returns 503 collector_store_unavailable while the migration is not applied.
 */
export async function authenticateCollectorRequestV1(request: Request, endpoint: string): Promise<CollectorAuthResultV1> {
  const parsed = parseCollectorKey(collectorBearer(request.headers.get("authorization")));
  if (!parsed) return failedAuth(request, "collector_unauthorized");

  const found = await adminSupabase().from("sr_radar_collectors")
    .select(COLLECTOR_COLUMNS).eq("key_hash", hashCollectorKey(parsed.key)).limit(1).maybeSingle();
  if (found.error) {
    if (isMissingSchemaError(found.error)) return { collector: null, response: collectorStoreUnavailableResponse() };
    return { collector: null, response: noStore(500, { error: "collector_auth_failed" }) };
  }
  const row = found.data as CollectorRecordV1 | null;
  if (!row || !row.key_hash || !verifyCollectorKey(parsed.key, row.key_hash)) {
    return failedAuth(request, "collector_unauthorized");
  }
  if (row.status !== "active") return failedAuth(request, "collector_revoked", 403);

  const since = new Date(Date.now() - 60 * 60_000).toISOString();
  const used = await adminSupabase().from("sr_radar_collector_usage")
    .select("id", { count: "exact", head: true })
    .eq("collector_id", row.id).gte("at", since).neq("http_status", 429);
  if (used.error) {
    if (isMissingSchemaError(used.error)) return { collector: null, response: collectorStoreUnavailableResponse() };
    return { collector: null, response: noStore(500, { error: "collector_rate_check_failed" }) };
  }
  const limit = Math.max(1, Number(row.rate_limit_per_hour) || 30);
  if ((used.count ?? 0) >= limit) {
    await recordCollectorUsageV1(row, request, { endpoint, httpStatus: 429 });
    return {
      collector: null,
      response: noStore(429, {
        error: "collector_rate_limited",
        limit_per_hour: limit,
        retry_after_seconds: 900,
      }, { "Retry-After": "900" }),
    };
  }
  return { collector: row, response: null };
}

export async function recordCollectorUsageV1(
  collector: Pick<CollectorRecordV1, "id">,
  request: Request,
  input: { endpoint: string; httpStatus: number; received?: number; accepted?: number; rejected?: number; runId?: string | null },
) {
  const now = new Date().toISOString();
  await Promise.all([
    adminSupabase().from("sr_radar_collector_usage").insert({
      collector_id: collector.id,
      at: now,
      endpoint: input.endpoint.slice(0, 80),
      http_status: input.httpStatus,
      received: Math.max(0, input.received ?? 0),
      accepted: Math.max(0, input.accepted ?? 0),
      rejected: Math.max(0, input.rejected ?? 0),
      run_id: input.runId ?? null,
      ip_hash: requestIpHash(request),
    }),
    adminSupabase().from("sr_radar_collectors").update({ last_used_at: now }).eq("id", collector.id),
  ]).catch(() => undefined);
}

export function collectorPingPayloadV1(collector: CollectorRecordV1) {
  const pub = publicCollectorV1(collector);
  return {
    ok: true,
    collector: { name: pub.name, key_prefix: pub.key_prefix, status: pub.status },
    scopes: pub.scopes,
    allowed_sources: pub.allowed_sources,
    limits: {
      max_batch: Math.min(pub.max_batch, 500),
      rate_limit_per_hour: pub.rate_limit_per_hour,
      max_pois_per_batch: 50,
    },
    server_time: new Date().toISOString(),
    contract: "srrotas-radar-collector-api-v1",
  };
}

// ---------------------------------------------------------------- admin (session) operations

export async function listCollectorsV1() {
  const q = await adminSupabase().from("sr_radar_collectors").select(COLLECTOR_COLUMNS)
    .order("created_at", { ascending: false }).limit(200);
  throwIfError(q.error);
  return (q.data ?? []).map(publicCollectorV1);
}

export async function createCollectorV1(input: Record<string, unknown>, actorEmail: string) {
  const config = normalizeCollectorConfigV1(input, false);
  const generated = generateCollectorKey();
  const q = await adminSupabase().from("sr_radar_collectors").insert({
    ...config,
    status: "active",
    key_prefix: generated.displayPrefix,
    key_hash: generated.hash,
    created_by: actorEmail,
  }).select(COLLECTOR_COLUMNS).single();
  throwIfError(q.error);
  return { collector: publicCollectorV1(q.data), key: generated.key };
}

export async function rotateCollectorKeyV1(id: string, actorEmail: string) {
  const generated = generateCollectorKey();
  const now = new Date().toISOString();
  const q = await adminSupabase().from("sr_radar_collectors").update({
    key_prefix: generated.displayPrefix,
    key_hash: generated.hash,
    rotated_at: now,
    rotated_by: actorEmail,
    updated_at: now,
  }).eq("id", id).eq("status", "active").select(COLLECTOR_COLUMNS).maybeSingle();
  throwIfError(q.error);
  if (!q.data) throw new Error("collector_not_found_or_revoked");
  return { collector: publicCollectorV1(q.data), key: generated.key };
}

export async function revokeCollectorV1(id: string, actorEmail: string) {
  const now = new Date().toISOString();
  const q = await adminSupabase().from("sr_radar_collectors").update({
    status: "revoked",
    revoked_at: now,
    revoked_by: actorEmail,
    updated_at: now,
  }).eq("id", id).eq("status", "active").select(COLLECTOR_COLUMNS).maybeSingle();
  throwIfError(q.error);
  if (!q.data) throw new Error("collector_not_found_or_revoked");
  return publicCollectorV1(q.data);
}

export async function updateCollectorV1(id: string, input: Record<string, unknown>) {
  const patch = normalizeCollectorConfigV1(input, true);
  if (!Object.keys(patch).length) throw new Error("collector_patch_empty");
  const q = await adminSupabase().from("sr_radar_collectors")
    .update({ ...patch, updated_at: new Date().toISOString() })
    .eq("id", id).select(COLLECTOR_COLUMNS).maybeSingle();
  throwIfError(q.error);
  if (!q.data) throw new Error("collector_not_found");
  return publicCollectorV1(q.data);
}

export async function listCollectorActivityV1(collectorId: string | null, limit = 100) {
  const n = Math.max(10, Math.min(Number(limit) || 100, 300));
  let runs = adminSupabase().from("sr_radar_source_runs")
    .select("id,source,status,fetched_count,saved_count,rejected_count,expired_count,error_message,collector_id,run_id,created_at")
    .not("collector_id", "is", null).order("created_at", { ascending: false }).limit(n);
  let usage = adminSupabase().from("sr_radar_collector_usage")
    .select("id,collector_id,at,endpoint,http_status,received,accepted,rejected,run_id")
    .order("at", { ascending: false }).limit(n);
  if (collectorId) {
    runs = runs.eq("collector_id", collectorId);
    usage = usage.eq("collector_id", collectorId);
  }
  const [r, u] = await Promise.all([runs, usage]);
  throwIfError(r.error);
  throwIfError(u.error);
  return { runs: r.data ?? [], usage: u.data ?? [] };
}
