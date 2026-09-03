import { adminSupabase } from "./supabase";

const ENERGY_TYPES = new Set(["fuel", "electric"]);
const UNITS = new Set(["liter", "kwh"]);

function numeric(value: unknown, max: number) {
  if (value === null || value === undefined || value === "") return null;
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0 || n > max) throw new Error("invalid_numeric_value");
  return Math.round(n * 1000) / 1000;
}

function timestamp(value: unknown) {
  if (value === null || value === undefined || String(value).trim() === "") return new Date().toISOString();
  const d = new Date(String(value));
  if (!Number.isFinite(d.getTime())) throw new Error("invalid_timestamp");
  return d.toISOString();
}

async function ownedJourney(driverId: string, journeyId: string) {
  const result = await adminSupabase()
    .from("driver_journeys")
    .select("id,started_at,ended_at")
    .eq("driver_id", driverId)
    .eq("id", journeyId)
    .maybeSingle();
  if (result.error) throw new Error(result.error.message);
  if (!result.data) throw new Error("journey_not_found");
  return result.data;
}

export async function saveJourneyMetric(driverId: string, input: Record<string, unknown>) {
  const journeyId = String(input.journey_id ?? "").trim();
  if (!journeyId) throw new Error("journey_id_required");
  await ownedJourney(driverId, journeyId);

  const startKm = numeric(input.odometer_start_km, 9_999_999.9);
  const endKm = numeric(input.odometer_end_km, 9_999_999.9);
  if (startKm === null && endKm === null) throw new Error("odometer_required");
  if (startKm !== null && endKm !== null && endKm < startKm) throw new Error("odometer_end_before_start");

  const existing = await adminSupabase()
    .from("journey_vehicle_metrics")
    .select("odometer_start_km,odometer_end_km")
    .eq("driver_id", driverId)
    .eq("journey_id", journeyId)
    .maybeSingle();
  if (existing.error) throw new Error(existing.error.message);

  const finalStart = startKm ?? numeric(existing.data?.odometer_start_km, 9_999_999.9);
  const finalEnd = endKm ?? numeric(existing.data?.odometer_end_km, 9_999_999.9);
  if (finalStart !== null && finalEnd !== null && finalEnd < finalStart) throw new Error("odometer_end_before_start");

  const saved = await adminSupabase()
    .from("journey_vehicle_metrics")
    .upsert({
      driver_id: driverId,
      journey_id: journeyId,
      odometer_start_km: finalStart,
      odometer_end_km: finalEnd,
      updated_at: timestamp(input.updated_at),
    }, { onConflict: "journey_id" })
    .select("journey_id,odometer_start_km,odometer_end_km,distance_km,updated_at")
    .single();
  if (saved.error) throw new Error(saved.error.message);
  return saved.data;
}

export async function saveEnergyEntry(driverId: string, deviceId: string, input: Record<string, unknown>) {
  const journeyId = String(input.journey_id ?? "").trim();
  const clientEntryId = String(input.client_entry_id ?? "").trim();
  const energyType = String(input.energy_type ?? "").trim().toLowerCase();
  const unit = String(input.unit ?? "").trim().toLowerCase();
  if (!journeyId || !clientEntryId) throw new Error("energy_fields_required");
  if (!ENERGY_TYPES.has(energyType) || !UNITS.has(unit)) throw new Error("invalid_energy_type");
  if (energyType === "fuel" && unit !== "liter") throw new Error("invalid_energy_unit");
  if (energyType === "electric" && unit !== "kwh") throw new Error("invalid_energy_unit");
  await ownedJourney(driverId, journeyId);

  const amountPaid = numeric(input.amount_paid, 100_000);
  const quantity = numeric(input.quantity, 10_000);
  if ((amountPaid ?? 0) <= 0 && (quantity ?? 0) <= 0) throw new Error("energy_value_required");

  const saved = await adminSupabase()
    .from("journey_energy_entries")
    .upsert({
      driver_id: driverId,
      device_id: deviceId,
      journey_id: journeyId,
      client_entry_id: clientEntryId.slice(0, 100),
      energy_type: energyType,
      amount_paid: amountPaid,
      quantity,
      unit,
      fuel_type: String(input.fuel_type ?? "").trim().slice(0, 40) || null,
      recorded_at: timestamp(input.recorded_at),
      updated_at: new Date().toISOString(),
    }, { onConflict: "driver_id,client_entry_id" })
    .select("client_entry_id,journey_id,energy_type,amount_paid,quantity,unit,fuel_type,recorded_at,updated_at")
    .single();
  if (saved.error) throw new Error(saved.error.message);
  return saved.data;
}

export async function listJourneyMetrics(driverId: string, days = 30) {
  const safeDays = Math.max(1, Math.min(days || 30, 90));
  const from = new Date(Date.now() - safeDays * 86_400_000).toISOString();
  const journeys = await adminSupabase()
    .from("driver_journeys")
    .select("id")
    .eq("driver_id", driverId)
    .gte("started_at", from)
    .order("started_at", { ascending: false })
    .limit(150);
  if (journeys.error) throw new Error(journeys.error.message);
  const ids = (journeys.data ?? []).map((row: any) => String(row.id)).filter(Boolean);
  if (!ids.length) return { metrics: [], energy_entries: [] };

  const [metrics, energy] = await Promise.all([
    adminSupabase()
      .from("journey_vehicle_metrics")
      .select("journey_id,odometer_start_km,odometer_end_km,distance_km,updated_at")
      .eq("driver_id", driverId)
      .in("journey_id", ids),
    adminSupabase()
      .from("journey_energy_entries")
      .select("client_entry_id,journey_id,energy_type,amount_paid,quantity,unit,fuel_type,recorded_at,updated_at")
      .eq("driver_id", driverId)
      .in("journey_id", ids)
      .order("recorded_at", { ascending: true }),
  ]);
  if (metrics.error) throw new Error(metrics.error.message);
  if (energy.error) throw new Error(energy.error.message);
  return { metrics: metrics.data ?? [], energy_entries: energy.data ?? [] };
}
