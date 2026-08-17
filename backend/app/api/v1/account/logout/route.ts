import { authenticateDevice } from "@/src/device-auth";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const ctx = await authenticateDevice(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const { error } = await adminSupabase()
    .from("driver_devices")
    .update({ revoked: true })
    .eq("id", ctx.deviceId);

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true });
}
