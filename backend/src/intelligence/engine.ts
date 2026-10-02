import { ensurePreferences } from "../preferences";
import { queryAnalyticsDomain } from "./analytics-domain";
import { NATURAL_QUESTION_VERSION, type QuestionContext, type QuestionPlan, type StructuredAnswer } from "./contracts";
import { buildEvidence } from "./evidence-policy";
import { normalizeQuestion } from "./normalizer";
import { resolveQuestionPeriod } from "./period-resolver";
import { resolveEntities, resolveIntent, resolveMetric, sanitizeContext } from "./resolver";
import { renderAnswer } from "./renderer";

export async function planNaturalQuestion(
  driverId: string,
  question: string,
  rawContext?: unknown,
  options: { days?: number; from?: string; to?: string } = {},
): Promise<QuestionPlan> {
  const prefs = await ensurePreferences(driverId);
  const context = sanitizeContext(rawContext);
  const normalized = normalizeQuestion(question);

  // O contexto recebido nunca contém driverId nem autoridade de acesso; serve
  // somente para carregar intenção/métrica/período/entidades da conversa curta.
  let period = resolveQuestionPeriod(
    normalized.normalized,
    prefs.timezone || "America/Sao_Paulo",
    context,
    new Date(),
    Math.max(1, Math.min(90, Number(options.days ?? 7) || 7)),
  );
  if (options.from || options.to) {
    const to = options.to ? new Date(options.to) : new Date();
    const from = options.from ? new Date(options.from) : new Date(to.getTime() - Math.max(1, Math.min(90, Number(options.days ?? 7) || 7)) * 86_400_000);
    if (!Number.isFinite(from.getTime()) || !Number.isFinite(to.getTime()) || from >= to || to.getTime() - from.getTime() > 90 * 86_400_000) {
      throw new Error("natural_question_range_invalid");
    }
    period = { period: { kind: "CUSTOM", label: "Período selecionado", from: from.toISOString(), to: to.toISOString() }, inherited: false };
  }
  const heuristicRegionCount = normalized.normalized.includes(" versus ") ? 2 : context?.entities.regions.length ?? 0;
  const intent = resolveIntent(normalized.normalized, heuristicRegionCount, context);
  const entities = resolveEntities(normalized.normalized, context);
  const selectedMetric = resolveMetric(normalized.normalized, context);

  return {
    rawQuestion: question,
    normalizedQuestion: normalized.normalized,
    intent: intent.intent,
    metric: selectedMetric.metric,
    period: period.period,
    entities: entities.entities,
    inherited: {
      intent: intent.inherited,
      metric: selectedMetric.inherited,
      period: period.inherited,
      entities: entities.inherited,
    },
  };
}

export async function askNaturalQuestion(
  driverId: string,
  question: string,
  rawContext?: unknown,
  options: { days?: number; from?: string; to?: string } = {},
): Promise<StructuredAnswer> {
  const plan = await planNaturalQuestion(driverId, question, rawContext, options);
  const result = await queryAnalyticsDomain(driverId, plan);
  const evidence = buildEvidence(plan.period, result);
  const context: QuestionContext = {
    version: NATURAL_QUESTION_VERSION,
    intent: plan.intent,
    metric: plan.metric,
    entities: plan.entities,
    period: plan.period,
  };
  const answer = renderAnswer(plan, result, evidence);
  const observedCount = result.metrics.find((item) => item.key === "offers")?.value;
  return {
    version: NATURAL_QUESTION_VERSION,
    answer,
    intent: plan.intent,
    resolved: {
      metric: plan.metric,
      period: plan.period,
      entities: plan.entities,
    },
    evidence,
    metrics: result.metrics,
    alternatives: result.alternatives ?? [],
    limitations: result.limitations ?? [],
    context,
    status: result.status,
    details: result.details,
    range: { from: plan.period.from, to: plan.period.to },
    offer_count: typeof observedCount === "number" ? observedCount : result.semantic === "OBSERVED_OFFER" ? result.sampleCount : 0,
    model: "deterministic-v1",
    usage: null,
  };
}
