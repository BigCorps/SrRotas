import { adminSupabase } from "./supabase";

export type StrategyPreset = "popular" | "comfort" | "premium" | "custom";
export type AppTheme = "auto" | "light" | "dark";
export type HudThemeMode = "follow_app" | "light" | "dark";

export type DriverPreferences = {
  min_per_km: number;
  red_per_km_below: number;
  min_per_hour: number;
  red_per_hour_below: number;
  good_rating_from: number;
  red_rating_below: number;
  min_per_minute: number;
  red_per_minute_below: number;
  min_fare: number;
  max_pickup_km: number;
  max_pickup_minutes: number;
  min_profit: number;
  min_profit_per_hour: number;
  red_profit_per_hour_below: number;
  min_profit_percent: number;
  red_profit_percent_below: number;
  cost_per_km: number;
  timezone: string;
  collective_stats_opt_in: boolean;
  strategy_preset: StrategyPreset;
  app_theme: AppTheme;
  hud_theme_mode: HudThemeMode;
};

export const DEFAULT_PREFERENCES: DriverPreferences = {
  min_per_km: 1.5,
  red_per_km_below: 1.2,
  min_per_hour: 30,
  red_per_hour_below: 24,
  good_rating_from: 4.85,
  red_rating_below: 4.7,
  min_per_minute: 0.5,
  red_per_minute_below: 0.4,
  min_fare: 0,
  max_pickup_km: 4,
  max_pickup_minutes: 8,
  min_profit: 0,
  min_profit_per_hour: 0,
  red_profit_per_hour_below: 0,
  min_profit_percent: 0,
  red_profit_percent_below: 0,
  cost_per_km: 0.85,
  timezone: "America/Sao_Paulo",
  collective_stats_opt_in: false,
  strategy_preset: "popular",
  app_theme: "auto",
  hud_theme_mode: "follow_app",
};


const PRESET_VALUES: Record<Exclude<StrategyPreset, "custom">, Partial<DriverPreferences>> = {
  popular: {
    red_per_km_below: 1.2, min_per_km: 1.5,
    red_per_minute_below: 0.4, min_per_minute: 0.5,
    red_per_hour_below: 24, min_per_hour: 30,
  },
  comfort: {
    red_per_km_below: 1.5, min_per_km: 1.8,
    red_per_minute_below: 0.5, min_per_minute: 0.65,
    red_per_hour_below: 30, min_per_hour: 39,
  },
  premium: {
    red_per_km_below: 1.8, min_per_km: 2.2,
    red_per_minute_below: 0.65, min_per_minute: 0.85,
    red_per_hour_below: 39, min_per_hour: 51,
  },
};

const FIELDS = [
  "min_per_km","red_per_km_below","min_per_hour","red_per_hour_below",
  "good_rating_from","red_rating_below","min_per_minute","red_per_minute_below",
  "min_fare","max_pickup_km","max_pickup_minutes","min_profit","min_profit_per_hour",
  "red_profit_per_hour_below","min_profit_percent","red_profit_percent_below","cost_per_km",
  "timezone","collective_stats_opt_in","strategy_preset","app_theme","hud_theme_mode",
].join(",");

function numberOr(v: unknown, fallback: number, min = 0, max = 100000) {
  const n = Number(v);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback;
}
function boolOr(v: unknown, fallback: boolean) {
  if (typeof v === "boolean") return v;
  if (v === "true" || v === 1 || v === "1") return true;
  if (v === "false" || v === 0 || v === "0") return false;
  return fallback;
}
function enumOr<T extends string>(v: unknown, allowed: readonly T[], fallback: T): T {
  const text = String(v ?? "");
  return (allowed as readonly string[]).includes(text) ? text as T : fallback;
}

export async function ensurePreferences(driverId: string) {
  const supabase = adminSupabase();
  const { data, error } = await supabase.from("driver_preferences").select(FIELDS).eq("driver_id", driverId).maybeSingle();
  if (error) throw new Error(error.message);
  if (data) return data as DriverPreferences;
  const created = await supabase.from("driver_preferences").insert({ driver_id: driverId, ...DEFAULT_PREFERENCES }).select(FIELDS).single();
  if (created.error) throw new Error(created.error.message);
  return created.data as DriverPreferences;
}

export async function updatePreferences(driverId: string, input: Record<string, unknown>) {
  const c = await ensurePreferences(driverId);
  const requestedPreset = enumOr(
    input.strategy_preset,
    ["popular","comfort","premium","custom"] as const,
    c.strategy_preset || "custom",
  );
  const presetDefaults = requestedPreset === "custom" ? {} : PRESET_VALUES[requestedPreset];
  const fallback = <K extends keyof DriverPreferences>(key: K) =>
    (presetDefaults[key] ?? c[key]) as DriverPreferences[K];

  const row: DriverPreferences = {
    min_per_km: numberOr(input.min_per_km, Number(fallback("min_per_km")), 0, 1000),
    red_per_km_below: numberOr(input.red_per_km_below, Number(fallback("red_per_km_below")), 0, 1000),
    min_per_hour: numberOr(input.min_per_hour, Number(fallback("min_per_hour")), 0, 10000),
    red_per_hour_below: numberOr(input.red_per_hour_below, Number(fallback("red_per_hour_below")), 0, 10000),
    good_rating_from: numberOr(input.good_rating_from, Number(c.good_rating_from), 0, 5),
    red_rating_below: numberOr(input.red_rating_below, Number(c.red_rating_below), 0, 5),
    min_per_minute: numberOr(input.min_per_minute, Number(fallback("min_per_minute")), 0, 1000),
    red_per_minute_below: numberOr(input.red_per_minute_below, Number(fallback("red_per_minute_below")), 0, 1000),
    min_fare: numberOr(input.min_fare, Number(c.min_fare), 0, 10000),
    max_pickup_km: numberOr(input.max_pickup_km, Number(fallback("max_pickup_km")), 0, 1000),
    max_pickup_minutes: numberOr(input.max_pickup_minutes, Number(fallback("max_pickup_minutes")), 0, 120),
    min_profit: numberOr(input.min_profit, Number(c.min_profit), 0, 10000),
    min_profit_per_hour: numberOr(input.min_profit_per_hour, Number(c.min_profit_per_hour), 0, 10000),
    red_profit_per_hour_below: numberOr(input.red_profit_per_hour_below, Number(c.red_profit_per_hour_below), 0, 10000),
    min_profit_percent: numberOr(input.min_profit_percent, Number(c.min_profit_percent), 0, 100),
    red_profit_percent_below: numberOr(input.red_profit_percent_below, Number(c.red_profit_percent_below), 0, 100),
    cost_per_km: numberOr(input.cost_per_km, Number(c.cost_per_km), 0, 1000),
    timezone: String(input.timezone ?? c.timezone ?? DEFAULT_PREFERENCES.timezone).slice(0, 80),
    collective_stats_opt_in: boolOr(input.collective_stats_opt_in, Boolean(c.collective_stats_opt_in)),
    strategy_preset: requestedPreset,
    app_theme: enumOr(input.app_theme, ["auto","light","dark"] as const, c.app_theme || "auto"),
    hud_theme_mode: enumOr(input.hud_theme_mode, ["follow_app","light","dark"] as const, c.hud_theme_mode || "follow_app"),
  };
  const { data, error } = await adminSupabase().from("driver_preferences")
    .upsert({ driver_id: driverId, ...row, updated_at: new Date().toISOString() }, { onConflict: "driver_id" })
    .select(FIELDS).single();
  if (error) throw new Error(error.message);
  return data as DriverPreferences;
}
