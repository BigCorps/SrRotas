import { authenticateBillingActor } from "@/src/billing-auth";
import {
  continuityEstimate,
  regionalIntelligence,
} from "@/src/regional-intelligence";

export const runtime = "nodejs";

export async function GET(request: Request) {
  const ctx = await authenticateBillingActor(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const url = new URL(request.url);
  const days = Math.max(
    1,
    Math.min(Number(url.searchParams.get("days") || "30") || 30, 90),
  );
  const cell = (url.searchParams.get("cell") || "").trim();
  const eta = (url.searchParams.get("eta") || "").trim();

  try {
    if (cell || eta) {
      if (!cell || !eta) {
        return Response.json(
          { error: "cell_and_eta_required" },
          { status: 400 },
        );
      }
      return Response.json(
        await continuityEstimate(
          ctx.driverId,
          cell,
          eta,
          Math.max(days, 7),
        ),
      );
    }

    return Response.json(
      await regionalIntelligence(ctx.driverId, days),
    );
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "intelligence_failed";
    const status =
      message === "invalid_cell" || message === "invalid_eta" ? 400 : 500;

    return Response.json({ error: message }, { status });
  }
}
