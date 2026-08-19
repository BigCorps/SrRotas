"use client";

import { useEffect, useMemo, useState } from "react";

type Point = { key: string; label: string; offer_count: number };
type Journey = { id: string; started_at: string; ended_at: string | null; offer_count: number; good_count: number; regular_count: number; bad_count: number; average_per_km: number | null; average_per_hour: number | null };
type Dash = {
  summary: { offer_count: number; average_per_km: number | null; average_per_hour: number | null; verdicts: { boa: number; regular: number; ruim: number } };
  daily: Point[];
  journeys: Journey[];
  note?: string;
};

function money(value: number | null | undefined) {
  return typeof value === "number" ? value.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "—";
}

export default function HistoricoPage() {
  const [days, setDays] = useState(7);
  const [verdict, setVerdict] = useState("");
  const [data, setData] = useState<Dash | null>(null);
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    setBusy(true);
    const params = new URLSearchParams({ days: String(days) });
    if (verdict) params.set("verdict", verdict);
    fetch(`/api/v1/analytics?${params}`, { cache: "no-store" })
      .then(async (r) => { if (!r.ok) throw new Error("analytics_failed"); return r.json(); })
      .then(setData).catch(() => setData(null)).finally(() => setBusy(false));
  }, [days, verdict]);

  const maxDaily = useMemo(() => Math.max(1, ...(data?.daily ?? []).map((point) => point.offer_count)), [data]);
  const summary = data?.summary;
  const total = Math.max(1, summary?.offer_count ?? 0);
  const goodPct = ((summary?.verdicts.boa ?? 0) / total) * 100;
  const regularPct = ((summary?.verdicts.regular ?? 0) / total) * 100;

  return <>
    <section className="srPageHead compact"><div><span className="srEyebrow">HISTÓRICO</span><h1>Entenda o que apareceu nas suas jornadas.</h1><p>Métricas estruturadas e filtros reais, sem gastar créditos de IA.</p></div><span className="srMutedPill">{busy ? "Atualizando..." : "Dados sincronizados"}</span></section>

    <section className="srToolbar">
      {[7,30,90].map(value => <button key={value} className={days === value ? "active" : ""} onClick={() => setDays(value)}>{value} dias</button>)}
      <span/>
      <button className={verdict === "" ? "active" : ""} onClick={() => setVerdict("")}>Todos</button>
      <button className={verdict === "boa" ? "active" : ""} onClick={() => setVerdict("boa")}>Boas</button>
      <button className={verdict === "regular" ? "active" : ""} onClick={() => setVerdict("regular")}>Atenção</button>
      <button className={verdict === "ruim" ? "active" : ""} onClick={() => setVerdict("ruim")}>Ruins</button>
    </section>

    <section className="srMetrics srMetricsWide">
      <div><span>Ofertas</span><strong>{summary?.offer_count ?? "—"}</strong><small>período selecionado</small></div>
      <div><span>R$/km</span><strong>{money(summary?.average_per_km)}</strong><small>média observada</small></div>
      <div><span>R$/h</span><strong>{money(summary?.average_per_hour)}</strong><small>média observada</small></div>
      <div><span>Boas</span><strong>{summary?.verdicts.boa ?? "—"}</strong><small>pela sua estratégia</small></div>
    </section>

    <section className="srGrid2 srSectionGap">
      <article className="srPanel">
        <div className="srPanelHead"><h2>Ofertas por dia</h2><span className="srMutedPill">SQL/TypeScript</span></div>
        {data?.daily?.length ? <div className="srLiveBars">{data.daily.map(point => <div className="srLiveBar" key={point.key}><span>{point.offer_count}</span><i style={{ height: `${Math.max(8, (point.offer_count / maxDaily) * 100)}%` }}/><small>{point.label}</small></div>)}</div> : <div className="srEmpty"><span>⌁</span><strong>Sem ofertas no período</strong><p>Troque o período ou aguarde novas jornadas sincronizadas.</p></div>}
      </article>

      <article className="srPanel">
        <div className="srPanelHead"><h2>Distribuição</h2><span className="srMutedPill">Sem IA</span></div>
        <div className="srDonut" style={{ background: `conic-gradient(var(--sr-good) 0 ${goodPct}%, var(--sr-warn) ${goodPct}% ${goodPct + regularPct}%, var(--sr-bad) ${goodPct + regularPct}% 100%)` }}><span>{summary?.offer_count ?? 0}<small>ofertas</small></span></div>
        <div className="srLegend"><b><i className="good"/>Boa {summary?.verdicts.boa ?? 0}</b><b><i className="warn"/>Atenção {summary?.verdicts.regular ?? 0}</b><b><i className="bad"/>Ruim {summary?.verdicts.ruim ?? 0}</b></div>
      </article>
    </section>

    <section className="srPanel srSectionGap">
      <div className="srPanelHead"><div><span className="srEyebrow">ÚLTIMAS JORNADAS</span><h2>Detalhes e comparações</h2></div></div>
      {data?.journeys?.length ? <div className="srTableWrap"><table className="srTable"><thead><tr><th>Início</th><th>Ofertas</th><th>Boas</th><th>R$/km</th><th>R$/h</th></tr></thead><tbody>{data.journeys.slice(0,12).map(j => <tr key={j.id}><td>{new Date(j.started_at).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })}</td><td>{j.offer_count}</td><td>{j.good_count}</td><td>{money(j.average_per_km)}</td><td>{money(j.average_per_hour)}</td></tr>)}</tbody></table></div> : <div className="srEmpty"><span>↗</span><strong>Nenhuma jornada neste período</strong><p>As jornadas sincronizadas aparecem aqui automaticamente.</p></div>}
      <p className="srDataNote">{data?.note || "Os números representam ofertas observadas, não corridas aceitas ou concluídas."}</p>
    </section>
  </>;
}
