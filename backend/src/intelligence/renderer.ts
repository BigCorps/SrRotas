import type { DomainResult, EvidenceEnvelope, QuestionPlan } from "./contracts";

function coverageText(evidence: EvidenceEnvelope) {
  const value = evidence.coverage.value;
  if (value === null) return "";
  return ` Cobertura: ${Math.round(value * 100)}% (${evidence.coverage.label.toLowerCase()}).`;
}

function metric(result: DomainResult, key: string) {
  return result.metrics.find((item) => item.key === key);
}

export function renderAnswer(plan: QuestionPlan, result: DomainResult, evidence: EvidenceEnvelope) {
  if (result.status === "not_ready") {
    return `${result.title}: ${result.limitations?.[0] ?? "Esta análise ainda depende de uma etapa anterior."}`;
  }
  if (result.status === "insufficient") {
    return `${result.title}: dados insuficientes para responder com segurança. ${result.limitations?.[0] ?? "Não vou completar a resposta com estimativas não sustentadas."}`;
  }

  switch (plan.intent) {
    case "REALIZED_EARNINGS": {
      const revenue = metric(result, "realized_revenue");
      const rides = metric(result, "completed_rides");
      return `Em ${plan.period.label.toLowerCase()}, o faturamento confirmado é ${revenue?.formatted ?? "—"}, em ${rides?.formatted ?? "—"} corridas concluídas.${coverageText(evidence)}`;
    }
    case "ACTUAL_DISTANCE":
      return `Em ${plan.period.label.toLowerCase()}, há ${metric(result, "actual_distance")?.formatted ?? "—"} de distância real registrada por odômetro.${coverageText(evidence)}`;
    case "OPERATING_COST": {
      const actual = metric(result, "actual_energy_spend");
      const estimated = metric(result, "estimated_operating_cost");
      return `Em ${plan.period.label.toLowerCase()}, há ${actual?.formatted ?? "—"} de combustível/energia registrado. O custo operacional modelado é ${estimated?.formatted ?? "—"}; esse segundo valor é estimativa, não gasto comprovado.${coverageText(evidence)}`;
    }
    case "PROFITABILITY": {
      const revenue = metric(result, "realized_revenue");
      const profit = metric(result, "estimated_profit");
      const perHour = metric(result, "estimated_profit_per_hour");
      return `Em ${plan.period.label.toLowerCase()}, o faturamento confirmado é ${revenue?.formatted ?? "—"}. Após aplicar o custo operacional modelado à distância real disponível, o resultado estimado é ${profit?.formatted ?? "—"}${perHour ? ` (${perHour.formatted})` : ""}.${coverageText(evidence)}`;
    }
    case "BEST_JOURNEY":
      return `A melhor jornada conciliada neste recorte teve ${metric(result, "journey_revenue")?.formatted ?? "—"} de faturamento confirmado, com ${metric(result, "journey_completed")?.formatted ?? "—"} corridas concluídas.${coverageText(evidence)}`;
    case "COMPARE_PERIODS": {
      const current = metric(result, "current");
      const previous = metric(result, "previous");
      const delta = metric(result, "delta_pct");
      return `${current?.label ?? plan.period.label}: ${current?.formatted ?? "—"}; período anterior: ${previous?.formatted ?? "—"}. Variação: ${delta?.formatted ?? "—"}. A comparação usa ofertas observadas, não faturamento realizado.`;
    }
    case "REGION_PERFORMANCE":
    case "COMPARE_REGIONS":
      return `${result.metrics[0]?.label ?? "Região com melhor indicador"}: ${result.metrics[0]?.formatted ?? "—"}, com amostra mínima aplicada. Isso descreve ofertas observadas no período.`;
    case "HOUR_PERFORMANCE":
      return `${result.metrics[0]?.label ?? "Melhor horário"}: ${result.metrics[0]?.formatted ?? "—"}. O ranking só inclui faixas com amostra suficiente.`;
    case "CATEGORY_PERFORMANCE":
      return `${result.metrics[0]?.label ?? "Melhor categoria"}: ${result.metrics[0]?.formatted ?? "—"}. O resultado é baseado em ofertas observadas.`;
    case "OFFER_QUALITY":
      return `Em ${plan.period.label.toLowerCase()}, foram ${metric(result, "good")?.formatted ?? "0"} ofertas boas, ${metric(result, "regular")?.formatted ?? "0"} regulares e ${metric(result, "bad")?.formatted ?? "0"} ruins.`;
    case "COMPLETED_RIDES":
      return `Em ${plan.period.label.toLowerCase()}, há ${metric(result, "completed_rides")?.formatted ?? "0"} corridas concluídas confirmadas. Ofertas observadas não entram nessa contagem.`;
    case "DESTINATION_CONTINUITY":
      return `${result.title}: ${result.metrics[0]?.formatted ?? "—"}. ${result.limitations?.[0] ?? "É histórico, não garantia."}`;
    case "CURRENT_REGION_ADVICE":
      return `Pelo histórico disponível para esta faixa, ${metric(result, "region")?.formatted ?? "nenhuma região"} tem a maior aderência ao seu perfil (${metric(result, "score")?.formatted ?? "—"}). Não é demanda em tempo real nem garantia de corrida.`;
    case "MARKET_QUALITY":
      return `Comparando ${plan.period.label.toLowerCase()} ao período anterior equivalente, R$/h variou ${metric(result, "per_hour_delta")?.formatted ?? "—"} e R$/km ${metric(result, "per_km_delta")?.formatted ?? "—"}. Isso mede ofertas observadas.`;
    case "WAIT_OR_MOVE":
      return `${result.note ?? "A continuidade histórica foi calculada."} ${result.metrics[0] ? `Indicador disponível: ${result.metrics[0].formatted}.` : ""}`.trim();
    case "PERIOD_SUMMARY":
    default: {
      const offers = metric(result, "offers");
      const perKm = metric(result, "avg_per_km");
      const perHour = metric(result, "avg_per_hour");
      const realized = metric(result, "realized_revenue");
      return `Em ${plan.period.label.toLowerCase()}, encontrei ${offers?.formatted ?? "0"} ofertas observadas, média de ${perKm?.formatted ?? "—"}/km e ${perHour?.formatted ?? "—"}/h. Faturamento confirmado disponível: ${realized?.formatted ?? "—"}.`;
    }
  }
}
