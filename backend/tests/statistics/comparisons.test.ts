import test from "node:test";
import assert from "node:assert/strict";
import { planSmartComparison, comparableMetric, supportsExactWindowComparison } from "../../src/intelligence/statistics-comparisons";

const zone = "America/Sao_Paulo";
const now = new Date("2026-10-13T15:00:00.000Z"); // Tue, 12:00 local

test("today vs yesterday uses same elapsed local daypart", () => {
  const p = planSmartComparison("today_yesterday", zone, now);
  assert.equal(p.current.from, "2026-10-13T03:00:00.000Z");
  assert.equal(p.current.to, now.toISOString());
  assert.equal(p.references.length, 1);
  assert.equal(p.references[0].from, "2026-10-12T03:00:00.000Z");
  assert.equal(p.references[0].to, "2026-10-12T15:00:00.000Z");
});

test("same weekday compares four prior Tuesdays, not arbitrary weekdays", () => {
  const p = planSmartComparison("same_weekday", zone, now);
  assert.equal(p.references.length, 4);
  assert.deepEqual(p.references.map((v) => v.label),
    ["2026-10-06", "2026-09-29", "2026-09-22", "2026-09-15"]);
  assert.ok(p.references.every((v) => new Date(v.to).getTime() - new Date(v.from).getTime() === 12 * 60 * 60_000));
});

test("personal average uses seven comparable prior dayparts", () => {
  const p = planSmartComparison("today_personal_average", zone, now);
  assert.equal(p.references.length, 7);
  assert.equal(p.aggregation, "personal_mean");
  assert.equal(p.references[0].label, "2026-10-12");
  assert.equal(p.references[6].label, "2026-10-06");
});

test("week prior aligns Monday and current partial duration", () => {
  const p = planSmartComparison("week_previous", zone, now);
  assert.equal(p.current.from, "2026-10-12T03:00:00.000Z");
  assert.equal(p.current.to, now.toISOString());
  assert.equal(p.references[0].from, "2026-10-05T03:00:00.000Z");
  assert.equal(p.references[0].to, "2026-10-06T15:00:00.000Z");
});

test("missing data and financial coverage prevent fake comparable metrics", () => {
  const valid = { status: "ok" as const, semantic: "REALIZED_REVENUE", value: 100, coverage: 1 };
  assert.deepEqual(comparableMetric(valid, [{ ...valid, value: 50 }]), {
    current: 100, reference: 50, absolute: 50, percentage: 100, comparable: true,
  });
  assert.equal(comparableMetric(valid, [{ ...valid, value: 50, coverage: 0.5 }]).comparable, false);
  assert.equal(comparableMetric({ ...valid, value: null }, [valid]).comparable, false);
  assert.equal(comparableMetric(valid, [{ ...valid, value: 0 }]).percentage, null);
  assert.equal(comparableMetric(valid, [{ ...valid, semantic: "OBSERVED_OFFER" }]).comparable, false);
});

test("partial windows compare only timestamp-scoped observed metrics", () => {
  assert.equal(supportsExactWindowComparison("OBSERVED_OFFER"), true);
  for (const semantic of ["REALIZED_REVENUE", "REALIZED_RIDE", "ACTUAL_DISTANCE", "ACTUAL_COST", "ESTIMATED_COST", "MIXED"]) {
    assert.equal(supportsExactWindowComparison(semantic), false, semantic);
  }
});

test("day-part window clamps at actual end of day over DST boundaries", () => {
  const p = planSmartComparison("today_yesterday", "America/New_York", new Date("2026-03-08T23:30:00Z"));
  assert.ok(p.references.every((r) => new Date(r.to).getTime() <= new Date(r.from).getTime() + 24 * 60 * 60_000));
  assert.ok(new Date(p.current.to).getTime() > new Date(p.current.from).getTime());
});
