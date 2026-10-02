const CONTROLLED_VOCABULARY = [
  "hoje", "ontem", "semana", "mes", "ultimos", "dias", "horas",
  "ganhei", "faturei", "recebi", "rodei", "distancia", "gastei", "custo",
  "lucro", "lucratividade", "jornada", "regiao", "bairro", "horario",
  "categoria", "ofertas", "boas", "ruins", "concluidas", "continuidade",
  "mercado", "oportunidades", "esperar", "deslocar", "comparar", "versus",
  "quilometro", "hora", "minuto", "coletiva", "pessoal", "uber", "comfort",
  "black", "moto", "priority", "pinheiros", "perdizes",
] as const;

const DIRECT_TOKEN_ALIASES: Record<string, string> = {
  hj: "hoje",
  hje: "hoje",
  ontemm: "ontem",
  qnt: "quanto",
  qto: "quanto",
  qtd: "quantidade",
  faturey: "faturei",
  ganhei: "ganhei",
  gastey: "gastei",
  kms: "km",
  kilometros: "quilometros",
  quilometros: "quilometro",
  hrs: "horas",
  hr: "hora",
  regiao: "regiao",
  regioes: "regiao",
  concluida: "concluidas",
  concluidas: "concluidas",
};

function stripAccents(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
}

function editDistance(a: string, b: string) {
  if (a === b) return 0;
  if (!a.length) return b.length;
  if (!b.length) return a.length;
  const previous = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i += 1) {
    let diagonal = previous[0];
    previous[0] = i;
    for (let j = 1; j <= b.length; j += 1) {
      const old = previous[j];
      previous[j] = Math.min(
        previous[j] + 1,
        previous[j - 1] + 1,
        diagonal + (a[i - 1] === b[j - 1] ? 0 : 1),
      );
      diagonal = old;
    }
  }
  return previous[b.length];
}

function controlledCorrection(token: string) {
  if (DIRECT_TOKEN_ALIASES[token]) return DIRECT_TOKEN_ALIASES[token];
  if (token.length < 5 || /^\d+$/.test(token)) return token;
  let best: string | null = null;
  let bestDistance = 2;
  let ties = 0;
  for (const candidate of CONTROLLED_VOCABULARY) {
    if (Math.abs(candidate.length - token.length) > 1) continue;
    const distance = editDistance(token, candidate);
    if (distance < bestDistance) {
      best = candidate;
      bestDistance = distance;
      ties = 1;
    } else if (distance === bestDistance) {
      ties += 1;
    }
  }
  return bestDistance <= 1 && ties === 1 && best ? best : token;
}

export function normalizeQuestion(value: string) {
  const normalized = stripAccents(value.toLowerCase())
    .replace(/r\$\s*\/\s*km/g, " por_km ")
    .replace(/r\$\s*\/\s*h(?:ora)?/g, " por_hora ")
    .replace(/r\$\s*\/\s*min(?:uto)?/g, " por_minuto ")
    .replace(/\bvs\.?\b/g, " versus ")
    .replace(/[^a-z0-9/._-]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
  const tokens = normalized.split(" ").filter(Boolean).map(controlledCorrection);
  return {
    raw: value,
    normalized: tokens.join(" "),
    tokens,
  };
}

export function hasAny(text: string, values: string[]) {
  return values.some((value) => text.includes(value));
}
