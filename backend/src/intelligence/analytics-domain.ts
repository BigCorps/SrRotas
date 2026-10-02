import { fetchOffers, summarizeOffers, type OfferRow } from "../analytics";
import { getCostProfile } from "../costs";
import { destinationContinuity } from "../destination-continuity";
import { listJourneyMetrics } from "../journey-metrics-026";
import { listJourneyRealized0262 } from "../journey-realized-0262";
import { nowIntelligence } from "../now-intelligence";
import { ensurePreferences } from "../preferences";
import { adminSupabase } from "../supabase";
import type { AnswerMetric, DomainResult, MetricKey, QuestionPlan, ResolvedPeriod } from "./contracts";
import { coverageEnvelope } from "./evidence-policy";
import { previousEquivalentPeriod } from "./period-resolver";

const MIN_RANKING_SAMPLES = 20;
const MAX_CANONICAL_ROWS = 50_000;
const CANONICAL_FIELDS = [
  "id", "journey_id", "observed_at", "platform", "fare", "pickup_km", "trip_km", "total_km",
  "total_minutes", "per_km", "per_hour", "per_minute", "estimated_cost", "estimated_profit",
  "profit_per_hour", "profit_percent", "passenger_rating", "advertised_per_km", "service_type", "verdict",
  "capture_method", "confidence", "offer_type", "pickup_label", "destination_label", "pickup_lat", "pickup_lng",
  "destination_lat", "destination_lng", "pickup_cell", "destination_cell", "estimated_arrival_at", "context_confidence",
  "geocode_status", "geocode_source", "context_version", "context_source_type", "context_time_source", "data_source",
].join(",");

type JourneyRow = {
  id: string;
  platform: string;
  started_at: string;
  ended_at: string | null;
};

type RealizedItem = {
  journey_id: string;
  completed_trips: number;
  realized_revenue: number;
  fare_matched_trips: number;
  revenue_complete: boolean;
  session_earnings: number | null;
  session_completed_trips: number | null;
  session_offered_trips: number | null;
  session_confidence: number | null;
};

type MetricRow = {
  journey_id: string;
  odometer_start_km?: number | string | null;
  odometer_end_km?: number | string | null;
  distance_km?: number | string | null;
};

type EnergyRow = {
  journey_id: string;
  amount_paid?: number | string | null;
  quantity?: number | string | null;
  energy_type?: string | null;
};

function n(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function r2(value: number) {
  return Math.round(value * 100) / 100;
}

function money(value: number | null) {
  return value === null ? "—" : value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function number(value: number | null, suffix = "") {
  return value === null ? "—" : `${value.toLocaleString("pt-BR", { maximumFractionDigits: 2 })}${suffix}`;
}

function metric(key: string, label: string, value: number | string | null, formatted: string, semantic: AnswerMetric["semantic"]): AnswerMetric {
  return { key, label, value, formatted, semantic };
}

function safeLookbackDays(period: ResolvedPeriod) {
  const diff = Date.now() - new Date(period.from).getTime();
  return Math.max(1, Math.min(90, Math.ceil(diff / 86_400_000) + 1));
}

function normalizeLabel(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function localHour(value: string, timeZone: string) {
  return Number(new Intl.DateTimeFormat("en-US", { timeZone, hour: "2-digit", hour12: false }).format(new Date(value))) % 24;
}

function durationHours(journey: JourneyRow) {
  if (!journey.ended_at) return null;
  const ms = new Date(journey.ended_at).getTime() - new Date(journey.started_at).getTime();
  return Number.isFinite(ms) && ms > 0 ? ms / 3_600_000 : null;
}

async function canonicalOffers(driverId: string, plan: QuestionPlan) {
  const rows: OfferRow[] = [];
  const pageSize = 1000;
  let truncated = false;
  for (let offset = 0; offset < MAX_CANONICAL_ROWS; offset += pageSize) {
    let query: any = adminSupabase()
      .from("sr_personal_offer_canonical_v1")
      .select(CANONICAL_FIELDS)
      .eq("driver_id", driverId)
      .gte("observed_at", plan.period.from)
      .lt("observed_at", plan.period.to)
      .order("observed_at", { ascending: true })
      .range(offset, offset + pageSize - 1);
    if (plan.entities.platform) query = query.eq("platform", plan.entities.platform);
    if (plan.entities.serviceType) query = query.ilike("service_type", `%${plan.entities.serviceType}%`);
    const { data, error } = await query;
    if (error) throw new Error(error.message);
    const page = (data ?? []) as unknown as OfferRow[];
    rows.push(...page);
    if (page.length < pageSize) return { rows, truncated };
  }
  truncated = true;
  return { rows, truncated };
}

async function canonicalOffersForPeriod(driverId: string, plan: QuestionPlan, period: ResolvedPeriod) {
  return canonicalOffers(driverId, { ...plan, period });
}

async function journeysInPeriod(driverId: string, period: ResolvedPeriod) {
  const { data, error } = await adminSupabase()
    .from("driver_journeys")
    .select("id,platform,started_at,ended_at")
    .eq("driver_id", driverId)
    .gte("started_at", period.from)
    .lt("started_at", period.to)
    .order("started_at", { ascending: true })
    .limit(500);
  if (error) throw new Error(error.message);
  return (data ?? []).map((row: any) => ({
    id: String(row.id),
    platform: String(row.platform ?? ""),
    started_at: String(row.started_at),
    ended_at: row.ended_at ? String(row.ended_at) : null,
  })) as JourneyRow[];
}

async function realizedBundle(driverId: string, period: ResolvedPeriod) {
  const journeys = await journeysInPeriod(driverId, period);
  if (!journeys.length) return {
    journeys,
    items: [] as RealizedItem[],
    completed: 0,
    revenue: 0,
    matched: 0,
    sessionCompleted: 0,
    sessionEarnings: 0,
  };
  const ids = new Set(journeys.map((j) => j.id));
  const result = await listJourneyRealized0262(driverId, safeLookbackDays(period));
  const items = ((result.items ?? []) as RealizedItem[]).filter((item) => ids.has(String(item.journey_id)));
  return {
    journeys,
    items,
    completed: items.reduce((sum, item) => sum + Math.max(0, Number(item.completed_trips || 0)), 0),
    revenue: r2(items.reduce((sum, item) => sum + Math.max(0, Number(item.realized_revenue || 0)), 0)),
    matched: items.reduce((sum, item) => sum + Math.max(0, Number(item.fare_matched_trips || 0)), 0),
    sessionCompleted: items.reduce((sum, item) => sum + Math.max(0, Number(item.session_completed_trips || 0)), 0),
    sessionEarnings: r2(items.reduce((sum, item) => sum + Math.max(0, Number(item.session_earnings || 0)), 0)),
  };
}

async function vehicleBundle(driverId: string, period: ResolvedPeriod, journeys?: JourneyRow[]) {
  const periodJourneys = journeys ?? await journeysInPeriod(driverId, period);
  if (!periodJourneys.length) return {
    journeys: periodJourneys,
    metrics: [] as MetricRow[],
    energy: [] as EnergyRow[],
    distance: 0,
    metricsWithDistance: 0,
    energySpend: 0,
    energyEntriesWithSpend: 0,
  };
  const ids = new Set(periodJourneys.map((j) => j.id));
  const result = await listJourneyMetrics(driverId, safeLookbackDays(period));
  const metrics = ((result.metrics ?? []) as MetricRow[]).filter((row) => ids.has(String(row.journey_id)));
  const energy = ((result.energy_entries ?? []) as EnergyRow[]).filter((row) => ids.has(String(row.journey_id)));
  const validDistances = metrics.map((row) => n(row.distance_km)).filter((value): value is number => value !== null && value >= 0);
  const spends = energy.map((row) => n(row.amount_paid)).filter((value): value is number => value !== null && value >= 0);
  return {
    journeys: periodJourneys,
    metrics,
    energy,
    distance: r2(validDistances.reduce((a, b) => a + b, 0)),
    metricsWithDistance: validDistances.length,
    energySpend: r2(spends.reduce((a, b) => a + b, 0)),
    energyEntriesWithSpend: spends.length,
  };
}

async function costAndProfit(driverId: string, period: ResolvedPeriod) {
  const realized = await realizedBundle(driverId, period);
  const vehicle = await vehicleBundle(driverId, period, realized.journeys);
  const profile = await getCostProfile(driverId);
  const effective = n((profile as any)?.calculation?.effective_cost_per_km)
    ?? n((profile as any)?.legacy_cost_per_km);
  const estimatedOperatingCost = effective !== null && vehicle.metricsWithDistance > 0
    ? r2(vehicle.distance * effective)
    : null;
  const estimatedProfit = estimatedOperatingCost !== null && realized.matched > 0
    ? r2(realized.revenue - estimatedOperatingCost)
    : null;
  return { realized, vehicle, profile, effective, estimatedOperatingCost, estimatedProfit };
}

function metricValue(summary: ReturnType<typeof summarizeOffers>, selected: MetricKey | null) {
  switch (selected) {
    case "PER_KM": return summary.average_per_km;
    case "PER_MINUTE": return summary.average_per_minute;
    case "FARE": return summary.average_fare;
    case "PER_HOUR":
    default: return summary.average_per_hour;
  }
}

function metricLabel(selected: MetricKey | null) {
  switch (selected) {
    case "PER_KM": return "R$/km";
    case "PER_MINUTE": return "R$/min";
    case "FARE": return "Tarifa média";
    case "PER_HOUR":
    default: return "R$/h";
  }
}

function metricFormatted(selected: MetricKey | null, value: number | null) {
  return selected === "FARE" ? money(value) : value === null ? "—" : `R$ ${number(value)}`;
}

function groupRows(offers: OfferRow[], key: (row: OfferRow) => string | null) {
  const groups = new Map<string, OfferRow[]>();
  for (const offer of offers) {
    const value = key(offer)?.trim();
    if (!value) continue;
    groups.set(value, [...(groups.get(value) ?? []), offer]);
  }
  return [...groups.entries()].map(([label, rows]) => ({ label, rows, summary: summarizeOffers(rows) }));
}

function rankedGroups(groups: ReturnType<typeof groupRows>, selected: MetricKey | null) {
  return groups
    .filter((group) => group.rows.length >= MIN_RANKING_SAMPLES && metricValue(group.summary, selected) !== null)
    .sort((a, b) => (metricValue(b.summary, selected) ?? -Infinity) - (metricValue(a.summary, selected) ?? -Infinity) || b.rows.length - a.rows.length);
}

function filterNamedRegions(groups: ReturnType<typeof groupRows>, regions: string[]) {
  if (!regions.length) return groups;
  return groups.filter((group) => {
    const normalized = normalizeLabel(group.label);
    return regions.some((region) => normalized.includes(normalizeLabel(region)));
  });
}

async function periodSummary(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const [{ rows, truncated }, realized, vehicle] = await Promise.all([
    canonicalOffers(driverId, plan),
    realizedBundle(driverId, plan.period),
    vehicleBundle(driverId, plan.period),
  ]);
  const summary = summarizeOffers(rows);
  const metrics = [
    metric("offers", "Ofertas observadas", summary.offer_count, number(summary.offer_count), "OBSERVED_OFFER"),
    metric("avg_per_km", "R$/km médio", summary.average_per_km, summary.average_per_km === null ? "—" : `R$ ${number(summary.average_per_km)}`, "OBSERVED_OFFER"),
    metric("avg_per_hour", "R$/h médio", summary.average_per_hour, summary.average_per_hour === null ? "—" : `R$ ${number(summary.average_per_hour)}`, "OBSERVED_OFFER"),
    metric("realized_revenue", "Faturamento confirmado", realized.revenue, money(realized.revenue), "REALIZED_REVENUE"),
    metric("completed_rides", "Corridas confirmadas", realized.completed, number(realized.completed), "REALIZED_RIDE"),
    metric("actual_distance", "Distância real registrada", vehicle.distance, number(vehicle.distance, " km"), "ACTUAL_DISTANCE"),
  ];
  const coverage = coverageEnvelope(realized.matched, realized.completed, "Corridas concluídas com tarifa conhecida");
  return {
    status: rows.length || realized.completed || vehicle.metricsWithDistance ? "ok" : "insufficient",
    title: `Resumo — ${plan.period.label}`,
    semantic: "MIXED",
    source: "personal_canonical+outcomes+journey_metrics",
    scope: "personal",
    sampleCount: rows.length,
    coverage,
    completeness: truncated || (realized.completed > 0 && realized.matched < realized.completed) ? "partial" : "complete",
    metrics,
    details: { observed: summary, realized, distance_coverage: coverageEnvelope(vehicle.metricsWithDistance, realized.journeys.length, "Jornadas com odômetro completo") },
    limitations: [
      ...(truncated ? ["A massa de ofertas atingiu o limite analítico de 50 mil registros neste recorte."] : []),
      "Ofertas observadas não são faturamento realizado.",
      ...(realized.completed > 0 && realized.matched < realized.completed ? ["Parte das corridas concluídas ainda não possui tarifa conciliada."] : []),
    ],
  };
}

async function realizedEarnings(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const realized = await realizedBundle(driverId, plan.period);
  const coverage = coverageEnvelope(realized.matched, realized.completed, "Corridas concluídas com tarifa conhecida");
  return {
    status: realized.completed > 0 ? "ok" : "insufficient",
    title: `Faturamento realizado — ${plan.period.label}`,
    semantic: "REALIZED_REVENUE",
    source: "ride_outcomes+ride_offers",
    scope: "personal",
    sampleCount: realized.completed,
    coverage,
    completeness: realized.completed > 0 && realized.matched === realized.completed ? "complete" : "partial",
    metrics: [
      metric("realized_revenue", "Faturamento confirmado", realized.revenue, money(realized.revenue), "REALIZED_REVENUE"),
      metric("completed_rides", "Corridas concluídas confirmadas", realized.completed, number(realized.completed), "REALIZED_RIDE"),
      metric("fare_matched", "Corridas com tarifa conhecida", realized.matched, number(realized.matched), "REALIZED_RIDE"),
    ],
    details: { session_completed_trips: realized.sessionCompleted, session_earnings: realized.sessionEarnings },
    limitations: realized.completed === 0
      ? ["Não há corridas COMPLETED confirmadas neste período; ofertas observadas não foram somadas como faturamento."]
      : realized.matched < realized.completed
        ? ["O faturamento mostrado cobre somente corridas concluídas com tarifa conhecida."]
        : [],
  };
}

async function actualDistance(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const journeys = await journeysInPeriod(driverId, plan.period);
  const vehicle = await vehicleBundle(driverId, plan.period, journeys);
  const coverage = coverageEnvelope(vehicle.metricsWithDistance, journeys.length, "Jornadas com odômetro completo");
  return {
    status: vehicle.metricsWithDistance > 0 ? "ok" : "insufficient",
    title: `Distância real — ${plan.period.label}`,
    semantic: "ACTUAL_DISTANCE",
    source: "journey_vehicle_metrics",
    scope: "personal",
    sampleCount: vehicle.metricsWithDistance,
    coverage,
    completeness: vehicle.metricsWithDistance === journeys.length && journeys.length > 0 ? "complete" : "partial",
    metrics: [metric("actual_distance", "Distância real registrada", vehicle.distance, number(vehicle.distance, " km"), "ACTUAL_DISTANCE")],
    limitations: vehicle.metricsWithDistance === 0 ? ["Não há jornada com odômetro inicial e final suficiente neste período."] : [],
  };
}

async function operatingCost(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const bundle = await costAndProfit(driverId, plan.period);
  const journeyCoverage = coverageEnvelope(bundle.vehicle.metricsWithDistance, bundle.realized.journeys.length, "Jornadas com distância real");
  const metrics: AnswerMetric[] = [
    metric("actual_energy_spend", "Energia/combustível registrado", bundle.vehicle.energyEntriesWithSpend > 0 ? bundle.vehicle.energySpend : null, bundle.vehicle.energyEntriesWithSpend > 0 ? money(bundle.vehicle.energySpend) : "—", "ACTUAL_COST"),
    metric("actual_distance", "Distância real registrada", bundle.vehicle.distance, number(bundle.vehicle.distance, " km"), "ACTUAL_DISTANCE"),
    metric("effective_cost_per_km", "Custo modelado por km", bundle.effective, bundle.effective === null ? "—" : `R$ ${number(bundle.effective)}/km`, "ESTIMATED_COST"),
    metric("estimated_operating_cost", "Custo operacional estimado", bundle.estimatedOperatingCost, money(bundle.estimatedOperatingCost), "ESTIMATED_COST"),
  ];
  return {
    status: bundle.vehicle.energyEntriesWithSpend > 0 || bundle.estimatedOperatingCost !== null ? "ok" : "insufficient",
    title: `Custos — ${plan.period.label}`,
    semantic: "MIXED",
    source: "journey_energy_entries+journey_vehicle_metrics+cost_profile",
    scope: "personal",
    sampleCount: Math.max(bundle.vehicle.energyEntriesWithSpend, bundle.vehicle.metricsWithDistance),
    coverage: journeyCoverage,
    completeness: journeyCoverage.value === 1 ? "complete" : "partial",
    metrics,
    limitations: [
      "Valores pagos em combustível/energia são registros reais; custo operacional por km continua sendo estimativa do perfil de custos.",
      ...(bundle.vehicle.metricsWithDistance === 0 ? ["Sem distância real suficiente, o custo operacional modelado do período não pode ser calculado."] : []),
    ],
  };
}

async function profitability(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const bundle = await costAndProfit(driverId, plan.period);
  const coverage = coverageEnvelope(bundle.realized.matched, bundle.realized.completed, "Corridas concluídas com tarifa conhecida");
  const workHours = r2(bundle.realized.journeys.map(durationHours).filter((v): v is number => v !== null).reduce((a, b) => a + b, 0));
  const profitPerHour = bundle.estimatedProfit !== null && workHours > 0 ? r2(bundle.estimatedProfit / workHours) : null;
  return {
    status: bundle.estimatedProfit !== null && bundle.realized.matched > 0 ? "ok" : "insufficient",
    title: `Lucratividade — ${plan.period.label}`,
    semantic: "MIXED",
    source: "realized_revenue+actual_distance+cost_profile",
    scope: "personal",
    sampleCount: bundle.realized.completed,
    coverage,
    completeness: coverage.value === 1 && bundle.vehicle.metricsWithDistance === bundle.realized.journeys.length ? "complete" : "partial",
    metrics: [
      metric("realized_revenue", "Faturamento confirmado", bundle.realized.revenue, money(bundle.realized.revenue), "REALIZED_REVENUE"),
      metric("estimated_operating_cost", "Custo operacional estimado", bundle.estimatedOperatingCost, money(bundle.estimatedOperatingCost), "ESTIMATED_COST"),
      metric("estimated_profit", "Resultado estimado", bundle.estimatedProfit, money(bundle.estimatedProfit), "ESTIMATED_COST"),
      metric("estimated_profit_per_hour", "Resultado estimado por hora", profitPerHour, profitPerHour === null ? "—" : `R$ ${number(profitPerHour)}/h`, "ESTIMATED_COST"),
    ],
    limitations: [
      "O resultado é faturamento confirmado menos custo operacional modelado; não deve ser apresentado como lucro contábil real.",
      ...(bundle.estimatedProfit === null ? ["Faltam distância real e/ou perfil de custo suficiente para calcular o resultado."] : []),
    ],
  };
}

async function bestJourney(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const realized = await realizedBundle(driverId, plan.period);
  const vehicle = await vehicleBundle(driverId, plan.period, realized.journeys);
  const metricsByJourney = new Map(vehicle.metrics.map((row) => [String(row.journey_id), n(row.distance_km)]));
  const journeyById = new Map(realized.journeys.map((row) => [row.id, row]));
  const candidates = realized.items
    .filter((item) => item.completed_trips > 0 && item.fare_matched_trips > 0)
    .map((item) => {
      const journey = journeyById.get(String(item.journey_id));
      const hours = journey ? durationHours(journey) : null;
      return {
        journey_id: String(item.journey_id),
        revenue: Number(item.realized_revenue || 0),
        completed: Number(item.completed_trips || 0),
        revenue_complete: item.revenue_complete,
        distance: metricsByJourney.get(String(item.journey_id)) ?? null,
        hours,
        revenue_per_hour: hours && hours > 0 ? r2(Number(item.realized_revenue || 0) / hours) : null,
        started_at: journey?.started_at ?? null,
      };
    });
  const selectedMetric = plan.metric === "PER_HOUR" || plan.metric === "PROFIT_PER_HOUR" ? "revenue_per_hour" : "revenue";
  const ranked = candidates.filter((row) => row.revenue_complete).sort((a, b) => (Number(b[selectedMetric] ?? -Infinity) - Number(a[selectedMetric] ?? -Infinity)));
  const best = ranked[0];
  if (!best) return {
    status: "insufficient", title: `Melhor jornada — ${plan.period.label}`, semantic: "REALIZED_REVENUE", source: "ride_outcomes+journeys",
    scope: "personal", sampleCount: candidates.length, metrics: [], limitations: ["Ainda não há jornadas com corridas concluídas e faturamento conciliado suficiente para ranquear."],
  };
  return {
    status: "ok",
    title: `Melhor jornada — ${plan.period.label}`,
    semantic: "MIXED",
    source: "ride_outcomes+journeys+journey_vehicle_metrics",
    scope: "personal",
    sampleCount: ranked.length,
    coverage: coverageEnvelope(ranked.filter((row) => row.revenue_complete).length, candidates.length, "Jornadas com faturamento completo"),
    metrics: [
      metric("journey_revenue", "Faturamento da jornada", best.revenue, money(best.revenue), "REALIZED_REVENUE"),
      metric("journey_completed", "Corridas concluídas", best.completed, number(best.completed), "REALIZED_RIDE"),
      metric("journey_revenue_per_hour", "Faturamento por hora de jornada", best.revenue_per_hour, best.revenue_per_hour === null ? "—" : `R$ ${number(best.revenue_per_hour)}/h`, "REALIZED_REVENUE"),
      metric("journey_distance", "Distância real", best.distance, best.distance === null ? "—" : number(best.distance, " km"), "ACTUAL_DISTANCE"),
    ],
    details: { journey: best },
  };
}

async function comparePeriods(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const previous = previousEquivalentPeriod(plan.period);
  const [currentFound, previousFound] = await Promise.all([
    canonicalOffers(driverId, plan),
    canonicalOffersForPeriod(driverId, plan, previous),
  ]);
  const current = summarizeOffers(currentFound.rows);
  const before = summarizeOffers(previousFound.rows);
  const selected: MetricKey = plan.metric ?? "PER_HOUR";
  const currentValue = metricValue(current, selected);
  const previousValue = metricValue(before, selected);
  const deltaPct = currentValue !== null && previousValue !== null && previousValue !== 0 ? r2(((currentValue - previousValue) / Math.abs(previousValue)) * 100) : null;
  return {
    status: currentFound.rows.length >= MIN_RANKING_SAMPLES && previousFound.rows.length >= MIN_RANKING_SAMPLES ? "ok" : "insufficient",
    title: `${plan.period.label} × período anterior`,
    semantic: "OBSERVED_OFFER",
    source: "personal_canonical",
    scope: "personal",
    sampleCount: currentFound.rows.length + previousFound.rows.length,
    coverage: coverageEnvelope(Math.min(currentFound.rows.length, previousFound.rows.length), Math.max(currentFound.rows.length, previousFound.rows.length), "Equilíbrio entre amostras comparadas"),
    completeness: currentFound.truncated || previousFound.truncated ? "partial" : "complete",
    metrics: [
      metric("current", `${metricLabel(selected)} — ${plan.period.label}`, currentValue, metricFormatted(selected, currentValue), "OBSERVED_OFFER"),
      metric("previous", `${metricLabel(selected)} — anterior`, previousValue, metricFormatted(selected, previousValue), "OBSERVED_OFFER"),
      metric("delta_pct", "Variação", deltaPct, deltaPct === null ? "—" : `${number(deltaPct)}%`, "MARKET_OBSERVATION"),
    ],
    details: { current_period: plan.period, previous_period: previous, current_sample: currentFound.rows.length, previous_sample: previousFound.rows.length },
    limitations: currentFound.rows.length < MIN_RANKING_SAMPLES || previousFound.rows.length < MIN_RANKING_SAMPLES ? ["A comparação exige pelo menos 20 ofertas em cada lado para uma conclusão segura."] : [],
  };
}

async function regionPerformance(driverId: string, plan: QuestionPlan, compare = false): Promise<DomainResult> {
  if (plan.entities.source === "collective") {
    return {
      status: "insufficient", title: `Regiões — ${plan.period.label}`, semantic: "MARKET_OBSERVATION", source: "collective",
      scope: "collective", sampleCount: 0, metrics: [], limitations: ["O recorte coletivo por período de calendário ainda não é calculado no Gate 2. O modo Agora continua disponível com agregações coletivas protegidas."],
    };
  }
  const { rows, truncated } = await canonicalOffers(driverId, plan);
  let groups = groupRows(rows, (offer) => offer.pickup_label || offer.pickup_cell || null);
  groups = filterNamedRegions(groups, plan.entities.regions);
  const selected = plan.metric ?? "PER_KM";
  const ranked = rankedGroups(groups, selected);
  const picked = compare && plan.entities.regions.length >= 2
    ? plan.entities.regions.map((name) => ranked.find((group) => normalizeLabel(group.label).includes(normalizeLabel(name)))).filter(Boolean) as typeof ranked
    : ranked.slice(0, 5);
  if (!picked.length) {
    const maxSample = groups.reduce((max, group) => Math.max(max, group.rows.length), 0);
    return {
      status: "insufficient", title: `Regiões — ${plan.period.label}`, semantic: "OBSERVED_OFFER", source: "personal_canonical", scope: "personal",
      sampleCount: rows.length, metrics: [], details: { maximum_group_sample: maxSample }, limitations: ["Nenhuma região atingiu a amostra mínima de 20 ofertas no recorte solicitado; o Sr. Rotas não cria ranking com amostra insuficiente."],
    };
  }
  const best = picked[0];
  return {
    status: "ok",
    title: compare ? `Comparação de regiões — ${plan.period.label}` : `Regiões — ${plan.period.label}`,
    semantic: "OBSERVED_OFFER",
    source: "personal_canonical",
    scope: "personal",
    sampleCount: picked.reduce((sum, item) => sum + item.rows.length, 0),
    completeness: truncated ? "partial" : "complete",
    metrics: [metric("best_region_metric", `${metricLabel(selected)} — ${best.label}`, metricValue(best.summary, selected), metricFormatted(selected, metricValue(best.summary, selected)), "OBSERVED_OFFER")],
    details: {
      ranking: picked.map((group) => ({ region: group.label, sample_count: group.rows.length, value: metricValue(group.summary, selected), metric: selected })),
    },
    alternatives: picked.slice(1, 4).map((group) => `${group.label}: ${metricFormatted(selected, metricValue(group.summary, selected))} (${group.rows.length} ofertas)`),
    limitations: ["Ranking baseado em ofertas observadas; não comprova corridas concluídas nem faturamento realizado."],
  };
}

async function hourPerformance(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const [{ rows, truncated }, prefs] = await Promise.all([canonicalOffers(driverId, plan), ensurePreferences(driverId)]);
  const selected = plan.metric ?? "PER_HOUR";
  const groups = groupRows(rows, (offer) => `${String(localHour(offer.observed_at, prefs.timezone || "America/Sao_Paulo")).padStart(2, "0")}:00`);
  const ranked = rankedGroups(groups, selected);
  if (!ranked.length) return {
    status: "insufficient", title: `Horários — ${plan.period.label}`, semantic: "OBSERVED_OFFER", source: "personal_canonical", scope: "personal",
    sampleCount: rows.length, metrics: [], limitations: ["Nenhum horário atingiu a amostra mínima de 20 ofertas no período."],
  };
  const best = ranked[0];
  return {
    status: "ok", title: `Horários — ${plan.period.label}`, semantic: "OBSERVED_OFFER", source: "personal_canonical", scope: "personal",
    sampleCount: rows.length, completeness: truncated ? "partial" : "complete",
    metrics: [metric("best_hour", `${metricLabel(selected)} — ${best.label}`, metricValue(best.summary, selected), metricFormatted(selected, metricValue(best.summary, selected)), "OBSERVED_OFFER")],
    details: { ranking: ranked.slice(0, 8).map((group) => ({ hour: group.label, sample_count: group.rows.length, value: metricValue(group.summary, selected) })) },
    alternatives: ranked.slice(1, 4).map((group) => `${group.label}: ${metricFormatted(selected, metricValue(group.summary, selected))} (${group.rows.length} ofertas)`),
  };
}

async function categoryPerformance(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const { rows, truncated } = await canonicalOffers(driverId, plan);
  const selected = plan.metric ?? "PER_KM";
  const ranked = rankedGroups(groupRows(rows, (offer) => offer.service_type || "unknown"), selected);
  if (!ranked.length) return {
    status: "insufficient", title: `Categorias — ${plan.period.label}`, semantic: "OBSERVED_OFFER", source: "personal_canonical", scope: "personal",
    sampleCount: rows.length, metrics: [], limitations: ["Nenhuma categoria atingiu a amostra mínima de 20 ofertas no período."],
  };
  const best = ranked[0];
  return {
    status: "ok", title: `Categorias — ${plan.period.label}`, semantic: "OBSERVED_OFFER", source: "personal_canonical", scope: "personal",
    sampleCount: rows.length, completeness: truncated ? "partial" : "complete",
    metrics: [metric("best_category", `${metricLabel(selected)} — ${best.label}`, metricValue(best.summary, selected), metricFormatted(selected, metricValue(best.summary, selected)), "OBSERVED_OFFER")],
    details: { ranking: ranked.map((group) => ({ category: group.label, sample_count: group.rows.length, value: metricValue(group.summary, selected) })) },
    alternatives: ranked.slice(1, 4).map((group) => `${group.label}: ${metricFormatted(selected, metricValue(group.summary, selected))} (${group.rows.length} ofertas)`),
  };
}

async function offerQuality(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const { rows, truncated } = await canonicalOffers(driverId, plan);
  const summary = summarizeOffers(rows);
  const goodRate = rows.length ? r2((summary.verdicts.boa / rows.length) * 100) : null;
  return {
    status: rows.length >= MIN_RANKING_SAMPLES ? "ok" : "insufficient", title: `Qualidade das ofertas — ${plan.period.label}`, semantic: "OBSERVED_OFFER",
    source: "personal_canonical", scope: "personal", sampleCount: rows.length, completeness: truncated ? "partial" : "complete",
    metrics: [
      metric("good", "Boas", summary.verdicts.boa, number(summary.verdicts.boa), "OBSERVED_OFFER"),
      metric("regular", "Regulares", summary.verdicts.regular, number(summary.verdicts.regular), "OBSERVED_OFFER"),
      metric("bad", "Ruins", summary.verdicts.ruim, number(summary.verdicts.ruim), "OBSERVED_OFFER"),
      metric("good_rate", "Percentual boas", goodRate, goodRate === null ? "—" : `${number(goodRate)}%`, "OBSERVED_OFFER"),
    ],
    limitations: rows.length < MIN_RANKING_SAMPLES ? ["Amostra abaixo de 20 ofertas; trate a distribuição como indicativa."] : [],
  };
}

async function completedRides(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const realized = await realizedBundle(driverId, plan.period);
  return {
    status: realized.completed > 0 ? "ok" : "insufficient", title: `Corridas concluídas — ${plan.period.label}`, semantic: "REALIZED_RIDE",
    source: "ride_outcomes", scope: "personal", sampleCount: realized.completed,
    coverage: coverageEnvelope(realized.matched, realized.completed, "Corridas concluídas com tarifa conhecida"),
    metrics: [metric("completed_rides", "Corridas concluídas confirmadas", realized.completed, number(realized.completed), "REALIZED_RIDE")],
    details: { session_completed_trips: realized.sessionCompleted },
    limitations: realized.completed === 0 ? ["Nenhuma corrida COMPLETED foi confirmada neste período. Ofertas observadas não são usadas para preencher essa contagem."] : [],
  };
}

async function latestDestination(driverId: string, period: ResolvedPeriod) {
  const found = await fetchOffers(driverId, { from: period.from, to: period.to, limit: 50 });
  return found.offers.find((offer) => offer.destination_cell && offer.estimated_arrival_at) ?? null;
}

async function destinationContinuityResult(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const offer = await latestDestination(driverId, plan.period);
  if (!offer?.destination_cell || !offer.estimated_arrival_at) return {
    status: "insufficient", title: "Continuidade no destino", semantic: "HISTORICAL_CONTINUITY", source: "none", scope: "personal", sampleCount: 0,
    metrics: [], limitations: ["Não encontrei uma oferta operacional recente com destino e ETA suficientes para calcular continuidade."],
  };
  const result = await destinationContinuity(driverId, { cell: offer.destination_cell, eta: offer.estimated_arrival_at, destinationLabel: offer.destination_label });
  const display = result.display;
  return {
    status: display.kind === "insufficient" ? "insufficient" : "ok",
    title: `Continuidade — ${display.region_label || "destino"}`,
    semantic: display.kind === "probability" ? "REGIONAL_EXPOSURE" : "HISTORICAL_CONTINUITY",
    source: String(display.source),
    scope: String(display.source).startsWith("collective") ? "collective" : "personal",
    sampleCount: Number(display.samples || 0),
    metrics: [
      metric("p10", "Chance histórica em até 10 min", display.probability_pct, display.probability_pct === null ? String(display.level) : `${number(display.probability_pct)}%`, display.kind === "probability" ? "REGIONAL_EXPOSURE" : "HISTORICAL_CONTINUITY"),
    ],
    details: { destination: offer.destination_label, cell: offer.destination_cell, eta: offer.estimated_arrival_at, display },
    limitations: ["Estimativa histórica; não representa demanda ao vivo e não garante nova corrida."],
  };
}

async function currentRegionAdvice(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const result: any = await nowIntelligence(driverId, { mode: "now", source: plan.entities.source, region: plan.entities.regions[0] ?? null });
  const preferred = String(result.preferred || "sr_rotas_seed");
  const list = preferred === "collective" ? result.collective : preferred === "personal" ? result.personal : result.seed;
  const rows = Array.isArray(list) ? list : [];
  const best = rows[0];
  if (!best) return {
    status: "insufficient", title: "Onde ficar agora", semantic: "MARKET_OBSERVATION", source: preferred, scope: preferred === "collective" ? "collective" : "personal",
    sampleCount: 0, metrics: [], limitations: ["Não há amostra histórica suficiente nesta faixa para indicar uma região com segurança."],
  };
  return {
    status: best.confidence === "insufficient" ? "insufficient" : "ok",
    title: "Onde ficar agora",
    semantic: "MARKET_OBSERVATION",
    source: preferred,
    scope: preferred === "collective" ? "collective" : preferred === "personal" ? "personal" : "mixed",
    sampleCount: Number(best.sample_count || 0),
    metrics: [
      metric("region", "Região histórica mais aderente", String(best.region_label), String(best.region_label), "MARKET_OBSERVATION"),
      metric("score", "Aderência ao perfil", Number(best.score || 0), `${number(Number(best.score || 0))}/100`, "MARKET_OBSERVATION"),
      metric("per_km", "R$/km histórico", n(best.median_per_km ?? best.average_per_km), n(best.median_per_km ?? best.average_per_km) === null ? "—" : `R$ ${number(n(best.median_per_km ?? best.average_per_km))}`, "MARKET_OBSERVATION"),
      metric("per_hour", "R$/h histórico", n(best.median_per_hour ?? best.average_per_hour), n(best.median_per_hour ?? best.average_per_hour) === null ? "—" : `R$ ${number(n(best.median_per_hour ?? best.average_per_hour))}`, "MARKET_OBSERVATION"),
    ],
    alternatives: rows.slice(1, 4).map((row: any) => `${row.region_label}: aderência ${row.score}/100, ${row.sample_count} ofertas`),
    details: { scope: result.scope, preferred, target: result.target },
    limitations: [String(result.note || "Histórico agregado; não é demanda ao vivo e não garante corrida.")],
  };
}

async function marketQuality(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const previous = previousEquivalentPeriod(plan.period);
  const [currentFound, previousFound] = await Promise.all([canonicalOffers(driverId, plan), canonicalOffersForPeriod(driverId, plan, previous)]);
  const current = summarizeOffers(currentFound.rows);
  const before = summarizeOffers(previousFound.rows);
  const enough = currentFound.rows.length >= MIN_RANKING_SAMPLES && previousFound.rows.length >= MIN_RANKING_SAMPLES;
  const perHourDelta = current.average_per_hour !== null && before.average_per_hour !== null && before.average_per_hour !== 0
    ? r2(((current.average_per_hour - before.average_per_hour) / Math.abs(before.average_per_hour)) * 100)
    : null;
  const perKmDelta = current.average_per_km !== null && before.average_per_km !== null && before.average_per_km !== 0
    ? r2(((current.average_per_km - before.average_per_km) / Math.abs(before.average_per_km)) * 100)
    : null;
  return {
    status: enough ? "ok" : "insufficient", title: `Qualidade do mercado — ${plan.period.label}`, semantic: "MARKET_OBSERVATION", source: "personal_canonical",
    scope: "personal", sampleCount: currentFound.rows.length + previousFound.rows.length,
    coverage: coverageEnvelope(Math.min(currentFound.rows.length, previousFound.rows.length), Math.max(currentFound.rows.length, previousFound.rows.length), "Equilíbrio entre períodos"),
    metrics: [
      metric("per_hour_delta", "Variação de R$/h", perHourDelta, perHourDelta === null ? "—" : `${number(perHourDelta)}%`, "MARKET_OBSERVATION"),
      metric("per_km_delta", "Variação de R$/km", perKmDelta, perKmDelta === null ? "—" : `${number(perKmDelta)}%`, "MARKET_OBSERVATION"),
      metric("offers", "Ofertas no período", current.offer_count, number(current.offer_count), "OBSERVED_OFFER"),
    ],
    details: { current, previous: before, previous_period: previous },
    limitations: enough ? ["Qualidade é relativa ao período anterior equivalente e mede ofertas observadas, não demanda ao vivo."] : ["São necessárias pelo menos 20 ofertas em cada período para classificar a diferença com segurança."],
  };
}

async function waitOrMove(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const continuity = await destinationContinuityResult(driverId, plan);
  if (continuity.status !== "ok") return { ...continuity, title: "Esperar ou se deslocar?" };
  const probability = n(continuity.metrics.find((item) => item.key === "p10")?.value);
  const advice = await currentRegionAdvice(driverId, plan);
  const alternatives = [...(advice.alternatives ?? [])];
  const note = probability !== null && probability >= 60
    ? "A exposição histórica dá suporte para esperar alguns minutos, mas não há garantia de nova corrida."
    : "A continuidade histórica não é forte o bastante para justificar sozinha ficar parado. O Sr. Rotas não recomenda deslocamento sem considerar distância e custo do reposicionamento.";
  return {
    ...continuity,
    title: "Esperar ou se deslocar?",
    semantic: "MIXED",
    source: `${continuity.source}+${advice.source}`,
    scope: continuity.scope === advice.scope ? continuity.scope : "mixed",
    alternatives,
    note,
    limitations: [...(continuity.limitations ?? []), "Sem rota/custo de reposicionamento confiáveis, o motor não inventa uma ordem de deslocamento."],
  };
}

function missedOpportunities(plan: QuestionPlan): DomainResult {
  return {
    status: "not_ready",
    title: `Oportunidades não realizadas — ${plan.period.label}`,
    semantic: "MIXED",
    source: "reconciliation_required",
    scope: "personal",
    sampleCount: 0,
    metrics: [],
    limitations: ["A análise de oportunidades perdidas depende da conciliação de jornadas. O Gate 2 reconhece a intenção, mas não estima dinheiro perdido antes da confirmação humana prevista para o Gate 3."],
  };
}

export async function queryAnalyticsDomain(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  switch (plan.intent) {
    case "REALIZED_EARNINGS": return realizedEarnings(driverId, plan);
    case "ACTUAL_DISTANCE": return actualDistance(driverId, plan);
    case "OPERATING_COST": return operatingCost(driverId, plan);
    case "PROFITABILITY": return profitability(driverId, plan);
    case "BEST_JOURNEY": return bestJourney(driverId, plan);
    case "COMPARE_PERIODS": return comparePeriods(driverId, plan);
    case "REGION_PERFORMANCE": return regionPerformance(driverId, plan, false);
    case "COMPARE_REGIONS": return regionPerformance(driverId, plan, true);
    case "HOUR_PERFORMANCE": return hourPerformance(driverId, plan);
    case "CATEGORY_PERFORMANCE": return categoryPerformance(driverId, plan);
    case "OFFER_QUALITY": return offerQuality(driverId, plan);
    case "COMPLETED_RIDES": return completedRides(driverId, plan);
    case "DESTINATION_CONTINUITY": return destinationContinuityResult(driverId, plan);
    case "CURRENT_REGION_ADVICE": return currentRegionAdvice(driverId, plan);
    case "MARKET_QUALITY": return marketQuality(driverId, plan);
    case "MISSED_OPPORTUNITIES": return missedOpportunities(plan);
    case "WAIT_OR_MOVE": return waitOrMove(driverId, plan);
    case "PERIOD_SUMMARY":
    default: return periodSummary(driverId, plan);
  }
}
