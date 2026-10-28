import { serverEnv } from "./env";
import { parseKnownFare } from "./journey-fare-coverage";
import { listJourneyMetrics } from "./journey-metrics-026";
import { listJourneyRealized0262 } from "./journey-realized-0262";
import { adminSupabase } from "./supabase";
import {
  buildStatisticsSeries, MAX_PERIOD_JOURNEYS, revenueComparison, revenueInsight,
  type JourneyInput, type RealizedTotals, type RideInput,
} from "./intelligence/statistics-series";

/** Read-only loader for `/api/v1/statistics/series`. Every query is scoped by
 * driver_id; the realized totals come from the same canonical reader used by
 * the performance snapshot. No writes, no persisted aggregates. */
const OUTCOME_LIMIT = 1000;
const MAX_OFFER_ROWS = 50_000;

function lookbackDays(fromIso: string, nowMs: number) {
  return Math.max(1, Math.min(90, Math.ceil((nowMs - Date.parse(fromIso)) / 86_400_000) + 1));
}

async function periodJourneys(driverId: string, from: string, to: string) {
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .select("id,started_at,ended_at")
    .eq("driver_id", driverId)
    .gte("started_at", from)
    .lt("started_at", to)
    .order("started_at", { ascending: true })
    .limit(MAX_PERIOD_JOURNEYS);
  if (error) throw new Error(error.message);
  return (data ?? []).map((row: any) => ({
    id: String(row.id), started_at: String(row.started_at), ended_at: row.ended_at ? String(row.ended_at) : null,
  })) as JourneyInput[];
}

/** Same outcome/fare rule as listJourneyRealized0262, one row per completed ride. */
async function completedRides(driverId: string, journeyIds: string[]) {
  if (!journeyIds.length) return { rides: [] as RideInput[], limited: false };
  const outcomes = await adminSupabase()
    .from("ride_outcomes")
    .select("journey_id,ride_offer_id,local_offer_id")
    .eq("driver_id", driverId)
    .eq("status", "COMPLETED")
    .neq("source", "uber_history_ocr")
    .in("journey_id", journeyIds)
    .limit(OUTCOME_LIMIT);
  if (outcomes.error) throw new Error(outcomes.error.message);
  const rows = (outcomes.data ?? []) as Array<{ journey_id: string | null; ride_offer_id: number | string | null; local_offer_id: string | null }>;
  const offerIds = [...new Set(rows.map((r) => r.ride_offer_id).filter((v) => v !== null).map(String))];
  const localIds = [...new Set(rows.map((r) => r.local_offer_id).filter((v): v is string => !!v))];
  type OfferRow = { id: number | string; local_offer_id: string | null; fare: unknown; service_type: string | null; total_km: unknown };
  const offers: OfferRow[] = [];
  const select = "id,local_offer_id,fare,service_type,total_km";
  if (offerIds.length) {
    const found = await adminSupabase().from("ride_offers").select(select).eq("driver_id", driverId).in("id", offerIds);
    if (found.error) throw new Error(found.error.message);
    offers.push(...((found.data ?? []) as OfferRow[]));
  }
  if (localIds.length) {
    const found = await adminSupabase().from("ride_offers").select(select).eq("driver_id", driverId).in("local_offer_id", localIds);
    if (found.error) throw new Error(found.error.message);
    offers.push(...((found.data ?? []) as OfferRow[]));
  }
  const byId = new Map(offers.map((o) => [String(o.id), o]));
  const byLocal = new Map(offers.filter((o) => o.local_offer_id).map((o) => [String(o.local_offer_id), o]));
  const rides: RideInput[] = [];
  for (const outcome of rows) {
    const journeyId = String(outcome.journey_id ?? "");
    if (!journeyId) continue;
    const byOffer = outcome.ride_offer_id !== null ? byId.get(String(outcome.ride_offer_id)) : undefined;
    const byOfferFare = byOffer ? parseKnownFare(byOffer.fare) : undefined;
    const local = outcome.local_offer_id ? byLocal.get(outcome.local_offer_id) : undefined;
    const offer = typeof byOfferFare === "number" ? byOffer : local ?? byOffer;
    const fare = typeof byOfferFare === "number" ? byOfferFare : local ? parseKnownFare(local.fare) : null;
    const km = Number(offer?.total_km);
    rides.push({
      journey_id: journeyId,
      fare,
      service_type: offer?.service_type ? String(offer.service_type) : null,
      offer_total_km: offer && offer.total_km !== null && Number.isFinite(km) ? km : null,
    });
  }
  return { rides, limited: rows.length >= OUTCOME_LIMIT };
}

async function offerTimes(driverId: string, from: string, to: string) {
  const times: string[] = [];
  const page = 1000;
  for (let offset = 0; offset < MAX_OFFER_ROWS; offset += page) {
    const { data, error } = await adminSupabase()
      .from("sr_personal_offer_canonical_v1")
      .select("observed_at")
      .eq("driver_id", driverId)
      .gte("observed_at", from)
      .lt("observed_at", to)
      .order("observed_at", { ascending: true })
      .range(offset, offset + page - 1);
    if (error) throw new Error(error.message);
    const batch = (data ?? []) as Array<{ observed_at: string }>;
    times.push(...batch.map((r) => String(r.observed_at)));
    if (batch.length < page) return { times, truncated: false };
  }
  return { times, truncated: true };
}

async function loadPeriod(driverId: string, from: string, to: string, nowMs: number, withOffers: boolean) {
  const days = lookbackDays(from, nowMs);
  const [journeys, realizedResult, metricsResult, offers] = await Promise.all([
    periodJourneys(driverId, from, to),
    listJourneyRealized0262(driverId, days),
    listJourneyMetrics(driverId, days),
    withOffers ? offerTimes(driverId, from, to) : Promise.resolve({ times: [] as string[], truncated: false }),
  ]);
  const ids = new Set(journeys.map((j) => j.id));
  const realized = new Map<string, RealizedTotals>();
  for (const item of (realizedResult.items ?? []) as any[]) {
    const id = String(item.journey_id);
    if (!ids.has(id)) continue;
    realized.set(id, {
      completed: Math.max(0, Number(item.completed_trips || 0)),
      revenue: Math.max(0, Number(item.realized_revenue || 0)),
      matched: Math.max(0, Number(item.fare_matched_trips || 0)),
    });
  }
  const distances = new Map<string, number | null>();
  for (const row of (metricsResult.metrics ?? []) as any[]) {
    const id = String(row.journey_id);
    if (!ids.has(id)) continue;
    const value = row.distance_km === null || row.distance_km === undefined || row.distance_km === "" ? null : Number(row.distance_km);
    distances.set(id, value !== null && Number.isFinite(value) && value >= 0 ? value : null);
  }
  const { rides, limited } = await completedRides(driverId, [...realized.keys()]);
  return buildStatisticsSeries({
    period: { from, to }, timeZone: serverEnv().timezone, nowMs,
    journeys, journeyListLimited: journeys.length >= MAX_PERIOD_JOURNEYS,
    realized, rides, ridesLimited: limited, distances,
    offerTimes: offers.times, offersTruncated: offers.truncated,
  });
}

export async function statisticsSeries(driverId: string, from: string, to: string, nowMs = Date.now()) {
  const fromMs = Date.parse(from);
  const toMs = Date.parse(to);
  const durationMs = toMs - fromMs;
  const previousFrom = new Date(fromMs - durationMs).toISOString();
  const current = await loadPeriod(driverId, from, to, nowMs, true);
  const days = Math.max(1, Math.round(durationMs / 86_400_000));
  // The previous window is only read when the current one could be compared.
  const previous = current.series.realized_revenue.status === "complete" && nowMs - fromMs + durationMs <= 89 * 86_400_000
    ? await loadPeriod(driverId, previousFrom, from, nowMs, false)
    : null;
  const comparison = revenueComparison(
    current.series.realized_revenue,
    previous ? previous.series.realized_revenue : null,
    { from: previousFrom, to: from },
  );
  return { ...current, comparison, insight: revenueInsight(current.series.realized_revenue, comparison, days) };
}
