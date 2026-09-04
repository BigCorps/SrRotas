import { adminSupabase } from "./supabase";

function num(value: unknown) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function integer(value: unknown, max: number) {
  const n = num(value);
  if (n === null) return null;
  const rounded = Math.round(n);
  return rounded >= 0 && rounded <= max ? rounded : null;
}

function iso(value: unknown) {
  if (value === null || value === undefined || String(value).trim() === "") return null;
  const date = new Date(String(value));
  return Number.isFinite(date.getTime()) ? date.toISOString() : null;
}

function text(value: unknown, max = 180) {
  const normalized = String(value ?? "").trim();
  return normalized ? normalized.slice(0, max) : null;
}

function bounded(value: unknown, max: number) {
  const n = num(value);
  return n !== null && n >= 0 && n <= max ? Math.round(n * 1000) / 1000 : null;
}

function overlapMinutes(aStart: string, aEnd: string, bStart: string, bEnd: string) {
  const start = Math.max(new Date(aStart).getTime(), new Date(bStart).getTime());
  const end = Math.min(new Date(aEnd).getTime(), new Date(bEnd).getTime());
  return Math.max(0, (end - start) / 60_000);
}

async function resolveSessionJourney(
  driverId: string,
  input: Record<string, unknown>,
): Promise<string | null> {
  const startedAt = iso(input.started_at);
  const endedAt = iso(input.ended_at);
  if (!startedAt || !endedAt) return null;

  const requested = text(input.journey_id, 80);
  if (requested) {
    const exact = await adminSupabase()
      .from("driver_journeys")
      .select("id,started_at,ended_at")
      .eq("driver_id", driverId)
      .eq("id", requested)
      .maybeSingle();
    if (exact.error) throw new Error(exact.error.message);
    if (exact.data) {
      const end = exact.data.ended_at ?? endedAt;
      if (overlapMinutes(startedAt, endedAt, exact.data.started_at, end) >= 10) {
        return String(exact.data.id);
      }
    }
  }

  const windowFrom = new Date(new Date(startedAt).getTime() - 6 * 60 * 60_000).toISOString();
  const windowTo = new Date(new Date(endedAt).getTime() + 6 * 60 * 60_000).toISOString();
  const candidates = await adminSupabase()
    .from("driver_journeys")
    .select("id,started_at,ended_at")
    .eq("driver_id", driverId)
    .gte("started_at", windowFrom)
    .lte("started_at", windowTo)
    .order("started_at", { ascending: true })
    .limit(20);
  if (candidates.error) throw new Error(candidates.error.message);

  const sessionStart = new Date(startedAt).getTime();
  const sessionEnd = new Date(endedAt).getTime();
  const ranked = (candidates.data ?? []).map((row: any) => {
    const journeyStart = new Date(row.started_at).getTime();
    const journeyEnd = new Date(row.ended_at ?? endedAt).getTime();
    const overlap = overlapMinutes(startedAt, endedAt, row.started_at, row.ended_at ?? endedAt);
    const startDiff = Math.abs(journeyStart - sessionStart) / 60_000;
    const endDiff = Math.abs(journeyEnd - sessionEnd) / 60_000;
    const score = startDiff + endDiff - Math.min(120, overlap) * 0.55;
    return { id: String(row.id), overlap, startDiff, endDiff, score };
  })
    .filter((row: { overlap: number; startDiff: number; endDiff: number; score: number; id: string }) => row.overlap >= 10 && row.startDiff <= 240 && row.endDiff <= 240)
    .sort((a: { score: number }, b: { score: number }) => a.score - b.score);

  if (!ranked.length) return null;
  if (ranked.length > 1 && ranked[1].score - ranked[0].score < 20) return null;
  return ranked[0].id;
}

async function matchOffer(driverId: string, ride: Record<string, unknown>) {
  const status = text(ride.ride_status, 20) ?? "completed";
  const at = iso(ride.occurred_at);
  const fare = num(ride.fare);
  if (status !== "completed" || !at || fare === null || fare <= 0) return null;

  const center = new Date(at).getTime();
  const from = new Date(center - 45 * 60_000).toISOString();
  const to = new Date(center + 45 * 60_000).toISOString();
  let query = adminSupabase()
    .from("ride_offers")
    .select("id,journey_id,local_offer_id,service_type,observed_at,fare")
    .eq("driver_id", driverId)
    .gte("observed_at", from)
    .lte("observed_at", to)
    .gte("fare", fare - 0.011)
    .lte("fare", fare + 0.011);
  const service = text(ride.service_type, 30);
  if (service && service !== "unknown") query = query.eq("service_type", service);
  const { data, error } = await query.limit(3);
  if (error) throw new Error(error.message);
  const rows = data ?? [];
  return rows.length === 1 ? rows[0] : null;
}

function isMissingColumn(error: any, column: string) {
  const message = String(error?.message ?? "").toLowerCase();
  return message.includes(column.toLowerCase()) &&
    (message.includes("column") || message.includes("schema cache") || error?.code === "PGRST204");
}

export async function saveSessionImport(driverId: string, input: Record<string, unknown>) {
  const sourceKey = text(input.source_key, 120);
  if (!sourceKey) throw new Error("source_key_required");
  const journeyId = await resolveSessionJourney(driverId, input);
  const baseRow = {
    driver_id: driverId,
    source_key: sourceKey,
    captured_at: iso(input.captured_at) ?? new Date().toISOString(),
    started_at: iso(input.started_at),
    ended_at: iso(input.ended_at),
    earnings: bounded(input.earnings, 1_000_000),
    completed_trips: integer(input.completed_trips, 10_000),
    offered_trips: integer(input.offered_trips, 100_000),
    confidence: Math.max(0, Math.min(1, num(input.confidence) ?? 0)),
  };
  const extendedRow = {
    ...baseRow,
    journey_id: journeyId,
    observation: text(input.observation, 800),
  };

  let saved = await adminSupabase()
    .from("uber_session_imports")
    .upsert(extendedRow, { onConflict: "driver_id,source_key" })
    .select("*")
    .single();

  // Janela de implantação: se o código chegar antes da migration 0.26.2,
  // preserva o comportamento 0.26.1 em vez de derrubar a digitalização.
  if (saved.error && (isMissingColumn(saved.error, "journey_id") || isMissingColumn(saved.error, "observation"))) {
    saved = await adminSupabase()
      .from("uber_session_imports")
      .upsert(baseRow, { onConflict: "driver_id,source_key" })
      .select("*")
      .single();
  }
  if (saved.error) throw new Error(saved.error.message);
  return saved.data;
}

export async function saveCompletedRides(
  driverId: string,
  deviceId: string,
  input: Record<string, unknown>,
) {
  const rides = Array.isArray(input.rides) ? input.rides : [];
  if (!rides.length) throw new Error("rides_required");
  const saved: any[] = [];

  for (const raw of rides.slice(0, 100)) {
    const ride = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
    const sourceKey = text(ride.source_key, 120);
    const fare = bounded(ride.fare, 100_000);
    const rideStatus = (text(ride.ride_status, 20) ?? "completed").toLowerCase();
    if (!sourceKey || fare === null) continue;
    if (rideStatus !== "completed" && rideStatus !== "cancelled") continue;
    if (rideStatus === "completed" && fare <= 0) continue;

    const match = await matchOffer(driverId, ride);
    const baseRow = {
      driver_id: driverId,
      device_id: deviceId,
      source_key: sourceKey,
      captured_at: iso(ride.captured_at) ?? new Date().toISOString(),
      occurred_at: iso(ride.occurred_at),
      fare,
      service_type: text(ride.service_type, 40) ?? "unknown",
      pickup_label: text(ride.pickup_label),
      destination_label: text(ride.destination_label),
      confidence: Math.max(0, Math.min(1, num(ride.confidence) ?? 0)),
      matched_ride_offer_id: match?.id ?? null,
    };
    const extendedRow = {
      ...baseRow,
      duration_seconds: integer(ride.duration_seconds, 24 * 60 * 60),
      distance_km: bounded(ride.distance_km, 2_000),
      surge_amount: bounded(ride.surge_amount, 100_000),
      extra_amount: bounded(ride.extra_amount, 100_000),
      ride_status: rideStatus,
    };

    let upserted = await adminSupabase()
      .from("uber_completed_ride_imports")
      .upsert(extendedRow, { onConflict: "driver_id,source_key" })
      .select("*")
      .single();
    if (
      upserted.error &&
      ["duration_seconds", "distance_km", "surge_amount", "extra_amount", "ride_status"]
        .some((column) => isMissingColumn(upserted.error, column))
    ) {
      // Compatibilidade durante o deploy: canceladas exigem a migration porque
      // a constraint 0.26.1 não aceita tarifa zero.
      if (rideStatus === "cancelled" || fare <= 0) continue;
      upserted = await adminSupabase()
        .from("uber_completed_ride_imports")
        .upsert(baseRow, { onConflict: "driver_id,source_key" })
        .select("*")
        .single();
    }
    if (upserted.error) throw new Error(upserted.error.message);
    saved.push(upserted.data);

    // Só uma corrida concluída com correspondência única pode corrigir outcome.
    // Cancelamentos importados permanecem registros de histórico e não alteram
    // automaticamente uma oferta observada.
    if (rideStatus === "completed" && match?.journey_id && match?.local_offer_id) {
      const current = await adminSupabase()
        .from("ride_outcomes")
        .select("revision")
        .eq("driver_id", driverId)
        .eq("local_offer_id", match.local_offer_id)
        .maybeSingle();
      if (current.error) throw new Error(current.error.message);
      const revision = Math.max(1, Number(current.data?.revision ?? 0) + 1);
      const outcome = {
        driver_id: driverId,
        device_id: deviceId,
        journey_id: match.journey_id,
        ride_offer_id: match.id,
        local_offer_id: match.local_offer_id,
        status: "COMPLETED",
        completed_at: baseRow.occurred_at ?? baseRow.captured_at,
        corrected_at: new Date().toISOString(),
        source: "uber_history_ocr",
        revision,
        updated_at: new Date().toISOString(),
      };
      const result = await adminSupabase()
        .from("ride_outcomes")
        .upsert(outcome, { onConflict: "driver_id,local_offer_id" });
      if (result.error) throw new Error(result.error.message);
    }
  }
  return saved;
}
