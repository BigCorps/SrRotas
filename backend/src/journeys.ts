import { adminSupabase } from "./supabase";
import { fetchOffers, summarizeOffers } from "./analytics";

export type JourneyRow = {
  id: string;
  driver_id: string;
  device_id: string | null;
  platform: string;
  started_at: string;
  ended_at: string | null;
  end_reason: string | null;
  created_at: string;
};

export async function startJourney(driverId: string, deviceId: string, input: Record<string, unknown>) {
  const requestedId = String(input.journey_id ?? "").trim();
  const startedAt = input.started_at ? new Date(String(input.started_at)).toISOString() : new Date().toISOString();
  const supabase = adminSupabase();

  if (requestedId) {
    const existing = await supabase
      .from("driver_journeys")
      .select("id,driver_id,device_id,platform,started_at,ended_at,end_reason,created_at")
      .eq("id", requestedId)
      .maybeSingle();
    if (existing.error) throw new Error(existing.error.message);
    if (existing.data) {
      if (String(existing.data.driver_id) !== driverId) throw new Error("journey_id_conflict");
      return existing.data as JourneyRow;
    }
  }

  const row: Record<string, unknown> = {
    driver_id: driverId, device_id: deviceId, platform: String(input.platform ?? "uber").slice(0, 30), started_at: startedAt,
  };
  if (requestedId) row.id = requestedId;
  const { data, error } = await supabase
    .from("driver_journeys")
    .insert(row)
    .select("id,driver_id,device_id,platform,started_at,ended_at,end_reason,created_at")
    .single();
  if (error) throw new Error(error.message);
  return data as JourneyRow;
}

export async function endJourney(driverId: string, journeyId: string, input: Record<string, unknown>) {
  const endedAt = input.ended_at ? new Date(String(input.ended_at)).toISOString() : new Date().toISOString();
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .update({ ended_at: endedAt, end_reason: String(input.end_reason ?? "user_or_system").slice(0, 120) })
    .eq("driver_id", driverId)
    .eq("id", journeyId)
    .select("id,driver_id,device_id,platform,started_at,ended_at,end_reason,created_at")
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!data) throw new Error("journey_not_found");
  return data as JourneyRow;
}

export async function listJourneys(driverId: string, limit = 30) {
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .select("id,driver_id,device_id,platform,started_at,ended_at,end_reason,created_at")
    .eq("driver_id", driverId)
    .order("started_at", { ascending: false })
    .limit(Math.max(1, Math.min(limit, 100)));
  if (error) throw new Error(error.message);
  return (data ?? []) as JourneyRow[];
}

export async function currentJourney(driverId: string) {
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .select("id,driver_id,device_id,platform,started_at,ended_at,end_reason,created_at")
    .eq("driver_id", driverId)
    .is("ended_at", null)
    .order("started_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw new Error(error.message);
  return (data ?? null) as JourneyRow | null;
}

export async function journeySummary(driverId: string, journeyId: string) {
  const { data: journey, error } = await adminSupabase()
    .from("driver_journeys")
    .select("id,driver_id,device_id,platform,started_at,ended_at,end_reason,created_at")
    .eq("driver_id", driverId)
    .eq("id", journeyId)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!journey) throw new Error("journey_not_found");
  const found = await fetchOffers(driverId, { journeyId, limit: 500 });
  return {
    journey,
    summary: summarizeOffers(found.offers),
    note: "As métricas representam ofertas observadas durante a jornada; não comprovam corridas aceitas ou concluídas.",
  };
}
