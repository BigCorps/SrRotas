import { readRadarJsonV2 } from "@/src/radar-hardening-v2";
import {
  adminJsonV1,
  radarAdminErrorV1,
  radarAdminRateLimitV1,
  requireRadarAdminV1,
} from "@/src/radar-admin-http-v1";
import { listReviewPoisV1, reviewPoiV1 } from "@/src/radar-review-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Inactive POI candidates (collector proposals, reconcile candidates, importer). */
export async function GET(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  try {
    return adminJsonV1({
      generated_at: new Date().toISOString(),
      pois: await listReviewPoisV1(200),
    });
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}

/** { id, decision, note }: approve publishes (active); reject/request_changes keep it inactive. */
export async function PATCH(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  const limited = await radarAdminRateLimitV1(
    request,
    "admin-radar-poi-review",
    checked.actor!.email,
    200,
  );
  if (limited) return limited;
  try {
    const body = await readRadarJsonV2(request, 16384);
    const poi = await reviewPoiV1(
      body && typeof body === "object" ? body : {},
      checked.actor!.email,
    );
    return adminJsonV1({ ok: true, poi });
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}
