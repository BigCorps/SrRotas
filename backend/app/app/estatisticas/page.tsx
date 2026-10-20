"use client";

import { useEffect, useMemo, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";
import IntelligenceNav from "../_components/IntelligenceNav";
import styles from "./statistics.module.css";

type Metric = { key: string; label: string; formatted: string; semantic: string; value?: number | string | null };
type Evidence = {
  source: string;
  sampleCount: number;
  confidence: string;
  dataQuality: string;
  completeness: string;
  coverage?: { value: number | null; label: string };
  period: { label: string };
};
type Answer = {
  answer: string;
  intent: string;
  status: string;
  evidence: Evidence;
  metrics: Metric[];
  alternatives: string[];
  limitations: string[];
};
type ResponseBody = { section: string; preset: string; period: { label: string } | null; answers: Answer[] };
type ComparableItem = {
  key: string; label: string; semantic: string;
  current_formatted: string; reference_formatted: string;
  comparable: boolean; absolute: number | null; percentage: number | null;
  comparison_reason?: string | null;
};
type ComparisonResponse = {
  schema: string; kind: string; time_zone: string; provisional: boolean;
  current_period: { from: string; to: string };
  reference_period: { from: string; to: string };
  metrics: ComparableItem[]; notes: string[];
};

const sections = [
  ["resumo", "Resumo"],
  ["produtividade", "Produtividade"],
  ["horarios", "Horários"],
  ["regioes", "Regiões"],
  ["corridas", "Corridas"],
  ["oportunidades", "Oportunidades"],
  ["custos", "Custos"],
] as const;

type Preset = "yesterday" | "7" | "30" | "custom";

function pct(value: number | null | undefined) {
  return typeof value === "number" ? `${Math.round(value * 100)}%` : "—";
}


function findMetric(answer: Answer | undefined, key: string): Metric | undefined {
  return answer?.metrics.find((item) => item.key === key);
}
function displayMetric(item: Metric | undefined) {
  return item?.value === null || item?.value === undefined || !item?.formatted || item.formatted === "—" ? "—" : item.formatted;
}
function comparisonNote(item: ComparableItem) {
  if (!item.comparable) return item.comparison_reason || "Amostra insuficiente para comparação.";
  if (item.percentage === null) return "Percentual indisponível: o período anterior tinha valor zero.";
  const percentage = item.percentage.toLocaleString("pt-BR", { maximumFractionDigits: 1 });
  return "Variação de " + (item.percentage > 0 ? "+" : "") + percentage + "% nas ofertas observadas.";
}

export default function EstatisticasPage() {
  const [section, setSection] = useState("resumo");
  const [preset, setPreset] = useState<Preset>("7");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [data, setData] = useState<ResponseBody | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [compareData, setCompareData] = useState<ComparisonResponse | null>(null);
  const [compareBusy, setCompareBusy] = useState(false);
  const [compareError, setCompareError] = useState("");
  const [compareKind, setCompareKind] = useState<string>("");

  const customValid = preset !== "custom" || Boolean(
    from && to && from <= to &&
    Date.parse(to + "T00:00:00") - Date.parse(from + "T00:00:00") < 90 * 86_400_000
  );

  const query = useMemo(() => {
    const params = new URLSearchParams({ section, preset });
    if (preset === "custom" && from) params.set("from", new Date(`${from}T00:00:00`).toISOString());
    if (preset === "custom" && to) params.set("to", new Date(`${to}T23:59:59.999`).toISOString());
    return params.toString();
  }, [section, preset, from, to]);

  useEffect(() => {
    if (!customValid) { setBusy(false); setData(null); setError(""); return; }
    let active = true;
    setBusy(true);
    setData(null);
    setError("");
    fetch(`/api/v1/statistics?${query}`, { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (response.status === 401) throw new Error("Sua sessão expirou. Entre novamente para consultar seus dados.");
        if (response.status === 402 || response.status === 403) throw new Error("Estas análises não estão disponíveis para seu plano atual.");
        if (response.status === 429) throw new Error("Muitas consultas em sequência. Tente novamente em instantes.");
        if (!response.ok) throw new Error("Não foi possível carregar esta análise agora.");
        return body as ResponseBody;
      })
      .then((body) => { if (active) setData(body); })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : "Não foi possível carregar esta análise agora.");
      })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [query, customValid, retry]);

  async function loadComparison(kind: "today_yesterday" | "week_previous") {
    setCompareKind(kind);
    setCompareData(null);
    setCompareError("");
    setCompareBusy(true);
    try {
      const response = await fetch(`/api/v1/statistics/comparisons?kind=${kind}`, { cache: "no-store" });
      if (response.status === 402 || response.status === 403) throw new Error("Seu plano não tem acesso às comparações.");
      if (response.status === 429) throw new Error("Limite de consultas atingido. Tente mais tarde.");
      if (!response.ok) throw new Error("Não foi possível comparar estes períodos agora.");
      const result = await response.json() as ComparisonResponse;
      setCompareData(result);
    } catch (err) {
      setCompareError(err instanceof Error ? err.message : "Não foi possível comparar estes períodos agora.");
    } finally {
      setCompareBusy(false);
    }
  }

  const overview = section === "resumo";
  const productivity = section === "produtividade";
  const showKpis = overview || productivity;
  const summary = data?.answers.find((answer) => answer.intent === "PERIOD_SUMMARY");
  const profitability = data?.answers.find((answer) => answer.intent === "PROFITABILITY");
  const rides = findMetric(summary, "completed_rides");
  const distance = findMetric(summary, "actual_distance");
  const headline = [
    {
      title: "Faturamento confirmado",
      value: summary?.status === "ok" && typeof rides?.value === "number" && rides.value > 0 &&
        typeof summary.evidence.coverage?.value === "number" && summary.evidence.coverage.value > 0
        ? displayMetric(findMetric(summary, "realized_revenue")) : "—",
      note: "Somente corridas realizadas e conciliadas", estimated: false,
    },
    {
      title: "Resultado estimado",
      value: profitability?.status === "ok" && profitability.evidence.completeness === "complete"
        ? displayMetric(findMetric(profitability, "estimated_profit")) : "—",
      note: "Exige conciliação e odômetro completos; não é lucro contábil", estimated: true,
    },
    {
      title: "Corridas confirmadas",
      value: summary?.status === "ok" ? displayMetric(rides) : "—",
      note: "Não inclui ofertas apenas observadas", estimated: false,
    },
    {
      title: "Distância registrada",
      value: summary?.status === "ok" && typeof distance?.value === "number" && distance.value > 0
        ? displayMetric(distance) : "—",
      note: "Exige registro real de odômetro", estimated: false,
    },
    {
      title: "Horas de jornadas encerradas",
      value: displayMetric(findMetric(summary, "closed_journey_hours")),
      note: "Pode ser parcial; inclui a duração total das jornadas iniciadas no período", estimated: false,
    },
    {
      title: "Faturamento por hora",
      value: displayMetric(findMetric(summary, "realized_revenue_per_hour")),
      note: "Somente com todas as jornadas encerradas e tarifas conciliadas", estimated: false,
    },
    {
      title: "Faturamento por km",
      value: displayMetric(findMetric(summary, "realized_revenue_per_km")),
      note: "Exige também distância de odômetro completa", estimated: false,
    },
  ];

  const chosenKpis = productivity
    ? headline.filter((item) => ["Horas de jornadas encerradas", "Faturamento por hora", "Faturamento por km", "Corridas confirmadas", "Distância registrada"].includes(item.title))
    : headline;

  return (
    <div className={styles.page}>
      <AccountPageHeader
        title="Estatísticas"
        subtitle="Ganhos, custos e produtividade com dados que você pode conferir."
      />
      <IntelligenceNav />

      <section className={styles.periodPanel} aria-label="Filtros de estatísticas">
        <div>
          <h2 className={styles.heading}>Seu desempenho</h2>
          <p className={styles.muted}>{data?.period?.label || "Escolha o período e a análise."}</p>
        </div>
        <div className={styles.periodButtons} role="group" aria-label="Período">
          {([["yesterday","Ontem"],["7","7 dias"],["30","30 dias"],["custom","Personalizado"]] as const).map(([value,label]) => (
            <button key={value} type="button" className={styles.pill}
              aria-pressed={preset === value} onClick={() => setPreset(value)}>{label}</button>
          ))}
        </div>
        {preset === "custom" ? (
          <div className={styles.customRange}>
            <label>De <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
            <label>Até <input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
          </div>
        ) : null}
        {!customValid ? <p className={styles.muted} role="status">Selecione datas em ordem, com intervalo de até 90 dias.</p> : null}
        <nav className={styles.sectionTabs} aria-label="Áreas de análise">
          {sections.map(([value,label]) => (
            <button key={value} type="button" className={styles.pill}
              aria-pressed={section === value} onClick={() => setSection(value)}>{label}</button>
          ))}
        </nav>
      </section>

      {error ? (
        <div className={styles.error} role="alert">
          {error}
          <button type="button" className={styles.retry} onClick={() => setRetry((value) => value + 1)}>
            Tentar novamente
          </button>
        </div>
      ) : null}
      {busy ? <div className={styles.empty} role="status">Carregando suas estatísticas…</div> : null}

      {showKpis && data && !busy && !error ? (
        <section className={styles.dashboard} aria-label="Principais resultados do período">
          <h2 className={styles.dashboardTitle}>{productivity ? "Tempo e rendimento" : "Visão rápida do período"}</h2>
          <div className={styles.kpiGrid}>
            {chosenKpis.map((item) => (
              <div className={item.estimated ? styles.kpi + " " + styles.kpiEstimated : styles.kpi} key={item.title}>
                <span className={styles.kpiLabel}>{item.title}</span>
                <strong>{item.value}</strong>
                <span className={styles.kpiNote}>{item.note}</span>
              </div>
            ))}
          </div>
          <p className={styles.note}>“—” significa dado ainda não comprovado. Ofertas observadas não são faturamento e resultado estimado não é lucro efetivo.{summary?.evidence.completeness === "partial" ? " O período contém evidências parciais: alguns totais podem não representar todas as jornadas." : ""}</p>
        </section>
      ) : null}

      {!busy && !error && data?.answers.length === 0 ? (
        <div className={styles.empty}>Não há resultados suficientes para o período.</div>
      ) : null}

      {!busy && !error && data?.answers.map((answer, index) => {
        const visibleMetrics = overview || (productivity && answer.intent === "PERIOD_SUMMARY")
          ? answer.metrics.filter((item) => ![
              "realized_revenue", "estimated_profit", "completed_rides", "actual_distance",
              "closed_journey_hours", "realized_revenue_per_hour", "realized_revenue_per_km",
            ].includes(item.key))
          : answer.metrics;
        const friendlyTitles: Record<string,string> = {
          PERIOD_SUMMARY: section === "produtividade" ? "Tempo e rendimento da jornada" : "Ofertas e atividade",
          PROFITABILITY: "Como calculamos o resultado",
          OPERATING_COST: "Despesas reais e custos estimados",
          REALIZED_EARNINGS: "Faturamento confirmado",
          ACTUAL_DISTANCE: "Distância real",
          COMPLETED_RIDES: "Corridas realizadas",
          HOUR_PERFORMANCE: "Melhores horários observados",
          REGION_PERFORMANCE: "Regiões com melhores ofertas",
          OFFER_QUALITY: "Qualidade das ofertas",
          MARKET_QUALITY: "Comportamento das ofertas",
          BEST_JOURNEY: "Melhor jornada",
        };
        return (
          <article className={styles.answer} key={answer.intent + "-" + index}>
            <h3>{friendlyTitles[answer.intent] || "Análise do período"}</h3>
            <p>{answer.answer}</p>
            {answer.evidence.completeness === "partial" &&
              ["PROFITABILITY", "REALIZED_EARNINGS", "PERIOD_SUMMARY"].includes(answer.intent) ? (
                <p className={styles.note}>Dados parciais: confira a cobertura e os limites antes de usar este resultado como total do período.</p>
              ) : null}
            {visibleMetrics.length ? (
              <div className={styles.statGrid}>
                {visibleMetrics.map((item) => (
                  <div className={styles.stat} key={answer.intent + "-" + item.key}>
                    <small>{item.label}</small>
                    <strong>{item.formatted}</strong>
                  </div>
                ))}
              </div>
            ) : null}
            {answer.alternatives?.length ? (
              <div className={styles.alt}>
                <span>Outros resultados relevantes</span>
                <ul>{answer.alternatives.slice(0,3).map((item) => <li key={item}>{item}</li>)}</ul>
              </div>
            ) : null}
            <details className={styles.metaDetails}>
              <summary>Fonte dos dados e limitações</summary>
              <p>Amostra: {answer.evidence.sampleCount.toLocaleString("pt-BR")} · Cobertura: {pct(answer.evidence.coverage?.value)} · {answer.evidence.completeness === "complete" ? "Completo" : "Parcial"}</p>
              {answer.limitations?.length ? <ul>{answer.limitations.map((item) => <li key={item}>{item}</li>)}</ul> : null}
              <p>Confiança: {answer.evidence.confidence} · Qualidade: {answer.evidence.dataQuality}</p>
            </details>
          </article>
        );
      })}

      {overview ? (
        <section className={styles.comparison} aria-label="Comparativos por período">
          <div className={styles.comparisonHeader}>
            <h2 className={styles.dashboardTitle}>Comparar períodos</h2>
            <p className={styles.muted}>Apenas ofertas observadas; não compara receita ou lucro.</p>
          </div>
          <div className={styles.comparisonButtons}>
            <button type="button" className={styles.pill} disabled={compareBusy}
              aria-pressed={compareKind === "today_yesterday"}
              onClick={() => loadComparison("today_yesterday")}>Hoje × ontem</button>
            <button type="button" className={styles.pill} disabled={compareBusy}
              aria-pressed={compareKind === "week_previous"}
              onClick={() => loadComparison("week_previous")}>Semana × anterior</button>
          </div>
          {compareBusy ? <div className={styles.empty} role="status">Calculando comparativo…</div> : null}
          {compareError ? <div className={styles.error} role="alert">{compareError}</div> : null}
          {compareData && !compareBusy ? (
            <div className={styles.dashboard} aria-live="polite">
              {compareData.provisional ? <p className={styles.note}>Dados provisórios: a sincronização pode estar incompleta.</p> : null}
              <div className={styles.comparisonGrid}>
                {compareData.metrics.map((item) => (
                  <div className={styles.comparisonMetric} key={item.key}>
                    <small>{item.label}</small>
                    <strong>{item.current_formatted}</strong>
                    <small>Anterior: {item.reference_formatted}</small>
                    <small>{comparisonNote(item)}</small>
                  </div>
                ))}
              </div>
              <details className={styles.metaDetails}>
                <summary>Como funciona este comparativo</summary>
                <ul>{compareData.notes.map((note) => <li key={note}>{note}</li>)}</ul>
              </details>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
