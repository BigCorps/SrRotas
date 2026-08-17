import { adminSupabase } from "@/src/supabase";
import { createDeviceSession, createDriverForAuthUser, normalizeDeviceName, normalizeDisplayName, normalizeEmail } from "@/src/account";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const email = normalizeEmail(body?.email);
  const password = String(body?.password ?? "");
  const displayName = normalizeDisplayName(body?.display_name);
  const deviceName = normalizeDeviceName(body?.device_name);

  if (!email || !email.includes("@")) return Response.json({ error: "invalid_email", message: "Informe um e-mail válido." }, { status: 400 });
  if (password.length < 8) return Response.json({ error: "weak_password", message: "A senha precisa ter pelo menos 8 caracteres." }, { status: 400 });

  const supabase = adminSupabase();
  const existing = await supabase.from("drivers").select("id").eq("email", email).maybeSingle();
  if (existing.error) return Response.json({ error: existing.error.message }, { status: 500 });
  if (existing.data) return Response.json({ error: "email_in_use", message: "Este e-mail já possui uma conta." }, { status: 409 });

  // Alpha fechado: cria usuário já confirmado para que o fluxo de testes não dependa
  // da configuração de SMTP. Antes da produção, 0.12 troca para verificação de e-mail.
  const created = await supabase.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { display_name: displayName },
  });
  if (created.error || !created.data.user) {
    const message = created.error?.message || "account_create_failed";
    return Response.json({ error: "account_create_failed", message }, { status: 400 });
  }

  try {
    const driver = await createDriverForAuthUser(created.data.user.id, email, displayName);
    const session = await createDeviceSession(String(driver.id), deviceName);
    return Response.json({
      ok: true,
      driver_id: driver.id,
      device_id: session.deviceId,
      device_token: session.token,
      email: driver.email ?? email,
      display_name: driver.display_name ?? displayName,
    }, { status: 201 });
  } catch (error) {
    await supabase.auth.admin.deleteUser(created.data.user.id).catch(() => undefined);
    return Response.json({ error: error instanceof Error ? error.message : "account_driver_failed" }, { status: 500 });
  }
}
