import { refreshEventRadar026 } from "@/src/event-radar-026";
import { serverEnv } from "@/src/env";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 60;

export async function GET(request: Request) {
  const secret = serverEnv().cronSecret;
  if (!secret) {
    return Response.json({ error: "cron_secret_missing" }, { status: 503 });
  }
  if (request.headers.get("authorization") !== `Bearer ${secret}`) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }
  try {
    return Response.json(await refreshEventRadar026());
  } catch (error) {
    return Response.json(
      { error: error instanceof Error ? error.message : "event_radar_refresh_failed" },
      { status: 500 },
    );
  }
}
