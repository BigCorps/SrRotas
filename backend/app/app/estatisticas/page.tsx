"use client";

import { useEffect, useMemo, useState } from "react";
import IntelligenceNav from "../_components/IntelligenceNav";
import styles from "./statistics.module.css";
import { reasonsForUnavailableRates, type PeriodRateEvidence } from "./coverage-reasons";
import ReconciliationReview from "./ReconciliationReview";
import type { PerformanceSnapshot, PerformanceKey } from "@/src/intelligence/performance-snapshot";

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
  details?: PeriodRateEvidence["details"];
};
type ResponseBody = { section: string; preset: string; period: { label: string } | null; answers: Answer[]; snapshot?: PerformanceSnapshot };
type ComparableItem = {
  key: string; label: string; semantic: string;
  current_formatted: string; reference_formatted: string;
  comparable: boolean; absolute: number | null; percentage: number | null;
  comparison_reason?: string | null;
};
type ReconciliationOverview = {
  status: "SEM_HISTORICO" | "REVISADO" | "PENDENTE";
  total_completed_imports: number;
  reviewed: number;
  confirmed: number;
  no_match: number;
  unresolved: number;
  coverage_pct: number | null;
  missing_occurrence_time: number;
  future_occurrence_time: number;
  scope: "all_imported_completed_rides";
  final_payment_verified: false;
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


type IconName = "revenue" | "profit" | "car" | "pin" | "clock" | "gauge" | "road" | "eye" | "trend" | "star" | "compare";
/** Small inline SVG icons: no remote assets, font dependency or client-side charting lib. */
function PerformanceIcon({ kind }: { kind: IconName }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round">
      {kind === "revenue" ? <><circle cx="12" cy="12" r="9" /><path d="M14.7 8.5c-.9-1.2-4.8-1.3-5.2.8-.5 2.4 5.7 1.1 5.4 4.1-.2 2.5-5.4 2.7-6.1.6M12 5v14" /></> : null}
      {kind === "profit" ? <><rect x="4" y="13" width="3.5" height="7" rx="1"/><rect x="10.3" y="9" width="3.5" height="11" rx="1"/><rect x="16.5" y="4" width="3.5" height="16" rx="1"/></> : null}
      {kind === "car" ? <><path d="m5 11 2-5h10l2 5M4 11h16v7H4zM7 18v2m10-2v2M7 14h2m6 0h2"/></> : null}
      {kind === "pin" ? <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></> : null}
      {kind === "clock" ? <><circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 3"/></> : null}
      {kind === "gauge" ? <><path d="M4 17a9 9 0 1 1 16 0M12 15l5-6"/><circle cx="12" cy="15" r="1"/></> : null}
      {kind === "road" ? <><path d="m8 2-4 20M16 2l4 20M12 3v4m0 4v4m0 4v2"/></> : null}
      {kind === "eye" ? <><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></> : null}
      {kind === "trend" ? <><path d="m3 17 6-6 4 3 8-8M16 6h5v5"/></> : null}
      {kind === "star" ? <path d="m12 2 3.1 6.7 7.2.8-5.3 4.9 1.5 7.1L12 18l-6.5 3.5L7 14.4 1.7 9.5l7.2-.8z"/> : null}
      {kind === "compare" ? <><path d="M5 7h14m-4-4 4 4-4 4M19 17H5m4-4-4 4 4 4"/></> : null}
    </svg>
  );
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
  const [reconciliationData, setReconciliationData] = useState<ReconciliationOverview | null>(null);
  const [reconciliationBusy, setReconciliationBusy] = useState(false);
  const [reconciliationError, setReconciliationError] = useState("");

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

  async function loadReconciliationOverview() {
    if (reconciliationBusy) return;
    setReconciliationBusy(true);
    setReconciliationError("");
    try {
      // Triggered only by the driver's click; no extra queries on page load.
      const response = await fetch("/api/v1/reconciliation?summary_only=1", { cache: "no-store" });
      if (response.status === 401) throw new Error("Sua sessão expirou. Entre novamente.");
      if (response.status === 402 || response.status === 403) throw new Error("Seu plano não possui acesso a essa análise.");
      if (!response.ok) throw new Error("Não foi possível consultar a conciliação agora.");
      const result = await response.json() as ReconciliationOverview;
      setReconciliationData(result);
    } catch (cause) {
      setReconciliationData(null);
      setReconciliationError(cause instanceof Error ? cause.message : "Não foi possível consultar a conciliação.");
    } finally {
      setReconciliationBusy(false);
    }
  }

  const overview = section === "resumo";
  const productivity = section === "produtividade";
  const showKpis = overview || productivity;
  const summary = data?.answers.find((answer) => answer.intent === "PERIOD_SUMMARY");
  const quality = data?.answers.find((answer) => answer.intent === "OFFER_QUALITY");
  const periodLabel = data?.snapshot?.period?.label || data?.period?.label || "Período selecionado";
  // Reuse the typed projection from the canonical answer engine. Existing answers
  // remain as fallback for older deployed APIs during independent preview QA.
  const snapshotValue = (key: PerformanceKey, fallback: string) =>
    data?.snapshot?.metrics[key]?.formatted ?? fallback;
  const profitability = data?.answers.find((answer) => answer.intent === "PROFITABILITY");
  const rides = findMetric(summary, "completed_rides");
  const distance = findMetric(summary, "actual_distance");
  const headline: Array<{ title: string; value: string; note: string; estimated: boolean; icon: IconName; tone: string }> = [
    {
      title: "Tarifas de corridas concluídas",
      value: snapshotValue("realized_revenue", summary?.status === "ok" && typeof rides?.value === "number" && rides.value > 0 &&
        typeof summary.evidence.coverage?.value === "number" && summary.evidence.coverage.value > 0
        ? displayMetric(findMetric(summary, "realized_revenue")) : "—"),
      note: "Ofertas vinculadas · não comprova recebimento", estimated: false, icon: "revenue", tone: "mint",
    },
    {
      title: "Resultado estimado",
      value: snapshotValue("estimated_profit", profitability?.status === "ok" && profitability.evidence.completeness === "complete"
        ? displayMetric(findMetric(profitability, "estimated_profit")) : "—"),
      note: "Estimado com custo modelado; não é lucro recebido", estimated: true, icon: "profit", tone: "orange",
    },
    {
      title: "Corridas confirmadas",
      value: snapshotValue("completed_rides", summary?.status === "ok" ? displayMetric(rides) : "—"),
      note: "Não inclui ofertas apenas observadas", estimated: false, icon: "car", tone: "mint",
    },
    {
      title: "Distância registrada",
      value: snapshotValue("actual_distance", summary?.status === "ok" && typeof distance?.value === "number" && distance.value > 0
        ? displayMetric(distance) : "—"),
      note: "Exige odômetro", estimated: false, icon: "pin", tone: "blue",
    },
    {
      title: "Horas de jornadas encerradas",
      value: snapshotValue("closed_journey_hours", displayMetric(findMetric(summary, "closed_journey_hours"))),
      note: "Pode ser parcial; jornadas iniciadas no período", estimated: false, icon: "clock", tone: "purple",
    },
    {
      title: "Tarifas por hora",
      value: snapshotValue("realized_revenue_per_hour", displayMetric(findMetric(summary, "realized_revenue_per_hour"))),
      note: "Somente com jornadas e tarifas completas", estimated: false, icon: "gauge", tone: "blue",
    },
    {
      title: "Tarifas por km",
      value: snapshotValue("realized_revenue_per_km", displayMetric(findMetric(summary, "realized_revenue_per_km"))),
      note: "Exige também distância completa", estimated: false, icon: "road", tone: "blue",
    },
  ];

  const rateReasons = reasonsForUnavailableRates(summary);

  const activityMetrics: Array<{ label: string; value: string; note: string; icon: IconName; tone: string }> = [
    { label: "Ofertas observadas", value: snapshotValue("offers", displayMetric(findMetric(summary,"offers"))), note: "Capturas observadas, não corridas", icon: "eye", tone: "blue" },
    { label: "R$/km médio das ofertas", value: snapshotValue("avg_per_km", displayMetric(findMetric(summary,"avg_per_km"))), note: "Média de ofertas observadas", icon: "trend", tone: "mint" },
    { label: "R$/h médio das ofertas", value: snapshotValue("avg_per_hour", displayMetric(findMetric(summary,"avg_per_hour"))), note: "Média de ofertas observadas", icon: "clock", tone: "blue" },
    { label: "Ofertas classificadas como boas", value: snapshotValue("good_rate", quality?.status === "ok" ? displayMetric(findMetric(quality,"good_rate")) : "—"), note: "Percentual de ofertas boas · amostra mínima de 20", icon: "star", tone: "orange" },
  ];

  const chosenKpis = productivity
    ? headline.filter((item) => ["Horas de jornadas encerradas", "Tarifas por hora", "Tarifas por km", "Corridas confirmadas", "Distância registrada"].includes(item.title))
    : headline;

  return (
    <div className={styles.page}>
      <section className={styles.periodPanel} aria-label="Filtros de estatísticas">
        <div className={styles.hero}>
          <h1 className={styles.heading}>Seu desempenho</h1>
          <p className={styles.heroSubtitle}>Ganhos, custos e produtividade com dados que você pode conferir.</p>
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
      <div className={styles.contextRow} role="status"><span>Período: {periodLabel}</span><span>Dados reais e limitações explicadas</span></div>
      <IntelligenceNav />

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
              <div className={[styles.kpi, item.estimated ? styles.kpiEstimated : "", styles[item.tone]].filter(Boolean).join(" ")}
                key={item.title} aria-label={`${item.title} em ${periodLabel}: ${item.value}`}>
                <span className={styles.kpiIcon}><PerformanceIcon kind={item.icon} /></span>
                <span className={styles.kpiContent}>
                  <span className={styles.kpiLabel}>{item.title}</span>
                  <strong>{item.value}</strong>
                  <span className={styles.kpiNote}>{item.note}</span>
                </span>
              </div>
            ))}
          </div>
          <p className={styles.evidenceNote}>“—” significa dado ainda não comprovado. As tarifas de corridas concluídas vêm das ofertas originais, não de comprovantes de pagamento. Resultado estimado não é lucro efetivo.{summary?.evidence.completeness === "partial" ? " O período contém evidências parciais: alguns totais podem não representar todas as jornadas." : ""}</p>
          {rateReasons.length ? (
            <div className={styles.coverageWarning} role="note">
              <span className={styles.coverageIcon} aria-hidden="true">!</span>
              <div>
                <strong>Por que algumas taxas estão indisponíveis?</strong>
                <ul>{rateReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      {!busy && !error && data?.answers.length === 0 ? (
        <div className={styles.empty}>Não há resultados suficientes para o período.</div>
      ) : null}

      {overview && data && !busy && !error ? (
        <section className={styles.activity} aria-label="Ofertas e atividade do período">
          <div className={styles.sectionHeading}><PerformanceIcon kind="profit" /><h2>Ofertas e atividade</h2></div>
          <p className={styles.activityDescription}>Leituras do período selecionado. Indicadores de ofertas não comprovam corridas realizadas, valores recebidos ou lucro efetivo.</p>
          <div className={styles.activityGrid}>
            {activityMetrics.map((item) => (
              <div className={styles.activityMetric} key={item.label} aria-label={`${item.label} em ${periodLabel}: ${item.value}`}>
                <span className={[styles.activityIcon, styles[item.tone]].join(" ")}><PerformanceIcon kind={item.icon} /></span>
                <div><small>{item.label}</small><strong>{item.value}</strong><span className={styles.activityNote}>{item.note}</span></div>
              </div>
            ))}
          </div>
          <details className={styles.metaDetails}>
            <summary>Fonte dos dados e limitações</summary>
            <p>A análise vem da mesma fundação utilizada pelo Pergunte: ofertas canônicas, corridas operacionais confirmadas, jornadas e odômetro, respeitando o significado e a cobertura de cada métrica.</p>
            <p>Período: {periodLabel}. Não há valores de exemplo exibidos nesta tela.</p>
          </details>
        </section>
      ) : null}

      {!busy && !error && data?.answers.map((answer, index) => {
        if (overview && answer.intent === "OFFER_QUALITY") return null;
        const visibleMetrics = overview || (productivity && answer.intent === "PERIOD_SUMMARY")
          ? answer.metrics.filter((item) => ![
              "realized_revenue", "estimated_profit", "completed_rides", "actual_distance",
              "closed_journey_hours", "realized_revenue_per_hour", "realized_revenue_per_km",
            ].includes(item.key))
          : answer.metrics;
        // Resumo de ofertas já ocupa o cartão compacto exigido pelo mockup.
        if (overview && answer.intent === "PERIOD_SUMMARY") return null;
        const friendlyTitles: Record<string,string> = {
          PERIOD_SUMMARY: section === "produtividade" ? "Tempo e rendimento da jornada" : "Ofertas e atividade",
          PROFITABILITY: "Como calculamos o resultado",
          OPERATING_COST: "Despesas reais e custos estimados",
          REALIZED_EARNINGS: "Tarifas de corridas concluídas",
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
              <p>Amostra: {answer.evidence.sampleCount.toLocaleString("pt-BR")} · {answer.evidence.coverage?.label || "Cobertura da amostra"}: {pct(answer.evidence.coverage?.value)} · Escopo do resultado: {answer.evidence.completeness === "complete" ? "completo" : "parcial"}</p>
              {answer.limitations?.length ? <ul>{answer.limitations.map((item) => <li key={item}>{item}</li>)}</ul> : null}
              <p>Confiança: {answer.evidence.confidence} · Qualidade: {answer.evidence.dataQuality}</p>
            </details>
          </article>
        );
      })}

      {section === "corridas" ? (
        <section className={styles.comparison} aria-label="Estado da Conciliação Uber">
          <div className={styles.comparisonHeader}>
            <div>
              <h2 className={styles.dashboardTitle}>Conciliação do histórico Uber</h2>
              <p className={styles.muted}>Consulte o andamento da revisão humana das corridas importadas. O resumo considera todo o histórico da conta, não apenas o filtro acima.</p>
            </div>
            <button type="button" className={styles.pill} disabled={reconciliationBusy} onClick={loadReconciliationOverview}>
              {reconciliationBusy ? "Consultando…" : reconciliationData ? "Atualizar situação" : "Consultar situação"}
            </button>
          </div>
          {reconciliationError ? <p className={styles.error} role="alert">{reconciliationError}</p> : null}
          {reconciliationData && !reconciliationBusy ? (
            <div>
              <div className={styles.comparisonGrid}>
                {([
                  ["Corridas concluídas importadas", reconciliationData.total_completed_imports],
                  ["Importações revisadas", reconciliationData.reviewed],
                  ["Pendentes de revisão", reconciliationData.unresolved],
                  ["Correspondências aprovadas manualmente", reconciliationData.confirmed],
                  ["Revisadas sem correspondência", reconciliationData.no_match],
                ] as const).map(([label, value]) => (
                  <div className={styles.comparisonMetric} key={label}>
                    <small>{label}</small><strong>{value.toLocaleString("pt-BR")}</strong>
                  </div>
                ))}
              </div>
              <p className={styles.note}>
                {reconciliationData.status === "SEM_HISTORICO" ? "Não existem corridas concluídas importadas nesta conta." :
                  `Revisão humana do histórico: ${reconciliationData.coverage_pct?.toLocaleString("pt-BR") ?? "—"}%. `}
                {reconciliationData.missing_occurrence_time > 0 ? ` ${reconciliationData.missing_occurrence_time} registros sem horário de ocorrência.` : ""}
                {reconciliationData.future_occurrence_time > 0 ? ` ${reconciliationData.future_occurrence_time} registros com horário futuro, sujeitos a conferência.` : ""}
                {" "}Pistas do digitalizador não são aprovações. Esta consulta não confirma corridas, valores efetivamente recebidos ou pagamento final.
              </p>
            </div>
          ) : null}
        </section>
      ) : null}

      {section === "corridas" ? <ReconciliationReview /> : null}

      {overview ? (
        <section className={styles.comparison} aria-label="Comparativos por período">
          <div className={styles.comparisonHeader}>
            <div className={styles.sectionHeading}><PerformanceIcon kind="compare" /><h2 className={styles.dashboardTitle}>Comparar períodos</h2></div>
            <p className={styles.muted}>Apenas ofertas observadas; não compara receita ou lucro.</p>
          </div>
          <div className={styles.comparisonButtons}>
            <button type="button" className={styles.pill} disabled={compareBusy}
              aria-pressed={compareKind === "today_yesterday"}
              onClick={() => loadComparison("today_yesterday")}><PerformanceIcon kind="clock" /> Hoje × ontem <span aria-hidden="true">›</span></button>
            <button type="button" className={styles.pill} disabled={compareBusy}
              aria-pressed={compareKind === "week_previous"}
              onClick={() => loadComparison("week_previous")}><PerformanceIcon kind="profit" /> Semana × anterior <span aria-hidden="true">›</span></button>
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
