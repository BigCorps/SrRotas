import { authenticateBillingActor } from "@/src/billing-auth";
import { listJourneyRealized0262 } from "@/src/journey-realized-0262";

export const runtime = "nodejs";

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const url = new URL(request.url);
  const days = Math.max(1, Math.min(Number(url.searchParams.get("days") || 30) || 30, 90));
  try {
    return Response.json(await listJourneyRealized0262(auth.driverId, days));
  } catch (error) {
    const message = error instanceof Error ? error.message : "journey_realized_failed";
    return Response.json({ error: message }, { status: 500 });
  }
}
