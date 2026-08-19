import { adminSupabase } from "./supabase";
import { ensurePreferences } from "./preferences";

const MIN_PROBABILITY_SAMPLES = 20;
const MAX_ROWS = 5000;

type ExposureRow = {
  journey_id: string;
  cell: string;
  started_at: string;
  ended_at: string;
  duration_seconds: number;
  close_reason: string;
  next_offer_local_id: string | null;
};

type OfferMetricRow = {
  local_offer_id: string | null;
  observed_at: string;
  capture_method: string;
  destination_cell: string | null;
  estimated_arrival_at: string | null;
  per_km: number | null;
  per_minute: number | null;
  service_type: string;
};

type CollectiveRow = {
  cell: string;
  weekday_iso: number;
  hour_bucket: number;
  contributor_count: number;
  exposure_count: number;
  total_seconds: number;
  offer_hits: number;
  eligible_5: number;
  success_5: number;
  eligible_10: number;
  success_10: number;
  eligible_15: number;
  success_15: number;
  average_per_km: number | null;
  average_per_minute: number | null;
};

function num(value: unknown) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function nullableNumber(value: unknown) {
  if (value === null || value === undefined) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function r2(value: number) {
  return Math.round(value * 100) / 100;
}

function avg(values: Array<number | null | undefined>) {
  const valid = values.filter(
    (v): v is number => typeof v === "number" && Number.isFinite(v),
  );
  return valid.length
    ? r2(valid.reduce((a, b) => a + b, 0) / valid.length)
    : null;
}

function median(values: number[]) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return r2(
    sorted.length % 2
      ? sorted[mid]
      : (sorted[mid - 1] + sorted[mid]) / 2,
  );
}

function isOfferHit(row: ExposureRow) {
  return row.close_reason === "offer_observed" && Boolean(row.next_offer_local_id);
}

function reliability(eligible: number) {
  if (eligible < MIN_PROBABILITY_SAMPLES) return "insufficient";
  if (eligible < 50) return "low";
  if (eligible < 100) return "medium";
  return "high";
}

/**
 * Para um horizonte H:
 * - sucesso: oferta observada antes de H;
 * - denominador: intervalo que chegou a H OU sucesso antes de H.
 *
 * Pausa, troca de célula ou início de corrida antes de H são censuras,
 * portanto não viram "fracasso" artificial.
 */
function horizon(rows: ExposureRow[], minutes: number) {
  const seconds = minutes * 60;
  const eligible = rows.filter(
    (row) =>
      num(row.duration_seconds) >= seconds ||
      (isOfferHit(row) && num(row.duration_seconds) <= seconds),
  );
  const successes = rows.filter(
    (row) => isOfferHit(row) && num(row.duration_seconds) <= seconds,
  ).length;
  const level = reliability(eligible.length);

  return {
    minutes,
    probability_pct:
      level === "insufficient" || eligible.length === 0
        ? null
        : r2((successes / eligible.length) * 100),
    eligible_intervals: eligible.length,
    successes,
    reliability: level,
  };
}

const weekdayMap: Record<string, number> = {
  Mon: 1,
  Tue: 2,
  Wed: 3,
  Thu: 4,
  Fri: 5,
  Sat: 6,
  Sun: 7,
};

function timeParts(value: string, timeZone: string) {
  const date = new Date(value);
  const weekday = new Intl.DateTimeFormat("en-US", {
    timeZone,
    weekday: "short",
  }).format(date);
  const hour = Number(
    new Intl.DateTimeFormat("en-US", {
      timeZone,
      hour: "2-digit",
      hour12: false,
    }).format(date),
  ) % 24;

  return {
    weekdayIso: weekdayMap[weekday] ?? 1,
    hourBucket: Math.floor(hour / 3) * 3,
  };
}

function importConfidence(method: string) {
  if (!method.startsWith("historical-import/")) return "live";
  return method.slice("historical-import/".length) || "unknown";
}

async function personalData(driverId: string, days: number) {
  const to = new Date();
  const from = new Date(to.getTime() - days * 86_400_000);

  const [exposureResult, offerResult, importResult] = await Promise.all([
    adminSupabase()
      .from("zone_exposures")
      .select("journey_id,cell,started_at,ended_at,duration_seconds,close_reason,next_offer_local_id")
      .eq("driver_id", driverId)
      .gte("started_at", from.toISOString())
      .lt("started_at", to.toISOString())
      .order("started_at", { ascending: true })
      .limit(MAX_ROWS),

    adminSupabase()
      .from("ride_offers")
      .select("local_offer_id,observed_at,capture_method,destination_cell,estimated_arrival_at,per_km,per_minute,service_type")
      .eq("driver_id", driverId)
      .gte("observed_at", from.toISOString())
      .lt("observed_at", to.toISOString())
      .order("observed_at", { ascending: true })
      .limit(MAX_ROWS),

    adminSupabase()
      .from("ride_offers")
      .select("capture_method")
      .eq("driver_id", driverId)
      .like("capture_method", "historical-import/%")
      .limit(MAX_ROWS),
  ]);

  if (exposureResult.error) throw new Error(exposureResult.error.message);
  if (offerResult.error) throw new Error(offerResult.error.message);
  if (importResult.error) throw new Error(importResult.error.message);

  const exposures = (exposureResult.data ?? []).map((row: any) => ({
    journey_id: String(row.journey_id),
    cell: String(row.cell),
    started_at: String(row.started_at),
    ended_at: String(row.ended_at),
    duration_seconds: num(row.duration_seconds),
    close_reason: String(row.close_reason ?? "unknown"),
    next_offer_local_id: row.next_offer_local_id
      ? String(row.next_offer_local_id)
      : null,
  })) as ExposureRow[];

  const offers = (offerResult.data ?? []).map((row: any) => ({
    local_offer_id: row.local_offer_id ? String(row.local_offer_id) : null,
    observed_at: String(row.observed_at),
    capture_method: String(row.capture_method ?? "unknown"),
    destination_cell: row.destination_cell
      ? String(row.destination_cell)
      : null,
    estimated_arrival_at: row.estimated_arrival_at
      ? String(row.estimated_arrival_at)
      : null,
    per_km: nullableNumber(row.per_km),
    per_minute: nullableNumber(row.per_minute),
    service_type: String(row.service_type ?? "unknown"),
  })) as OfferMetricRow[];

  const importedMethods: string[] = (importResult.data ?? []).map((row: any) =>
    String(row.capture_method ?? "historical-import/unknown"),
  );

  return {
    from: from.toISOString(),
    to: to.toISOString(),
    exposures,
    offers,
    importedMethods,
  };
}

function collapseOfferBursts(rows: ExposureRow[]) {
  const sorted = [...rows].sort((a, b) =>
    a.started_at.localeCompare(b.started_at),
  );
  const lastOfferAt = new Map<string, number>();
  const kept: ExposureRow[] = [];
  let collapsed = 0;

  for (const row of sorted) {
    const key = `${row.journey_id}|${row.cell}`;
    const startMs = new Date(row.started_at).getTime();
    const eventMs = startMs + Math.max(0, num(row.duration_seconds)) * 1000;

    if (!isOfferHit(row)) {
      lastOfferAt.delete(key);
      kept.push(row);
      continue;
    }

    const previousEventMs = lastOfferAt.get(key);
    const sameBurst =
      num(row.duration_seconds) <= 15 &&
      previousEventMs !== undefined &&
      Number.isFinite(startMs) &&
      Math.abs(startMs - previousEventMs) <= 15_000;

    lastOfferAt.set(key, eventMs);
    if (sameBurst) {
      collapsed++;
      continue;
    }
    kept.push(row);
  }

  return { rows: kept, collapsed };
}

function summarizeCell(
  cell: string,
  exposures: ExposureRow[],
  offerByLocalId: Map<string, OfferMetricRow>,
  destinationOfferCount: number,
) {
  const linked = exposures
    .map((row) =>
      row.next_offer_local_id
        ? offerByLocalId.get(row.next_offer_local_id)
        : undefined,
    )
    .filter((row): row is OfferMetricRow => Boolean(row));

  const timeToOfferMinutes = exposures
    .filter(isOfferHit)
    .map((row) => num(row.duration_seconds) / 60)
    .filter(Number.isFinite);

  return {
    cell,
    exposure_count: exposures.length,
    available_minutes: r2(
      exposures.reduce((acc, row) => acc + num(row.duration_seconds), 0) / 60,
    ),
    offer_hits: exposures.filter(isOfferHit).length,
    mean_time_to_offer_minutes: avg(timeToOfferMinutes),
    median_time_to_offer_minutes: median(timeToOfferMinutes),
    average_per_km: avg(linked.map((row) => row.per_km)),
    average_per_minute: avg(linked.map((row) => row.per_minute)),
    destination_offer_count: destinationOfferCount,
    service_distribution: linked.reduce<Record<string, number>>((acc, row) => {
      const key = row.service_type || "unknown";
      acc[key] = (acc[key] ?? 0) + 1;
      return acc;
    }, {}),
    p5: horizon(exposures, 5),
    p10: horizon(exposures, 10),
    p15: horizon(exposures, 15),
  };
}

async function collectiveRows(): Promise<CollectiveRow[]> {
  const { data, error } = await adminSupabase()
    .from("sr_collective_region_hour_v1")
    .select(
      "cell,weekday_iso,hour_bucket,contributor_count,exposure_count,total_seconds,offer_hits,eligible_5,success_5,eligible_10,success_10,eligible_15,success_15,average_per_km,average_per_minute",
    )
    .limit(1000);

  if (error) return [] as CollectiveRow[];

  return (data ?? []).map((row: any) => ({
    cell: String(row.cell),
    weekday_iso: num(row.weekday_iso),
    hour_bucket: num(row.hour_bucket),
    contributor_count: num(row.contributor_count),
    exposure_count: num(row.exposure_count),
    total_seconds: num(row.total_seconds),
    offer_hits: num(row.offer_hits),
    eligible_5: num(row.eligible_5),
    success_5: num(row.success_5),
    eligible_10: num(row.eligible_10),
    success_10: num(row.success_10),
    eligible_15: num(row.eligible_15),
    success_15: num(row.success_15),
    average_per_km: nullableNumber(row.average_per_km),
    average_per_minute: nullableNumber(row.average_per_minute),
  }));
}

export async function regionalIntelligence(
  driverId: string,
  requestedDays = 30,
) {
  const days = Math.max(1, Math.min(requestedDays || 30, 90));
  const [prefs, personal, collective] = await Promise.all([
    ensurePreferences(driverId),
    personalData(driverId, days),
    collectiveRows(),
  ]);

  const cleanedExposure = collapseOfferBursts(personal.exposures);
  const statisticalExposures = cleanedExposure.rows;

  const offerByLocalId = new Map<string, OfferMetricRow>();
  const destinationCounts = new Map<string, number>();
  const historicalPositiveOffers = personal.importedMethods.length;
  const importedUnknownTime = personal.importedMethods.filter(
    (method) => importConfidence(method) === "unknown",
  ).length;

  for (const offer of personal.offers) {
    if (offer.local_offer_id) {
      offerByLocalId.set(offer.local_offer_id, offer);
    }
    if (offer.destination_cell) {
      destinationCounts.set(
        offer.destination_cell,
        (destinationCounts.get(offer.destination_cell) ?? 0) + 1,
      );
    }

  }

  const groups = new Map<string, ExposureRow[]>();
  for (const row of statisticalExposures) {
    groups.set(row.cell, [...(groups.get(row.cell) ?? []), row]);
  }

  const topRegions = [...groups.entries()]
    .map(([cell, rows]) =>
      summarizeCell(
        cell,
        rows,
        offerByLocalId,
        destinationCounts.get(cell) ?? 0,
      ),
    )
    .sort((a, b) => {
      const ap = a.p10.probability_pct;
      const bp = b.p10.probability_pct;
      if (ap !== null || bp !== null) return (bp ?? -1) - (ap ?? -1);
      return b.available_minutes - a.available_minutes;
    })
    .slice(0, 12);

  return {
    available: true,
    days,
    range: { from: personal.from, to: personal.to },
    minimum_probability_samples: MIN_PROBABILITY_SAMPLES,
    collective_opt_in: Boolean(prefs.collective_stats_opt_in),
    collective_available_regions: new Set(collective.map((row) => row.cell)).size,
    data_quality: {
      raw_exposure_count: personal.exposures.length,
      exposure_count: statisticalExposures.length,
      burst_intervals_collapsed: cleanedExposure.collapsed,
      cells_with_exposure: groups.size,
      offers_with_destination_cell: personal.offers.filter(
        (o) => Boolean(o.destination_cell),
      ).length,
      historical_positive_offers: historicalPositiveOffers,
      imported_unknown_time: importedUnknownTime,
      probability_ready_cells: topRegions.filter(
        (row) => row.p10.probability_pct !== null,
      ).length,
    },
    top_regions: topRegions,
    note:
      "Probabilidade usa somente exposição regional observada como denominador. " +
      "Screenshots históricos aumentam eventos positivos/contexto, mas nunca criam tempo disponível artificial.",
  };
}

function aggregateCollective(rows: CollectiveRow[]) {
  if (!rows.length) return null;

  const sumField = (key: keyof CollectiveRow) =>
    rows.reduce((acc, row) => acc + num(row[key]), 0);

  const weightedAverage = (
    key: "average_per_km" | "average_per_minute",
  ) => {
    let weighted = 0;
    let weight = 0;
    for (const row of rows) {
      const value = row[key];
      if (value === null || !Number.isFinite(value)) continue;
      const w = Math.max(1, row.offer_hits);
      weighted += value * w;
      weight += w;
    }
    return weight ? r2(weighted / weight) : null;
  };

  const h = (
    minutes: number,
    eligibleKey: keyof CollectiveRow,
    successKey: keyof CollectiveRow,
  ) => {
    const eligible = sumField(eligibleKey);
    const successes = sumField(successKey);
    const level = reliability(eligible);

    return {
      minutes,
      probability_pct:
        level === "insufficient" || eligible === 0
          ? null
          : r2((successes / eligible) * 100),
      eligible_intervals: eligible,
      successes,
      reliability: level,
    };
  };

  return {
    contributor_count_floor: Math.max(
      ...rows.map((row) => row.contributor_count),
    ),
    exposure_count: sumField("exposure_count"),
    available_minutes: r2(sumField("total_seconds") / 60),
    offer_hits: sumField("offer_hits"),
    average_per_km: weightedAverage("average_per_km"),
    average_per_minute: weightedAverage("average_per_minute"),
    p5: h(5, "eligible_5", "success_5"),
    p10: h(10, "eligible_10", "success_10"),
    p15: h(15, "eligible_15", "success_15"),
  };
}

export async function continuityEstimate(
  driverId: string,
  cell: string,
  eta: string,
  requestedDays = 60,
) {
  if (!/^g2:-?\d+:-?\d+$/.test(cell)) throw new Error("invalid_cell");

  const etaDate = new Date(eta);
  if (Number.isNaN(etaDate.getTime())) throw new Error("invalid_eta");

  const days = Math.max(7, Math.min(requestedDays || 60, 90));
  const [prefs, personal, collective] = await Promise.all([
    ensurePreferences(driverId),
    personalData(driverId, days),
    collectiveRows(),
  ]);

  const timeZone = prefs.timezone || "America/Sao_Paulo";
  const target = timeParts(etaDate.toISOString(), timeZone);
  const statisticalExposures = collapseOfferBursts(personal.exposures).rows;
  const allCell = statisticalExposures.filter((row) => row.cell === cell);

  const exact = allCell.filter((row) => {
    const p = timeParts(row.started_at, timeZone);
    return (
      p.weekdayIso === target.weekdayIso &&
      p.hourBucket === target.hourBucket
    );
  });

  const sameBand = allCell.filter((row) => {
    const p = timeParts(row.started_at, timeZone);
    return p.hourBucket === target.hourBucket;
  });

  const personalCandidates = [
    { scope: "weekday_and_3h", rows: exact },
    { scope: "3h_any_weekday", rows: sameBand },
    { scope: "cell_all_time", rows: allCell },
  ];

  let selected = personalCandidates[personalCandidates.length - 1];
  for (const candidate of personalCandidates) {
    selected = candidate;
    if (
      horizon(candidate.rows, 10).eligible_intervals >=
      MIN_PROBABILITY_SAMPLES
    ) {
      break;
    }
  }

  const offerByLocalId = new Map<string, OfferMetricRow>();
  for (const offer of personal.offers) {
    if (offer.local_offer_id) {
      offerByLocalId.set(offer.local_offer_id, offer);
    }
  }
  const personalSummary = summarizeCell(
    cell,
    selected.rows,
    offerByLocalId,
    0,
  );

  const collectiveCell = collective.filter((row) => row.cell === cell);
  const collectiveExact = collectiveCell.filter(
    (row) =>
      row.weekday_iso === target.weekdayIso &&
      row.hour_bucket === target.hourBucket,
  );
  const collectiveBand = collectiveCell.filter(
    (row) => row.hour_bucket === target.hourBucket,
  );

  const collectiveCandidates = [
    { scope: "weekday_and_3h", rows: collectiveExact },
    { scope: "3h_any_weekday", rows: collectiveBand },
    { scope: "cell_all_time", rows: collectiveCell },
  ];

  let collectiveSelection:
    | { scope: string; value: NonNullable<ReturnType<typeof aggregateCollective>> }
    | null = null;

  for (const candidate of collectiveCandidates) {
    const value = aggregateCollective(candidate.rows);
    if (!value) continue;
    collectiveSelection = { scope: candidate.scope, value };
    if (value.p10.eligible_intervals >= MIN_PROBABILITY_SAMPLES) break;
  }

  const preferred =
    personalSummary.p10.probability_pct !== null
      ? "personal"
      : collectiveSelection?.value.p10.probability_pct !== null &&
          collectiveSelection?.value.p10.probability_pct !== undefined
        ? "collective"
        : "insufficient";

  return {
    cell,
    eta: etaDate.toISOString(),
    days,
    target: {
      weekday_iso: target.weekdayIso,
      hour_bucket: target.hourBucket,
    },
    preferred_source: preferred,
    personal: {
      scope: selected.scope,
      ...personalSummary,
    },
    collective: collectiveSelection
      ? {
          scope: collectiveSelection.scope,
          ...collectiveSelection.value,
        }
      : null,
    minimum_probability_samples: MIN_PROBABILITY_SAMPLES,
    note:
      "Continuidade é uma estimativa estatística histórica. Não garante nova corrida e não altera o Offer Engine financeiro.",
  };
}
