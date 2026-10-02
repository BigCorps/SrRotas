import { NATURAL_QUESTION_VERSION, type QuestionContext, type QuestionIntent } from "./contracts";
import { normalizeQuestion } from "./normalizer";
import { resolveQuestionPeriod } from "./period-resolver";
import { resolveEntities, resolveIntent, resolveMetric } from "./resolver";

function assert(condition: unknown, message: string) {
  if (!condition) throw new Error(`gate2_smoke_failed:${message}`);
}

const CASES: Array<[string, QuestionIntent]> = [
  ["Quanto ganhei hoje?", "REALIZED_EARNINGS"],
  ["Quanto rodei hoje?", "ACTUAL_DISTANCE"],
  ["Quanto gastei hoje?", "OPERATING_COST"],
  ["Qual meu lucro hoje?", "PROFITABILITY"],
  ["Qual foi minha melhor jornada?", "BEST_JOURNEY"],
  ["Compare essa semana com a anterior", "COMPARE_PERIODS"],
  ["Qual região pagou melhor hoje?", "REGION_PERFORMANCE"],
  ["Pinheiros versus Perdizes", "COMPARE_REGIONS"],
  ["Quais meus melhores horários?", "HOUR_PERFORMANCE"],
  ["Qual categoria pagou melhor?", "CATEGORY_PERFORMANCE"],
  ["Como estão minhas ofertas boas e ruins?", "OFFER_QUALITY"],
  ["Quantas corridas concluídas fiz hoje?", "COMPLETED_RIDES"],
  ["Qual a continuidade depois do destino?", "DESTINATION_CONTINUITY"],
  ["Onde devo ficar agora?", "CURRENT_REGION_ADVICE"],
  ["Como está a qualidade do mercado hoje?", "MARKET_QUALITY"],
  ["Quanto perdi em oportunidades?", "MISSED_OPPORTUNITIES"],
  ["Vale esperar ou me deslocar?", "WAIT_OR_MOVE"],
  ["Me dê um resumo dos últimos 7 dias", "PERIOD_SUMMARY"],
];

export function runNaturalQuestionParserSmoke() {
  for (const [question, expected] of CASES) {
    const normalized = normalizeQuestion(question);
    const regions = resolveEntities(normalized.normalized).entities.regions;
    const intent = resolveIntent(normalized.normalized, normalized.normalized.includes(" versus ") ? 2 : regions.length).intent;
    assert(intent === expected, `${question} -> ${intent}, expected ${expected}`);
  }

  const now = new Date("2026-10-01T15:00:00.000Z");
  const baseNormalized = normalizeQuestion("Qual região pagou melhor hoje?");
  const basePeriod = resolveQuestionPeriod(baseNormalized.normalized, "America/Sao_Paulo", null, now).period;
  const baseMetric = resolveMetric(baseNormalized.normalized).metric;
  const baseEntities = resolveEntities(baseNormalized.normalized).entities;
  assert(baseEntities.regions.length === 0, "generic region question must not invent a named region");
  const comparedEntities = resolveEntities(normalizeQuestion("Pinheiros versus Perdizes").normalized).entities;
  assert(comparedEntities.regions.length === 2 && comparedEntities.regions[0] === "pinheiros" && comparedEntities.regions[1] === "perdizes", "compare region extraction");
  const context: QuestionContext = {
    version: NATURAL_QUESTION_VERSION,
    intent: "REGION_PERFORMANCE",
    metric: baseMetric,
    entities: baseEntities,
    period: basePeriod,
  };

  const yesterday = normalizeQuestion("E ontem?");
  const yesterdayPeriod = resolveQuestionPeriod(yesterday.normalized, "America/Sao_Paulo", context, now);
  const yesterdayIntent = resolveIntent(yesterday.normalized, 0, context);
  assert(yesterdayIntent.intent === "REGION_PERFORMANCE" && yesterdayIntent.inherited, "follow-up intent E ontem");
  assert(yesterdayPeriod.period.kind === "YESTERDAY" && !yesterdayPeriod.inherited, "follow-up period E ontem");

  const perHour = normalizeQuestion("E por hora?");
  const perHourIntent = resolveIntent(perHour.normalized, 0, context);
  const perHourMetric = resolveMetric(perHour.normalized, context);
  assert(perHourIntent.intent === "REGION_PERFORMANCE" && perHourIntent.inherited, "follow-up intent E por hora");
  assert(perHourMetric.metric === "PER_HOUR" && !perHourMetric.inherited, "follow-up metric E por hora");

  const typo = normalizeQuestion("Qnt faturey hj?");
  assert(typo.normalized.includes("faturei") && typo.normalized.includes("hoje"), "controlled typo normalization");

  const legacyDays = resolveQuestionPeriod(normalizeQuestion("Me dê um resumo").normalized, "America/Sao_Paulo", null, now, 30);
  assert(legacyDays.period.label === "Últimos 30 dias", "legacy days compatibility");

  return { ok: true, cases: CASES.length, followups: 2, typo: true, legacyDays: true, regionExtraction: true };
}
