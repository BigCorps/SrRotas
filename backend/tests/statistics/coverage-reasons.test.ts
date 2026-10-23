import test from "node:test";
import assert from "node:assert/strict";
import { reasonsForUnavailableRates, type PeriodRateEvidence } from "../../app/app/estatisticas/coverage-reasons";

const metrics = [
  { key: "realized_revenue_per_hour", value: null, formatted: "—" },
  { key: "realized_revenue_per_km", value: null, formatted: "—" },
];
const complete = {
  metrics,
  details: {
    realized: { sourceComplete: true, completed: 2, matched: 2 },
    productivity: { journeyCount: 2, closedJourneyCount: 2 },
    distance_coverage: { numerator: 2, denominator: 2 },
  },
} satisfies PeriodRateEvidence;

test("full coverage with measured rates needs no warnings", () => {
  const summary = {
    ...complete,
    metrics: [
      { key: "realized_revenue_per_hour", value: 45, formatted: "R$ 45/h" },
      { key: "realized_revenue_per_km", value: 2, formatted: "R$ 2/km" },
    ],
  };
  assert.deepEqual(reasonsForUnavailableRates(summary), []);
});

test("missing odometer explains km gap without falsely claiming no fares", () => {
  const summary = { ...complete, details: {
    ...complete.details, distance_coverage: { numerator: 1, denominator: 2 },
  }, metrics: [{ key: "realized_revenue_per_hour", value: 45, formatted: "R$ 45/h" }, metrics[1]] };
  const reasons = reasonsForUnavailableRates(summary);
  assert.equal(reasons.length, 1);
  assert.match(reasons[0], /odômetro/);
});

test("partial reader, open journey and missing fare have separate concrete explanations", () => {
  const summary = { ...complete, details: {
    realized: { sourceComplete: false, completed: 2, matched: 1 },
    productivity: { journeyCount: 2, closedJourneyCount: 1 },
    distance_coverage: { numerator: 1, denominator: 2 },
  }};
  const reasons = reasonsForUnavailableRates(summary);
  assert.equal(reasons.length, 4);
  assert.ok(reasons.some((r) => r.includes("150 jornadas")));
  assert.ok(reasons.some((r) => r.includes("abertas")));
  assert.ok(reasons.some((r) => r.includes("tarifa")));
  assert.ok(reasons.some((r) => r.includes("odômetro")));
});

test("empty period and missing detail payloads never invent a numerical rate", () => {
  assert.match(reasonsForUnavailableRates({ metrics, details: {
    realized: { sourceComplete: false, completed: 0, matched: 0 },
    productivity: { journeyCount: 0, closedJourneyCount: 0 },
  } })[0], /Não há jornadas/);
  assert.match(reasonsForUnavailableRates({ metrics })[0], /amostra ainda não atende/);
  assert.deepEqual(reasonsForUnavailableRates(undefined), []);
});
