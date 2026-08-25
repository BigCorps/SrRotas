import { authenticateBillingActor } from "@/src/billing-auth";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Ofertas marcadas manualmente pelo ✓, sem inferir aceite/conclusão. */
export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const url = new URL(request.url);
  const limit = Math.max(1, Math.min(Number(url.searchParams.get("limit") || 100) || 100, 500));
  const { data, error } = await adminSupabase()
    .from("ride_offers")
    .select("id,local_offer_id,journey_id,observed_at,platform,service_type,fare,pickup_km,pickup_minutes,trip_km,trip_minutes,total_km,total_minutes,per_km,per_minute,per_hour,verdict,report_selected_at")
    .eq("driver_id", auth.driverId)
    .eq("report_selected", true)
    .order("report_selected_at", { ascending: false })
    .limit(limit);

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({
    selected_offers: data ?? [],
    note: "Seleção manual para relatório. Não prova aceite, conclusão ou faturamento.",
  });
}
