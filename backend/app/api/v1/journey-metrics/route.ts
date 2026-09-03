import { authenticateBillingActor } from "@/src/billing-auth";
import { authenticateDevice } from "@/src/device-auth";
import {
  listJourneyMetrics,
  saveEnergyEntry,
  saveJourneyMetric,
} from "@/src/journey-metrics-026";

export const runtime = "nodejs";

function statusFor(message: string) {
  if (message === "journey_not_found") return 404;
  if (message.includes("required") || message.startsWith("invalid_") || message.startsWith("odometer_")) return 400;
  return 500;
}

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const url = new URL(request.url);
  const days = Math.max(1, Math.min(Number(url.searchParams.get("days") || 30) || 30, 90));
  try {
    return Response.json(await listJourneyMetrics(auth.driverId, days));
  } catch (error) {
    const message = error instanceof Error ? error.message : "journey_metrics_failed";
    return Response.json({ error: message }, { status: statusFor(message) });
  }
}

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => null);
  if (!body || typeof body !== "object") return Response.json({ error: "invalid_json" }, { status: 400 });
  const input = body as Record<string, unknown>;
  const action = String(input.action ?? "").trim().toLowerCase();
  try {
    if (action === "metrics") return Response.json({ ok: true, metric: await saveJourneyMetric(auth.driverId, input) });
    if (action === "energy") return Response.json({ ok: true, entry: await saveEnergyEntry(auth.driverId, auth.deviceId, input) });
    return Response.json({ error: "invalid_action" }, { status: 400 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "journey_metrics_failed";
    return Response.json({ error: message }, { status: statusFor(message) });
  }
}
