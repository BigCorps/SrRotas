import { resolveDriverAccess } from "@/src/access";
import { authenticateDevice } from "@/src/device-auth";
import { adminSupabase } from "@/src/supabase";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
function text(value: unknown, max: number) { return String(value ?? "").trim().slice(0, max); }
export async function GET(request: Request) {
  const device = await authenticateDevice(request);
  if (!device) return Response.json({ error: "unauthorized" }, { status: 401 });
  return Response.json({ ok: true, access: device.access }, { headers: { "Cache-Control": "no-store" } });
}
export async function POST(request: Request) {
  const device = await authenticateDevice(request);
  if (!device) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => ({}));
  const rawIdentity = text(body?.device_identity, 256);
  if (rawIdentity.length < 8) return Response.json({ error: "device_identity_required" }, { status: 400 });
  const deviceName = text(body?.device_name, 120) || "Android";
  const identityVersion = text(body?.identity_version, 60) || "android_ssaid_v1";
  const appVersionName = text(body?.app_version_name, 80) || null;
  const appVersionCodeRaw = Number(body?.app_version_code);
  const appVersionCode = Number.isFinite(appVersionCodeRaw) ? Math.trunc(appVersionCodeRaw) : null;
  const claimed = await adminSupabase().rpc("sr_claim_device_identity_v1", {
    p_driver_id: device.driverId,
    p_device_id: device.deviceId,
    p_identity_raw: rawIdentity,
    p_device_name: deviceName,
    p_identity_version: identityVersion,
    p_app_version_name: appVersionName,
    p_app_version_code: appVersionCode,
  });
  if (claimed.error) return Response.json({ error: "device_identity_claim_failed" }, { status: 500 });
  const access = await resolveDriverAccess(device.driverId, device.deviceId);
  return Response.json({ ok: true, claim: claimed.data, access }, { headers: { "Cache-Control": "no-store" } });
}
