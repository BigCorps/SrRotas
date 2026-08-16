import OpenAI from "openai";
import { serverEnv } from "./env";
import { fetchOffers, summarizeOffers } from "./analytics";
import { resolveRange } from "./ranges";
import { ensurePreferences } from "./preferences";
import { currentJourney } from "./journeys";

export async function askSrRotas(driverId: string, question: string, from?: string, to?: string) {
  const env = serverEnv();
  if (!env.openAiApiKey) throw new Error("openai_not_configured");
  const range = resolveRange(from, to, 7);
  const [{ offers }, strategy, journey] = await Promise.all([
    fetchOffers(driverId, { ...range, limit: 250 }),
    ensurePreferences(driverId),
    currentJourney(driverId),
  ]);
  const compact = offers.map((o) => ({
    at: o.observed_at, journey_id: o.journey_id, platform: o.platform, fare: o.fare,
    km: o.total_km, minutes: o.total_minutes, per_km: o.per_km, per_hour: o.per_hour,
    per_minute: o.per_minute, passenger_rating: o.passenger_rating, service_type: o.service_type,
    cost: o.estimated_cost, profit: o.estimated_profit, profit_per_hour: o.profit_per_hour,
    profit_percent: o.profit_percent, verdict: o.verdict,
    confidence: o.confidence, offer_type: o.offer_type,
  }));

  const client = new OpenAI({ apiKey: env.openAiApiKey });
  const response = await client.responses.create({
    model: env.openAiModel,
    store: false,
    instructions:
      "Você é o Sr. Rotas, um analista de rentabilidade para motoristas de aplicativos. Responda em português do Brasil e use somente os dados fornecidos. " +
      "Os registros representam OFERTAS OBSERVADAS e não comprovam aceitação, início, conclusão, faturamento ou lucro realizado. " +
      "Considere confidence ao avaliar leituras do Alpha. Diferencie claramente valores oferecidos de ganhos reais. " +
      "Não recomende burlar regras das plataformas nem automatizar aceite ou recusa.",
    input: `Pergunta: ${question}\nPeríodo: ${range.from} até ${range.to}\nEstratégia: ${JSON.stringify(strategy)}\nJornada atual: ${JSON.stringify(journey)}\nResumo: ${JSON.stringify(summarizeOffers(offers))}\nOfertas: ${JSON.stringify(compact)}`,
  });
  return { answer: response.output_text, range, offer_count: offers.length, model: env.openAiModel };
}

export const askDriver = askSrRotas;
