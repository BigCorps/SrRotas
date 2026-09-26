import { canAccessAdminOps } from "@/src/admin-access";
import { importActor } from "@/src/admin-imports";
import { allowSecurityAction } from "@/src/security-rate-limit";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function validUuid(value: unknown) {
  return /^[0-9a-f-]{36}$/i.test(String(value || ""));
}

async function requireAdmin(request: Request) {
  const actor = await importActor(request);
  if (!actor) return { actor: null, response: Response.json({ error: "unauthorized" }, { status: 401 }) };
  if (!actor.allowed || !canAccessAdminOps(actor.email)) {
    return { actor, response: Response.json({ error: "admin_ops_forbidden" }, { status: 403 }) };
  }
  const allowed = await allowSecurityAction(request, "admin-support-action", actor.email, 30, 900).catch(() => false);
  if (!allowed) return { actor, response: Response.json({ error: "rate_limited" }, { status: 429 }) };
  return { actor, response: null };
}

export async function POST(request: Request) {
  const checked = await requireAdmin(request);
  if (checked.response) return checked.response;

  const body = await request.json().catch(() => ({}));
  const action = String(body?.action || "");
  const driverId = String(body?.driver_id || "");
  if (!validUuid(driverId)) return Response.json({ error: "invalid_driver_id" }, { status: 400 });

  const supabase: any = adminSupabase();
  const driver = await supabase.from("drivers").select("id,display_name,email").eq("id", driverId).maybeSingle();
  if (driver.error) return Response.json({ error: driver.error.message }, { status: 500 });
  if (!driver.data) return Response.json({ error: "driver_not_found" }, { status: 404 });

  try {
    if (action === "device_set_revoked") {
      const deviceId = String(body?.device_id || "");
      const revoked = Boolean(body?.revoked);
      const expected = revoked ? "REVOGAR_APARELHO" : "REATIVAR_APARELHO";
      if (!validUuid(deviceId) || body?.confirmation !== expected) {
        return Response.json({ error: "confirmation_required", expected }, { status: 400 });
      }
      const result = await supabase.from("driver_devices")
        .update({ revoked })
        .eq("id", deviceId).eq("driver_id", driverId)
        .select("id,name,revoked,last_seen_at,created_at").maybeSingle();
      if (result.error) throw result.error;
      if (!result.data) return Response.json({ error: "device_not_found" }, { status: 404 });
      console.info("sr_admin_action", { admin: checked.actor?.email, action, driver_id: driverId, device_id: deviceId, revoked });
      return Response.json({ ok: true, action, driver: driver.data, device: result.data });
    }

    if (action === "trial_extend") {
      const days = Number(body?.days);
      if (![1, 7, 30].includes(days) || body?.confirmation !== "ESTENDER_TRIAL") {
        return Response.json({ error: "confirmation_required", expected: "ESTENDER_TRIAL" }, { status: 400 });
      }
      const current = await supabase.from("driver_trials")
        .select("driver_id,trial_started_at,trial_ends_at,ai_credits_granted,updated_at")
        .eq("driver_id", driverId).maybeSingle();
      if (current.error) throw current.error;
      if (!current.data?.trial_started_at) return Response.json({ error: "trial_not_started" }, { status: 409 });
      const oldEnd = current.data.trial_ends_at ? new Date(current.data.trial_ends_at) : new Date();
      const base = oldEnd.getTime() > Date.now() ? oldEnd : new Date();
      const newEnd = new Date(base.getTime() + days * 24 * 60 * 60 * 1000).toISOString();
      const result = await supabase.from("driver_trials")
        .update({ trial_ends_at: newEnd, updated_at: new Date().toISOString() })
        .eq("driver_id", driverId)
        .select("driver_id,trial_started_at,trial_ends_at,ai_credits_granted,updated_at").maybeSingle();
      if (result.error) throw result.error;
      console.info("sr_admin_action", { admin: checked.actor?.email, action, driver_id: driverId, days, new_end: newEnd });
      return Response.json({ ok: true, action, driver: driver.data, trial: result.data });
    }

    if (action === "revoke_web_sessions") {
      if (body?.confirmation !== "ENCERRAR_SESSOES_WEB") {
        return Response.json({ error: "confirmation_required", expected: "ENCERRAR_SESSOES_WEB" }, { status: 400 });
      }
      const result = await supabase.from("billing_web_sessions").delete().eq("driver_id", driverId);
      if (result.error) throw result.error;
      console.info("sr_admin_action", { admin: checked.actor?.email, action, driver_id: driverId });
      return Response.json({ ok: true, action, driver: driver.data });
    }

    if (action === "revoke_mcp_tokens") {
      if (body?.confirmation !== "REVOGAR_MCP") {
        return Response.json({ error: "confirmation_required", expected: "REVOGAR_MCP" }, { status: 400 });
      }
      const result = await supabase.from("mcp_access_tokens")
        .update({ revoked: true })
        .eq("driver_id", driverId).eq("revoked", false);
      if (result.error) throw result.error;
      console.info("sr_admin_action", { admin: checked.actor?.email, action, driver_id: driverId });
      return Response.json({ ok: true, action, driver: driver.data });
    }

    return Response.json({ error: "unsupported_admin_action" }, { status: 400 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "admin_action_failed";
    return Response.json({ error: message }, { status: 500 });
  }
}
