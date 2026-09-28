import { authenticateBillingActor } from "@/src/billing-auth";
import { adminSupabase } from "@/src/supabase";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
type DriverDeviceDbRow = { id:string; name:string|null; revoked:boolean; last_seen_at:string|null; created_at:string; identity_key:string|null; identity_version:string|null; identity_bound_at:string|null; app_version_name:string|null; app_version_code:number|null };
export async function GET(request: Request) {
  const actor = await authenticateBillingActor(request);
  if (!actor) return Response.json({ error: "unauthorized" }, { status: 401 });
  const { data, error } = await adminSupabase().from("driver_devices").select("id,name,revoked,last_seen_at,created_at,identity_key,identity_version,identity_bound_at,app_version_name,app_version_code").eq("driver_id", actor.driverId).order("created_at", { ascending: false }).limit(100);
  if (error) return Response.json({ error: error.message }, { status: 500 });
  const rows=(data??[]) as DriverDeviceDbRow[];
  const boundRows=rows.filter(d=>Boolean(d.identity_key));
  const visibleRows=boundRows.length?boundRows:rows.filter(d=>!d.revoked).slice(0,2);
  const devices=visibleRows.map(d=>({ id:String(d.id), name:String(d.name||"Aparelho Android"), revoked:Boolean(d.revoked), last_seen_at:d.last_seen_at, created_at:d.created_at, identity_bound:Boolean(d.identity_key), identity_version:d.identity_version, identity_bound_at:d.identity_bound_at, app_version_name:d.app_version_name, app_version_code:d.app_version_code }));
  return Response.json({ devices, active_count:boundRows.filter(d=>!d.revoked).length, max_active_devices:actor.access.max_active_devices, identity_adoption:boundRows.length?"bound":"legacy_pending", legacy_session_rows:rows.filter(d=>!d.identity_key).length, access_state:actor.access.state, enforcement_mode:actor.access.enforcement_mode, note:boundRows.length?"Aparelhos consolidados pela identidade 1.0-B.":"Identidade 1.0-B ainda não vinculada neste aparelho." });
}
