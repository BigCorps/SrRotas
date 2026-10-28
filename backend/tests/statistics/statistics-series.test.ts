import test from "node:test";
import assert from "node:assert/strict";
import {
  buildStatisticsSeries, dailyBuckets, localDayKey, revenueComparison, revenueInsight,
  type SeriesInput, type RealizedTotals,
} from "../../src/intelligence/statistics-series";

const TZ = "America/Sao_Paulo";
// 7-day rolling window ending 2026-10-24 15:00 local (18:00Z).
const period = { from: "2026-10-17T18:00:00.000Z", to: "2026-10-24T18:00:00.000Z" };
const now = Date.parse("2026-10-24T18:00:00.000Z");

function input(overrides: Partial<SeriesInput> = {}): SeriesInput {
  return {
    period, timeZone: TZ, nowMs: now,
    journeys: [], journeyListLimited: false,
    realized: new Map(), rides: [], ridesLimited: false,
    distances: new Map(), offerTimes: [], offersTruncated: false,
    ...overrides,
  };
}
const totals = (completed: number, revenue: number, matched = completed): RealizedTotals => ({ completed, revenue, matched });

test("7-day rolling window yields clipped local-day buckets with weekday labels", () => {
  const buckets = dailyBuckets(period.from, period.to, TZ);
  assert.equal(buckets.length, 8);
  assert.equal(buckets[0].key, "2026-10-17");
  assert.equal(buckets[0].label, "Sáb");
  assert.equal(buckets[0].partialWindow, true);
  assert.equal(buckets[0].from, period.from);
  assert.equal(buckets[1].partialWindow, false);
  assert.equal(buckets[1].from, "2026-10-18T03:00:00.000Z");
  assert.equal(buckets.at(-1)!.to, period.to);
  assert.equal(buckets.at(-1)!.partialWindow, true);
});

test("30 and 90 day windows use date labels; longer windows are refused", () => {
  const b30 = dailyBuckets("2026-09-24T18:00:00.000Z", period.to, TZ);
  assert.equal(b30.length, 31);
  assert.match(b30[3].label, /^\d{2}\/\d{2}$/);
  const b90 = dailyBuckets("2026-07-26T18:00:00.000Z", period.to, TZ);
  assert.equal(b90.length, 91);
  assert.throws(() => dailyBuckets("2026-07-01T00:00:00.000Z", period.to, TZ), /series_period_too_long/);
  assert.throws(() => dailyBuckets(period.to, period.from, TZ), /series_period_invalid/);
});

test("day boundary follows the driver's time zone, not UTC", () => {
  // 01:30Z on the 20th is still the 19th in São Paulo (UTC-3).
  assert.equal(localDayKey("2026-10-20T01:30:00.000Z", TZ), "2026-10-19");
  const s = buildStatisticsSeries(input({
    journeys: [{ id: "j1", started_at: "2026-10-20T01:30:00.000Z", ended_at: "2026-10-20T03:30:00.000Z" }],
    realized: new Map([["j1", totals(2, 50)]]),
    rides: [{ journey_id: "j1", fare: 20, service_type: "uberx", offer_total_km: 4 }, { journey_id: "j1", fare: 30, service_type: "uberx", offer_total_km: 12 }],
    distances: new Map([["j1", 40]]),
  }));
  const day = s.series.realized_revenue.buckets.find((b) => b.key === "2026-10-19")!;
  assert.equal(day.value, 50);
  assert.equal(s.series.realized_revenue.buckets.find((b) => b.key === "2026-10-20")!.value, 0);
});

test("complete daily revenue reproduces canonical totals; zero only for known empty days", () => {
  const s = buildStatisticsSeries(input({
    journeys: [
      { id: "a", started_at: "2026-10-18T12:00:00.000Z", ended_at: "2026-10-18T16:00:00.000Z" },
      { id: "b", started_at: "2026-10-22T12:00:00.000Z", ended_at: "2026-10-22T20:00:00.000Z" },
    ],
    realized: new Map([["a", totals(3, 90.5)], ["b", totals(2, 112.3)]]),
    rides: [
      { journey_id: "a", fare: 40, service_type: "uberx", offer_total_km: 3 },
      { journey_id: "a", fare: 41.6, service_type: "comfort", offer_total_km: 8 },
      { journey_id: "a", fare: 8.9, service_type: "uberx", offer_total_km: null },
      { journey_id: "b", fare: 100, service_type: "uberx", offer_total_km: 25 },
      { journey_id: "b", fare: 12.3, service_type: "uberx", offer_total_km: 6 },
    ],
    distances: new Map([["a", 60], ["b", 120]]),
  }));
  const rev = s.series.realized_revenue;
  assert.equal(rev.status, "complete");
  assert.equal(rev.completeness, "complete");
  assert.equal(rev.total, 202.8);
  assert.equal(rev.buckets.reduce((a, b) => a + (b.value ?? 0), 0).toFixed(2), "202.80");
  const empty = rev.buckets.find((b) => b.key === "2026-10-20")!;
  assert.deepEqual([empty.value, empty.status], [0, "no_activity"]);
  assert.equal(s.series.closed_journey_hours.total, 12);
  assert.equal(s.series.actual_distance.total, 180);
  assert.deepEqual([s.rides.completed, s.rides.maxFare, s.rides.minFare, s.rides.averageFare], [5, 100, 8.9, 40.56]);
  assert.equal(s.breakdowns!.service_type[0].label, "UberX");
  assert.equal(s.breakdowns!.distance_band.find((g) => g.key === "unknown")!.rides, 1);
});

test("unreconciled fares stay partial and never fill the gap with zero", () => {
  const s = buildStatisticsSeries(input({
    journeys: [
      { id: "a", started_at: "2026-10-19T12:00:00.000Z", ended_at: "2026-10-19T14:00:00.000Z" },
      { id: "b", started_at: "2026-10-21T12:00:00.000Z", ended_at: "2026-10-21T14:00:00.000Z" },
    ],
    realized: new Map([["a", totals(3, 60, 2)], ["b", totals(2, 0, 0)]]),
    rides: [
      { journey_id: "a", fare: 30, service_type: null, offer_total_km: null },
      { journey_id: "a", fare: 30, service_type: null, offer_total_km: null },
      { journey_id: "a", fare: null, service_type: null, offer_total_km: null },
      { journey_id: "b", fare: null, service_type: null, offer_total_km: null },
      { journey_id: "b", fare: null, service_type: null, offer_total_km: null },
    ],
  }));
  const rev = s.series.realized_revenue;
  assert.equal(rev.status, "partial");
  assert.equal(rev.completeness, "partial");
  const a = rev.buckets.find((b) => b.key === "2026-10-19")!;
  assert.deepEqual([a.value, a.status, a.coverage], [60, "partial", 0.6667]);
  const b = rev.buckets.find((x) => x.key === "2026-10-21")!;
  assert.deepEqual([b.value, b.status], [null, "unavailable"]);
  assert.equal(s.rides.status, "partial");
});

test("open journeys keep hours partial and do not count as zero", () => {
  const s = buildStatisticsSeries(input({
    journeys: [
      { id: "a", started_at: "2026-10-23T12:00:00.000Z", ended_at: null },
      { id: "b", started_at: "2026-10-22T12:00:00.000Z", ended_at: null },
      { id: "c", started_at: "2026-10-22T18:00:00.000Z", ended_at: "2026-10-22T21:00:00.000Z" },
    ],
    realized: new Map([["a", totals(0, 0)], ["b", totals(0, 0)], ["c", totals(0, 0)]]),
  }));
  const h = s.series.closed_journey_hours;
  assert.equal(h.status, "partial");
  assert.deepEqual([h.buckets.find((b) => b.key === "2026-10-23")!.value, h.buckets.find((b) => b.key === "2026-10-23")!.status], [null, "unavailable"]);
  assert.deepEqual([h.buckets.find((b) => b.key === "2026-10-22")!.value, h.buckets.find((b) => b.key === "2026-10-22")!.status], [3, "partial"]);
  // No completed ride at all: revenue unavailable, not a zero total.
  assert.equal(s.series.realized_revenue.status, "unavailable");
  assert.equal(s.series.realized_revenue.total, null);
});

test("journeys outside the canonical reader make days unknown instead of zero", () => {
  const s = buildStatisticsSeries(input({
    journeys: [{ id: "a", started_at: "2026-10-19T12:00:00.000Z", ended_at: "2026-10-19T13:00:00.000Z" }],
    realized: new Map(),
  }));
  assert.equal(s.coverage.sourceComplete, false);
  assert.ok(s.series.realized_revenue.buckets.every((b) => b.value === null && b.status === "unavailable"));
});

test("limited journey list never claims complete history", () => {
  const s = buildStatisticsSeries(input({ journeyListLimited: true }));
  assert.ok(s.series.closed_journey_hours.buckets.every((b) => b.value === null));
  assert.ok(s.series.realized_revenue.buckets.every((b) => b.status === "unavailable"));
});

test("observed offers (including V7 history) are a separate series, never revenue", () => {
  const s = buildStatisticsSeries(input({
    offerTimes: ["2026-10-18T12:00:00.000Z", "2026-10-18T13:00:00.000Z", "2026-10-20T12:00:00.000Z", "2026-10-01T00:00:00.000Z"],
  }));
  assert.equal(s.series.observed_offers.semantic, "OBSERVED_OFFER");
  assert.equal(s.series.observed_offers.total, 3);
  assert.equal(s.series.realized_revenue.total, null);
  assert.equal(s.series.realized_revenue.status, "unavailable");
  const t = buildStatisticsSeries(input({ offerTimes: ["2026-10-18T12:00:00.000Z"], offersTruncated: true }));
  assert.equal(t.series.observed_offers.status, "partial");
  assert.equal(t.series.observed_offers.buckets.find((b) => b.key === "2026-10-19")!.value, null);
});

test("inconsistent per-ride rows withhold extremes and breakdowns", () => {
  const s = buildStatisticsSeries(input({
    journeys: [{ id: "a", started_at: "2026-10-19T12:00:00.000Z", ended_at: "2026-10-19T13:00:00.000Z" }],
    realized: new Map([["a", totals(2, 50)]]),
    rides: [{ journey_id: "a", fare: 20, service_type: null, offer_total_km: null }],
  }));
  assert.equal(s.rides.consistent, false);
  assert.equal(s.rides.maxFare, null);
  assert.equal(s.rides.averageFare, 25);
  assert.equal(s.breakdowns, null);
});

test("comparison and insight require complete evidence in both windows", () => {
  const complete = { status: "complete" as const, completeness: "complete", total: 1248 };
  const c = revenueComparison(complete, { status: "complete", completeness: "complete", total: 1114.29 }, { from: "a", to: "b" });
  assert.equal(c.eligible, true);
  assert.equal(c.changePct, 12);
  assert.equal(revenueComparison(complete, { status: "partial", completeness: "partial", total: 900 }, null).eligible, false);
  assert.equal(revenueComparison(complete, { status: "complete", completeness: "complete", total: 0 }, null).changePct, null);
  assert.equal(revenueComparison({ ...complete, status: "partial" }, complete, null).eligible, false);
  const empty = buildStatisticsSeries(input()).series.realized_revenue;
  assert.equal(revenueInsight(empty, revenueComparison(empty, null, null), 7), null);
  const insight = revenueInsight(empty, c, 7);
  assert.equal(insight?.text, "Suas tarifas de corridas concluídas foram 12% maiores que nos 7 dias anteriores.");
});
