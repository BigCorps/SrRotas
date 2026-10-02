export const NATURAL_QUESTION_VERSION = "srrotas-natural-question-v1" as const;

export const INTENTS = [
  "PERIOD_SUMMARY",
  "REALIZED_EARNINGS",
  "ACTUAL_DISTANCE",
  "OPERATING_COST",
  "PROFITABILITY",
  "BEST_JOURNEY",
  "COMPARE_PERIODS",
  "REGION_PERFORMANCE",
  "COMPARE_REGIONS",
  "HOUR_PERFORMANCE",
  "CATEGORY_PERFORMANCE",
  "OFFER_QUALITY",
  "COMPLETED_RIDES",
  "DESTINATION_CONTINUITY",
  "CURRENT_REGION_ADVICE",
  "MARKET_QUALITY",
  "MISSED_OPPORTUNITIES",
  "WAIT_OR_MOVE",
] as const;

export type QuestionIntent = (typeof INTENTS)[number];

export const SEMANTICS = [
  "OBSERVED_OFFER",
  "REALIZED_RIDE",
  "REALIZED_REVENUE",
  "ESTIMATED_COST",
  "ACTUAL_COST",
  "ACTUAL_DISTANCE",
  "REGIONAL_EXPOSURE",
  "HISTORICAL_CONTINUITY",
  "MARKET_OBSERVATION",
  "MIXED",
] as const;

export type AnswerSemantic = (typeof SEMANTICS)[number];

export type MetricKey =
  | "REVENUE"
  | "DISTANCE"
  | "COST"
  | "PROFIT"
  | "PROFIT_PER_HOUR"
  | "PER_KM"
  | "PER_HOUR"
  | "PER_MINUTE"
  | "FARE"
  | "COMPLETED_RIDES"
  | "CONTINUITY"
  | "QUALITY"
  | "SAMPLE";

export type SourceMode = "personal" | "collective";

export type PeriodKind =
  | "TODAY"
  | "YESTERDAY"
  | "LAST_N_DAYS"
  | "THIS_WEEK"
  | "LAST_WEEK"
  | "THIS_MONTH"
  | "LAST_MONTH"
  | "CALENDAR_DAY"
  | "CUSTOM"
  | "CONTEXT";

export type ResolvedPeriod = {
  kind: PeriodKind;
  label: string;
  from: string;
  to: string;
};

export type QuestionEntities = {
  regions: string[];
  platform: string | null;
  serviceType: string | null;
  source: SourceMode;
};

export type QuestionContext = {
  version: typeof NATURAL_QUESTION_VERSION;
  intent: QuestionIntent;
  metric: MetricKey | null;
  entities: QuestionEntities;
  period: ResolvedPeriod;
};

export type EvidenceConfidence = "insufficient" | "low" | "medium" | "high";
export type EvidenceQuality = "insufficient" | "partial" | "good" | "strong";
export type EvidenceScope = "personal" | "collective" | "mixed";
export type EvidenceCompleteness = "complete" | "partial";

export type EvidenceCoverage = {
  value: number | null;
  numerator: number | null;
  denominator: number | null;
  label: string;
};

export type EvidenceEnvelope = {
  semantic: AnswerSemantic;
  source: string;
  period: ResolvedPeriod;
  sampleCount: number;
  coverage: EvidenceCoverage;
  confidence: EvidenceConfidence;
  dataQuality: EvidenceQuality;
  scope: EvidenceScope;
  completeness: EvidenceCompleteness;
};

export type AnswerMetric = {
  key: string;
  label: string;
  value: number | string | null;
  formatted: string;
  semantic: AnswerSemantic;
};

export type QuestionPlan = {
  rawQuestion: string;
  normalizedQuestion: string;
  intent: QuestionIntent;
  metric: MetricKey | null;
  period: ResolvedPeriod;
  entities: QuestionEntities;
  inherited: {
    intent: boolean;
    metric: boolean;
    period: boolean;
    entities: boolean;
  };
};

export type DomainResult = {
  status: "ok" | "insufficient" | "not_ready";
  title: string;
  semantic: AnswerSemantic;
  source: string;
  scope: EvidenceScope;
  sampleCount: number;
  coverage?: EvidenceCoverage;
  completeness?: EvidenceCompleteness;
  metrics: AnswerMetric[];
  details?: Record<string, unknown>;
  limitations?: string[];
  alternatives?: string[];
  note?: string;
};

export type StructuredAnswer = {
  version: typeof NATURAL_QUESTION_VERSION;
  answer: string;
  intent: QuestionIntent;
  resolved: {
    metric: MetricKey | null;
    period: ResolvedPeriod;
    entities: QuestionEntities;
  };
  evidence: EvidenceEnvelope;
  metrics: AnswerMetric[];
  alternatives: string[];
  limitations: string[];
  context: QuestionContext;
  status: DomainResult["status"];
  details?: Record<string, unknown>;
  // Compatibilidade com o contrato anterior de /api/v1/ask.
  range: { from: string; to: string };
  offer_count: number;
  model: "deterministic-v1";
  usage: null;
};
