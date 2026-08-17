import { authenticateDevice } from "@/src/device-auth";
import { adminSupabase } from "@/src/supabase";
import { normalizeDisplayName } from "@/src/account";

export const runtime = "nodejs";

export async function GET(request: Request) {
  const ctx = await authenticateDevice(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const { data, error } = await adminSupabase()
    .from("drivers")
    .select("id, display_name, email, onboarding_completed, auth_user_id")
    .eq("id", ctx.driverId)
    .single();
  if (error) return Response.json({ error: error.message }, { status: 500 });

  return Response.json({
    ok: true,
    driver_id: data.id,
    display_name: data.display_name ?? "Motorista",
    email: data.email ?? "",
    onboarding_completed: Boolean(data.onboarding_completed),
    legacy: !data.auth_user_id,
  });
}

export async function PATCH(request: Request) {
  const ctx = await authenticateDevice(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json().catch(() => ({}));
  const update: Record<string, unknown> = { updated_at: new Date().toISOString() };

  if ("display_name" in body) update.display_name = normalizeDisplayName(body?.display_name);
  if ("onboarding_completed" in body) update.onboarding_completed = Boolean(body?.onboarding_completed);

  const { data, error } = await adminSupabase()
    .from("drivers")
    .update(update)
    .eq("id", ctx.driverId)
    .select("id, display_name, email, onboarding_completed")
    .single();

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true, ...data });
}
