import { adminSupabase } from "./supabase";
import { ensurePreferences, type DriverPreferences } from "./preferences";

const MIN_SEED_SAMPLES = 20;
const MIN_PERSONAL_SAMPLES = 8;
const DEFAULT_LIMIT = 120;

type SourceMode = "personal" | "collective";
type Mode = "now" | "today" | "week" | "search";
type ServiceProfile = "popular" | "comfort" | "premium" | "unknown";

type Row = {
  region_key: string;
  region_label: string;
  weekday_iso: number;
  hour_bucket: number;
  service_profile: string;
  sample_count: number;
  average_fare: number | null;
  median_fare?: number | null;
  p25_fare?: number | null;
  p75_fare?: number | null;
  average_per_km: number | null;
  median_per_km?: number | null;
  p25_per_km?: number | null;
  p75_per_km?: number | null;
  average_per_minute: number | null;
  median_per_minute?: number | null;
  p25_per_minute?: number | null;
  p75_per_minute?: number | null;
  average_per_hour: number | null;
  median_per_hour?: number | null;
  p25_per_hour?: number | null;
  p75_per_hour?: number | null;
  average_pickup_km: number | null;
  average_pickup_minutes: number | null;
  contributor_count?: number;
};

type QueryInput = {
  weekday?: number;
  hour?: number;
  profile?: ServiceProfile | null;
  region?: string | null;
  limit?: number;
};

function n(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function row(raw: any): Row {
  return {
    region_key: String(raw.region_key || ""),
    region_label: String(raw.region_label || "Região"),
    weekday_iso: Number(raw.weekday_iso || 1),
    hour_bucket: Number(raw.hour_bucket || 0),
    service_profile: String(raw.service_profile || "unknown"),
    sample_count: Number(raw.sample_count || 0),
    average_fare: n(raw.average_fare),
    median_fare: n(raw.median_fare),
    p25_fare: n(raw.p25_fare),
    p75_fare: n(raw.p75_fare),
    average_per_km: n(raw.average_per_km),
    median_per_km: n(raw.median_per_km),
    p25_per_km: n(raw.p25_per_km),
    p75_per_km: n(raw.p75_per_km),
    average_per_minute: n(raw.average_per_minute),
    median_per_minute: n(raw.median_per_minute),
    p25_per_minute: n(raw.p25_per_minute),
    p75_per_minute: n(raw.p75_per_minute),
    average_per_hour: n(raw.average_per_hour),
    median_per_hour: n(raw.median_per_hour),
    p25_per_hour: n(raw.p25_per_hour),
    p75_per_hour: n(raw.p75_per_hour),
    average_pickup_km: n(raw.average_pickup_km),
    average_pickup_minutes: n(raw.average_pickup_minutes),
    contributor_count: n(raw.contributor_count) ?? undefined,
  };
}

const weekdays: Record<string, number> = {
  Mon: 1,
  Tue: 2,
  Wed: 3,
  Thu: 4,
  Fri: 5,
  Sat: 6,
  Sun: 7,
};

function timeParts(value: Date, timeZone: string) {
  const weekday = new Intl.DateTimeFormat("en-US", {
    timeZone,
    weekday: "short",
  }).format(value);
  const hour =
    Number(
      new Intl.DateTimeFormat("en-US", {
        timeZone,
        hour: "2-digit",
        hour12: false,
      }).format(value),
    ) % 24;
  return {
    weekdayIso: weekdays[weekday] ?? 1,
    hour,
    hourBucket: Math.floor(hour / 3) * 3,
  };
}

function cleanProfile(value?: string | null): ServiceProfile | null {
  return ["popular", "comfort", "premium", "unknown"].includes(String(value))
    ? (String(value) as ServiceProfile)
    : null;
}

function confidence(samples: number) {
  if (samples >= 250) return "high";
  if (samples >= 80) return "medium";
  if (samples >= 20) return "low";
  return "insufficient";
}

function targetFit(value: number | null | undefined, target: number) {
  if (value === null || value === undefined || !Number.isFinite(value)) return null;
  if (target <= 0) return 1;
  return Math.max(0, Math.min(1.15, value / target));
}

function maximumFit(value: number | null | undefined, maximum: number) {
  if (value === null || value === undefined || !Number.isFinite(value)) return null;
  if (maximum <= 0) return 1;
  if (value <= maximum) return 1;
  return Math.max(0, Math.min(1, maximum / value));
}

function fitScore(r: Row, prefs: DriverPreferences) {
  const parts: Array<[number | null, number]> = [
    [targetFit(r.median_per_km ?? r.average_per_km, prefs.min_per_km), 30],
    [targetFit(r.median_per_minute ?? r.average_per_minute, prefs.min_per_minute), 25],
    [targetFit(r.median_per_hour ?? r.average_per_hour, prefs.min_per_hour), 25],
    [maximumFit(r.average_pickup_km, prefs.max_pickup_km), 10],
    [maximumFit(r.average_pickup_minutes, prefs.max_pickup_minutes), 10],
  ];
  let weighted = 0;
  let weight = 0;
  for (const [value, w] of parts) {
    if (value === null) continue;
    weighted += value * w;
    weight += w;
  }
  return weight ? Math.round(Math.min(100, (weighted / weight) * 100)) : 0;
}

function wording(score: number, samples: number) {
  if (samples < MIN_SEED_SAMPLES) return "Dados insuficientes para comparar com segurança";
  if (score >= 88) return "Forte aderência histórica ao seu perfil nesta faixa";
  if (score >= 72) return "Histórico compatível com as suas metas nesta faixa";
  if (score >= 55) return "Aderência histórica moderada; compare antes de se deslocar";
  return "Histórico abaixo de parte das suas metas";
}

function decorate(r: Row, source: string, prefs: DriverPreferences) {
  const score = fitScore(r, prefs);
  return {
    ...r,
    source,
    confidence: confidence(r.sample_count),
    score,
    wording: wording(score, r.sample_count),
  };
}

function cleanSearch(value?: string | null) {
  return (value || "").trim().replace(/[%_]/g, "").slice(0, 80) || null;
}

async function seedRows(input: QueryInput) {
  let q: any = adminSupabase().from("sr_region_seed_v1").select("*");
  if (input.weekday) q = q.eq("weekday_iso", input.weekday);
  if (input.hour !== undefined) q = q.eq("hour_bucket", input.hour);
  if (input.profile) q = q.eq("service_profile", input.profile);
  if (input.region) q = q.ilike("region_label", `%${input.region}%`);
  const { data, error } = await q
    .order("sample_count", { ascending: false })
    .limit(input.limit ?? DEFAULT_LIMIT);
  if (error) throw new Error(error.message);
  return (data ?? []).map(row) as Row[];
}

async function personalRows(driverId: string, input: QueryInput) {
  let q: any = adminSupabase()
    .from("sr_personal_offer_region_hour_v1")
    .select("*")
    .eq("driver_id", driverId);
  if (input.weekday) q = q.eq("weekday_iso", input.weekday);
  if (input.hour !== undefined) q = q.eq("hour_bucket", input.hour);
  if (input.profile) q = q.eq("service_profile", input.profile);
  if (input.region) q = q.ilike("region_label", `%${input.region}%`);
  const { data, error } = await q
    .order("sample_count", { ascending: false })
    .limit(input.limit ?? DEFAULT_LIMIT);
  if (error) throw new Error(error.message);
  return (data ?? []).map(row) as Row[];
}

async function collectiveRows(input: QueryInput) {
  let q: any = adminSupabase().from("sr_collective_offer_region_hour_v1").select("*");
  if (input.weekday) q = q.eq("weekday_iso", input.weekday);
  if (input.hour !== undefined) q = q.eq("hour_bucket", input.hour);
  if (input.profile) q = q.eq("service_profile", input.profile);
  if (input.region) q = q.ilike("region_label", `%${input.region}%`);
  const { data, error } = await q
    .order("sample_count", { ascending: false })
    .limit(input.limit ?? DEFAULT_LIMIT);
  if (error) throw new Error(error.message);
  return (data ?? []).map(row) as Row[];
}

function weighted(rows: Row[], key: keyof Row) {
  let total = 0;
  let weight = 0;
  for (const item of rows) {
    const value = n(item[key]);
    if (value === null) continue;
    const w = Math.max(1, item.sample_count);
    total += value * w;
    weight += w;
  }
  return weight ? Math.round((total / weight) * 100) / 100 : null;
}

/**
 * Combina janelas já agregadas sem voltar a ofertas individuais.
 * Para períodos amplos usamos médias ponderadas e um intervalo conservador
 * (menor P25 / maior P75) em vez de fingir uma mediana exata reconstruída.
 */
function mergeRows(rows: Row[], minimumSamples = 1) {
  const groups = new Map<string, Row[]>();
  for (const item of rows) {
    const key = `${item.region_key}|${item.service_profile}`;
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  const merged: Row[] = [];
  for (const items of groups.values()) {
    const samples = items.reduce((sum, item) => sum + item.sample_count, 0);
    if (samples < minimumSamples) continue;
    const first = items[0];
    const lows = (key: keyof Row) => items.map((item) => n(item[key])).filter((v): v is number => v !== null);
    const minOrNull = (values: number[]) => values.length ? Math.min(...values) : null;
    const maxOrNull = (values: number[]) => values.length ? Math.max(...values) : null;
    const sameWeekday = items.every((item) => item.weekday_iso === first.weekday_iso);
    const sameHour = items.every((item) => item.hour_bucket === first.hour_bucket);
    merged.push({
      ...first,
      weekday_iso: sameWeekday ? first.weekday_iso : 0,
      hour_bucket: sameHour ? first.hour_bucket : -1,
      sample_count: samples,
      average_fare: weighted(items, "average_fare"),
      median_fare: weighted(items, "median_fare") ?? weighted(items, "average_fare"),
      p25_fare: minOrNull(lows("p25_fare")),
      p75_fare: maxOrNull(lows("p75_fare")),
      average_per_km: weighted(items, "average_per_km"),
      median_per_km: weighted(items, "median_per_km") ?? weighted(items, "average_per_km"),
      p25_per_km: minOrNull(lows("p25_per_km")),
      p75_per_km: maxOrNull(lows("p75_per_km")),
      average_per_minute: weighted(items, "average_per_minute"),
      median_per_minute: weighted(items, "median_per_minute") ?? weighted(items, "average_per_minute"),
      p25_per_minute: minOrNull(lows("p25_per_minute")),
      p75_per_minute: maxOrNull(lows("p75_per_minute")),
      average_per_hour: weighted(items, "average_per_hour"),
      median_per_hour: weighted(items, "median_per_hour") ?? weighted(items, "average_per_hour"),
      p25_per_hour: minOrNull(lows("p25_per_hour")),
      p75_per_hour: maxOrNull(lows("p75_per_hour")),
      average_pickup_km: weighted(items, "average_pickup_km"),
      average_pickup_minutes: weighted(items, "average_pickup_minutes"),
      contributor_count: Math.max(...items.map((item) => item.contributor_count ?? 0)) || undefined,
    });
  }
  return merged;
}

function top(rows: Row[], source: string, prefs: DriverPreferences, limit = 12) {
  return [...rows]
    .sort(
      (a, b) =>
        fitScore(b, prefs) - fitScore(a, prefs) ||
        b.sample_count - a.sample_count,
    )
    .slice(0, limit)
    .map((item) => decorate(item, source, prefs));
}

function preferredSource(
  source: SourceMode,
  optIn: boolean,
  personal: Row[],
  collective: Row[],
) {
  if (source === "collective" && optIn && collective.length) return "collective";
  if (source === "personal" && personal.length) return "personal";
  return "sr_rotas_seed";
}

export async function nowIntelligence(
  driverId: string,
  input: {
    mode?: Mode;
    source?: SourceMode;
    at?: string;
    weekday?: number;
    hour?: number;
    profile?: string | null;
    region?: string | null;
  },
) {
  const prefs = await ensurePreferences(driverId);
  const mode: Mode = ["now", "today", "week", "search"].includes(String(input.mode))
    ? input.mode!
    : "now";
  const source: SourceMode = input.source === "collective" ? "collective" : "personal";
  const timeZone = prefs.timezone || "America/Sao_Paulo";
  const at =
    input.at && !Number.isNaN(new Date(input.at).getTime())
      ? new Date(input.at)
      : new Date();
  const current = timeParts(at, timeZone);
  const weekday =
    input.weekday && input.weekday >= 1 && input.weekday <= 7
      ? input.weekday
      : current.weekdayIso;
  const hour =
    typeof input.hour === "number" && Number.isFinite(input.hour)
      ? Math.max(0, Math.min(23, input.hour))
      : current.hour;
  const hourBucket = Math.floor(hour / 3) * 3;
  const explicitAll = input.profile === "all";
  const profile = explicitAll
    ? null
    : cleanProfile(input.profile) ??
      (prefs.strategy_preset !== "custom"
        ? cleanProfile(prefs.strategy_preset)
        : null);
  const region = cleanSearch(input.region);
  const optIn = Boolean(prefs.collective_stats_opt_in);

  const base = {
    mode,
    source,
    strategy_preset: prefs.strategy_preset,
    selected_service_profile: profile,
    collective_opt_in: optIn,
    time_zone: timeZone,
    minimum_seed_samples: MIN_SEED_SAMPLES,
  };

  if (mode === "week" || mode === "search") {
    const query = { profile, region, limit: 500 };
    const [seed, personal, collective] = await Promise.all([
      seedRows(query),
      source === "personal" ? personalRows(driverId, query) : Promise.resolve([] as Row[]),
      source === "collective" && optIn
        ? collectiveRows(query)
        : Promise.resolve([] as Row[]),
    ]);
    const seedMerged = mergeRows(seed, MIN_SEED_SAMPLES);
    const personalMerged = mergeRows(personal, MIN_PERSONAL_SAMPLES);
    const collectiveMerged = mergeRows(collective, 3);
    return {
      ...base,
      target: { region, service_profile: profile },
      scope: "all_historical_windows",
      seed: top(seedMerged, "sr_rotas_seed", prefs, mode === "search" ? 24 : 28),
      personal: top(personalMerged, "personal", prefs, mode === "search" ? 24 : 28),
      collective: top(collectiveMerged, "collective", prefs, mode === "search" ? 24 : 28),
      preferred: preferredSource(source, optIn, personalMerged, collectiveMerged),
      note:
        mode === "search"
          ? "A pesquisa compara janelas históricas agregadas da região. Não representa demanda ao vivo nem garante corrida."
          : "A semana resume janelas históricas agregadas. Não representa demanda ao vivo nem garante corrida.",
    };
  }

  if (mode === "today") {
    const buckets = [
      hourBucket,
      ...[0, 3, 6, 9, 12, 15, 18, 21].filter((value) => value > hourBucket),
    ].slice(0, 4);
    const seedAll: Row[] = [];
    const personalAll: Row[] = [];
    const collectiveAll: Row[] = [];
    for (const bucket of buckets) {
      seedAll.push(...(await seedRows({ weekday, hour: bucket, profile, region, limit: 40 })));
      if (source === "personal") {
        personalAll.push(...(await personalRows(driverId, { weekday, hour: bucket, profile, region, limit: 40 })));
      }
      if (source === "collective" && optIn) {
        collectiveAll.push(...(await collectiveRows({ weekday, hour: bucket, profile, region, limit: 40 })));
      }
    }
    let seedMerged = mergeRows(seedAll, MIN_SEED_SAMPLES);
    const personalMerged = mergeRows(personalAll, MIN_PERSONAL_SAMPLES);
    const collectiveMerged = mergeRows(collectiveAll, 3);
    let scope = "today_current_and_next_3h";
    if (!seedMerged.length) {
      seedMerged = mergeRows(
        await seedRows({ profile, region, limit: 500 }),
        MIN_SEED_SAMPLES,
      );
      scope = "all_time_fallback";
    }
    return {
      ...base,
      target: { weekday_iso: weekday, hour_bucket: hourBucket, next_buckets: buckets },
      scope,
      seed: top(seedMerged, "sr_rotas_seed", prefs, 20),
      personal: top(personalMerged, "personal", prefs, 20),
      collective: top(collectiveMerged, "collective", prefs, 20),
      preferred: preferredSource(source, optIn, personalMerged, collectiveMerged),
      note:
        "Hoje considera a faixa atual e as próximas faixas do dia usando histórico agregado. Não garante nova corrida.",
    };
  }

  const query = { weekday, hour: hourBucket, profile, region, limit: DEFAULT_LIMIT };
  const [seedExactRaw, personalExactRaw, collectiveExactRaw] = await Promise.all([
    seedRows(query),
    source === "personal" ? personalRows(driverId, query) : Promise.resolve([] as Row[]),
    source === "collective" && optIn
      ? collectiveRows(query)
      : Promise.resolve([] as Row[]),
  ]);

  let seed = mergeRows(seedExactRaw, MIN_SEED_SAMPLES);
  let personal = mergeRows(personalExactRaw, MIN_PERSONAL_SAMPLES);
  let collective = mergeRows(collectiveExactRaw, 3);
  let scope = "weekday_and_3h";

  if (!seed.length) {
    seed = mergeRows(
      await seedRows({ hour: hourBucket, profile, region, limit: 500 }),
      MIN_SEED_SAMPLES,
    );
    scope = "same_3h_any_weekday";
  }
  if (!seed.length) {
    seed = mergeRows(
      await seedRows({ profile, region, limit: 500 }),
      MIN_SEED_SAMPLES,
    );
    scope = "all_time_fallback";
  }

  if (source === "personal" && !personal.length) {
    personal = mergeRows(
      await personalRows(driverId, { hour: hourBucket, profile, region, limit: 500 }),
      MIN_PERSONAL_SAMPLES,
    );
  }
  if (source === "collective" && optIn && !collective.length) {
    collective = mergeRows(
      await collectiveRows({ hour: hourBucket, profile, region, limit: 500 }),
      3,
    );
  }

  return {
    ...base,
    target: {
      weekday_iso: weekday,
      hour,
      hour_bucket: hourBucket,
      region,
      service_profile: profile,
    },
    scope,
    seed: top(seed, "sr_rotas_seed", prefs),
    personal: top(personal, "personal", prefs),
    collective: top(collective, "collective", prefs),
    preferred: preferredSource(source, optIn, personal, collective),
    note:
      scope === "weekday_and_3h"
        ? "Agora compara a mesma faixa de dia/horário com dados históricos agregados. Não é demanda em tempo real, não controla Uber/99 e não garante nova corrida."
        : "Não havia amostra segura suficiente exatamente nesta faixa; o Sr. Rotas ampliou o histórico de forma conservadora. Não é demanda em tempo real e não garante corrida.",
  };
}
