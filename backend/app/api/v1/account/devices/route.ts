import { authenticateBillingActor } from "@/src/billing-auth";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type DriverDeviceDbRow = {
  id: string;
  name: string | null;
  revoked: boolean;
  last_seen_at: string | null;
  created_at: string;
};

type DriverDeviceView = {
  id: string;
  name: string;
  revoked: boolean;
  last_seen_at: string | null;
  created_at: string;
};

export async function GET(request: Request) {
  const actor = await authenticateBillingActor(request);
  if (!actor) return Response.json({ error: "unauthorized" }, { status: 401 });

  const { data, error } = await adminSupabase()
    .from("driver_devices")
    .select("id,name,revoked,last_seen_at,created_at")
    .eq("driver_id", actor.driverId)
    .order("created_at", { ascending: false })
    .limit(20);

  if (error) return Response.json({ error: error.message }, { status: 500 });

  const rows = (data ?? []) as DriverDeviceDbRow[];
  const devices: DriverDeviceView[] = rows.map((device: DriverDeviceDbRow) => ({
    id: String(device.id),
    name: String(device.name || "Aparelho Android"),
    revoked: Boolean(device.revoked),
    last_seen_at: device.last_seen_at,
    created_at: device.created_at,
  }));

  return Response.json({
    devices,
    active_count: devices.filter((device: DriverDeviceView) => !device.revoked).length,
    max_active_devices: 2,
    note: "A revogação Web será ativada junto com device identity + Access Resolver na 1.0-B/C.",
  });
}
