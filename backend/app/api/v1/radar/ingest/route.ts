import { serverEnv } from "@/src/env";
import { ingestRadarEvents0261 } from "@/src/radar-ingest-0261";
import { isCollectorBearer, legacyRadarSecretAuthorized } from "@/src/radar-collectors-v1";
import { collectorIngestResponseV1, collectorPingResponseV1 } from "@/src/radar-collector-http-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// Legacy internal/admin path (RADAR_INGEST_SECRET): unchanged semantics, constant-time compare.
function authorized(request: Request) {
  return legacyRadarSecretAuthorized(request.headers.get("authorization"), serverEnv().radarIngestSecret);
}

export async function POST(request: Request) {
  // 1) Exact legacy secret keeps the previous behaviour byte-for-byte (incl. snapshot_complete).
  if (authorized(request)) {
    try {
      const body = await request.json();
      const payload = Array.isArray(body) ? { events: body } : body;
      return Response.json(await ingestRadarEvents0261(payload));
    } catch (error) {
      return Response.json(
        { error: error instanceof Error ? error.message : "radar_ingest_failed" },
        { status: 400 },
      );
    }
  }
  // 2) External collector key (`Bearer srrk_...`): scoped, rate limited, held for review.
  if (isCollectorBearer(request.headers.get("authorization"))) return collectorIngestResponseV1(request);
  // 3) Same responses as before for everything else.
  if (!serverEnv().radarIngestSecret) {
    return Response.json({ error: "radar_ingest_secret_missing" }, { status: 503 });
  }
  return Response.json({ error: "unauthorized" }, { status: 401 });
}

/** Collector connection test (alias of /api/v1/radar/collector/ping). No writes besides usage audit. */
export async function GET(request: Request) {
  if (!isCollectorBearer(request.headers.get("authorization"))) {
    return Response.json({ error: "unauthorized" }, { status: 401, headers: { "Cache-Control": "no-store" } });
  }
  return collectorPingResponseV1(request, "ingest:ping");
}
