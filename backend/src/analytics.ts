import { adminSupabase } from "./supabase";
import { resolveRange } from "./ranges";
import { serverEnv } from "./env";
import { ensurePreferences } from "./preferences";

export type OfferRow = {
  id: string;
  journey_id: string | null;
  observed_at: string;
  platform: string;
  fare: number;
  pickup_km: number | null;
  trip_km: number | null;
  total_km: number | null;
  total_minutes: number | null;
  per_km: number | null;
  per_hour: number | null;
  per_minute: number | null;
  estimated_cost: number | null;
  estimated_profit: number | null;
  profit_per_hour: number | null;
  profit_percent: number | null;
  passenger_rating: number | null;
  advertised_per_km: number | null;
  service_type: string;
  verdict: string;
  capture_method: string;
  confidence: number | null;
  offer_type: string;
  raw_text?: string;
};

type Filters = {
  platform?: string;
  verdict?: string;
  journeyId?: string;
  serviceType?: string;
  offerType?: string;
};

const baseFields =
  "id,journey_id,observed_at,platform,fare,pickup_km,trip_km,total_km,total_minutes,per_km,per_hour,per_minute,estimated_cost,estimated_profit,profit_per_hour,profit_percent,passenger_rating,advertised_per_km,service_type,verdict,capture_method,confidence,offer_type";

function applyFilters(query: any, input: Filters) {
  let q = query;
  if (input.platform) q = q.eq("platform", input.platform);
  if (input.verdict) q = q.eq("verdict", input.verdict);
  if (input.journeyId) q = q.eq("journey_id", input.journeyId);
  if (input.serviceType) q = q.eq("service_type", input.serviceType);
  if (input.offerType) q = q.eq("offer_type", input.offerType);
  return q;
}

export async function fetchOffers(
  driverId: string,
  input: {
    from?: string;
    to?: string;
    platform?: string;
    verdict?: string;
    journeyId?: string;
    serviceType?: string;
    offerType?: string;
    limit?: number;
    raw?: boolean;
  } = {},
) {
  const range = resolveRange(input.from, input.to);
  const limit = Math.max(1, Math.min(input.limit ?? 200, 500));
  const fields = input.raw ? `${baseFields},raw_text` : baseFields;
  let query = adminSupabase()
    .from("ride_offers")
    .select(fields)
    .eq("driver_id", driverId)
    .gte("observed_at", range.from)
    .lt("observed_at", range.to)
    .order("observed_at", { ascending: false })
    .limit(limit);
  query = applyFilters(query, input);
  const { data, error } = await query;
  if (error) throw new Error(error.message);
  return { range, offers: (data ?? []) as unknown as OfferRow[] };
}

async function fetchOffersPaged(
  driverId: string,
  range: { from: string; to: string },
  filters: Filters,
  maxRows = 5000,
) {
  const pageSize = 1000;
  const rows: OfferRow[] = [];
  for (let offset = 0; offset < maxRows; offset += pageSize) {
    let query = adminSupabase()
      .from("ride_offers")
      .select(baseFields)
      .eq("driver_id", driverId)
      .gte("observed_at", range.from)
      .lt("observed_at", range.to)
      .order("observed_at", { ascending: true })
      .range(offset, offset + pageSize - 1);
    query = applyFilters(query, filters);
    const { data, error } = await query;
    if (error) throw new Error(error.message);
    const page = (data ?? []) as unknown as OfferRow[];
    rows.push(...page);
    if (page.length < pageSize) return { offers: rows, truncated: false };
  }
  return { offers: rows, truncated: true };
}

function avg(values: Array<number | null | undefined>) {
  const valid = values.filter((v): v is number => typeof v === "number" && Number.isFinite(v));
  if (!valid.length) return null;
  return round2(valid.reduce((a, b) => a + b, 0) / valid.length);
}

function sum(values: Array<number | null | undefined>) {
  return round2(values.reduce<number>((acc, v) => acc + (typeof v === "number" && Number.isFinite(v) ? v : 0), 0));
}

function round2(value: number) {
  return Math.round(value * 100) / 100;
}

export function summarizeOffers(offers: OfferRow[]) {
  return {
    offer_count: offers.length,
    total_offered_fare: sum(offers.map((o) => o.fare)),
    average_fare: avg(offers.map((o) => o.fare)),
    average_per_km: avg(offers.map((o) => o.per_km)),
    average_per_hour: avg(offers.map((o) => o.per_hour)),
    average_per_minute: avg(offers.map((o) => o.per_minute)),
    average_passenger_rating: avg(offers.map((o) => o.passenger_rating)),
    average_profit_per_hour: avg(offers.map((o) => o.profit_per_hour)),
    average_profit_percent: avg(offers.map((o) => o.profit_percent)),
    average_parser_confidence: avg(offers.map((o) => o.confidence)),
    estimated_total_cost: sum(offers.map((o) => o.estimated_cost)),
    estimated_total_profit: sum(offers.map((o) => o.estimated_profit)),
    average_estimated_profit: avg(offers.map((o) => o.estimated_profit)),
    offer_types: {
      exclusive: offers.filter((o) => o.offer_type === "exclusive").length,
      radar: offers.filter((o) => o.offer_type === "radar").length,
    },
    verdicts: {
      boa: offers.filter((o) => o.verdict === "boa").length,
      regular: offers.filter((o) => o.verdict === "regular").length,
      ruim: offers.filter((o) => o.verdict === "ruim").length,
    },
  };
}

function pct(current: number | null | undefined, previous: number | null | undefined) {
  if (typeof current !== "number" || typeof previous !== "number" || !Number.isFinite(current) || !Number.isFinite(previous) || previous === 0) return null;
  return round2(((current - previous) / Math.abs(previous)) * 100);
}

function localDay(value: string, timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" })
    .formatToParts(new Date(value));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")}`;
}

function localDayLabel(value: string, timeZone: string) {
  return new Intl.DateTimeFormat("pt-BR", { timeZone, day: "2-digit", month: "2-digit" }).format(new Date(value));
}

function localHour(value: string, timeZone: string) {
  return Number(new Intl.DateTimeFormat("en-US", { timeZone, hour: "2-digit", hour12: false }).format(new Date(value))) % 24;
}

function groupedPoints(offers: OfferRow[], keyFn: (o: OfferRow) => { key: string; label: string }) {
  const groups = new Map<string, { label: string; rows: OfferRow[] }>();
  for (const offer of offers) {
    const part = keyFn(offer);
    const current = groups.get(part.key) ?? { label: part.label, rows: [] };
    current.rows.push(offer);
    groups.set(part.key, current);
  }
  return [...groups.entries()].map(([key, group]) => ({
    key,
    label: group.label,
    ...summarizeOffers(group.rows),
  }));
}

async function journeyRows(driverId: string, range: { from: string; to: string }) {
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .select("id,platform,started_at,ended_at,end_reason")
    .eq("driver_id", driverId)
    .gte("started_at", range.from)
    .lt("started_at", range.to)
    .order("started_at", { ascending: false })
    .limit(100);
  if (error) throw new Error(error.message);
  return data ?? [];
}

export async function historyDashboard(
  driverId: string,
  input: { days?: number; verdict?: string; serviceType?: string; offerType?: string } = {},
) {
  const days = Math.max(1, Math.min(input.days ?? 7, 90));
  const range = resolveRange(undefined, undefined, days);
  const durationMs = new Date(range.to).getTime() - new Date(range.from).getTime();
  const previousRange = {
    from: new Date(new Date(range.from).getTime() - durationMs).toISOString(),
    to: range.from,
  };
  const filters: Filters = {
    verdict: input.verdict,
    serviceType: input.serviceType,
    offerType: input.offerType,
  };

  const [currentFound, previousFound, journeysRaw] = await Promise.all([
    fetchOffersPaged(driverId, range, filters),
    fetchOffersPaged(driverId, previousRange, filters),
    journeyRows(driverId, range),
  ]);

  const current = currentFound.offers;
  const previous = previousFound.offers;
  const currentSummary = summarizeOffers(current);
  const previousSummary = summarizeOffers(previous);
  const tz = serverEnv().timezone;

  const daily = groupedPoints(current, (o) => ({
    key: localDay(o.observed_at, tz),
    label: localDayLabel(o.observed_at, tz),
  })).sort((a, b) => a.key.localeCompare(b.key));

  const hours = groupedPoints(current, (o) => {
    const hour = localHour(o.observed_at, tz);
    return { key: String(hour), label: `${String(hour).padStart(2, "0")}h` };
  }).sort((a, b) => Number(a.key) - Number(b.key));

  const serviceGroups = new Map<string, OfferRow[]>();
  for (const offer of current) {
    const service = offer.service_type || "unknown";
    serviceGroups.set(service, [...(serviceGroups.get(service) ?? []), offer]);
  }
  const services = [...serviceGroups.entries()]
    .map(([service_type, rows]) => ({ service_type, ...summarizeOffers(rows) }))
    .sort((a, b) => b.offer_count - a.offer_count);

  const byJourney = new Map<string, OfferRow[]>();
  for (const offer of current) {
    if (offer.journey_id) byJourney.set(offer.journey_id, [...(byJourney.get(offer.journey_id) ?? []), offer]);
  }
  const journeys = journeysRaw.map((j: any) => {
    const rows = byJourney.get(String(j.id)) ?? [];
    const s = summarizeOffers(rows);
    const duration = j.ended_at
      ? Math.max(0, Math.round((new Date(j.ended_at).getTime() - new Date(j.started_at).getTime()) / 60000))
      : null;
    return {
      id: String(j.id),
      platform: j.platform,
      started_at: j.started_at,
      ended_at: j.ended_at,
      duration_minutes: duration,
      offer_count: s.offer_count,
      good_count: s.verdicts.boa,
      regular_count: s.verdicts.regular,
      bad_count: s.verdicts.ruim,
      average_per_km: s.average_per_km,
      average_per_hour: s.average_per_hour,
      estimated_profit_observed: s.estimated_total_profit,
    };
  });

  const topOffers = [...current]
    .sort((a, b) => {
      const gradeA = a.verdict === "boa" ? 1 : 0;
      const gradeB = b.verdict === "boa" ? 1 : 0;
      if (gradeA !== gradeB) return gradeB - gradeA;
      return (b.profit_per_hour ?? -1) - (a.profit_per_hour ?? -1)
        || (b.per_minute ?? -1) - (a.per_minute ?? -1)
        || (b.per_km ?? -1) - (a.per_km ?? -1);
    })
    .slice(0, 10)
    .map((o) => ({
      observed_at: o.observed_at,
      fare: o.fare,
      service_type: o.service_type,
      offer_type: o.offer_type,
      verdict: o.verdict,
      per_km: o.per_km,
      per_hour: o.per_hour,
      per_minute: o.per_minute,
      estimated_profit: o.estimated_profit,
      passenger_rating: o.passenger_rating,
    }));

  return {
    range,
    filters: {
      days,
      verdict: input.verdict ?? null,
      service_type: input.serviceType ?? null,
      offer_type: input.offerType ?? null,
    },
    summary: currentSummary,
    comparison: {
      previous_range: previousRange,
      previous: previousSummary,
      delta: {
        offer_count_pct: pct(currentSummary.offer_count, previousSummary.offer_count),
        average_per_km_pct: pct(currentSummary.average_per_km, previousSummary.average_per_km),
        average_per_hour_pct: pct(currentSummary.average_per_hour, previousSummary.average_per_hour),
        average_per_minute_pct: pct(currentSummary.average_per_minute, previousSummary.average_per_minute),
        average_estimated_profit_pct: pct(currentSummary.average_estimated_profit, previousSummary.average_estimated_profit),
      },
    },
    daily,
    hours,
    services,
    journeys,
    top_offers: topOffers,
    truncated: currentFound.truncated || previousFound.truncated,
    note: "Todas as métricas representam ofertas observadas. Não provam aceite, conclusão, faturamento ou ganho realizado.",
  };
}

export async function driverSummary(driverId: string, from?: string, to?: string) {
  const result = await fetchOffers(driverId, { from, to, limit: 500 });
  return { range: result.range, summary: summarizeOffers(result.offers) };
}

export async function bestHours(driverId: string, days = 30) {
  const to = new Date();
  const from = new Date(to.getTime() - Math.max(1, Math.min(days, 180)) * 86400000);
  const { offers } = await fetchOffers(driverId, { from: from.toISOString(), to: to.toISOString(), limit: 500 });
  const tz = serverEnv().timezone;
  const groups = new Map<string, OfferRow[]>();
  for (const offer of offers) {
    const hour = new Intl.DateTimeFormat("pt-BR", { timeZone: tz, hour: "2-digit", hour12: false }).format(new Date(offer.observed_at));
    const key = `${hour}:00`;
    groups.set(key, [...(groups.get(key) ?? []), offer]);
  }
  return [...groups.entries()]
    .map(([hour, rows]) => ({ hour, ...summarizeOffers(rows) }))
    .sort((a, b) => (b.average_estimated_profit ?? -Infinity) - (a.average_estimated_profit ?? -Infinity));
}

export async function costBreakdown(driverId: string, from?: string, to?: string) {
  const { range, offers } = await fetchOffers(driverId, { from, to, limit: 500 });
  return {
    range,
    total_km_observed: sum(offers.map((o) => o.total_km)),
    total_fare_offered: sum(offers.map((o) => o.fare)),
    estimated_cost: sum(offers.map((o) => o.estimated_cost)),
    estimated_profit: sum(offers.map((o) => o.estimated_profit)),
    note: "Os valores representam ofertas observadas. Eles não provam que cada corrida foi aceita ou concluída.",
  };
}

export async function strategyProgress(driverId: string, from?: string, to?: string) {
  const [prefs, found] = await Promise.all([
    ensurePreferences(driverId),
    fetchOffers(driverId, { from, to, limit: 500 }),
  ]);
  const gradeHigher = (value: number | null, redBelow: number, greenFrom: number) => {
    if (value === null || (redBelow <= 0 && greenFrom <= 0)) return "ignored";
    if (redBelow > 0 && value < redBelow) return "red";
    if (greenFrom > 0 && value >= greenFrom) return "green";
    return "yellow";
  };
  const evaluated = found.offers.map((o) => ({
    ...o,
    grades: {
      per_km: gradeHigher(o.per_km, prefs.red_per_km_below, prefs.min_per_km),
      per_hour: gradeHigher(o.per_hour, prefs.red_per_hour_below, prefs.min_per_hour),
      per_minute: gradeHigher(o.per_minute, prefs.red_per_minute_below, prefs.min_per_minute),
      rating: gradeHigher(o.passenger_rating, prefs.red_rating_below, prefs.good_rating_from),
      profit_per_hour: gradeHigher(o.profit_per_hour, prefs.red_profit_per_hour_below, prefs.min_profit_per_hour),
      profit_percent: gradeHigher(o.profit_percent, prefs.red_profit_percent_below, prefs.min_profit_percent),
    },
    hard_rules: {
      min_fare: prefs.min_fare <= 0 || o.fare >= prefs.min_fare,
      pickup: prefs.max_pickup_km <= 0 || (o.pickup_km ?? Infinity) <= prefs.max_pickup_km,
      profit: prefs.min_profit <= 0 || (o.estimated_profit ?? -Infinity) >= prefs.min_profit,
    },
  }));
  const matches = evaluated.filter((o) => Object.values(o.hard_rules).every(Boolean) && !Object.values(o.grades).includes("red"));
  return {
    range: found.range,
    strategy: prefs,
    observed_offer_count: evaluated.length,
    offers_without_red_metrics: matches.length,
    match_rate: evaluated.length ? Math.round((matches.length / evaluated.length) * 10000) / 100 : 0,
    summary: summarizeOffers(found.offers),
    note: "Taxa calculada sobre ofertas observadas. Não representa taxa de aceitação, conclusão ou ganho realizado.",
  };
}
