import { journeySourceCoverage } from "./driver-productivity";

/** Daily read-only series for the native Statistics reports.
 * Pure aggregation over rows already scoped by the loader; nothing is
 * persisted and no global formula changes. Revenue buckets add up the SAME
 * per-journey canonical totals (`listJourneyRealized0262`) used by the
 * snapshot KPI, grouped by the local day the journey started. A null value
 * is a gap, never a zero; zero only appears with explicit eligibility. */
export const STATISTICS_SERIES_SCHEMA = "srrotas-statistics-series-v1" as const;
export const MAX_SERIES_DAYS = 91;
export const MAX_PERIOD_JOURNEYS = 500;

export type BucketStatus = "complete" | "partial" | "unavailable" | "no_activity";
export type SeriesStatus = "complete" | "partial" | "unavailable";
export type SeriesBucket = {
  key: string;
  from: string;
  to: string;
  label: string;
  weekday: string;
  date: string;
  partialWindow: boolean;
  value: number | null;
  status: BucketStatus;
  coverage: number | null;
  sampleCount: number;
};
export type DailySeries = {
  metric: "realized_revenue" | "completed_rides" | "closed_journey_hours" | "actual_distance" | "observed_offers";
  semantic: "REALIZED_REVENUE" | "REALIZED_RIDE" | "MIXED" | "ACTUAL_DISTANCE" | "OBSERVED_OFFER";
  unit: "BRL" | "rides" | "hours" | "km" | "offers";
  status: SeriesStatus;
  completeness: "complete" | "partial" | null;
  coverage: number | null;
  sampleCount: number;
  total: number | null;
  limitations: string[];
  buckets: SeriesBucket[];
};
export type JourneyInput = { id: string; started_at: string; ended_at: string | null };
export type RealizedTotals = { completed: number; revenue: number; matched: number };
export type RideInput = { journey_id: string; fare: number | null; service_type: string | null; offer_total_km: number | null };
export type GroupRow = { key: string; label: string; rides: number; ridesWithFare: number; revenue: number | null; averageFare: number | null };
export type RidesSummary = {
  status: SeriesStatus;
  completed: number;
  withKnownFare: number;
  averageFare: number | null;
  maxFare: number | null;
  minFare: number | null;
  consistent: boolean;
};
export type SeriesInput = {
  period: { from: string; to: string };
  timeZone: string;
  nowMs: number;
  journeys: JourneyInput[];
  journeyListLimited: boolean;
  /** Journey ids also returned by the canonical realized reader (150 / 90 d). */
  realized: Map<string, RealizedTotals>;
  rides: RideInput[];
  ridesLimited: boolean;
  /** Valid odometer distance per journey (null/absent = missing). */
  distances: Map<string, number | null>;
  offerTimes: string[];
  offersTruncated: boolean;
};

const WEEKDAYS = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];

export function r2(value: number) {
  return Math.round(value * 100) / 100;
}

function localYmd(ms: number, timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" })
    .formatToParts(new Date(ms));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  return { year: get("year"), month: get("month"), day: get("day") };
}

/** UTC instant of local midnight, robust to DST transitions. */
export function localMidnightUtc(year: number, month: number, day: number, timeZone: string): number {
  let candidate = Date.UTC(year, month - 1, day, 0, 0, 0, 0);
  for (let i = 0; i < 3; i += 1) {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone, year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
    }).formatToParts(new Date(candidate));
    const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
    const asUtc = Date.UTC(get("year"), get("month") - 1, get("day"), get("hour") % 24, get("minute"), get("second"));
    candidate -= asUtc - Date.UTC(year, month - 1, day, 0, 0, 0, 0);
  }
  return candidate;
}

export function localDayKey(value: string | number, timeZone: string) {
  const { year, month, day } = localYmd(typeof value === "number" ? value : Date.parse(value), timeZone);
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/** Local calendar days intersecting [from, to), clipped to the period. */
export function dailyBuckets(from: string, to: string, timeZone: string) {
  const fromMs = Date.parse(from);
  const toMs = Date.parse(to);
  if (!Number.isFinite(fromMs) || !Number.isFinite(toMs) || fromMs >= toMs) throw new Error("series_period_invalid");
  if (toMs - fromMs > MAX_SERIES_DAYS * 86_400_000) throw new Error("series_period_too_long");
  const buckets: Array<Omit<SeriesBucket, "value" | "status" | "coverage" | "sampleCount">> = [];
  let { year, month, day } = localYmd(fromMs, timeZone);
  let start = localMidnightUtc(year, month, day, timeZone);
  while (start < toMs) {
    const next = new Date(Date.UTC(year, month - 1, day + 1, 12));
    const nextStart = localMidnightUtc(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate(), timeZone);
    const bucketFrom = Math.max(start, fromMs);
    const bucketTo = Math.min(nextStart, toMs);
    const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day, 12)).getUTCDay()];
    const date = `${String(day).padStart(2, "0")}/${String(month).padStart(2, "0")}`;
    buckets.push({
      key: `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`,
      from: new Date(bucketFrom).toISOString(),
      to: new Date(bucketTo).toISOString(),
      label: weekday,
      weekday,
      date,
      partialWindow: bucketFrom !== start || bucketTo !== nextStart,
    });
    start = nextStart;
    ({ year, month, day } = { year: next.getUTCFullYear(), month: next.getUTCMonth() + 1, day: next.getUTCDate() });
  }
  const labelByDate = buckets.length > 8;
  return buckets.map((b) => ({ ...b, label: labelByDate ? b.date : b.weekday }));
}

function seriesStatus(buckets: SeriesBucket[], anyValue: boolean): SeriesStatus {
  if (!anyValue) return "unavailable";
  return buckets.every((b) => b.status === "complete" || b.status === "no_activity") ? "complete" : "partial";
}

function ratio(numerator: number, denominator: number) {
  return denominator > 0 ? Math.round((numerator / denominator) * 10_000) / 10_000 : null;
}

export function buildStatisticsSeries(input: SeriesInput) {
  const { period, timeZone } = input;
  const buckets = dailyBuckets(period.from, period.to, timeZone);
  const fromMs = Date.parse(period.from);
  const toMs = Date.parse(period.to);
  const journeys = input.journeys.filter((j) => {
    const t = Date.parse(j.started_at);
    return Number.isFinite(t) && t >= fromMs && t < toMs;
  });
  const readerCount = journeys.filter((j) => input.realized.has(j.id)).length;
  const sourceComplete = !input.journeyListLimited && journeySourceCoverage({
    periodStartMs: fromMs, nowMs: input.nowMs, periodJourneyCount: journeys.length, readerJourneyCount: readerCount,
  });
  // No journey at all is a known absence only when the journey list itself is complete.
  const journeysKnown = !input.journeyListLimited;
  const byDay = new Map<string, JourneyInput[]>();
  for (const j of journeys) {
    const key = localDayKey(j.started_at, timeZone);
    byDay.set(key, [...(byDay.get(key) ?? []), j]);
  }
  const limitations: string[] = [];
  if (input.journeyListLimited) limitations.push(`O período tem ${MAX_PERIOD_JOURNEYS} jornadas ou mais; as séries ficam parciais.`);
  if (!sourceComplete && journeys.length > 0) limitations.push("A conciliação consulta até 150 jornadas recentes (máximo de 90 dias); dias fora desse alcance ficam sem valor.");

  // Revenue and completed rides: canonical per-journey totals only.
  let totalRevenue = 0, totalCompleted = 0, totalMatched = 0;
  const revenueBuckets: SeriesBucket[] = [];
  const ridesBuckets: SeriesBucket[] = [];
  for (const b of buckets) {
    const day = byDay.get(b.key) ?? [];
    if (!day.length) {
      const known = journeysKnown && sourceComplete;
      revenueBuckets.push({ ...b, value: known ? 0 : null, status: known ? "no_activity" : "unavailable", coverage: null, sampleCount: 0 });
      ridesBuckets.push({ ...b, value: known ? 0 : null, status: known ? "no_activity" : "unavailable", coverage: null, sampleCount: 0 });
      continue;
    }
    const inReader = day.filter((j) => input.realized.has(j.id));
    const allInReader = inReader.length === day.length;
    let completed = 0, revenue = 0, matched = 0;
    for (const j of inReader) {
      const t = input.realized.get(j.id)!;
      completed += Math.max(0, t.completed);
      revenue += Math.max(0, t.revenue);
      matched += Math.max(0, t.matched);
    }
    totalCompleted += completed; totalRevenue += revenue; totalMatched += matched;
    ridesBuckets.push({
      ...b,
      value: inReader.length ? completed : null,
      status: !inReader.length ? "unavailable" : allInReader ? "complete" : "partial",
      coverage: null,
      sampleCount: inReader.length,
    });
    let status: BucketStatus;
    let value: number | null;
    if (!inReader.length) { status = "unavailable"; value = null; }
    else if (completed === 0) { status = allInReader ? "complete" : "unavailable"; value = allInReader ? 0 : null; }
    else if (matched === 0) { status = "unavailable"; value = null; }
    else { status = allInReader && matched === completed ? "complete" : "partial"; value = r2(revenue); }
    revenueBuckets.push({ ...b, value, status, coverage: completed > 0 ? ratio(matched, completed) : null, sampleCount: completed });
  }
  const anyRevenue = totalMatched > 0;
  const revenueSeries: DailySeries = {
    metric: "realized_revenue", semantic: "REALIZED_REVENUE", unit: "BRL",
    status: seriesStatus(revenueBuckets, anyRevenue),
    completeness: anyRevenue ? (sourceComplete && totalMatched === totalCompleted ? "complete" : "partial") : null,
    coverage: ratio(totalMatched, totalCompleted),
    sampleCount: totalCompleted,
    total: anyRevenue ? r2(totalRevenue) : null,
    limitations: [
      "Soma das tarifas ofertadas vinculadas a corridas concluídas, agrupada pelo dia de início da jornada. Não comprova o recebimento final.",
      ...(totalCompleted > totalMatched ? ["Parte das corridas concluídas ainda não possui tarifa conciliada."] : []),
      ...limitations,
    ],
    buckets: revenueBuckets,
  };
  if (revenueSeries.status === "complete" && revenueSeries.completeness !== "complete") revenueSeries.status = "partial";
  const ridesSeries: DailySeries = {
    metric: "completed_rides", semantic: "REALIZED_RIDE", unit: "rides",
    status: seriesStatus(ridesBuckets, totalCompleted > 0),
    completeness: totalCompleted > 0 ? (sourceComplete ? "complete" : "partial") : null,
    coverage: null, sampleCount: totalCompleted, total: totalCompleted > 0 ? totalCompleted : null,
    limitations: ["Corridas confirmadas como concluídas, pelo dia de início da jornada.", ...limitations],
    buckets: ridesBuckets,
  };

  // Hours: closed journeys only, by start day (same rule as the KPI).
  let totalHoursMs = 0, closedCount = 0;
  const hourBuckets: SeriesBucket[] = buckets.map((b) => {
    const day = byDay.get(b.key) ?? [];
    if (!day.length) return { ...b, value: journeysKnown ? 0 : null, status: journeysKnown ? "no_activity" : "unavailable", coverage: null, sampleCount: 0 };
    let ms = 0, closed = 0;
    for (const j of day) {
      const s = Date.parse(j.started_at);
      const e = j.ended_at ? Date.parse(j.ended_at) : Number.NaN;
      if (Number.isFinite(s) && Number.isFinite(e) && e > s) { ms += e - s; closed++; }
    }
    totalHoursMs += ms; closedCount += closed;
    return {
      ...b,
      value: closed ? Math.round(ms / 36_000) / 100 : null,
      status: !closed ? "unavailable" : closed === day.length ? "complete" : "partial",
      coverage: ratio(closed, day.length),
      sampleCount: day.length,
    };
  });
  const hoursSeries: DailySeries = {
    metric: "closed_journey_hours", semantic: "MIXED", unit: "hours",
    status: seriesStatus(hourBuckets, closedCount > 0),
    completeness: closedCount > 0 ? (closedCount === journeys.length && journeysKnown ? "complete" : "partial") : null,
    coverage: ratio(closedCount, journeys.length), sampleCount: journeys.length,
    total: closedCount > 0 ? Math.round(totalHoursMs / 36_000) / 100 : null,
    limitations: [
      "Duração integral das jornadas encerradas, pelo dia de início; jornadas em andamento ficam fora.",
      ...(input.journeyListLimited ? [limitations[0]] : []),
    ],
    buckets: hourBuckets,
  };

  // Distance: registered odometer per journey, by start day.
  let totalKm = 0, withKm = 0;
  const kmBuckets: SeriesBucket[] = buckets.map((b) => {
    const day = byDay.get(b.key) ?? [];
    if (!day.length) return { ...b, value: journeysKnown ? 0 : null, status: journeysKnown ? "no_activity" : "unavailable", coverage: null, sampleCount: 0 };
    let km = 0, known = 0;
    for (const j of day) {
      const d = input.distances.get(j.id);
      if (typeof d === "number" && Number.isFinite(d) && d >= 0) { km += d; known++; }
    }
    totalKm += km; withKm += known;
    return {
      ...b,
      value: known ? r2(km) : null,
      status: !known ? "unavailable" : known === day.length ? "complete" : "partial",
      coverage: ratio(known, day.length), sampleCount: day.length,
    };
  });
  const distanceSeries: DailySeries = {
    metric: "actual_distance", semantic: "ACTUAL_DISTANCE", unit: "km",
    status: seriesStatus(kmBuckets, withKm > 0 && totalKm > 0),
    completeness: withKm > 0 ? (withKm === journeys.length && journeysKnown ? "complete" : "partial") : null,
    coverage: ratio(withKm, journeys.length), sampleCount: journeys.length,
    total: withKm > 0 && totalKm > 0 ? r2(totalKm) : null,
    limitations: ["Distância do odômetro informada nas jornadas, pelo dia de início."],
    buckets: kmBuckets,
  };

  // Observed offers: counted separately, never revenue.
  const offerCounts = new Map<string, number>();
  for (const at of input.offerTimes) {
    const t = Date.parse(at);
    if (!Number.isFinite(t) || t < fromMs || t >= toMs) continue;
    const key = localDayKey(t, timeZone);
    offerCounts.set(key, (offerCounts.get(key) ?? 0) + 1);
  }
  const offerBuckets: SeriesBucket[] = buckets.map((b) => ({
    ...b,
    value: input.offersTruncated ? (offerCounts.get(b.key) ?? null) : (offerCounts.get(b.key) ?? 0),
    status: input.offersTruncated ? "partial" : offerCounts.has(b.key) ? "complete" : "no_activity",
    coverage: null,
    sampleCount: offerCounts.get(b.key) ?? 0,
  }));
  const offerTotal = [...offerCounts.values()].reduce((a, c) => a + c, 0);
  const offersSeries: DailySeries = {
    metric: "observed_offers", semantic: "OBSERVED_OFFER", unit: "offers",
    status: offerTotal > 0 ? (input.offersTruncated ? "partial" : "complete") : "unavailable",
    completeness: offerTotal > 0 ? (input.offersTruncated ? "partial" : "complete") : null,
    coverage: null, sampleCount: offerTotal, total: offerTotal > 0 ? offerTotal : null,
    limitations: ["Ofertas observadas não comprovam corridas realizadas nem ganhos.", ...(input.offersTruncated ? ["A consulta de ofertas atingiu o limite analítico."] : [])],
    buckets: offerBuckets,
  };

  const rides = ridesDetail(input, journeys, sourceComplete, totalCompleted, totalMatched, totalRevenue);
  return {
    schema: STATISTICS_SERIES_SCHEMA,
    time_zone: timeZone,
    period: { from: period.from, to: period.to },
    granularity: "day" as const,
    coverage: { sourceComplete, journeys: journeys.length, reconciledJourneys: readerCount },
    series: {
      realized_revenue: revenueSeries,
      completed_rides: ridesSeries,
      closed_journey_hours: hoursSeries,
      actual_distance: distanceSeries,
      observed_offers: offersSeries,
    },
    rides: rides.summary,
    breakdowns: rides.breakdowns,
  };
}

const DISTANCE_BANDS: Array<{ key: string; label: string; min: number; max: number }> = [
  { key: "0-5", label: "Até 5 km", min: 0, max: 5 },
  { key: "5-10", label: "5 a 10 km", min: 5, max: 10 },
  { key: "10-20", label: "10 a 20 km", min: 10, max: 20 },
  { key: "20+", label: "Acima de 20 km", min: 20, max: Number.POSITIVE_INFINITY },
];

function groupRows(rows: RideInput[], keyOf: (row: RideInput) => { key: string; label: string }): GroupRow[] {
  const groups = new Map<string, { label: string; rides: number; fares: number[] }>();
  for (const row of rows) {
    const { key, label } = keyOf(row);
    const g = groups.get(key) ?? { label, rides: 0, fares: [] };
    g.rides++;
    if (typeof row.fare === "number") g.fares.push(row.fare);
    groups.set(key, g);
  }
  return [...groups.entries()].map(([key, g]) => {
    const revenue = g.fares.length ? r2(g.fares.reduce((a, b) => a + b, 0)) : null;
    return { key, label: g.label, rides: g.rides, ridesWithFare: g.fares.length, revenue, averageFare: revenue !== null ? r2(revenue / g.fares.length) : null };
  }).sort((a, b) => b.rides - a.rides || a.label.localeCompare(b.label));
}

function serviceLabel(value: string | null) {
  const v = (value ?? "").trim().toLowerCase();
  const known: Record<string, string> = {
    uberx: "UberX", comfort: "Comfort", black: "Black", electric: "Electric", priority: "Priority", moto: "Moto",
    "99pop": "99Pop", "99plus": "99Plus", "99moto": "99Moto", "99taxi": "99Táxi", indrive: "inDrive", maxim: "Maxim",
  };
  return v ? { key: v, label: known[v] ?? "Outra modalidade" } : { key: "unknown", label: "Não identificada" };
}

function ridesDetail(input: SeriesInput, journeys: JourneyInput[], sourceComplete: boolean, completed: number, matched: number, revenue: number) {
  const ids = new Set(journeys.filter((j) => input.realized.has(j.id)).map((j) => j.id));
  const rows = input.rides.filter((r) => ids.has(r.journey_id));
  const fares = rows.map((r) => r.fare).filter((f): f is number => typeof f === "number" && Number.isFinite(f) && f >= 0);
  // Per-ride rows must reproduce the canonical totals; otherwise extremes are withheld.
  const consistent = !input.ridesLimited && rows.length === completed && fares.length === matched &&
    Math.abs(r2(fares.reduce((a, b) => a + b, 0)) - r2(revenue)) < 0.01;
  const summary: RidesSummary = {
    status: completed === 0 || matched === 0 ? "unavailable" : sourceComplete && matched === completed ? "complete" : "partial",
    completed,
    withKnownFare: matched,
    averageFare: matched > 0 ? r2(revenue / matched) : null,
    maxFare: consistent && fares.length ? Math.max(...fares) : null,
    minFare: consistent && fares.length ? Math.min(...fares) : null,
    consistent,
  };
  const breakdowns = consistent && completed > 0 ? {
    service_type: groupRows(rows, (r) => serviceLabel(r.service_type)),
    distance_band: groupRows(rows, (r) => {
      const km = r.offer_total_km;
      if (typeof km !== "number" || !Number.isFinite(km) || km < 0) return { key: "unknown", label: "Distância não informada" };
      const band = DISTANCE_BANDS.find((d) => km >= d.min && km < d.max)!;
      return { key: band.key, label: band.label };
    }),
  } : null;
  return { summary, breakdowns };
}

/** Change vs. the previous equivalent window, only between two complete readings. */
export function revenueComparison(
  current: { status: SeriesStatus; completeness: string | null; total: number | null },
  previous: { status: SeriesStatus; completeness: string | null; total: number | null } | null,
  previousPeriod: { from: string; to: string } | null,
) {
  const eligible = current.status === "complete" && current.completeness === "complete" && current.total !== null &&
    previous !== null && previous.status === "complete" && previous.completeness === "complete" &&
    previous.total !== null && previous.total > 0;
  return {
    eligible,
    previousPeriod,
    previousTotal: eligible ? previous!.total : null,
    changePct: eligible ? Math.round(((current.total! - previous!.total!) / previous!.total!) * 1000) / 10 : null,
    reason: eligible ? null : "Comparação exige tarifas conciliadas completas nos dois períodos.",
  };
}

const WEEKDAY_NAMES: Record<string, string> = { Dom: "domingo", Seg: "segunda-feira", Ter: "terça-feira", Qua: "quarta-feira", Qui: "quinta-feira", Sex: "sexta-feira", "Sáb": "sábado" };

/** Short factual reading; null unless the numbers explicitly support it. */
export function revenueInsight(series: DailySeries, comparison: ReturnType<typeof revenueComparison>, days: number) {
  const parts: string[] = [];
  if (comparison.eligible && comparison.changePct !== null && comparison.changePct !== 0) {
    const pct = Math.abs(comparison.changePct).toLocaleString("pt-BR", { maximumFractionDigits: 1 });
    parts.push(`Suas tarifas de corridas concluídas foram ${pct}% ${comparison.changePct > 0 ? "maiores" : "menores"} que nos ${days} dias anteriores.`);
  }
  const valued = series.buckets.filter((b) => b.status === "complete" && b.value !== null && b.value > 0 && !b.partialWindow);
  if (series.status === "complete" && valued.length >= 3) {
    const best = valued.reduce((a, b) => (b.value! > a.value! ? b : a));
    const ties = valued.filter((b) => b.value === best.value).length;
    if (ties === 1) {
      const money = best.value!.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
      parts.push(`Dia de maior valor: ${WEEKDAY_NAMES[best.weekday] ?? best.weekday}, ${best.date} (${money}).`);
    }
  }
  return parts.length ? { text: parts.join(" "), basis: comparison.eligible ? "comparison+daily" : "daily" } : null;
}
