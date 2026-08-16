import { authenticateDevice } from "@/src/device-auth";
import { currentJourney, endJourney, journeySummary, listJourneys, startJourney } from "@/src/journeys";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => null);
  if (!body || typeof body !== "object") return Response.json({ error: "invalid_json" }, { status: 400 });
  try {
    const action = String((body as Record<string, unknown>).action ?? "start");
    if (action === "start") {
      return Response.json({ ok: true, journey: await startJourney(auth.driverId, auth.deviceId, body as Record<string, unknown>) });
    }
    if (action === "end") {
      const journeyId = String((body as Record<string, unknown>).journey_id ?? "").trim();
      if (!journeyId) return Response.json({ error: "journey_id_required" }, { status: 400 });
      return Response.json({ ok: true, journey: await endJourney(auth.driverId, journeyId, body as Record<string, unknown>) });
    }
    return Response.json({ error: "invalid_action" }, { status: 400 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "journey_failed";
    return Response.json({ error: message }, { status: message === "journey_not_found" ? 404 : 500 });
  }
}

export async function GET(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const url = new URL(request.url);
  try {
    const id = url.searchParams.get("id");
    if (id) return Response.json(await journeySummary(auth.driverId, id));
    if (url.searchParams.get("current") === "1") return Response.json({ journey: await currentJourney(auth.driverId) });
    return Response.json({ journeys: await listJourneys(auth.driverId, Number(url.searchParams.get("limit") || 30)) });
  } catch (error) {
    const message = error instanceof Error ? error.message : "journey_failed";
    return Response.json({ error: message }, { status: message === "journey_not_found" ? 404 : 500 });
  }
}
