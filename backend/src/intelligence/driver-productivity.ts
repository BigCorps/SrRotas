/** Driver-facing productivity computed only from already scoped journeys.
 * These are journeys STARTED in the selected interval; full journey durations
 * may cross its end. No offer is treated as a completed trip or as revenue.
 */
export type ProductivityJourney = { started_at: string; ended_at: string | null };

export type DriverProductivity = {
  closedJourneyHours: number | null;
  closedJourneyCount: number;
  journeyCount: number;
  allJourneysClosed: boolean;
  revenuePerHour: number | null;
  revenuePerKm: number | null;
};

export function driverProductivity(input: {
  journeys: ProductivityJourney[];
  realizedRevenue: number;
  completedRides: number;
  ridesWithKnownFare: number;
  distanceKm: number;
  journeysWithOdometer: number;
  journeyListLimited?: boolean;
}): DriverProductivity {
  const {
    journeys, realizedRevenue, completedRides, ridesWithKnownFare,
    distanceKm, journeysWithOdometer, journeyListLimited = false,
  } = input;
  let durationMs = 0;
  let closedJourneyCount = 0;
  for (const journey of journeys) {
    const start = Date.parse(journey.started_at);
    const end = journey.ended_at ? Date.parse(journey.ended_at) : Number.NaN;
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue;
    durationMs += end - start;
    closedJourneyCount++;
  }
  const journeyCount = journeys.length;
  const fullScope = journeyCount > 0 && !journeyListLimited &&
    closedJourneyCount === journeyCount;
  const closedJourneyHours = closedJourneyCount > 0
    ? Math.round(durationMs / 36_000) / 100 : null;
  const allFaresKnown = Number.isSafeInteger(completedRides) && completedRides > 0 &&
    ridesWithKnownFare === completedRides &&
    Number.isFinite(realizedRevenue) && realizedRevenue >= 0;
  const eligible = fullScope && allFaresKnown;
  const hours = durationMs / 3_600_000;
  const revenuePerHour = eligible && hours > 0
    ? Math.round(realizedRevenue / hours * 100) / 100 : null;
  const allOdometers = journeysWithOdometer === journeyCount &&
    Number.isFinite(distanceKm) && distanceKm > 0;
  const revenuePerKm = eligible && allOdometers
    ? Math.round(realizedRevenue / distanceKm * 100) / 100 : null;
  return {
    closedJourneyHours,
    closedJourneyCount,
    journeyCount,
    allJourneysClosed: fullScope,
    revenuePerHour,
    revenuePerKm,
  };
}

/**
 * The inherited journey-readers only fetch the 150 most recent journeys over
 * at most 90 days. Reconciled totals cannot be labelled full-period metrics
 * if this bounded reader might omit an earlier journey from the chosen range.
 * A one-day lookback margin avoids claiming completeness near the 90-day edge.
 */
export function journeySourceCoverage(input: {
  periodStartMs: number;
  nowMs: number;
  periodJourneyCount: number;
  readerJourneyCount: number;
}): boolean {
  const { periodStartMs, nowMs, periodJourneyCount, readerJourneyCount } = input;
  const ageMs = nowMs - periodStartMs;
  return [periodStartMs, nowMs].every(Number.isFinite) &&
    Number.isSafeInteger(periodJourneyCount) && periodJourneyCount > 0 && periodJourneyCount < 150 &&
    Number.isSafeInteger(readerJourneyCount) && readerJourneyCount === periodJourneyCount &&
    ageMs >= 0 && ageMs <= 89 * 86_400_000;
}
