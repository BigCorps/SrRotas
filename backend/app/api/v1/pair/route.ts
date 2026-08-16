import { adminSupabase } from "@/src/supabase";
import { newToken, safeEqual, sha256 } from "@/src/security";
import { serverEnv } from "@/src/env";
import { ensurePreferences } from "@/src/preferences";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const code = String(body?.code ?? "").trim();
  const deviceName = String(body?.device_name ?? "Android").trim().slice(0, 120) || "Android";
  const env = serverEnv();
  if (!code || !safeEqual(code, env.pairingCode)) return Response.json({ error: "invalid_pairing_code" }, { status: 401 });

  const supabase = adminSupabase();
  let { data: driver, error: driverError } = await supabase
    .from("drivers")
    .select("id")
    .order("created_at", { ascending: true })
    .limit(1)
    .maybeSingle();
  if (driverError) return Response.json({ error: driverError.message }, { status: 500 });

  if (!driver) {
    const created = await supabase.from("drivers").insert({ display_name: "Motorista teste" }).select("id").single();
    if (created.error) return Response.json({ error: created.error.message }, { status: 500 });
    driver = created.data;
  }

  try {
    await ensurePreferences(String(driver.id));
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "preferences_failed" }, { status: 500 });
  }

  const token = newToken();
  const { data: device, error } = await supabase
    .from("driver_devices")
    .insert({ driver_id: driver.id, name: deviceName, token_hash: sha256(token) })
    .select("id")
    .single();
  if (error) return Response.json({ error: error.message }, { status: 500 });

  return Response.json({ ok: true, driver_id: driver.id, device_id: device.id, device_token: token });
}
