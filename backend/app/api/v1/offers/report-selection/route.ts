import { authenticateDevice } from "@/src/device-auth";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json().catch(() => null) as Record<string, unknown> | null;
  const localOfferId = String(body?.local_offer_id ?? "").trim().slice(0, 100);
  const selected = body?.selected === true;
  if (!localOfferId) return Response.json({ error: "local_offer_id_required" }, { status: 400 });

  const supabase = adminSupabase();
  const found = await supabase
    .from("ride_offers")
    .select("id,journey_id")
    .eq("driver_id", auth.driverId)
    .eq("device_id", auth.deviceId)
    .eq("local_offer_id", localOfferId)
    .maybeSingle();

  if (found.error) return Response.json({ error: found.error.message }, { status: 500 });
  if (!found.data) return Response.json({ error: "offer_not_found" }, { status: 404 });

  if (selected) {
    let clear = supabase
      .from("ride_offers")
      .update({ report_selected: false, report_selected_at: null })
      .eq("driver_id", auth.driverId)
      .eq("report_selected", true);
    clear = found.data.journey_id
      ? clear.eq("journey_id", found.data.journey_id)
      : clear.eq("device_id", auth.deviceId).is("journey_id", null);
    const cleared = await clear;
    if (cleared.error) return Response.json({ error: cleared.error.message }, { status: 500 });
  }

  const updated = await supabase
    .from("ride_offers")
    .update({
      report_selected: selected,
      report_selected_at: selected ? new Date().toISOString() : null,
    })
    .eq("id", found.data.id)
    .eq("driver_id", auth.driverId)
    .select("id,report_selected,report_selected_at")
    .single();

  if (updated.error) return Response.json({ error: updated.error.message }, { status: 500 });
  return Response.json({ ok: true, selection: updated.data });
}
