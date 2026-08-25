import { adminSupabase } from "./supabase";
import { ensurePreferences } from "./preferences";

const MIN_PROBABILITY_SAMPLES = 20;
const MIN_SEED_SAMPLES = 20;
const PERSONAL_LOOKBACK_DAYS = 60;

type Level = "high" | "medium" | "low" | "insufficient";
type HistoricalScope = "weekday_and_3h" | "3h_any_weekday" | "region_all_time";
type ProbabilityScope = "weekday_and_3h" | "3h_any_weekday" | "cell_all_time";

type SeedRow = {
  region_key: string;
  region_label: string;
  weekday_iso: number;
  hour_bucket: number;
  sample_count: number;
};

type ExposureRow = {
  journey_id: string;
  started_at: string;
  duration_seconds: number;
  close_reason: string;
  next_offer_local_id: string | null;
};

type CollectiveRow = {
  weekday_iso: number;
  hour_bucket: number;
  eligible_10: number;
  success_10: number;
};

const weekdays: Record<string, number> = {
  Mon: 1,
  Tue: 2,
  Wed: 3,
  Thu: 4,
  Fri: 5,
  Sat: 6,
  Sun: 7,
};

function num(value: unknown) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function r2(value: number) {
  return Math.round(value * 100) / 100;
}

function timeParts(value: Date | string, timeZone: string) {
  const date = value instanceof Date ? value : new Date(value);
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
    weekdayIso: weekdays[weekday] ?? 1,
    hourBucket: Math.floor(hour / 3) * 3,
  };
}

function reliability(samples: number) {
  if (samples < MIN_PROBABILITY_SAMPLES) return "insufficient";
  if (samples < 50) return "low";
  if (samples < 100) return "medium";
  return "high";
}

function probabilityLevel(value: number | null): Level {
  if (value === null || !Number.isFinite(value)) return "insufficient";
  if (value >= 60) return "high";
  if (value >= 30) return "medium";
  return "low";
}

function historicalLevel(percentile: number | null): Level {
  if (percentile === null || !Number.isFinite(percentile)) return "insufficient";
  if (percentile >= 0.75) return "high";
  if (percentile >= 0.4) return "medium";
  return "low";
}

function historicalConfidence(samples: number) {
  if (samples >= 250) return "high";
  if (samples >= 80) return "medium";
  if (samples >= MIN_SEED_SAMPLES) return "low";
  return "insufficient";
}

function isOfferHit(row: ExposureRow) {
  return row.close_reason === "offer_observed" && Boolean(row.next_offer_local_id);
}

function collapseOfferBursts(rows: ExposureRow[]) {
  const sorted = [...rows].sort((a, b) => a.started_at.localeCompare(b.started_at));
  const lastOfferAt = new Map<string, number>();
  const kept: ExposureRow[] = [];

  for (const row of sorted) {
    const key = row.journey_id;
    const startMs = new Date(row.started_at).getTime();
    const eventMs = startMs + Math.max(0, row.duration_seconds) * 1000;

    if (!isOfferHit(row)) {
      lastOfferAt.delete(key);
      kept.push(row);
      continue;
    }

    const previous = lastOfferAt.get(key);
    const sameBurst =
      row.duration_seconds <= 15 &&
      previous !== undefined &&
      Number.isFinite(startMs) &&
      Math.abs(startMs - previous) <= 15_000;

    lastOfferAt.set(key, eventMs);
    if (!sameBurst) kept.push(row);
  }
  return kept;
}

function p10(rows: ExposureRow[]) {
  const seconds = 10 * 60;
  const eligible = rows.filter(
    (row) =>
      row.duration_seconds >= seconds ||
      (isOfferHit(row) && row.duration_seconds <= seconds),
  );
  const successes = rows.filter(
    (row) => isOfferHit(row) && row.duration_seconds <= seconds,
  ).length;
  const level = reliability(eligible.length);
  return {
    probability_pct:
      level === "insufficient" || eligible.length === 0
        ? null
        : r2((successes / eligible.length) * 100),
    eligible_intervals: eligible.length,
    successes,
    reliability: level,
  };
}

async function personalProbability(
  driverId: string,
  cell: string,
  target: { weekdayIso: number; hourBucket: number },
  timeZone: string,
) {
  const from = new Date(Date.now() - PERSONAL_LOOKBACK_DAYS * 86_400_000).toISOString();
  const { data, error } = await adminSupabase()
    .from("zone_exposures")
    .select("journey_id,started_at,duration_seconds,close_reason,next_offer_local_id")
    .eq("driver_id", driverId)
    .eq("cell", cell)
    .gte("started_at", from)
    .order("started_at", { ascending: false })
    .limit(1500);
  if (error) throw new Error(error.message);

  const rows = collapseOfferBursts(
    (data ?? []).map((row: any) => ({
      journey_id: String(row.journey_id || ""),
      started_at: String(row.started_at),
      duration_seconds: Math.max(0, num(row.duration_seconds)),
      close_reason: String(row.close_reason || "unknown"),
      next_offer_local_id: row.next_offer_local_id
        ? String(row.next_offer_local_id)
        : null,
    })),
  );

  const exact = rows.filter((row) => {
    const p = timeParts(row.started_at, timeZone);
    return p.weekdayIso === target.weekdayIso && p.hourBucket === target.hourBucket;
  });
  const sameBand = rows.filter(
    (row) => timeParts(row.started_at, timeZone).hourBucket === target.hourBucket,
  );

  const candidates: Array<{ scope: ProbabilityScope; rows: ExposureRow[] }> = [
    { scope: "weekday_and_3h", rows: exact },
    { scope: "3h_any_weekday", rows: sameBand },
    { scope: "cell_all_time", rows },
  ];

  let selected = candidates[candidates.length - 1];
  let estimate = p10(selected.rows);
  for (const candidate of candidates) {
    const current = p10(candidate.rows);
    selected = candidate;
    estimate = current;
    if (current.eligible_intervals >= MIN_PROBABILITY_SAMPLES) break;
  }

  return { scope: selected.scope, ...estimate };
}

async function collectiveProbability(
  cell: string,
  target: { weekdayIso: number; hourBucket: number },
) {
  const { data, error } = await adminSupabase()
    .from("sr_collective_region_hour_v1")
    .select("weekday_iso,hour_bucket,eligible_10,success_10")
    .eq("cell", cell)
    .limit(64);
  if (error) return null;

  const rows = (data ?? []).map((row: any) => ({
    weekday_iso: num(row.weekday_iso),
    hour_bucket: num(row.hour_bucket),
    eligible_10: num(row.eligible_10),
    success_10: num(row.success_10),
  })) as CollectiveRow[];

  const candidates: Array<{ scope: ProbabilityScope; rows: CollectiveRow[] }> = [
    {
      scope: "weekday_and_3h",
      rows: rows.filter(
        (row) =>
          row.weekday_iso === target.weekdayIso &&
          row.hour_bucket === target.hourBucket,
      ),
    },
    {
      scope: "3h_any_weekday",
      rows: rows.filter((row) => row.hour_bucket === target.hourBucket),
    },
    { scope: "cell_all_time", rows },
  ];

  let selected = candidates[candidates.length - 1];
  let eligible = 0;
  let successes = 0;
  for (const candidate of candidates) {
    selected = candidate;
    eligible = candidate.rows.reduce((sum, row) => sum + row.eligible_10, 0);
    successes = candidate.rows.reduce((sum, row) => sum + row.success_10, 0);
    if (eligible >= MIN_PROBABILITY_SAMPLES) break;
  }

  const level = reliability(eligible);
  return {
    scope: selected.scope,
    probability_pct:
      level === "insufficient" || eligible === 0
        ? null
        : r2((successes / eligible) * 100),
    eligible_intervals: eligible,
    successes,
    reliability: level,
  };
}

async function canonicalRegion(destinationLabel: string) {
  const clean = destinationLabel.trim().slice(0, 300);
  if (!clean) return null;
  const supabase = adminSupabase();
  const labelResult = await supabase.rpc("sr_region_canonical_label_v1", {
    value: clean,
  });
  if (labelResult.error) return null;
  const label = String(labelResult.data ?? "").trim();
  if (!label) return null;

  const keyResult = await supabase.rpc("sr_region_key_v1", { value: label });
  if (keyResult.error) return null;
  const key = String(keyResult.data ?? "").trim();
  return key ? { key, label } : null;
}

async function seedRows(weekdayIso?: number, hourBucket?: number) {
  // PostgREST costuma limitar respostas grandes; pagina para não distorcer o
  // percentil quando o seed crescer além de 1.000 grupos.
  const pageSize = 1000;
  const maxRows = 10000;
  const rows: SeedRow[] = [];

  for (let offset = 0; offset < maxRows; offset += pageSize) {
    let query: any = adminSupabase()
      .from("sr_region_seed_v1")
      .select("region_key,region_label,weekday_iso,hour_bucket,sample_count")
      .order("region_key", { ascending: true })
      .order("weekday_iso", { ascending: true })
      .order("hour_bucket", { ascending: true });
    if (weekdayIso !== undefined) query = query.eq("weekday_iso", weekdayIso);
    if (hourBucket !== undefined) query = query.eq("hour_bucket", hourBucket);

    const { data, error } = await query.range(offset, offset + pageSize - 1);
    if (error) throw new Error(error.message);
    const page = (data ?? []) as any[];
    rows.push(
      ...page.map((row: any) => ({
        region_key: String(row.region_key || ""),
        region_label: String(row.region_label || "Região"),
        weekday_iso: Number(row.weekday_iso || 1),
        hour_bucket: Number(row.hour_bucket || 0),
        sample_count: Number(row.sample_count || 0),
      })),
    );
    if (page.length < pageSize) break;
  }

  return rows;
}

function aggregateByRegion(rows: SeedRow[]) {
  const groups = new Map<string, { label: string; samples: number }>();
  for (const row of rows) {
    const current = groups.get(row.region_key) ?? {
      label: row.region_label,
      samples: 0,
    };
    current.samples += Math.max(0, row.sample_count);
    groups.set(row.region_key, current);
  }
  return groups;
}

function percentileRank(target: number, values: number[]) {
  const valid = values.filter((v) => Number.isFinite(v) && v >= 0);
  if (!valid.length) return null;
  return valid.filter((v) => v <= target).length / valid.length;
}

async function historicalIndicator(
  destinationLabel: string,
  eta: Date,
  timeZone: string,
) {
  const region = await canonicalRegion(destinationLabel);
  if (!region) return null;
  const target = timeParts(eta, timeZone);

  const scopes: Array<{
    scope: HistoricalScope;
    weekday?: number;
    hour?: number;
  }> = [
    {
      scope: "weekday_and_3h",
      weekday: target.weekdayIso,
      hour: target.hourBucket,
    },
    {
      scope: "3h_any_weekday",
      hour: target.hourBucket,
    },
    { scope: "region_all_time" },
  ];

  let selected: {
    scope: HistoricalScope;
    samples: number;
    population: number[];
  } | null = null;

  for (const spec of scopes) {
    const groups = aggregateByRegion(await seedRows(spec.weekday, spec.hour));
    const samples = groups.get(region.key)?.samples ?? 0;
    selected = {
      scope: spec.scope,
      samples,
      population: [...groups.values()].map((value) => value.samples),
    };
    if (samples >= MIN_SEED_SAMPLES) break;
  }

  if (!selected || selected.samples < MIN_SEED_SAMPLES) {
    return {
      region_label: region.label,
      scope: selected?.scope ?? "region_all_time",
      samples: selected?.samples ?? 0,
      percentile: null,
      level: "insufficient" as Level,
      confidence: "insufficient",
      source: "sr_rotas_seed",
      wording: "Dados históricos insuficientes para esta região de destino.",
    };
  }

  const percentile = percentileRank(selected.samples, selected.population);
  const level = historicalLevel(percentile);
  const wording =
    level === "high"
      ? "Alta recorrência histórica de ofertas nesta região/faixa."
      : level === "medium"
        ? "Recorrência histórica moderada de ofertas nesta região/faixa."
        : "Baixa recorrência histórica de ofertas nesta região/faixa.";

  return {
    region_label: region.label,
    scope: selected.scope,
    samples: selected.samples,
    percentile: percentile === null ? null : Math.round(percentile * 100),
    level,
    confidence: historicalConfidence(selected.samples),
    source: "sr_rotas_seed",
    wording,
  };
}

export async function destinationContinuity(
  driverId: string,
  input: {
    cell?: string | null;
    eta: string;
    destinationLabel?: string | null;
  },
) {
  const etaDate = new Date(input.eta);
  if (Number.isNaN(etaDate.getTime())) throw new Error("invalid_eta");

  const prefs = await ensurePreferences(driverId);
  const timeZone = prefs.timezone || "America/Sao_Paulo";
  const target = timeParts(etaDate, timeZone);
  const validCell =
    input.cell && /^g2:-?\d+:-?\d+$/.test(input.cell) ? input.cell : null;

  let personal: Awaited<ReturnType<typeof personalProbability>> | null = null;
  let collective: Awaited<ReturnType<typeof collectiveProbability>> | null = null;

  if (validCell) {
    personal = await personalProbability(driverId, validCell, target, timeZone);
    if (
      personal.probability_pct === null &&
      prefs.collective_stats_opt_in === true
    ) {
      collective = await collectiveProbability(validCell, target);
    }
  }

  const chosenProbability =
    personal?.probability_pct !== null && personal?.probability_pct !== undefined
      ? {
          probability_pct: personal.probability_pct,
          level: probabilityLevel(personal.probability_pct),
          samples: personal.eligible_intervals,
          reliability: personal.reliability,
          source: "personal_exposure",
          scope: personal.scope,
        }
      : collective?.probability_pct !== null &&
          collective?.probability_pct !== undefined
        ? {
            probability_pct: collective.probability_pct,
            level: probabilityLevel(collective.probability_pct),
            samples: collective.eligible_intervals,
            reliability: collective.reliability,
            source: "collective_exposure",
            scope: collective.scope,
          }
        : null;

  // Só consulta a massa histórica quando ainda não há P10 com denominador
  // observado. Isso reduz custo/latência e evita misturar duas grandezas.
  const historical =
    !chosenProbability && input.destinationLabel?.trim()
      ? await historicalIndicator(input.destinationLabel, etaDate, timeZone)
      : null;

  const display = chosenProbability
    ? {
        kind: "probability",
        probability_pct: chosenProbability.probability_pct,
        level: chosenProbability.level,
        samples: chosenProbability.samples,
        confidence: chosenProbability.reliability,
        source: chosenProbability.source,
        region_label: historical?.region_label ?? input.destinationLabel ?? null,
        wording:
          `Estimativa P10: ${chosenProbability.probability_pct}% de nova oferta ` +
          "em até 10 min, com base em exposição observada.",
      }
    : historical && historical.level !== "insufficient"
      ? {
          kind: "historical_indicator",
          probability_pct: null,
          level: historical.level,
          samples: historical.samples,
          confidence: historical.confidence,
          source: historical.source,
          region_label: historical.region_label,
          wording: historical.wording,
        }
      : {
          kind: "insufficient",
          probability_pct: null,
          level: "insufficient" as Level,
          samples: historical?.samples ?? 0,
          confidence: "insufficient",
          source: "none",
          region_label: historical?.region_label ?? input.destinationLabel ?? null,
          wording: "Dados insuficientes para estimar nova corrida no destino.",
        };

  return {
    eta: etaDate.toISOString(),
    cell: validCell,
    collective_opt_in: Boolean(prefs.collective_stats_opt_in),
    target: {
      weekday_iso: target.weekdayIso,
      hour_bucket: target.hourBucket,
    },
    display,
    probability: chosenProbability,
    historical,
    note:
      "Percentual P10 só é exibido com exposição observada suficiente. " +
      "Quando a amostra temporal ainda é insuficiente, a Base Sr. Rotas usa os históricos validados apenas para classificar recorrência como Alta, Média ou Baixa; não converte densidade histórica em percentual falso.",
  };
}
