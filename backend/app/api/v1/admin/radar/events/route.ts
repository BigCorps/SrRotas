import { adminJsonV1, radarAdminErrorV1, radarAdminRateLimitV1, requireRadarAdminV1, validUuidV1 } from "@/src/radar-admin-http-v1";
import { listReviewEventsV1, reviewEventV1 } from "@/src/radar-review-v1";
import { listCollectorsV1 } from "@/src/radar-collector-store-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Received events (default: collector-delivered) with review flags. Admin session only. */
export async function GET(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  const url = new URL(request.url);
  const collectorId = url.searchParams.get("collector_id");
  try {
    const [events, collectors] = await Promise.all([
      listReviewEventsV1({
        origin: url.searchParams.get("origin") === "all" ? "all" : "collectors",
        collectorId: collectorId && validUuidV1(collectorId) ? collectorId : null,
        source: url.searchParams.get("source")?.trim().toLowerCase() || null,
        reviewStatus: url.searchParams.get("review_status") || null,
        status: url.searchParams.get("status") || null,
        from: url.searchParams.get("from") || null,
        to: url.searchParams.get("to") || null,
        limit: Number(url.searchParams.get("limit") || 200),
      }),
      listCollectorsV1(),
    ]);
    return adminJsonV1({ generated_at: new Date().toISOString(), events, collectors });
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}

/** Review decision: { id, decision: approve|reject|request_changes, note } → decision log. */
export async function PATCH(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  const limited = await radarAdminRateLimitV1(request, "admin-radar-review", checked.actor!.email, 300);
  if (limited) return limited;
  try {
    const body = await request.json().catch(() => ({}));
    const event = await reviewEventV1(body && typeof body === "object" ? body : {}, checked.actor!.email);
    return adminJsonV1({ ok: true, event });
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}
