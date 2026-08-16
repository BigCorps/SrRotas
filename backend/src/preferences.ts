import { adminSupabase } from "./supabase";

export type DriverPreferences = {
  min_per_km: number;
  min_per_hour: number;
  min_fare: number;
  max_pickup_km: number;
  min_profit: number;
  cost_per_km: number;
  timezone: string;
};

export const DEFAULT_PREFERENCES: DriverPreferences = {
  min_per_km: 1.8,
  min_per_hour: 35,
  min_fare: 0,
  max_pickup_km: 5,
  min_profit: 0,
  cost_per_km: 0.85,
  timezone: "America/Sao_Paulo",
};

function numberOr(value: unknown, fallback: number, min = 0, max = 100000) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback;
}

export async function ensurePreferences(driverId: string) {
  const supabase = adminSupabase();
  const { data, error } = await supabase
    .from("driver_preferences")
    .select("min_per_km,min_per_hour,min_fare,max_pickup_km,min_profit,cost_per_km,timezone")
    .eq("driver_id", driverId)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (data) return data as DriverPreferences;

  const created = await supabase
    .from("driver_preferences")
    .insert({ driver_id: driverId, ...DEFAULT_PREFERENCES })
    .select("min_per_km,min_per_hour,min_fare,max_pickup_km,min_profit,cost_per_km,timezone")
    .single();
  if (created.error) throw new Error(created.error.message);
  return created.data as DriverPreferences;
}

export async function updatePreferences(driverId: string, input: Record<string, unknown>) {
  const current = await ensurePreferences(driverId);
  const row: DriverPreferences = {
    min_per_km: numberOr(input.min_per_km, Number(current.min_per_km), 0, 1000),
    min_per_hour: numberOr(input.min_per_hour, Number(current.min_per_hour), 0, 10000),
    min_fare: numberOr(input.min_fare, Number(current.min_fare), 0, 10000),
    max_pickup_km: numberOr(input.max_pickup_km, Number(current.max_pickup_km), 0, 1000),
    min_profit: numberOr(input.min_profit, Number(current.min_profit), 0, 10000),
    cost_per_km: numberOr(input.cost_per_km, Number(current.cost_per_km), 0, 1000),
    timezone: String(input.timezone ?? current.timezone ?? DEFAULT_PREFERENCES.timezone).slice(0, 80),
  };
  const { data, error } = await adminSupabase()
    .from("driver_preferences")
    .upsert({ driver_id: driverId, ...row, updated_at: new Date().toISOString() }, { onConflict: "driver_id" })
    .select("min_per_km,min_per_hour,min_fare,max_pickup_km,min_profit,cost_per_km,timezone")
    .single();
  if (error) throw new Error(error.message);
  return data as DriverPreferences;
}
