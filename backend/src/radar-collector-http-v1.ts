import {
  CollectorStoreUnavailableError,
  collectorSourceAllowed,
  collectorStoreUnavailableResponse,
  hasCollectorScope,
  normalizeCollectorSource,
  COLLECTOR_LIMITS,
} from "./radar-collectors-v1";
import {
  authenticateCollectorRequestV1,
  collectorPingPayloadV1,
  recordCollectorUsageV1,
} from "./radar-collector-store-v1";
import { CollectorRequestError, ingestCollectorEventsV1 } from "./radar-collector-ingest-v1";
import { ingestRadarPoisAdminV1 } from "./radar-poi-admin-v1";

/** HTTP glue for external collector endpoints. Collector keys never reach admin handlers. */

const MAX_BODY_BYTES = 2_000_000;
const NO_STORE = { "Cache-Control": "no-store" };

function json(status: number, body: Record<string, unknown>, headers: Record<string, string> = {}) {
  return Response.json(body, { status, headers: { ...NO_STORE, ...headers } });
}

async function readJson(request: Request): Promise<{ ok: true; body: unknown } | { ok: false; response: Response }> {
  const declared = Number(request.headers.get("content-length") || 0);
  if (declared > MAX_BODY_BYTES) return { ok: false, response: json(413, { error: "payload_too_large", max_bytes: MAX_BODY_BYTES }) };
  const raw = await request.text().catch(() => "");
  if (Buffer.byteLength(raw, "utf8") > MAX_BODY_BYTES) {
    return { ok: false, response: json(413, { error: "payload_too_large", max_bytes: MAX_BODY_BYTES }) };
  }
  try {
    return { ok: true, body: JSON.parse(raw) };
  } catch {
    return { ok: false, response: json(400, { error: "invalid_json" }) };
  }
}

export async function collectorPingResponseV1(request: Request, endpoint = "collector:ping") {
  const auth = await authenticateCollectorRequestV1(request, endpoint);
  if (auth.response) return auth.response;
  await recordCollectorUsageV1(auth.collector, request, { endpoint, httpStatus: 200 });
  return json(200, collectorPingPayloadV1(auth.collector));
}

export async function collectorIngestResponseV1(request: Request) {
  const endpoint = "events:ingest";
  const auth = await authenticateCollectorRequestV1(request, endpoint);
  if (auth.response) return auth.response;
  const collector = auth.collector;
  const finish = async (status: number, body: Record<string, unknown>, counts: { received?: number; accepted?: number; rejected?: number; runId?: string | null } = {}, headers: Record<string, string> = {}) => {
    await recordCollectorUsageV1(collector, request, { endpoint, httpStatus: status, ...counts });
    return json(status, body, headers);
  };

  if (!hasCollectorScope(collector, "events:ingest")) return finish(403, { error: "scope_missing", required_scope: "events:ingest" });
  const parsed = await readJson(request);
  if (!parsed.ok) {
    await recordCollectorUsageV1(collector, request, { endpoint, httpStatus: parsed.response.status });
    return parsed.response;
  }
  try {
    const summary: any = await ingestCollectorEventsV1(collector, parsed.body);
    return finish(200, summary, {
      received: Number(summary.received) || 0,
      accepted: Number(summary.accepted) || 0,
      rejected: Number(summary.rejected) || 0,
      runId: summary.run_id ?? null,
    });
  } catch (error) {
    if (error instanceof CollectorRequestError) return finish(error.httpStatus, { error: error.message, ...error.extra });
    if (error instanceof CollectorStoreUnavailableError) {
      await recordCollectorUsageV1(collector, request, { endpoint, httpStatus: 503 });
      return collectorStoreUnavailableResponse();
    }
    console.error("sr_radar_collector_ingest_failed", { collector_id: collector.id, message: error instanceof Error ? error.message.slice(0, 200) : "unknown" });
    return finish(500, { error: "collector_ingest_failed" });
  }
}

export async function collectorPoisResponseV1(request: Request) {
  const endpoint = "pois:propose";
  const auth = await authenticateCollectorRequestV1(request, endpoint);
  if (auth.response) return auth.response;
  const collector = auth.collector;
  const finish = async (status: number, body: Record<string, unknown>, received = 0, accepted = 0) => {
    await recordCollectorUsageV1(collector, request, { endpoint, httpStatus: status, received, accepted, rejected: status === 200 ? 0 : received });
    return json(status, body);
  };

  if (!hasCollectorScope(collector, "pois:propose")) return finish(403, { error: "scope_missing", required_scope: "pois:propose" });
  const parsed = await readJson(request);
  if (!parsed.ok) {
    await recordCollectorUsageV1(collector, request, { endpoint, httpStatus: parsed.response.status });
    return parsed.response;
  }
  const body = parsed.body;
  if (!body || typeof body !== "object" || Array.isArray(body)) return finish(400, { error: "payload_must_be_object" });
  const payload = body as Record<string, unknown>;
  const pois = Array.isArray(payload.pois) ? payload.pois : null;
  if (!pois || pois.length < 1) return finish(400, { error: "pois_required" });
  if (pois.length > COLLECTOR_LIMITS.poiBatchCap) {
    return finish(413, { error: "batch_too_large", max_batch: COLLECTOR_LIMITS.poiBatchCap, received: pois.length }, pois.length);
  }
  if (payload.dry_run !== undefined && typeof payload.dry_run !== "boolean") return finish(400, { error: "dry_run_invalid" }, pois.length);
  const cleaned: Record<string, unknown>[] = [];
  for (let index = 0; index < pois.length; index++) {
    const poi = pois[index];
    if (!poi || typeof poi !== "object" || Array.isArray(poi)) return finish(400, { error: "poi_invalid", index }, pois.length);
    const source = normalizeCollectorSource((poi as Record<string, unknown>).source);
    if (!collectorSourceAllowed(collector, source)) return finish(400, { error: "source_not_allowed", index }, pois.length);
    // A collector can never mark its own proposal as manual/admin.
    const { ingest_kind: _ignored, status: _status, ...rest } = poi as Record<string, unknown>;
    cleaned.push({ ...rest, source });
  }
  try {
    const result: any = await ingestRadarPoisAdminV1({ pois: cleaned, dry_run: payload.dry_run === true }, { collectorId: collector.id });
    const accepted = result.dry_run ? 0 : Number(result.saved) || 0;
    return finish(200, {
      ok: true,
      contract: "srrotas-radar-collector-api-v1",
      dry_run: Boolean(result.dry_run),
      received: pois.length,
      created: result.created ?? 0,
      updated: result.updated ?? 0,
      pending_review: result.pending_review ?? 0,
      ...(result.dry_run ? { validated: Array.isArray(result.items) ? result.items.length : 0 } : {}),
    }, pois.length, accepted);
  } catch (error) {
    const message = error instanceof Error ? error.message : "poi_proposal_failed";
    return finish(400, { error: /^[a-z_]+$/.test(message) ? message : "poi_proposal_failed" }, pois.length);
  }
}
