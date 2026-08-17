import { randomUUID } from "node:crypto";
import OpenAI from "openai";
import { serverEnv } from "./env";
import { fetchOffers, historyDashboard, summarizeOffers } from "./analytics";
import { resolveRange } from "./ranges";
import { ensurePreferences } from "./preferences";
import { currentJourney } from "./journeys";
import { adminSupabase } from "./supabase";

export async function askSrRotas(
  driverId: string,
  question: string,
  from?: string,
  to?: string,
  days?: number,
) {
  const env = serverEnv();
  if (!env.openAiApiKey) throw new Error("openai_not_configured");

  const safeDays = Math.max(1, Math.min(days ?? 7, 90));
  const range = from || to ? resolveRange(from, to, safeDays) : resolveRange(undefined, undefined, safeDays);
  const started = performance.now();
  const requestId = randomUUID();
  let offerCount = 0;

  try {
    const [{ offers }, strategy, journey, dashboard] = await Promise.all([
      // Detalhes recentes para perguntas pontuais; agregações cobrem o período inteiro.
      fetchOffers(driverId, { ...range, limit: 120 }),
      ensurePreferences(driverId),
      currentJourney(driverId),
      from || to ? Promise.resolve(null) : historyDashboard(driverId, { days: safeDays }),
    ]);
    offerCount = dashboard?.summary?.offer_count ?? offers.length;

    const compact = offers.map((o) => ({
      at: o.observed_at,
      journey_id: o.journey_id,
      fare: o.fare,
      km: o.total_km,
      minutes: o.total_minutes,
      per_km: o.per_km,
      per_hour: o.per_hour,
      per_minute: o.per_minute,
      rating: o.passenger_rating,
      service: o.service_type,
      profit: o.estimated_profit,
      profit_per_hour: o.profit_per_hour,
      verdict: o.verdict,
      confidence: o.confidence,
      type: o.offer_type,
    }));

    const aggregate = dashboard
      ? {
          summary: dashboard.summary,
          comparison: dashboard.comparison,
          daily: dashboard.daily,
          hours: dashboard.hours,
          services: dashboard.services,
          top_offers: dashboard.top_offers,
        }
      : { summary: summarizeOffers(offers) };

    const client = new OpenAI({ apiKey: env.openAiApiKey });
    const response = await client.responses.create({
      model: env.openAiModel,
      store: false,
      truncation: "auto",
      max_output_tokens: 900,
      instructions:
        "Você é o Sr. Rotas, analista de rentabilidade para motoristas de aplicativo. Responda em português do Brasil, de forma prática e curta. " +
        "Use SOMENTE os dados fornecidos. Os registros são OFERTAS OBSERVADAS: não comprovam aceite, início, conclusão, faturamento ou lucro realizado. " +
        "Quando comparar valores, diga explicitamente que são ofertas observadas. Considere confidence em leituras Alpha. " +
        "Não recomende burlar regras de plataformas, manipular GPS, automatizar aceite/recusa ou dirigir de forma insegura. " +
        "Quando a evidência for insuficiente, diga isso em vez de inventar uma conclusão.",
      input:
        `Pergunta: ${question}\n` +
        `Período: ${range.from} até ${range.to}\n` +
        `Estratégia: ${JSON.stringify(strategy)}\n` +
        `Jornada atual: ${JSON.stringify(journey)}\n` +
        `Agregações: ${JSON.stringify(aggregate)}\n` +
        `Amostra de até 120 ofertas recentes: ${JSON.stringify(compact)}`,
    });

    const usage = response.usage
      ? {
          input_tokens: response.usage.input_tokens,
          output_tokens: response.usage.output_tokens,
          total_tokens: response.usage.total_tokens,
        }
      : null;

    await logUsage({
      driverId,
      requestId,
      model: env.openAiModel,
      status: "success",
      offerCount,
      durationMs: Math.max(0, Math.round(performance.now() - started)),
      inputTokens: usage?.input_tokens ?? null,
      outputTokens: usage?.output_tokens ?? null,
      totalTokens: usage?.total_tokens ?? null,
      errorCode: null,
    });

    return {
      answer: response.output_text,
      range,
      offer_count: offerCount,
      model: env.openAiModel,
      usage,
    };
  } catch (error) {
    const code = error instanceof Error ? error.message.slice(0, 120) : "ask_failed";
    await logUsage({
      driverId,
      requestId,
      model: env.openAiModel,
      status: "error",
      offerCount,
      durationMs: Math.max(0, Math.round(performance.now() - started)),
      inputTokens: null,
      outputTokens: null,
      totalTokens: null,
      errorCode: code,
    });
    throw error;
  }
}

async function logUsage(input: {
  driverId: string;
  requestId: string;
  model: string;
  status: "success" | "error";
  offerCount: number;
  durationMs: number;
  inputTokens: number | null;
  outputTokens: number | null;
  totalTokens: number | null;
  errorCode: string | null;
}) {
  await adminSupabase().from("ai_usage_logs").insert({
    driver_id: input.driverId,
    request_id: input.requestId,
    source: "sr_rotas_app",
    model: input.model,
    status: input.status,
    input_tokens: input.inputTokens,
    output_tokens: input.outputTokens,
    total_tokens: input.totalTokens,
    offer_count: input.offerCount,
    duration_ms: input.durationMs,
    error_code: input.errorCode,
  }).then(() => undefined).catch(() => undefined);
}

export const askDriver = askSrRotas;
