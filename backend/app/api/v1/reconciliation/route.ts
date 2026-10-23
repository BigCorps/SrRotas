import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { reconciliationDetail, reconciliationOverview, reconciliationSummary } from "@/src/reconciliation-v1";

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
      : url.searchParams.get("summary_only") === "1"
        ? await reconciliationOverview(auth.driverId)
        : await reconciliationSummary(auth.driverId, Number(url.searchParams.get("limit") ?? 40));
    return Response.json(body, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    const message = error instanceof Error ? error.message : "reconciliation_failed";
    return Response.json({ error: message }, { status: 400, headers: { "Cache-Control": "no-store" } });
  }
}

/**
 * Fail closed while reconciliation is not transactional and independently reviewed.
 * The former POST chain could overwrite other outcomes or partially persist writes.
 * This endpoint deliberately contains no write call and cannot be enabled by a flag.
 */
export async function POST(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const denied = accessDeniedResponse(auth.access, "can_analytics");
  if (denied) return denied;

  return Response.json(
    { error: "reconciliation_writes_locked", message: "Confirmações reais indisponíveis até homologação transacional." },
    { status: 423, headers: { "Cache-Control": "no-store" } },
  );
}
