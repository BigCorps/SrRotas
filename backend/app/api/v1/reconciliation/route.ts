import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { decideReconciliation, reconciliationDetail, reconciliationSummary } from "@/src/reconciliation-v1";

export const runtime = "nodejs";
export const maxDuration = 60;

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const denied = accessDeniedResponse(auth.access, "can_analytics");
  if (denied) return denied;

  const url = new URL(request.url);
  const importId = url.searchParams.get("import_id")?.trim();
  try {
    const body = importId
      ? await reconciliationDetail(auth.driverId, importId)
      : await reconciliationSummary(auth.driverId, Number(url.searchParams.get("limit") ?? 40));
    return Response.json(body, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    const message = error instanceof Error ? error.message : "reconciliation_failed";
    return Response.json({ error: message }, { status: 400, headers: { "Cache-Control": "no-store" } });
  }
}

export async function POST(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const denied = accessDeniedResponse(auth.access, "can_analytics");
  if (denied) return denied;

  const body = await request.json().catch(() => ({}));
  const importId = String(body?.import_id ?? "").trim();
  const decision = String(body?.decision ?? "").trim();
  if (!importId || (decision !== "confirmed" && decision !== "no_match")) {
    return Response.json({ error: "reconciliation_payload_invalid" }, { status: 400 });
  }

  try {
    const result = await decideReconciliation(auth.driverId, {
      importId,
      decision: decision as "confirmed" | "no_match",
      rideOfferId: body?.ride_offer_id ?? null,
    });
    return Response.json(result, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    const message = error instanceof Error ? error.message : "reconciliation_failed";
    return Response.json({ error: message }, { status: 400, headers: { "Cache-Control": "no-store" } });
  }
}
