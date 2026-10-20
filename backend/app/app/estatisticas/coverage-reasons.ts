/** Explains why verified per-hour/per-km rates are absent without estimating missing data. */
export type PeriodRateEvidence = {
  metrics?: Array<{ key: string; value?: unknown; formatted?: string }>;
  details?: {
    realized?: { sourceComplete?: boolean; completed?: number; matched?: number };
    productivity?: { journeyCount?: number; closedJourneyCount?: number };
    distance_coverage?: { numerator?: number | null; denominator?: number | null };
  };
};

export function reasonsForUnavailableRates(summary: PeriodRateEvidence | undefined): string[] {
  const metrics = summary?.metrics ?? [];
  const hour = metrics.find((item) => item.key === "realized_revenue_per_hour");
  const km = metrics.find((item) => item.key === "realized_revenue_per_km");
  const unavailable = (item: typeof hour) => item?.value == null || item.formatted === "—";
  if (!hour && !km) return [];
  const missingHour = hour ? unavailable(hour) : false;
  const missingKm = km ? unavailable(km) : false;
  if (!missingHour && !missingKm) return [];

  const source = summary?.details?.realized;
  const work = summary?.details?.productivity;
  const distance = summary?.details?.distance_coverage;
  const reasons: string[] = [];

  if (work?.journeyCount === 0) {
    reasons.push("Não há jornadas iniciadas neste período para calcular taxas de rendimento.");
  } else {
    if (source?.sourceComplete === false && (work?.journeyCount ?? 0) > 0) {
      reasons.push("A leitura financeira não cobre todas as jornadas do período; o leitor atual tem limite de 150 jornadas recentes e 90 dias.");
    }
    if (typeof work?.journeyCount === "number" && typeof work.closedJourneyCount === "number" &&
        work.closedJourneyCount < work.journeyCount) {
      reasons.push("Há jornadas ainda abertas ou sem encerramento válido; as horas totais não estão completas.");
    }
    if (source?.completed === 0) {
      reasons.push("Não há corridas concluídas confirmadas para calcular as tarifas médias.");
    } else if (typeof source?.completed === "number" && typeof source.matched === "number" &&
               source.matched < source.completed) {
      reasons.push("Algumas corridas concluídas ainda não têm tarifa de oferta vinculada.");
    }
    if (missingKm && typeof distance?.denominator === "number" && distance.denominator > 0 &&
        typeof distance.numerator === "number" && distance.numerator < distance.denominator) {
      reasons.push("Faltam distâncias reais completas de odômetro em parte das jornadas; por isso a taxa por km fica indisponível.");
    }
  }

  if (reasons.length === 0) {
    reasons.push("A amostra ainda não atende a todos os critérios para calcular essas taxas sem estimativas.");
  }
  return reasons;
}
