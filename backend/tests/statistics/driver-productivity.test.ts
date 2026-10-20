import test from "node:test";
import assert from "node:assert/strict";
import { driverProductivity } from "../../src/intelligence/driver-productivity";

const j1 = { started_at: "2026-10-20T10:00:00.000Z", ended_at: "2026-10-20T12:00:00.000Z" };
const j2 = { started_at: "2026-10-20T13:00:00.000Z", ended_at: "2026-10-20T14:30:00.000Z" };

test("only verified, complete journeys and fare coverage produce revenue rates", () => {
  const result = driverProductivity({
    journeys: [j1, j2],
    completedRides: 5,
    ridesWithKnownFare: 5,
    realizedRevenue: 350,
    distanceKm: 100,
    journeysWithOdometer: 2,
  });
  assert.deepEqual(result, {
    closedJourneyHours: 3.5,
    closedJourneyCount: 2,
    journeyCount: 2,
    allJourneysClosed: true,
    revenuePerHour: 100,
    revenuePerKm: 3.5,
  });
});

test("open journeys mean no complete-period rates, but closed hours remain labelled partial", () => {
  const result = driverProductivity({
    journeys: [j1, { ...j2, ended_at: null }],
    completedRides: 5,
    ridesWithKnownFare: 5,
    realizedRevenue: 350,
    distanceKm: 100,
    journeysWithOdometer: 2,
  });
  assert.equal(result.closedJourneyHours, 2);
  assert.equal(result.allJourneysClosed, false);
  assert.equal(result.revenuePerHour, null);
  assert.equal(result.revenuePerKm, null);
});

test("revenue not fully reconciled never becomes a confirmed rate", () => {
  const result = driverProductivity({
    journeys: [j1], completedRides: 3, ridesWithKnownFare: 2,
    realizedRevenue: 90, distanceKm: 40, journeysWithOdometer: 1,
  });
  assert.equal(result.revenuePerHour, null);
  assert.equal(result.revenuePerKm, null);
});

test("missing odometer only blocks per km; measured hours can be used for per hour", () => {
  const result = driverProductivity({
    journeys: [j1], completedRides: 3, ridesWithKnownFare: 3,
    realizedRevenue: 90, distanceKm: 40, journeysWithOdometer: 0,
  });
  assert.equal(result.revenuePerHour, 45);
  assert.equal(result.revenuePerKm, null);
});

test("no journeys, invalid dates, zero distance or capped list cannot pretend completeness", () => {
  const common = {
    completedRides: 1, ridesWithKnownFare: 1, realizedRevenue: 40,
    distanceKm: 10, journeysWithOdometer: 1,
  };
  assert.equal(driverProductivity({ ...common, journeys: [] }).revenuePerHour, null);
  const invalid = driverProductivity({ ...common,
    journeys: [{ started_at: "2026-10-20T12:00:00Z", ended_at: "2026-10-20T11:00:00Z" }],
  });
  assert.equal(invalid.closedJourneyHours, null);
  assert.equal(invalid.revenuePerHour, null);
  assert.equal(driverProductivity({ ...common, journeys: [j1], distanceKm: 0 }).revenuePerKm, null);
  assert.equal(driverProductivity({ ...common, journeys: [j1], journeyListLimited: true }).revenuePerHour, null);
});

test("a recorded zero fare stays zero only if fully reconciled", () => {
  const result = driverProductivity({
    journeys: [j1], completedRides: 1, ridesWithKnownFare: 1,
    realizedRevenue: 0, distanceKm: 12, journeysWithOdometer: 1,
  });
  assert.equal(result.revenuePerHour, 0);
  assert.equal(result.revenuePerKm, 0);
});
