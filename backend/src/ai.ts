import OpenAI from "openai";
import { serverEnv } from "./env";
import { fetchOffers, summarizeOffers } from "./analytics";
import { resolveRange } from "./ranges";

export async function askSrRotas(
  driverId: string,
  question: string,
  from?: string,
  to?: string,
) {
  const env = serverEnv();
  if (!env.openAiApiKey) throw new Error("openai_not_configured");
  const range = resolveRange(from, to, 7);
  const { offers } = await fetchOffers(driverId, { ...range, limit: 250 });
  const compact = offers.map((o) => ({
    at: o.observed_at,
    platform: o.platform,
    fare: o.fare,
    km: o.total_km,
    minutes: o.total_minutes,
    per_km: o.per_km,
    per_hour: o.per_hour,
    cost: o.estimated_cost,
    profit: o.estimated_profit,
    verdict: o.verdict,
    confidence: o.confidence,
    offer_type: o.offer_type,
  }));

  const client = new OpenAI({ apiKey: env.openAiApiKey });
  const response = await client.responses.create({
    model: env.openAiModel,
    store: false,
    instructions:
      "Você é o Sr. Rotas, um analista de rentabilidade para motoristas de aplicativos. Responda em português do Brasil e use somente os dados fornecidos. " +
      "Os registros representam ofertas observadas e não comprovam aceitação ou conclusão. Considere confidence ao avaliar leituras do Alpha. " +
      "Não recomende burlar regras das plataformas nem automatizar aceite ou recusa.",
    input: `Pergunta: ${question}\nPeríodo: ${range.from} até ${range.to}\nResumo: ${JSON.stringify(summarizeOffers(offers))}\nOfertas: ${JSON.stringify(compact)}`,
  });

  return {
    answer: response.output_text,
    range,
    offer_count: offers.length,
    model: env.openAiModel,
  };
}

// Compatibilidade com a rota do primeiro ZIP.
export const askDriver = askSrRotas;
