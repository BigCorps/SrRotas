import { collectorPingResponseV1 } from "@/src/radar-collector-http-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Safe connection test for external collectors: returns identity, scopes and limits. */
export async function GET(request: Request) {
  return collectorPingResponseV1(request, "collector:ping");
}
