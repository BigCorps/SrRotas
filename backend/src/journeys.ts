import { adminSupabase } from "./supabase";
import { fetchOffers, summarizeOffers } from "./analytics";

export type JourneyState = "ACTIVE" | "PAUSED" | "ENDED";
export type RideOutcomeStatus = "OFFERED" | "DOING_RIDE" | "COMPLETED" | "NOT_COMPLETED" | "CANCELLED";

export type JourneyRow = {
  id: string;
  driver_id: string;
  device_id: string | null;
  platform: string;
  started_at: string;
  ended_at: string | null;
  end_reason: string | null;
  state: JourneyState;
  state_updated_at: string;
  created_at: string;
};

const JOURNEY_COLUMNS = "id,driver_id,device_id,platform,started_at,ended_at,end_reason,state,state_updated_at,created_at";
const VALID_STATES = new Set<JourneyState>(["ACTIVE", "PAUSED", "ENDED"]);
const VALID_EVENTS = new Set(["start", "pause", "resume", "end"]);
const VALID_OUTCOMES = new Set<RideOutcomeStatus>(["OFFERED", "DOING_RIDE", "COMPLETED", "NOT_COMPLETED", "CANCELLED"]);
const VALID_EXPOSURE_REASONS = new Set(["offer_observed", "ride_started", "pause", "journey_end", "cell_changed", "location_unavailable", "not_available", "service_destroyed", "unknown"]);

function iso(value: unknown, fallbackNow = false) {
  if (value === null || value === undefined || String(value).trim() === "") {
    if (fallbackNow) return new Date().toISOString();
    return null;
  }
  const parsed = new Date(String(value));
  if (!Number.isFinite(parsed.getTime())) throw new Error("invalid_timestamp");
  return parsed.toISOString();
}

async function ownedJourney(driverId: string, journeyId: string) {
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .select(JOURNEY_COLUMNS)
    .eq("driver_id", driverId)
    .eq("id", journeyId)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!data) throw new Error("journey_not_found");
  return data as JourneyRow;
}

export async function startJourney(driverId: string, deviceId: string, input: Record<string, unknown>) {
  const requestedId = String(input.journey_id ?? "").trim();
  const startedAt = iso(input.started_at, true)!;
  const supabase = adminSupabase();

  if (requestedId) {
    const existing = await supabase.from("driver_journeys").select(JOURNEY_COLUMNS).eq("id", requestedId).maybeSingle();
    if (existing.error) throw new Error(existing.error.message);
    if (existing.data) {
      if (String(existing.data.driver_id) !== driverId) throw new Error("journey_id_conflict");
      return existing.data as JourneyRow;
    }
  }

  const row: Record<string, unknown> = {
    driver_id: driverId,
    device_id: deviceId,
    platform: String(input.platform ?? "uber").slice(0, 30),
    started_at: startedAt,
    state: "ACTIVE",
    state_updated_at: startedAt,
  };
  if (requestedId) row.id = requestedId;
  const { data, error } = await supabase.from("driver_journeys").insert(row).select(JOURNEY_COLUMNS).single();
  if (error) throw new Error(error.message);
  return data as JourneyRow;
}

export async function endJourney(driverId: string, journeyId: string, input: Record<string, unknown>) {
  const endedAt = iso(input.ended_at, true)!;
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .update({
      ended_at: endedAt,
      end_reason: String(input.end_reason ?? "user_or_system").slice(0, 120),
      state: "ENDED",
      state_updated_at: endedAt,
    })
    .eq("driver_id", driverId)
    .eq("id", journeyId)
    .select(JOURNEY_COLUMNS)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!data) throw new Error("journey_not_found");
  return data as JourneyRow;
}

export async function recordJourneyStateEvent(driverId: string, deviceId: string, input: Record<string, unknown>) {
  const clientEventId = String(input.client_event_id ?? "").trim();
  const journeyId = String(input.journey_id ?? "").trim();
  const eventType = String(input.event_type ?? "").trim().toLowerCase();
  const state = String(input.state ?? "").trim().toUpperCase() as JourneyState;
  if (!clientEventId || !journeyId) throw new Error("journey_event_fields_required");
  if (!VALID_EVENTS.has(eventType) || !VALID_STATES.has(state)) throw new Error("invalid_journey_state_event");
  const occurredAt = iso(input.occurred_at, true)!;
  await ownedJourney(driverId, journeyId);

  const supabase = adminSupabase();
  const inserted = await supabase
    .from("journey_state_events")
    .upsert({
      client_event_id: clientEventId,
      driver_id: driverId,
      device_id: deviceId,
      journey_id: journeyId,
      event_type: eventType,
      state,
      occurred_at: occurredAt,
    }, { onConflict: "driver_id,client_event_id", ignoreDuplicates: false })
    .select("id,client_event_id,journey_id,event_type,state,occurred_at")
    .single();
  if (inserted.error) throw new Error(inserted.error.message);

  const update: Record<string, unknown> = { state, state_updated_at: occurredAt };
  if (state === "ENDED") update.ended_at = occurredAt;
  const changed = await supabase
    .from("driver_journeys")
    .update(update)
    .eq("driver_id", driverId)
    .eq("id", journeyId)
    .lte("state_updated_at", occurredAt);
  if (changed.error) throw new Error(changed.error.message);
  return inserted.data;
}

export async function upsertRideOutcome(driverId: string, deviceId: string, input: Record<string, unknown>) {
  const localOfferId = String(input.local_offer_id ?? "").trim();
  const journeyId = String(input.journey_id ?? "").trim();
  const status = String(input.status ?? "").trim().toUpperCase() as RideOutcomeStatus;
  const revision = Math.max(1, Math.min(1_000_000, Number(input.revision ?? 1) || 1));
  if (!localOfferId || !journeyId) throw new Error("ride_outcome_fields_required");
  if (!VALID_OUTCOMES.has(status)) throw new Error("invalid_ride_outcome_status");
  await ownedJourney(driverId, journeyId);

  const supabase = adminSupabase();
  const existing = await supabase
    .from("ride_outcomes")
    .select("id,revision")
    .eq("driver_id", driverId)
    .eq("local_offer_id", localOfferId)
    .maybeSingle();
  if (existing.error) throw new Error(existing.error.message);
  if (existing.data && Number(existing.data.revision) > revision) {
    const current = await supabase.from("ride_outcomes").select("*").eq("id", existing.data.id).single();
    if (current.error) throw new Error(current.error.message);
    return current.data;
  }

  const linkedOffer = await supabase
    .from("ride_offers")
    .select("id")
    .eq("driver_id", driverId)
    .eq("local_offer_id", localOfferId)
    .order("observed_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (linkedOffer.error) throw new Error(linkedOffer.error.message);

  const row = {
    driver_id: driverId,
    device_id: deviceId,
    journey_id: journeyId,
    ride_offer_id: linkedOffer.data?.id ?? null,
    local_offer_id: localOfferId,
    status,
    started_at: iso(input.started_at),
    completed_at: iso(input.completed_at),
    cancelled_at: iso(input.cancelled_at),
    corrected_at: iso(input.corrected_at),
    source: String(input.source ?? "android").slice(0, 40),
    revision,
    updated_at: new Date().toISOString(),
  };
  const saved = await supabase.from("ride_outcomes").upsert(row, { onConflict: "driver_id,local_offer_id" }).select("*").single();
  if (saved.error) throw new Error(saved.error.message);
  return saved.data;
}

export async function saveRegionalExposure(driverId: string, deviceId: string, input: Record<string, unknown>) {
  const clientExposureId = String(input.client_exposure_id ?? "").trim();
  const journeyId = String(input.journey_id ?? "").trim();
  const cell = String(input.cell ?? "").trim();
  const closeReasonRaw = String(input.close_reason ?? "unknown").trim().toLowerCase();
  const closeReason = VALID_EXPOSURE_REASONS.has(closeReasonRaw) ? closeReasonRaw : "unknown";
  if (!clientExposureId || !journeyId || !/^g2:-?\d+:-?\d+$/.test(cell)) throw new Error("invalid_exposure_fields");
  const startedAt = iso(input.started_at)!;
  const endedAt = iso(input.ended_at)!;
  if (!startedAt || !endedAt || new Date(endedAt).getTime() < new Date(startedAt).getTime()) throw new Error("invalid_exposure_window");
  await ownedJourney(driverId, journeyId);
  const durationSeconds = Math.max(0, Math.min(86_400, Math.floor(Number(input.duration_seconds ?? 0) || 0)));
  const accuracy = input.location_accuracy_m === null || input.location_accuracy_m === undefined
    ? null
    : Math.max(0, Math.min(50_000, Number(input.location_accuracy_m)));

  const saved = await adminSupabase().from("zone_exposures").upsert({
    client_exposure_id: clientExposureId,
    driver_id: driverId,
    device_id: deviceId,
    journey_id: journeyId,
    cell,
    started_at: startedAt,
    ended_at: endedAt,
    duration_seconds: durationSeconds,
    close_reason: closeReason,
    next_offer_local_id: String(input.next_offer_local_id ?? "").trim() || null,
    location_accuracy_m: accuracy !== null && Number.isFinite(accuracy) ? accuracy : null,
  }, { onConflict: "driver_id,client_exposure_id" }).select("*").single();
  if (saved.error) throw new Error(saved.error.message);
  return saved.data;
}

export async function listJourneys(driverId: string, limit = 30) {
  const { data, error } = await adminSupabase().from("driver_journeys").select(JOURNEY_COLUMNS).eq("driver_id", driverId).order("started_at", { ascending: false }).limit(Math.max(1, Math.min(limit, 100)));
  if (error) throw new Error(error.message);
  return (data ?? []) as JourneyRow[];
}

export async function currentJourney(driverId: string) {
  const { data, error } = await adminSupabase().from("driver_journeys").select(JOURNEY_COLUMNS).eq("driver_id", driverId).is("ended_at", null).order("started_at", { ascending: false }).limit(1).maybeSingle();
  if (error) throw new Error(error.message);
  return (data ?? null) as JourneyRow | null;
}

export async function journeySummary(driverId: string, journeyId: string) {
  const journey = await ownedJourney(driverId, journeyId);
  const found = await fetchOffers(driverId, { journeyId, limit: 500 });
  const supabase = adminSupabase();
  const [rides, exposure] = await Promise.all([
    supabase.from("ride_outcomes").select("status").eq("driver_id", driverId).eq("journey_id", journeyId),
    supabase.from("zone_exposures").select("duration_seconds").eq("driver_id", driverId).eq("journey_id", journeyId),
  ]);
  if (rides.error) throw new Error(rides.error.message);
  if (exposure.error) throw new Error(exposure.error.message);
  const rideRows = (rides.data ?? []) as Array<{ status?: string | null }>;
  const rideSummary = rideRows.reduce<Record<string, number>>((acc, row) => {
    const key = String(row.status || "OFFERED");
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
  const exposureRows = (exposure.data ?? []) as Array<{ duration_seconds?: number | null }>;
  const exposureSeconds = exposureRows.reduce((sum: number, row) => sum + Math.max(0, Number(row.duration_seconds ?? 0)), 0);
  return {
    journey,
    summary: summarizeOffers(found.offers),
    ride_summary: rideSummary,
    exposure_seconds: exposureSeconds,
    note: "Ofertas observadas e corridas confirmadas são métricas diferentes. Corridas só contam como realizadas após marcação explícita do motorista.",
  };
}
