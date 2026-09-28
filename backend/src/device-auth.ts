import { resolveDriverAccess, type DriverAccess } from "./access";
import { adminSupabase } from "./supabase";
import { bearerToken, sha256 } from "./security";

export type DeviceContext = { deviceId: string; driverId: string; access: DriverAccess };

export async function authenticateDevice(request: Request): Promise<DeviceContext | null> {
  const token = bearerToken(request);
  if (!token) return null;
  const hash = sha256(token);
  const supabase = adminSupabase();
  const { data, error } = await supabase
    .from("driver_devices")
    .select("id,driver_id")
    .eq("token_hash", hash)
    .eq("revoked", false)
    .maybeSingle();
  if (error || !data) return null;
  await supabase.from("driver_devices").update({ last_seen_at: new Date().toISOString() }).eq("id", data.id).then(() => undefined).catch(() => undefined);
  const access = await resolveDriverAccess(String(data.driver_id), String(data.id));
  return { deviceId: String(data.id), driverId: String(data.driver_id), access };
}
