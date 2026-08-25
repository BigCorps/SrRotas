import { authenticateBillingActor } from "@/src/billing-auth";
import { destinationContinuity } from "@/src/destination-continuity";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const ctx = await authenticateBillingActor(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const url = new URL(request.url);
  const cell = (url.searchParams.get("cell") || "").trim() || null;
  const eta = (url.searchParams.get("eta") || "").trim();
  const destination = (url.searchParams.get("destination") || "").trim();

  if (!eta) return Response.json({ error: "eta_required" }, { status: 400 });
  if (!cell && !destination) {
    return Response.json(
      { error: "destination_or_cell_required" },
      { status: 400 },
    );
  }

  try {
    return Response.json(
      await destinationContinuity(ctx.driverId, {
        cell,
        eta,
        destinationLabel: destination || null,
      }),
    );
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "destination_intelligence_failed";
    return Response.json(
      { error: message },
      { status: message === "invalid_eta" ? 400 : 500 },
    );
  }
}
