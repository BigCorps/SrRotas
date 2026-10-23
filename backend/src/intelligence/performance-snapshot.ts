import type { StructuredAnswer, AnswerSemantic, ResolvedPeriod } from "./contracts";

/** Single evidence-backed performance projection shared by UI/IA/Agora consumers.
 * No extra calculations or persisted aggregates; only validated readings from
 * the canonical NaturalQuestionEngine answers. Never infer final payment. */
export const PERFORMANCE_SNAPSHOT_SCHEMA = "srrotas-performance-snapshot-v1" as const;
export const PERFORMANCE_KEYS = [
  "realized_revenue", "estimated_profit", "completed_rides", "actual_distance",
  "closed_journey_hours", "realized_revenue_per_hour", "realized_revenue_per_km",
  "offers", "avg_per_km", "avg_per_hour", "good_rate",
] as const;
export type PerformanceKey = (typeof PERFORMANCE_KEYS)[number];
export type PerformanceReading = {
  key: PerformanceKey;
  formatted: string;
  value: number | null;
  semantic: AnswerSemantic | null;
  status: "available" | "unavailable";
  source: string | null;
  sampleCount: number;
  completeness: "complete" | "partial" | null;
  coverage: number | null;
};
export type PerformanceSnapshot = {
  schema: typeof PERFORMANCE_SNAPSHOT_SCHEMA;
  period: ResolvedPeriod | null;
  metrics: Record<PerformanceKey, PerformanceReading>;
  limitations: string[];
};

export function buildPerformanceSnapshot(answers: StructuredAnswer[]): PerformanceSnapshot {
  const summary = answers.find((a) => a.intent === "PERIOD_SUMMARY");
  const profitability = answers.find((a) => a.intent === "PROFITABILITY");
  const quality = answers.find((a) => a.intent === "OFFER_QUALITY");
  const reading = (key: PerformanceKey): PerformanceReading => {
    const owner = key === "estimated_profit" ? profitability :
      key === "good_rate" ? quality : summary;
    const metric = owner?.metrics.find((m) => m.key === key);
    const numeric = typeof metric?.value === "number" && Number.isFinite(metric.value)
      ? metric.value : null;
    const eligible = owner?.status === "ok" && numeric !== null &&
      Boolean(metric?.formatted && metric.formatted !== "—");
    const completeProfit = key !== "estimated_profit" ||
      owner?.evidence.completeness === "complete";
    const knownFare = key !== "realized_revenue" ||
      ((summary?.metrics.find((m) => m.key === "completed_rides")?.value ?? 0) as number) > 0 &&
      typeof summary?.evidence.coverage?.value === "number" && summary.evidence.coverage.value > 0;
    const knownDistance = key !== "actual_distance" || numeric !== null && numeric > 0;
    const available = eligible && completeProfit && knownFare && knownDistance;
    return {
      key, value: available ? numeric : null,
      formatted: available ? metric!.formatted : "—",
      semantic: metric?.semantic ?? null,
      status: available ? "available" : "unavailable",
      source: owner?.evidence.source ?? null,
      sampleCount: owner?.evidence.sampleCount ?? 0,
      completeness: owner?.evidence.completeness ?? null,
      coverage: owner?.evidence.coverage?.value ?? null,
    };
  };
  const metrics = Object.fromEntries(PERFORMANCE_KEYS.map((key) => [key, reading(key)])) as Record<PerformanceKey, PerformanceReading>;
  return {
    schema: PERFORMANCE_SNAPSHOT_SCHEMA,
    period: summary?.evidence.period ?? answers[0]?.evidence.period ?? null,
    metrics,
    limitations: [
      "Tarifas de corridas concluídas refletem as ofertas vinculadas, não o recebimento final.",
      "Ofertas observadas não comprovam corridas realizadas ou ganhos.",
      ...(summary?.evidence.completeness === "partial" ? ["O período contém evidências parciais."] : []),
    ],
  };
}
