import { authenticateBillingActor } from "@/src/billing-auth";
import { historyDashboard } from "@/src/analytics";
import { regionalIntelligence } from "@/src/regional-intelligence";

export const runtime = "nodejs";
const VERDICTS = new Set(["boa", "regular", "ruim"]);
const SERVICES = new Set([
  "uberx",
  "comfort",
  "black",
  "electric",
  "priority",
  "moto",
  "unknown",
]);
const OFFER_TYPES = new Set(["exclusive", "radar"]);

export async function GET(request: Request) {
  const ctx = await authenticateBillingActor(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const url = new URL(request.url);
  const days = Math.max(
    1,
    Math.min(Number(url.searchParams.get("days") || "7") || 7, 90),
  );
  const verdictRaw = url.searchParams.get("verdict") || "";
  const serviceRaw = url.searchParams.get("service_type") || "";
  const offerTypeRaw = url.searchParams.get("offer_type") || "";

  if (verdictRaw && !VERDICTS.has(verdictRaw)) {
    return Response.json({ error: "invalid_verdict" }, { status: 400 });
  }
  if (serviceRaw && !SERVICES.has(serviceRaw)) {
    return Response.json({ error: "invalid_service_type" }, { status: 400 });
  }
  if (offerTypeRaw && !OFFER_TYPES.has(offerTypeRaw)) {
    return Response.json({ error: "invalid_offer_type" }, { status: 400 });
  }

  try {
    const [history, regional] = await Promise.all([
      historyDashboard(ctx.driverId, {
        days,
        verdict: verdictRaw || undefined,
        serviceType: serviceRaw || undefined,
        offerType: offerTypeRaw || undefined,
      }),
      regionalIntelligence(ctx.driverId, days).catch((error) => ({
        available: false,
        error:
          error instanceof Error
            ? error.message
            : "regional_intelligence_failed",
      })),
    ]);

    return Response.json({
      ...history,
      regional_intelligence: regional,
    });
  } catch (error) {
    return Response.json(
      {
        error:
          error instanceof Error ? error.message : "analytics_failed",
      },
      { status: 500 },
    );
  }
}
