import { adminSupabase } from "@/src/supabase";
import { createDeviceSession, createDriverForAuthUser, driverForAuthUser, normalizeDeviceName, normalizeDisplayName, normalizeEmail, passwordAuth } from "@/src/account";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const email = normalizeEmail(body?.email);
  const password = String(body?.password ?? "");
  const deviceName = normalizeDeviceName(body?.device_name);

  if (!email || !password) return Response.json({ error: "missing_credentials", message: "Informe e-mail e senha." }, { status: 400 });

  let user;
  try {
    user = await passwordAuth(email, password);
  } catch {
    return Response.json({ error: "invalid_credentials", message: "E-mail ou senha incorretos." }, { status: 401 });
  }

  try {
    let driver = await driverForAuthUser(user.id);
    if (!driver) {
      driver = await createDriverForAuthUser(user.id, email, normalizeDisplayName(user.user_metadata?.display_name));
    } else {
      await adminSupabase().from("drivers").update({ last_login_at: new Date().toISOString() }).eq("id", driver.id);
    }
    const session = await createDeviceSession(String(driver.id), deviceName);
    return Response.json({
      ok: true,
      driver_id: driver.id,
      device_id: session.deviceId,
      device_token: session.token,
      email: driver.email ?? email,
      display_name: driver.display_name ?? "Motorista",
      onboarding_completed: Boolean(driver.onboarding_completed),
    });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "login_session_failed" }, { status: 500 });
  }
}
