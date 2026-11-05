import { adminSupabase } from "./supabase";
import { sha256Hex } from "./radar-collectors-v1";
export class RadarHttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function radarRpcV2(name: string, args: Record<string, unknown>) {
  const { data, error } = await adminSupabase().rpc(name, args);
  if (error)
    throw new RadarHttpError(
      /^PT\d{3}$/.test(error.code) ? Number(error.code.slice(2)) : 503,
      /^PT\d{3}$/.test(error.code)
        ? error.message
        : "collector_store_unavailable",
    );
  return data;
}
export async function radarBudgetV2(
  key: string,
  limit: number,
  seconds: number,
) {
  return Boolean(
    await radarRpcV2("sr_radar_budget_v2", {
      p_key: sha256Hex(key),
      p_limit: limit,
      p_seconds: seconds,
    }),
  );
}
export function sameRadarOriginV2(request: Request) {
  const host = request.headers.get("host") ?? new URL(request.url).host;
  const origin = request.headers.get("origin");
  return (
    origin === `https://${host}` ||
    (/^(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?$/.test(host) &&
      origin === `http://${host}`)
  );
}
export async function readRadarJsonV2(
  request: Request,
  max = 2_000_000,
): Promise<any> {
  if (
    !request.headers
      .get("content-type")
      ?.toLowerCase()
      .startsWith("application/json")
  )
    throw new RadarHttpError(415, "json_required");
  if (Number(request.headers.get("content-length") ?? 0) > max)
    throw new RadarHttpError(413, "payload_too_large");
  const reader = request.body?.getReader();
  if (!reader) throw new RadarHttpError(400, "body_required");
  const chunks: Uint8Array[] = [];
  let count = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    count += value.length;
    if (count > max) {
      await reader.cancel();
      throw new RadarHttpError(413, "payload_too_large");
    }
    chunks.push(value);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new RadarHttpError(400, "invalid_json");
  }
}
