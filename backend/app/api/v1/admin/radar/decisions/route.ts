import { adminJsonV1, radarAdminErrorV1, requireRadarAdminV1 } from "@/src/radar-admin-http-v1";
import { listReviewDecisionsV1 } from "@/src/radar-review-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  const limit = Number(new URL(request.url).searchParams.get("limit") || 200);
  try {
    return adminJsonV1({ generated_at: new Date().toISOString(), decisions: await listReviewDecisionsV1(limit) });
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}
