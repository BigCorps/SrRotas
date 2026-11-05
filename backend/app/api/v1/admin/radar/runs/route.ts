import { adminJsonV1, radarAdminErrorV1, requireRadarAdminV1, validUuidV1 } from "@/src/radar-admin-http-v1";
import { listCollectorActivityV1 } from "@/src/radar-collector-store-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Last collector runs (sr_radar_source_runs) and usage audit rows. No payloads. */
export async function GET(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  const url = new URL(request.url);
  const collectorId = url.searchParams.get("collector_id");
  try {
    const activity = await listCollectorActivityV1(
      collectorId && validUuidV1(collectorId) ? collectorId : null,
      Number(url.searchParams.get("limit") || 100),
    );
    return adminJsonV1({ generated_at: new Date().toISOString(), ...activity });
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}
