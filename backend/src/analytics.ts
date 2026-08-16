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
  estimated_cost: number | null;
  estimated_profit: number | null;
  verdict: string;
  capture_method: string;
  confidence: number | null;
  offer_type: string;
  raw_text?: string;
};

export async function fetchOffers(
  driverId: string,
  input: { from?: string; to?: string; platform?: string; verdict?: string; journeyId?: string; limit?: number; raw?: boolean } = {},
) {
  const range = resolveRange(input.from, input.to);
  const limit = Math.max(1, Math.min(input.limit ?? 200, 500));
  const baseFields = "id,journey_id,observed_at,platform,fare,pickup_km,trip_km,total_km,total_minutes,per_km,per_hour,estimated_cost,estimated_profit,verdict,capture_method,confidence,offer_type";
  const fields = input.raw ? `${baseFields},raw_text` : baseFields;
  let query = adminSupabase()
    .from("ride_offers")
    .select(fields)
    .eq("driver_id", driverId)
    .gte("observed_at", range.from)
    .lt("observed_at", range.to)
    .order("observed_at", { ascending: false })
    .limit(limit);
  if (input.platform) query = query.eq("platform", input.platform);
  if (input.verdict) query = query.eq("verdict", input.verdict);
  if (input.journeyId) query = query.eq("journey_id", input.journeyId);
  const { data, error } = await query;
  if (error) throw new Error(error.message);
  return { range, offers: (data ?? []) as unknown as OfferRow[] };
}

function avg(values: Array<number | null | undefined>) {
  const valid = values.filter((v): v is number => typeof v === "number" && Number.isFinite(v));
  if (!valid.length) return null;
  return Math.round((valid.reduce((a, b) => a + b, 0) / valid.length) * 100) / 100;
}

function sum(values: Array<number | null | undefined>) {
  return Math.round(values.reduce<number>((acc, v) => acc + (typeof v === "number" && Number.isFinite(v) ? v : 0), 0) * 100) / 100;
}

export function summarizeOffers(offers: OfferRow[]) {
  return {
    offer_count: offers.length,
    total_offered_fare: sum(offers.map((o) => o.fare)),
    average_fare: avg(offers.map((o) => o.fare)),
    average_per_km: avg(offers.map((o) => o.per_km)),
    average_per_hour: avg(offers.map((o) => o.per_hour)),
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
  const evaluated = found.offers.map((o) => ({
    ...o,
    rules: {
      per_km: prefs.min_per_km <= 0 || (o.per_km ?? -Infinity) >= prefs.min_per_km,
      per_hour: prefs.min_per_hour <= 0 || (o.per_hour ?? -Infinity) >= prefs.min_per_hour,
      min_fare: prefs.min_fare <= 0 || o.fare >= prefs.min_fare,
      pickup: prefs.max_pickup_km <= 0 || (o.pickup_km ?? Infinity) <= prefs.max_pickup_km,
      profit: prefs.min_profit <= 0 || (o.estimated_profit ?? -Infinity) >= prefs.min_profit,
    },
  }));
  const matches = evaluated.filter((o) => Object.values(o.rules).every(Boolean));
  return {
    range: found.range,
    strategy: prefs,
    observed_offer_count: evaluated.length,
    offers_matching_all_rules: matches.length,
    match_rate: evaluated.length ? Math.round((matches.length / evaluated.length) * 10000) / 100 : 0,
    summary: summarizeOffers(found.offers),
    note: "Taxa calculada sobre ofertas observadas. Não representa taxa de aceitação, conclusão ou ganho realizado.",
  };
}
