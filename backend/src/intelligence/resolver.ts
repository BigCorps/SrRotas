import { INTENTS, NATURAL_QUESTION_VERSION, type MetricKey, type QuestionContext, type QuestionEntities, type QuestionIntent, type SourceMode } from "./contracts";
import { hasAny } from "./normalizer";

const INTENT_SET = new Set<string>(INTENTS);

function cleanRegion(value: string) {
  return value
    .replace(/\b(hoje|ontem|agora|essa semana|esta semana|semana passada|este mes|esse mes|mes passado)\b.*$/g, "")
    .replace(/\b(por_km|por_hora|por_minuto|por km|por hora|por minuto)\b.*$/g, "")
    .replace(/\b(com|usando|na base|pela base)\b.*$/g, "")
    .replace(/^(regiao|bairro|zona|em|na|no|de|da|do)\s+/, "")
    .trim()
    .slice(0, 80);
}

export function sanitizeContext(value: unknown): QuestionContext | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, any>;
  if (raw.version !== NATURAL_QUESTION_VERSION || !INTENT_SET.has(String(raw.intent))) return null;
  const source: SourceMode = raw.entities?.source === "collective" ? "collective" : "personal";
  const regions = Array.isArray(raw.entities?.regions)
    ? raw.entities.regions.map((v: unknown) => String(v).slice(0, 80)).filter(Boolean).slice(0, 2)
    : [];
  const period = raw.period;
  if (!period || Number.isNaN(new Date(period.from).getTime()) || Number.isNaN(new Date(period.to).getTime())) return null;
  const periodFrom = new Date(period.from).getTime();
  const periodTo = new Date(period.to).getTime();
  if (periodFrom >= periodTo || periodTo - periodFrom > 90 * 86_400_000) return null;
  return {
    version: NATURAL_QUESTION_VERSION,
    intent: raw.intent as QuestionIntent,
    metric: typeof raw.metric === "string" ? raw.metric as MetricKey : null,
    entities: {
      regions,
      platform: raw.entities?.platform ? String(raw.entities.platform).slice(0, 30) : null,
      serviceType: raw.entities?.serviceType ? String(raw.entities.serviceType).slice(0, 40) : null,
      source,
    },
    period: {
      kind: period.kind,
      label: String(period.label || "Período anterior").slice(0, 80),
      from: new Date(period.from).toISOString(),
      to: new Date(period.to).toISOString(),
    },
  };
}

export function resolveMetric(text: string, context?: QuestionContext | null): { metric: MetricKey | null; inherited: boolean } {
  const rules: Array<[RegExp, MetricKey]> = [
    [/\b(lucro por hora|lucro\/h|profit por hora)\b/, "PROFIT_PER_HOUR"],
    [/\b(por_km|por km|km pago|r por km|pagou melhor|melhor paga)\b/, "PER_KM"],
    [/\b(por_hora|por hora|r por hora)\b/, "PER_HOUR"],
    [/\b(por_minuto|por minuto|r por minuto)\b/, "PER_MINUTE"],
    [/\b(faturamento|faturei|ganhei|recebi|receita)\b/, "REVENUE"],
    [/\b(distancia|rodei|rodado|quilometro| km )\b/, "DISTANCE"],
    [/\b(custo|custos|gastei|gasto)\b/, "COST"],
    [/\b(lucro|lucratividade|rentabilidade)\b/, "PROFIT"],
    [/\b(concluidas|realizadas|completei)\b/, "COMPLETED_RIDES"],
    [/\b(continuidade|nova corrida|espera)\b/, "CONTINUITY"],
    [/\b(qualidade|boas|ruins|regular)\b/, "QUALITY"],
  ];
  for (const [pattern, metric] of rules) if (pattern.test(` ${text} `)) return { metric, inherited: false };
  return context?.metric ? { metric: context.metric, inherited: true } : { metric: null, inherited: false };
}

export function resolveEntities(text: string, context?: QuestionContext | null): { entities: QuestionEntities; inherited: boolean } {
  const source: SourceMode = /\b(coletiva|coletivo|base coletiva)\b/.test(text) ? "collective" : "personal";
  const platform = /\b99\b/.test(text) ? "99" : /\buber\b/.test(text) ? "uber" : null;
  const serviceType = /\bcomfort\b/.test(text)
    ? "comfort"
    : /\bblack\b/.test(text)
      ? "black"
      : /\bmoto\b/.test(text)
        ? "moto"
        : /\bpriority\b/.test(text)
          ? "priority"
          : /\buber\s*x\b|\buberx\b/.test(text)
            ? "uberx"
            : null;

  const regions: string[] = [];
  const versus = text.match(/(?:regiao\s+|bairro\s+)?([a-z0-9 ._-]{2,60}?)\s+versus\s+(?:regiao\s+|bairro\s+)?([a-z0-9 ._-]{2,60})/);
  if (versus) {
    const a = cleanRegion(versus[1]);
    const b = cleanRegion(versus[2]);
    if (a) regions.push(a);
    if (b && b !== a) regions.push(b);
  } else {
    const named = text.match(/\b(?:regiao|bairro|zona)\s+(?:de|da|do)\s+([a-z0-9 ._-]{2,60})/);
    if (named) {
      const region = cleanRegion(named[1]);
      if (region) regions.push(region);
    }
  }

  const inherited = regions.length === 0 && !platform && !serviceType && source === "personal" && Boolean(context);
  const previous = context?.entities;
  return {
    entities: {
      regions: regions.length ? regions.slice(0, 2) : inherited ? previous?.regions ?? [] : [],
      platform: platform ?? (inherited ? previous?.platform ?? null : null),
      serviceType: serviceType ?? (inherited ? previous?.serviceType ?? null : null),
      source: source === "collective" ? "collective" : inherited ? previous?.source ?? "personal" : "personal",
    },
    inherited,
  };
}

function explicitIntent(text: string, regionCount: number): QuestionIntent | null {
  if (hasAny(text, ["esperar ou", "esperar ou me deslocar", "esperar ou deslocar", "vale esperar", "devo esperar", "me deslocar"])) return "WAIT_OR_MOVE";
  if (hasAny(text, ["oportunidades perdidas", "oportunidade perdida", "quanto perdi", "deixei de ganhar", "corridas que perdi"])) return "MISSED_OPPORTUNITIES";
  if (hasAny(text, ["onde devo ficar", "onde deveria estar", "onde ficar agora", "pra onde ir agora", "para onde ir agora", "melhor regiao agora"])) return "CURRENT_REGION_ADVICE";
  if (hasAny(text, ["continuidade", "depois do destino", "depois dessa corrida", "nova corrida no destino", "chance de nova corrida"])) return "DESTINATION_CONTINUITY";
  if (hasAny(text, ["melhor jornada", "jornada melhor", "jornada mais rentavel"])) return "BEST_JOURNEY";
  if (hasAny(text, ["corridas concluidas", "corridas realizadas", "quantas corridas fiz", "quantas completei"])) return "COMPLETED_RIDES";
  if (hasAny(text, ["quanto faturei", "quanto ganhei", "quanto recebi", "faturamento realizado", "receita realizada"])) return "REALIZED_EARNINGS";
  if (hasAny(text, ["quanto rodei", "distancia real", "km rodei", "quilometros rodei"])) return "ACTUAL_DISTANCE";
  if (hasAny(text, ["quanto gastei", "meu custo", "custos", "gastos"])) return "OPERATING_COST";
  if (hasAny(text, ["lucro", "lucratividade", "rentabilidade"])) return "PROFITABILITY";
  if (regionCount >= 2) return "COMPARE_REGIONS";
  if (hasAny(text, ["comparar", "compare", "comparacao", "versus ontem", "versus semana", "contra ontem", "contra semana"])) return "COMPARE_PERIODS";
  if (hasAny(text, ["regiao", "bairro", "zona"])) return "REGION_PERFORMANCE";
  if (hasAny(text, ["horario", "horarios", "faixa de hora", "melhores horas"])) return "HOUR_PERFORMANCE";
  if (hasAny(text, ["categoria", "categorias", "uberx", "comfort", "black", "moto", "priority"])) return "CATEGORY_PERFORMANCE";
  if (hasAny(text, ["ofertas boas", "ofertas ruins", "qualidade das ofertas", "boas regulares ruins"])) return "OFFER_QUALITY";
  if (hasAny(text, ["qualidade do mercado", "mercado hoje", "mercado ontem", "dia foi bom", "dia esta bom"])) return "MARKET_QUALITY";
  return null;
}

export function resolveIntent(text: string, regionCount: number, context?: QuestionContext | null): { intent: QuestionIntent; inherited: boolean } {
  const followUp = /^(e\b|e agora\b|e ontem\b|e hoje\b|e por_|e na\b|e no\b|e em\b)/.test(text);
  const explicit = explicitIntent(text, regionCount);
  if (followUp && context && !explicit) return { intent: context.intent, inherited: true };
  if (explicit) return { intent: explicit, inherited: false };
  if (context && text.split(" ").length <= 6) return { intent: context.intent, inherited: true };
  return { intent: "PERIOD_SUMMARY", inherited: false };
}
