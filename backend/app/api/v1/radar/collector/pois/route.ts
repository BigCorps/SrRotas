import { collectorPoisResponseV1 } from "@/src/radar-collector-http-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Collector POI proposals (scope pois:propose): always stored inactive + review pending. */
export async function POST(request: Request) {
  return collectorPoisResponseV1(request);
}
