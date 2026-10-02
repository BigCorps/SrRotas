import type { DomainResult, EvidenceCompleteness, EvidenceConfidence, EvidenceEnvelope, EvidenceQuality, ResolvedPeriod } from "./contracts";

function confidenceForSamples(samples: number): EvidenceConfidence {
  if (samples >= 100) return "high";
  if (samples >= 50) return "medium";
  if (samples >= 20) return "low";
  return "insufficient";
}

function factualConfidence(result: DomainResult, completeness: EvidenceCompleteness): EvidenceConfidence {
  if (result.status !== "ok" || result.sampleCount <= 0) return "insufficient";
  const coverage = result.coverage?.value;
  if (coverage === null || coverage === undefined) return completeness === "complete" ? "high" : "medium";
  if (coverage >= 0.999 && completeness === "complete") return "high";
  if (coverage >= 0.8) return "medium";
  if (coverage > 0) return "low";
  return "insufficient";
}

function quality(confidence: EvidenceConfidence, completeness: EvidenceCompleteness): EvidenceQuality {
  if (confidence === "insufficient") return "insufficient";
  if (completeness === "partial") return "partial";
  if (confidence === "high") return "strong";
  return "good";
}

export function buildEvidence(period: ResolvedPeriod, result: DomainResult): EvidenceEnvelope {
  const completeness = result.completeness ?? (result.status === "ok" ? "complete" : "partial");
  const coverage = result.coverage ?? {
    value: result.sampleCount > 0 ? 1 : null,
    numerator: result.sampleCount,
    denominator: result.sampleCount,
    label: "Amostra disponível",
  };
  const factual = ["REALIZED_RIDE", "REALIZED_REVENUE", "ACTUAL_DISTANCE", "ACTUAL_COST", "ESTIMATED_COST"].includes(result.semantic);
  let confidence = factual ? factualConfidence(result, completeness) : confidenceForSamples(result.sampleCount);
  if (result.semantic === "MIXED" && result.status === "ok") {
    confidence = coverage.value === null ? "medium" : coverage.value >= 0.8 ? "medium" : coverage.value > 0 ? "low" : "insufficient";
  }
  if (coverage.value !== null && coverage.value < 0.5 && confidence !== "insufficient") confidence = "low";
  if (result.status !== "ok") confidence = "insufficient";
  return {
    semantic: result.semantic,
    source: result.source,
    period,
    sampleCount: Math.max(0, Math.floor(result.sampleCount)),
    coverage,
    confidence,
    dataQuality: quality(confidence, completeness),
    scope: result.scope,
    completeness,
  };
}

export function coverageEnvelope(numerator: number, denominator: number, label: string) {
  const safeDenominator = Math.max(0, denominator);
  const safeNumerator = Math.max(0, Math.min(numerator, safeDenominator || numerator));
  return {
    value: safeDenominator > 0 ? Math.round((safeNumerator / safeDenominator) * 10_000) / 10_000 : null,
    numerator: safeNumerator,
    denominator: safeDenominator,
    label,
  };
}
