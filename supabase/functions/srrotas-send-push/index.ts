import { createClient } from "npm:@supabase/supabase-js@2.110.9";
import { serviceAuthorized } from "../_shared/service-auth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ONESIGNAL_APP_ID = Deno.env.get("ONESIGNAL_APP_ID") ?? "";
const ONESIGNAL_APP_API_KEY = Deno.env.get("ONESIGNAL_APP_API_KEY") ?? "";
const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

type Category = "operational" | "journey_summary" | "sync_alert" | "product_update" | "test";
type PreferenceRow = {
  operational_enabled: boolean;
  journey_summary_enabled: boolean;
  sync_alerts_enabled: boolean;
  product_updates_enabled: boolean;
};

function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}

function uuid(value: unknown) {
  const candidate = String(value ?? "").trim();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(candidate) ? candidate : null;
}

function category(value: unknown): Category | null {
  const candidate = String(value ?? "").trim();
  return ["operational","journey_summary","sync_alert","product_update","test"].includes(candidate) ? candidate as Category : null;
}

function enabledFor(prefs: PreferenceRow | null, cat: Category) {
  if (cat === "test") return true;
  if (!prefs) return cat !== "product_update";
  if (cat === "operational") return prefs.operational_enabled;
  if (cat === "journey_summary") return prefs.journey_summary_enabled;
  if (cat === "sync_alert") return prefs.sync_alerts_enabled;
  return prefs.product_updates_enabled;
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json({ success:false,error:"method_not_allowed" }, 405);
  if (!serviceAuthorized(request, SERVICE_ROLE_KEY)) return json({ success:false,error:"unauthorized" }, 401);
  if (!uuid(ONESIGNAL_APP_ID) || !ONESIGNAL_APP_API_KEY) return json({ success:false,error:"onesignal_not_configured" }, 503);

  try {
    const input = await request.json().catch(() => ({})) as Record<string, unknown>;
    const driverId = uuid(input.driver_id);
    const cat = category(input.category);
    const title = String(input.title ?? "").trim().slice(0, 100);
    const body = String(input.body ?? "").trim().slice(0, 400);
    const dedupeKey = String(input.dedupe_key ?? "").trim().slice(0, 180) || null;
    const data = input.data && typeof input.data === "object" && !Array.isArray(input.data)
      ? input.data as Record<string, unknown>
      : {};

    if (!driverId || !cat || !title || !body) return json({ success:false,error:"invalid_notification" }, 400);

    const pref = await admin.from("notification_preferences")
      .select("operational_enabled,journey_summary_enabled,sync_alerts_enabled,product_updates_enabled")
      .eq("driver_id", driverId).maybeSingle();
    if (pref.error) return json({ success:false,error:pref.error.message }, 500);

    if (!enabledFor(pref.data as PreferenceRow | null, cat)) {
      return json({ success:true,skipped:true,reason:"preference_disabled" });
    }

    if (dedupeKey) {
      const existing = await admin.from("notification_deliveries")
        .select("id,status,onesignal_message_id")
        .eq("driver_id", driverId).eq("dedupe_key", dedupeKey).maybeSingle();
      if (existing.error) return json({ success:false,error:existing.error.message }, 500);
      if (existing.data) return json({ success:true,duplicate:true,...existing.data });
    }

    const queued = await admin.from("notification_deliveries").insert({
      driver_id: driverId, category: cat, title, body, dedupe_key: dedupeKey, status: "queued",
      metadata: { route: String(data.route ?? ""), source: String(data.source ?? "") },
    }).select("id,onesignal_idempotency_key").single();

    if (queued.error) {
      if (queued.error.code === "23505" && dedupeKey) return json({ success:true,duplicate:true });
      return json({ success:false,error:queued.error.message }, 500);
    }

    const response = await fetch("https://api.onesignal.com/notifications", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Key ${ONESIGNAL_APP_API_KEY}`,
      },
      body: JSON.stringify({
        app_id: ONESIGNAL_APP_ID,
        include_aliases: { external_id: [driverId] },
        target_channel: "push",
        headings: { en: title },
        contents: { en: body },
        data: {
          category: cat,
          route: String(data.route ?? ""),
          source: String(data.source ?? "sr-rotas"),
        },
        idempotency_key: queued.data.onesignal_idempotency_key,
      }),
      signal: AbortSignal.timeout(20000),
    });

    const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
    if (!response.ok) {
      await admin.from("notification_deliveries").update({
        status:"failed", error_code:`onesignal_http_${response.status}`, metadata:{ response: payload }, 
      }).eq("id", queued.data.id);
      return json({ success:false,error:"onesignal_send_failed",details:payload }, 502);
    }

    const messageId = String(payload.id ?? "").trim() || null;
    await admin.from("notification_deliveries").update({
      status:"sent", onesignal_message_id:messageId, sent_at:new Date().toISOString(),
    }).eq("id", queued.data.id);

    return json({ success:true,message_id:messageId,delivery_id:queued.data.id });
  } catch (error) {
    return json({ success:false,error:"unexpected_error",message:error instanceof Error ? error.message : String(error) }, 500);
  }
});
