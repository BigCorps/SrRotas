import test from "node:test";
import assert from "node:assert/strict";
import { buildPerformanceSnapshot, PERFORMANCE_KEYS } from "../../src/intelligence/performance-snapshot";
import type { StructuredAnswer } from "../../src/intelligence/contracts";

const period = { kind: "LAST_N_DAYS", label: "Últimos 7 dias", from: "2026-10-17T00:00:00Z", to: "2026-10-24T00:00:00Z" } as const;
function answer(intent: StructuredAnswer["intent"], metrics: Array<{ key: string; value: number | null; formatted: string; semantic: "OBSERVED_OFFER" | "REALIZED_REVENUE" | "REALIZED_RIDE" | "ESTIMATED_COST" | "ACTUAL_DISTANCE" | "MIXED" }>, status: StructuredAnswer["status"]="ok", completeness: "complete" | "partial"="complete", coverage: number | null = 1): StructuredAnswer {
  return { intent, metrics, status, evidence: { source: "canonical", sampleCount: 24, completeness, period, coverage: { value: coverage } } } as StructuredAnswer;
}
const metric=(key:string,value:number|null,semantic:"OBSERVED_OFFER" | "REALIZED_REVENUE" | "REALIZED_RIDE" | "ESTIMATED_COST" | "ACTUAL_DISTANCE" | "MIXED"="OBSERVED_OFFER")=>({key,value,semantic,formatted:value===null?"—":String(value)});

test("a single typed snapshot preserves period, semantics, provenance and all mockup keys", () => {
  const snapshot=buildPerformanceSnapshot([
    answer("PERIOD_SUMMARY", [
      metric("offers",186),metric("avg_per_km",2.71),metric("avg_per_hour",39.8),
      metric("realized_revenue",1248,"REALIZED_REVENUE"),
      metric("completed_rides",31,"REALIZED_RIDE"),metric("actual_distance",426,"ACTUAL_DISTANCE"),
      metric("closed_journey_hours",29.4,"MIXED"),metric("realized_revenue_per_hour",42.45,"REALIZED_REVENUE"),
      metric("realized_revenue_per_km",2.93,"REALIZED_REVENUE"),
    ]),
    answer("PROFITABILITY",[metric("estimated_profit",842,"ESTIMATED_COST")]),
    answer("OFFER_QUALITY",[metric("good_rate",67)]),
  ]);
  assert.equal(snapshot.period?.label,"Últimos 7 dias");
  assert.deepEqual(Object.keys(snapshot.metrics), [...PERFORMANCE_KEYS]);
  assert.equal(snapshot.metrics.realized_revenue.semantic,"REALIZED_REVENUE");
  assert.equal(snapshot.metrics.offers.semantic,"OBSERVED_OFFER");
  assert.equal(snapshot.metrics.estimated_profit.status,"available");
  assert.equal(snapshot.metrics.good_rate.formatted,"67");
  assert.equal(snapshot.metrics.offers.source,"canonical");
});

test("partial profit, unlinked fares, no odometer and insufficient quality never become numbers", () => {
  const s=buildPerformanceSnapshot([
    answer("PERIOD_SUMMARY",[
      metric("completed_rides",2,"REALIZED_RIDE"),metric("realized_revenue",600,"REALIZED_REVENUE"),
      metric("actual_distance",0,"ACTUAL_DISTANCE"),metric("realized_revenue_per_km",null,"REALIZED_REVENUE"),
    ],"ok","partial",0),
    answer("PROFITABILITY",[metric("estimated_profit",390,"ESTIMATED_COST")],"ok","partial"),
    answer("OFFER_QUALITY",[metric("good_rate",75)],"insufficient"),
  ]);
  for (const key of ["realized_revenue","actual_distance","estimated_profit","good_rate","realized_revenue_per_km"] as const) {
    assert.equal(s.metrics[key].formatted,"—",key);
    assert.equal(s.metrics[key].value,null,key);
  }
  assert.equal(s.metrics.completed_rides.value,2);
});
