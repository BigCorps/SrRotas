import {
  RadarHttpError,
  radarBudgetV2,
  radarRpcV2,
} from "./radar-hardening-v2";
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
  "row_version,expires_at,id,name,status,allowed_sources,scopes,key_prefix,key_hash,max_batch,rate_limit_per_hour,created_at,created_by,rotated_at,revoked_at,revoked_by,last_used_at";

function noStore(
  status: number,
  body: Record<string, unknown>,
  extra: Record<string, string> = {},
) {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store", ...extra },
  });
}

function throwIfError(error: any) {
  if (!error) return;
  if (isMissingSchemaError(error)) throw new CollectorStoreUnavailableError();
  throw new Error(String(error.message || "collector_store_failed"));
}

export type CollectorAuthResultV1 =
  | { collector: CollectorRecordV1; response: null }
  | { collector: null; response: Response };

export async function authenticateCollectorRequestV1(
  request: Request,
  endpoint: string,
): Promise<CollectorAuthResultV1> {
  try {
    if (!(await radarBudgetV2("radar-preauth", 600, 60)))
      return {
        collector: null,
        response: noStore(
          429,
          { error: "collector_auth_rate_limited" },
          { "Retry-After": "60" },
        ),
      };
    const parsed = parseCollectorKey(
      collectorBearer(request.headers.get("authorization")),
    );
    const found = parsed
      ? await adminSupabase()
          .from("sr_radar_collectors")
          .select(COLLECTOR_COLUMNS)
          .eq("key_hash", hashCollectorKey(parsed.key))
          .maybeSingle()
      : { data: null, error: null };
    if (found.error)
      throw new RadarHttpError(503, "collector_store_unavailable");
    const row = found.data as CollectorRecordV1 | null;
    const matches = verifyCollectorKey(
      parsed?.key ?? "",
      row?.key_hash ?? "0".repeat(64),
    );
    if (
      !matches ||
      !row ||
      row.status !== "active" ||
      !row.expires_at ||
      !Number.isFinite(Date.parse(row.expires_at)) ||
      Date.parse(row.expires_at) <= Date.now()
    ) {
      const failureStatus = row?.status === "revoked" ? 403 : 401;
      const audit = await adminSupabase()
        .from("sr_radar_collector_usage")
        .insert({
          collector_id: row?.id ?? null,
          endpoint,
          http_status: failureStatus,
          ip_hash: requestIpHash(request),
        });
      if (audit.error)
        throw new RadarHttpError(503, "collector_audit_unavailable");
      return {
        collector: null,
        response: noStore(row?.status === "revoked" ? 403 : 401, {
          error:
            row?.status === "revoked"
              ? "collector_revoked"
              : "collector_unauthorized",
        }),
      };
    }
    if (
      !(await radarBudgetV2(
        "radar-collector:" + row.id,
        row.rate_limit_per_hour,
        3600,
      ))
    ) {
      await recordCollectorUsageV1(row, request, { endpoint, httpStatus: 429 });
      return {
        collector: null,
        response: noStore(
          429,
          { error: "collector_rate_limited" },
          { "Retry-After": "3600" },
        ),
      };
    }
    return { collector: row, response: null };
  } catch {
    return {
      collector: null,
      response: noStore(503, { error: "collector_store_unavailable" }),
    };
  }
}

export async function recordCollectorUsageV1(
  collector: Pick<CollectorRecordV1, "id">,
  request: Request,
  input: {
    endpoint: string;
    httpStatus: number;
    received?: number;
    accepted?: number;
    rejected?: number;
    runId?: string | null;
  },
) {
  const now = new Date().toISOString();
  const results = await Promise.all([
    adminSupabase()
      .from("sr_radar_collector_usage")
      .insert({
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
    adminSupabase()
      .from("sr_radar_collectors")
      .update({ last_used_at: now })
      .eq("id", collector.id),
  ]);
  if (results.some((r) => r.error))
    throw new RadarHttpError(503, "collector_audit_unavailable");
}

export function collectorPingPayloadV1(collector: CollectorRecordV1) {
  const pub = publicCollectorV1(collector);
  return {
    ok: true,
    collector: {
      name: pub.name,
      key_prefix: pub.key_prefix,
      status: pub.status,
    },
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
  const q = await adminSupabase()
    .from("sr_radar_collectors")
    .select(COLLECTOR_COLUMNS)
    .order("created_at", { ascending: false })
    .limit(200);
  throwIfError(q.error);
  return (q.data ?? []).map(publicCollectorV1);
}

export async function createCollectorV1(
  input: Record<string, unknown>,
  actorEmail: string,
) {
  const config = normalizeCollectorConfigV1(input, false);
  const generated = generateCollectorKey();
  const q = await adminSupabase()
    .from("sr_radar_collectors")
    .insert({
      ...config,
      status: "active",
      key_prefix: generated.displayPrefix,
      key_hash: generated.hash,
      created_by: actorEmail,
      changed_by: actorEmail,
    })
    .select(COLLECTOR_COLUMNS)
    .single();
  throwIfError(q.error);
  return { collector: publicCollectorV1(q.data), key: generated.key };
}

export async function rotateCollectorKeyV1(
  id: string,
  actorEmail: string,
  version: number,
  expiresAt?: string,
) {
  const generated = generateCollectorKey();
  const now = new Date().toISOString();
  const q = await adminSupabase()
    .from("sr_radar_collectors")
    .update({
      key_prefix: generated.displayPrefix,
      key_hash: generated.hash,
      rotated_at: now,
      rotated_by: actorEmail,
      changed_by: actorEmail,
      ...(expiresAt
        ? normalizeCollectorConfigV1({ expires_at: expiresAt }, true)
        : {}),
      updated_at: now,
    })
    .eq("id", id)
    .eq("row_version", version)
    .eq("status", "active")
    .select(COLLECTOR_COLUMNS)
    .maybeSingle();
  throwIfError(q.error);
  if (!q.data) throw new Error("collector_not_found_or_revoked");
  return { collector: publicCollectorV1(q.data), key: generated.key };
}

export async function revokeCollectorV1(
  id: string,
  actorEmail: string,
  version: number,
) {
  const now = new Date().toISOString();
  const q = await adminSupabase()
    .from("sr_radar_collectors")
    .update({
      status: "revoked",
      revoked_at: now,
      revoked_by: actorEmail,
      changed_by: actorEmail,
      updated_at: now,
    })
    .eq("id", id)
    .eq("row_version", version)
    .eq("status", "active")
    .select(COLLECTOR_COLUMNS)
    .maybeSingle();
  throwIfError(q.error);
  if (!q.data) throw new Error("collector_not_found_or_revoked");
  return publicCollectorV1(q.data);
}

export async function updateCollectorV1(
  id: string,
  input: Record<string, unknown>,
  actorEmail: string,
  version: number,
) {
  const patch = normalizeCollectorConfigV1(input, true);
  if (!Object.keys(patch).length) throw new Error("collector_patch_empty");
  const q = await adminSupabase()
    .from("sr_radar_collectors")
    .update({
      ...patch,
      changed_by: actorEmail,
      updated_at: new Date().toISOString(),
    })
    .eq("id", id)
    .eq("row_version", version)
    .select(COLLECTOR_COLUMNS)
    .maybeSingle();
  throwIfError(q.error);
  if (!q.data) throw new Error("collector_not_found");
  return publicCollectorV1(q.data);
}

export async function listCollectorActivityV1(
  collectorId: string | null,
  limit = 100,
) {
  const n = Math.max(10, Math.min(Number(limit) || 100, 300));
  let runs = adminSupabase()
    .from("sr_radar_source_runs")
    .select(
      "id,source,status,fetched_count,saved_count,rejected_count,expired_count,error_message,collector_id,run_id,created_at",
    )
    .not("collector_id", "is", null)
    .order("created_at", { ascending: false })
    .limit(n);
  let usage = adminSupabase()
    .from("sr_radar_collector_usage")
    .select(
      "id,collector_id,at,endpoint,http_status,received,accepted,rejected,run_id",
    )
    .order("at", { ascending: false })
    .limit(n);
  if (collectorId) {
    runs = runs.eq("collector_id", collectorId);
    usage = usage.eq("collector_id", collectorId);
  }
  const [r, u] = await Promise.all([runs, usage]);
  throwIfError(r.error);
  throwIfError(u.error);
  const health = await radarRpcV2("sr_radar_run_health_v2", {
    p_ids: (r.data ?? []).map((row: any) => row.id),
  });
  return {
    runs: (r.data ?? []).map((row: any) => ({
      ...row,
      approved_count:
        health.find((m: any) => String(m.id) === String(row.id))
          ?.approved_count ?? 0,
      rejected_count_review:
        health.find((m: any) => String(m.id) === String(row.id))
          ?.rejected_count ?? 0,
      expired_count:
        health.find((m: any) => String(m.id) === String(row.id))
          ?.expired_count ?? row.expired_count,
    })),
    usage: u.data ?? [],
  };
}
