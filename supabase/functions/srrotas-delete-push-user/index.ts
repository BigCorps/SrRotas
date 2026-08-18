import { serviceAuthorized } from "../_shared/service-auth.ts";

const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ONESIGNAL_APP_ID = Deno.env.get("ONESIGNAL_APP_ID") ?? "";
const ONESIGNAL_APP_API_KEY = Deno.env.get("ONESIGNAL_APP_API_KEY") ?? "";

function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}
function uuid(value: unknown) {
  const candidate = String(value ?? "").trim();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(candidate) ? candidate : null;
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json({ success:false,error:"method_not_allowed" }, 405);
  if (!serviceAuthorized(request, SERVICE_ROLE_KEY)) return json({ success:false,error:"unauthorized" }, 401);
  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const driverId = uuid(body.driver_id);
  if (!driverId) return json({ success:false,error:"invalid_driver_id" }, 400);

  // Account deletion must not be blocked while OneSignal/FCM setup is pending.
  if (!uuid(ONESIGNAL_APP_ID) || !ONESIGNAL_APP_API_KEY) {
    return json({ success:true,skipped:true,reason:"onesignal_not_configured" });
  }

  const response = await fetch(`https://api.onesignal.com/apps/${ONESIGNAL_APP_ID}/users/by/external_id/${encodeURIComponent(driverId)}`, {
    method: "DELETE",
    headers: { Authorization: `Key ${ONESIGNAL_APP_API_KEY}` },
    signal: AbortSignal.timeout(15000),
  });
  if (response.status === 404) return json({ success:true,already_deleted:true });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok && response.status !== 202) return json({ success:false,error:"onesignal_delete_failed",details:payload }, 502);
  return json({ success:true,accepted:true });
});
