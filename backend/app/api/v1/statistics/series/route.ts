import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { MAX_SERIES_DAYS } from "@/src/intelligence/statistics-series";
import { statisticsSeries } from "@/src/statistics-series";

export const runtime = "nodejs";
export const maxDuration = 60;
const noStore = { "Cache-Control": "private, no-store" };

/** Read-only daily series of the authenticated driver. Same auth and
 * entitlement as `/api/v1/statistics`; the period is the exact window the
 * client already used for the snapshot, never widened here. */
export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401, headers: noStore });
  const denied = accessDeniedResponse(auth.access, "can_analytics");
  if (denied) return denied;

  const url = new URL(request.url);
  const from = url.searchParams.get("from") || "";
  const to = url.searchParams.get("to") || "";
  const fromMs = Date.parse(from);
  const toMs = Date.parse(to);
  const now = Date.now();
  if (!Number.isFinite(fromMs) || !Number.isFinite(toMs) || fromMs >= toMs || toMs > now + 5 * 60_000 ||
    toMs - fromMs > MAX_SERIES_DAYS * 86_400_000) {
    return Response.json({ error: "series_period_invalid" }, { status: 400, headers: noStore });
  }
  try {
    const body = await statisticsSeries(auth.driverId, new Date(fromMs).toISOString(), new Date(toMs).toISOString(), now);
    return Response.json(body, { headers: noStore });
  } catch (error) {
    const message = error instanceof Error ? error.message : "statistics_series_failed";
    return Response.json({ error: message }, { status: 500, headers: noStore });
  }
}
