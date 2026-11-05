import { randomUUID } from "node:crypto";
import {
  readRadarJsonV2,
  RadarHttpError,
  radarRpcV2,
} from "./radar-hardening-v2";
import { sha256Hex } from "./radar-collectors-v1";
import { normalizeRadarPoiAdminV1 } from "./radar-poi-admin-v1";
import {
  CollectorStoreUnavailableError,
  collectorSourceAllowed,
  hasCollectorScope,
  normalizeCollectorSource,
  COLLECTOR_LIMITS,
} from "./radar-collectors-v1";
import {
  authenticateCollectorRequestV1,
  collectorPingPayloadV1,
  recordCollectorUsageV1,
} from "./radar-collector-store-v1";
import {
  stableJson,
  CollectorRequestError,
  ingestCollectorEventsV1,
} from "./radar-collector-ingest-v1";

/** HTTP glue for external collector endpoints. Collector keys never reach admin handlers. */

const MAX_BODY_BYTES = 2_000_000;
const NO_STORE = { "Cache-Control": "no-store" };

function json(
  status: number,
  body: Record<string, unknown>,
  headers: Record<string, string> = {},
) {
  return Response.json(body, { status, headers: { ...NO_STORE, ...headers } });
}

type UsageCounts = {
  received?: number;
  accepted?: number;
  rejected?: number;
  runId?: string | null;
};
type ConfirmedReceipt = { runId: string; replayable: boolean };

function requestedRunId(body: unknown): string | undefined {
  if (!body || typeof body !== "object" || !("run_id" in body)) return;
  const id = (body as { run_id: unknown }).run_id;
  return typeof id === "string" && /^[A-Za-z0-9._:-]{8,100}$/.test(id)
    ? id
    : undefined;
}
function confirmedReceipt(
  summary: Record<string, unknown>,
  input: unknown,
): ConfirmedReceipt | undefined {
  if (
    summary.ok !== true ||
    summary.dry_run !== false ||
    typeof summary.run_id !== "string"
  )
    return;
  return {
    runId: summary.run_id,
    replayable: requestedRunId(input) === summary.run_id,
  };
}

/** Business receipt/history were committed by the RPC before HTTP usage is attempted.
 * A secondary audit failure must never turn a known commit into an ambiguous 5xx.
 * The per-attempt audit state is not written into the immutable stored receipt.
 */
async function finishCollectorResponse(
  collector: Parameters<typeof recordCollectorUsageV1>[0],
  request: Request,
  endpoint: string,
  status: number,
  body: Record<string, unknown>,
  counts: UsageCounts = {},
  headers: Record<string, string> = {},
  receipt?: ConfirmedReceipt,
) {
  try {
    await recordCollectorUsageV1(collector, request, {
      endpoint,
      httpStatus: status,
      ...counts,
      runId: receipt?.runId ?? counts.runId,
    });
  } catch {
    const incidentId = randomUUID();
    const runId = receipt?.runId ?? counts.runId;
    console.warn("sr_radar_collector_http_audit_unconfirmed", {
      incident_id: incidentId,
      collector_id: collector.id,
      endpoint,
      run_id_hash: runId ? sha256Hex(runId) : null,
      commit_state: receipt ? "confirmed" : "not_confirmed",
      request_status: status,
    });
    if (receipt) {
      return json(
        status,
        {
          ...body,
          ingestion_committed: true,
          http_audit: {
            status: "unconfirmed",
            incident_id: incidentId,
            recovery: receipt.replayable
              ? "retry_same_run_id_same_payload"
              : "contact_admin_with_run_id",
          },
        },
        headers,
      );
    }
    // Includes dry-run/ping and unknown RPC outcomes: never claim a rollback here.
    return json(
      503,
      {
        error: "collector_http_audit_unavailable",
        incident_id: incidentId,
        request_status: status,
        ...(runId ? { run_id: runId } : {}),
        recovery: runId ? "retry_same_run_id_same_payload" : "contact_admin",
      },
      { "Retry-After": "60" },
    );
  }
  return json(
    status,
    receipt
      ? {
          ...body,
          ingestion_committed: true,
          http_audit: { status: "recorded" },
        }
      : body,
    headers,
  );
}

async function readJson(
  request: Request,
): Promise<{ ok: true; body: unknown } | { ok: false; response: Response }> {
  try {
    return { ok: true, body: await readRadarJsonV2(request, MAX_BODY_BYTES) };
  } catch (e) {
    return {
      ok: false,
      response: json(e instanceof RadarHttpError ? e.status : 400, {
        error: e instanceof Error ? e.message : "invalid_json",
      }),
    };
  }
}

export async function collectorPingResponseV1(
  request: Request,
  endpoint = "collector:ping",
) {
  const auth = await authenticateCollectorRequestV1(request, endpoint);
  if (auth.response) return auth.response;
  return finishCollectorResponse(
    auth.collector,
    request,
    endpoint,
    200,
    collectorPingPayloadV1(auth.collector),
  );
}

export async function collectorIngestResponseV1(request: Request) {
  const endpoint = "events:ingest";
  const auth = await authenticateCollectorRequestV1(request, endpoint);
  if (auth.response) return auth.response;
  const collector = auth.collector;
  let inputRunId: string | undefined;
  const finish = (
    status: number,
    body: Record<string, unknown>,
    counts: UsageCounts = {},
    headers: Record<string, string> = {},
    receipt?: ConfirmedReceipt,
  ) =>
    finishCollectorResponse(
      collector,
      request,
      endpoint,
      status,
      body,
      { ...counts, runId: counts.runId ?? inputRunId },
      headers,
      receipt,
    );

  if (!hasCollectorScope(collector, "events:ingest"))
    return finish(403, {
      error: "scope_missing",
      required_scope: "events:ingest",
    });
  const parsed = await readJson(request);
  if (!parsed.ok)
    return finish(parsed.response.status, await parsed.response.json());
  inputRunId = requestedRunId(parsed.body);
  try {
    const summary: any = await ingestCollectorEventsV1(collector, parsed.body);
    return finish(
      200,
      summary,
      {
        received: Number(summary.received) || 0,
        accepted: Number(summary.accepted) || 0,
        rejected: Number(summary.rejected) || 0,
        runId: summary.run_id ?? null,
      },
      {},
      confirmedReceipt(summary, parsed.body),
    );
  } catch (error) {
    if (error instanceof RadarHttpError)
      return finish(error.status, { error: error.message });
    if (error instanceof CollectorRequestError)
      return finish(error.httpStatus, { error: error.message, ...error.extra });
    if (error instanceof CollectorStoreUnavailableError)
      return finish(503, { error: "collector_store_unavailable" });
    console.error("sr_radar_collector_ingest_failed", {
      collector_id: collector.id,
      message: error instanceof Error ? error.message.slice(0, 200) : "unknown",
    });
    return finish(500, { error: "collector_ingest_failed" });
  }
}

export async function collectorPoisResponseV1(request: Request) {
  const endpoint = "pois:propose";
  const auth = await authenticateCollectorRequestV1(request, endpoint);
  if (auth.response) return auth.response;
  const collector = auth.collector;
  let inputRunId: string | undefined;
  const finish = (
    status: number,
    body: Record<string, unknown>,
    received = 0,
    accepted = 0,
    receipt?: ConfirmedReceipt,
  ) =>
    finishCollectorResponse(
      collector,
      request,
      endpoint,
      status,
      body,
      {
        received,
        accepted,
        rejected: Number(body.rejected ?? (status === 200 ? 0 : received)),
        runId: inputRunId,
      },
      {},
      receipt,
    );

  if (!hasCollectorScope(collector, "pois:propose"))
    return finish(403, {
      error: "scope_missing",
      required_scope: "pois:propose",
    });
  const parsed = await readJson(request);
  if (!parsed.ok)
    return finish(parsed.response.status, await parsed.response.json());
  inputRunId = requestedRunId(parsed.body);
  const body = parsed.body;
  if (!body || typeof body !== "object" || Array.isArray(body))
    return finish(400, { error: "payload_must_be_object" });
  const payload = body as Record<string, unknown>;
  const pois = Array.isArray(payload.pois) ? payload.pois : null;
  if (!pois || pois.length < 1) return finish(400, { error: "pois_required" });
  if (pois.length > COLLECTOR_LIMITS.poiBatchCap) {
    return finish(
      413,
      {
        error: "batch_too_large",
        max_batch: COLLECTOR_LIMITS.poiBatchCap,
        received: pois.length,
      },
      pois.length,
    );
  }
  if (payload.dry_run !== undefined && typeof payload.dry_run !== "boolean")
    return finish(400, { error: "dry_run_invalid" }, pois.length);
  const cleaned: Record<string, unknown>[] = [];
  for (let index = 0; index < pois.length; index++) {
    const poi = pois[index];
    if (!poi || typeof poi !== "object" || Array.isArray(poi))
      return finish(400, { error: "poi_invalid", index }, pois.length);
    const source = normalizeCollectorSource(
      (poi as Record<string, unknown>).source,
    );
    if (!collectorSourceAllowed(collector, source))
      return finish(400, { error: "source_not_allowed", index }, pois.length);
    // A collector can never mark its own proposal as manual/admin.
    const {
      ingest_kind: _ignored,
      status: _status,
      ...rest
    } = poi as Record<string, unknown>;
    cleaned.push({ ...rest, source });
  }
  try {
    const rows = cleaned.map((p, index) => ({
      index,
      row: normalizeRadarPoiAdminV1(p),
    }));
    const seen = new Set<string>();
    for (const item of rows) {
      const identity = item.row.source + "|" + item.row.source_external_id;
      if (seen.has(identity))
        return finish(400, { error: "duplicate_in_batch" }, pois.length);
      seen.add(identity);
    }
    if (rows.some((p) => !p.row.source_url))
      return finish(400, { error: "source_url_required" }, pois.length);
    const runId =
      payload.run_id === undefined ? randomUUID() : String(payload.run_id);
    if (!/^[A-Za-z0-9._:-]{8,100}$/.test(runId))
      return finish(400, { error: "run_id_invalid" });
    const result: any = await radarRpcV2("sr_radar_receive_v2", {
      p_collector: collector.id,
      p_key_hash: collector.key_hash,
      p_kind: "poi",
      p_run: runId,
      p_hash: sha256Hex(stableJson(payload)),
      p_items: rows,
      p_rejected: [],
      p_received: pois.length,
      p_dry: payload.dry_run === true,
    });
    return finish(
      200,
      result,
      pois.length,
      result.accepted,
      confirmedReceipt(result, payload),
    );
  } catch (error) {
    if (error instanceof RadarHttpError)
      return finish(error.status, { error: error.message });
    const message =
      error instanceof Error ? error.message : "poi_proposal_failed";
    return finish(
      400,
      { error: /^[a-z_]+$/.test(message) ? message : "poi_proposal_failed" },
      pois.length,
    );
  }
}
