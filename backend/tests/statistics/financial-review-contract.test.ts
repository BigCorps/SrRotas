import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const domain = readFileSync(new URL("../../src/intelligence/analytics-domain.ts", import.meta.url), "utf8");

test("estimated operating result per hour requires full realized and odometer coverage", () => {
  assert.match(domain, /const completeFinancialScope = verifiedRates\.revenuePerHour !== null && verifiedRates\.revenuePerKm !== null/);
  assert.match(domain, /const profitPerHour = completeFinancialScope && bundle\.estimatedProfit !== null && workHours > 0/);
  assert.match(domain, /completeness: completeFinancialScope && bundle\.estimatedProfit !== null \? "complete" : "partial"/);
});

test("best journey is not ranked when confirmed fares or journey scope are incomplete", () => {
  assert.match(domain, /if \(!canRankBestJourney\(\{/);
  assert.match(domain, /sourceComplete: realized\.sourceComplete/);
  assert.match(domain, /completedRides: realized\.completed/);
  assert.match(domain, /ridesWithKnownFare: realized\.matched/);
});
