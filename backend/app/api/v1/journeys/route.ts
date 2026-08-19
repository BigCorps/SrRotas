import { authenticateDevice } from "@/src/device-auth";
import { authenticateBillingActor } from "@/src/billing-auth";
import { fetchOffers } from "@/src/analytics";
import {
  currentJourney,
  endJourney,
  journeySummary,
  listJourneys,
  recordJourneyStateEvent,
  saveRegionalExposure,
  startJourney,
  upsertRideOutcome,
} from "@/src/journeys";
import { sendDriverPush } from "@/src/notifications";

export const runtime = "nodejs";

function money(value: unknown) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : "—";
}

function statusFor(message: string) {
  if (message.includes("required") || message.startsWith("invalid_")) return 400;
  if (message === "journey_not_found") return 404;
  if (message === "journey_id_conflict") return 409;
  return 500;
}

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => null);
  if (!body || typeof body !== "object") return Response.json({ error: "invalid_json" }, { status: 400 });

  try {
    const input = body as Record<string, unknown>;
    const action = String(input.action ?? "start");
    if (action === "start") return Response.json({ ok: true, journey: await startJourney(auth.driverId, auth.deviceId, input) });

    if (action === "end") {
      const journeyId = String(input.journey_id ?? "").trim();
      if (!journeyId) return Response.json({ error: "journey_id_required" }, { status: 400 });
      const journey = await endJourney(auth.driverId, journeyId, input);
      const summary = await journeySummary(auth.driverId, journeyId);
      await sendDriverPush(auth.driverId, {
        category: "journey_summary",
        title: "Jornada encerrada",
        body: `${summary.summary.offer_count} ofertas observadas · ${summary.summary.verdicts.boa} boas · média R$ ${money(summary.summary.average_per_km)}/km`,
        route: "historico",
        source: "journey_end",
        dedupeKey: `journey:${journeyId}:summary`,
      }).catch(() => undefined);
      return Response.json({ ok: true, journey, summary: summary.summary, ride_summary: summary.ride_summary, exposure_seconds: summary.exposure_seconds });
    }

    if (action === "state_event") return Response.json({ ok: true, event: await recordJourneyStateEvent(auth.driverId, auth.deviceId, input) });
    if (action === "ride_outcome") return Response.json({ ok: true, outcome: await upsertRideOutcome(auth.driverId, auth.deviceId, input) });
    if (action === "exposure") return Response.json({ ok: true, exposure: await saveRegionalExposure(auth.driverId, auth.deviceId, input) });
    return Response.json({ error: "invalid_action" }, { status: 400 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "journey_failed";
    return Response.json({ error: message }, { status: statusFor(message) });
  }
}

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const url = new URL(request.url);
  try {
    const id = url.searchParams.get("id")?.trim() || "";
    if (id) {
      const detail = await journeySummary(auth.driverId, id);
      if (url.searchParams.get("include_offers") === "1") {
        const found = await fetchOffers(auth.driverId, { journeyId: id, limit: 500 });
        return Response.json({ ...detail, offers: found.offers, offer_count_returned: found.offers.length });
      }
      return Response.json(detail);
    }
    if (url.searchParams.get("current") === "1") return Response.json({ journey: await currentJourney(auth.driverId) });
    return Response.json({ journeys: await listJourneys(auth.driverId, Number(url.searchParams.get("limit") || 30)) });
  } catch (error) {
    const message = error instanceof Error ? error.message : "journey_failed";
    return Response.json({ error: message }, { status: statusFor(message) });
  }
}
