import { serverEnv } from "@/src/env";
import { ingestRadarEvents0261 } from "@/src/radar-ingest-0261";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function authorized(request: Request) {
  const secret = serverEnv().radarIngestSecret;
  return Boolean(secret) && request.headers.get("authorization") === `Bearer ${secret}`;
}

export async function POST(request: Request) {
  if (!serverEnv().radarIngestSecret) {
    return Response.json({ error: "radar_ingest_secret_missing" }, { status: 503 });
  }
  if (!authorized(request)) return Response.json({ error: "unauthorized" }, { status: 401 });
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
