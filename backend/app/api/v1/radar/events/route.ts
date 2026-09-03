import { authenticateBillingActor } from "@/src/billing-auth";
import { nearbyEventRadar026 } from "@/src/event-radar-026";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const url = new URL(request.url);
  const lat = Number(url.searchParams.get("lat"));
  const lng = Number(url.searchParams.get("lng"));
  const radiusKm = Math.max(2, Math.min(Number(url.searchParams.get("radius_km") || 15) || 15, 30));
  const hours = Math.max(1, Math.min(Number(url.searchParams.get("hours") || 8) || 8, 24));
  if (!Number.isFinite(lat) || lat < -90 || lat > 90 || !Number.isFinite(lng) || lng < -180 || lng > 180) {
    return Response.json({ error: "invalid_location" }, { status: 400 });
  }

  try {
    return Response.json(await nearbyEventRadar026({ lat, lng, radiusKm, hours }));
  } catch (error) {
    return Response.json(
      { error: error instanceof Error ? error.message : "event_radar_failed" },
      { status: 500 },
    );
  }
}
