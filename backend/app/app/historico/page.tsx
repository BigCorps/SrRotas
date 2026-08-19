"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

type Point = { key: string; label: string; offer_count: number };
type Journey = {
  id: string;
  started_at: string;
  ended_at: string | null;
  duration_minutes: number | null;
  offer_count: number;
  good_count: number;
  regular_count: number;
  bad_count: number;
  average_per_km: number | null;
  average_per_hour: number | null;
};
type ServiceRow = { service_type: string; offer_count: number; average_per_km: number | null; average_per_hour: number | null };
type Dash = {
  summary: {
    offer_count: number;
    average_per_km: number | null;
    average_per_hour: number | null;
    verdicts: { boa: number; regular: number; ruim: number };
  };
  comparison?: { delta?: { offer_count_pct: number | null; average_per_km_pct: number | null; average_per_hour_pct: number | null } };
  daily: Point[];
  services: ServiceRow[];
  journeys: Journey[];
  note?: string;
};

const services = [
  ["", "Todos os serviços"],
  ["uberx", "UberX"],
  ["comfort", "Comfort"],
  ["black", "Black"],
  ["electric", "Electric"],
  ["priority", "Priority"],
  ["moto", "Moto"],
  ["unknown", "Não identificado"],
] as const;

const serviceNames = Object.fromEntries(services.filter(([value]) => value).map(([value, label]) => [value, label]));

function money(value: number | null | undefined) {
  return typeof value === "number"
    ? value.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : "—";
}

function delta(value: number | null | undefined) {
  if (typeof value !== "number" || !Number.isFinite(value)) return "sem comparação";
  return `${value > 0 ? "+" : ""}${value.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`;
}

export default function HistoricoPage() {
  const [days, setDays] = useState(7);
  const [verdict, setVerdict] = useState("");
  const [service, setService] = useState("");
  const [offerType, setOfferType] = useState("");
  const [data, setData] = useState<Dash | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    setBusy(true);
    setError("");

    const params = new URLSearchParams({ days: String(days) });
    if (verdict) params.set("verdict", verdict);
    if (service) params.set("service_type", service);
    if (offerType) params.set("offer_type", offerType);

    fetch(`/api/v1/analytics?${params}`, { cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) throw new Error("Não foi possível carregar o histórico.");
        return response.json();
      })
      .then(setData)
      .catch((cause) => {
        setData(null);
        setError(cause instanceof Error ? cause.message : "Não foi possível carregar o histórico.");
      })
      .finally(() => setBusy(false));
  }, [days, verdict, service, offerType]);

  const maxDaily = useMemo(() => Math.max(1, ...(data?.daily ?? []).map((point) => point.offer_count)), [data]);
  const summary = data?.summary;
  const total = Math.max(1, summary?.offer_count ?? 0);
  const goodPct = ((summary?.verdicts.boa ?? 0) / total) * 100;
  const regularPct = ((summary?.verdicts.regular ?? 0) / total) * 100;

  return <>
    <section className="srPageHead compact">
      <div><span className="srEyebrow">HISTÓRICO</span><h1>Entenda o que apareceu nas suas jornadas.</h1><p>Filtre período, serviço, tipo de oferta e verdict usando apenas métricas estruturadas.</p></div>
      <span className="srMutedPill">{busy ? "Atualizando..." : error ? "Falha ao carregar" : "Dados sincronizados"}</span>
    </section>

    <section className="srToolbar srFilterToolbar">
      <div className="srFilterGroup">
        <small>Período</small>
        <div>{[7, 30, 90].map((value) => <button key={value} className={days === value ? "active" : ""} onClick={() => setDays(value)}>{value} dias</button>)}</div>
      </div>

      <label className="srSelectField"><span>Serviço</span><select value={service} onChange={(event) => setService(event.target.value)}>{services.map(([value, label]) => <option key={value || "all"} value={value}>{label}</option>)}</select></label>
      <label className="srSelectField"><span>Tipo</span><select value={offerType} onChange={(event) => setOfferType(event.target.value)}><option value="">Exclusive + Radar</option><option value="exclusive">Exclusive</option><option value="radar">Radar</option></select></label>
      <label className="srSelectField"><span>Leitura</span><select value={verdict} onChange={(event) => setVerdict(event.target.value)}><option value="">Todas</option><option value="boa">Boas</option><option value="regular">Atenção</option><option value="ruim">Ruins</option></select></label>
    </section>

    {error ? <div className="srInlineError">{error}</div> : null}

    <section className="srMetrics srMetricsWide">
      <div><span>Ofertas</span><strong>{summary?.offer_count ?? "—"}</strong><small>{delta(data?.comparison?.delta?.offer_count_pct)} vs. anterior</small></div>
      <div><span>R$/km</span><strong>{money(summary?.average_per_km)}</strong><small>{delta(data?.comparison?.delta?.average_per_km_pct)} vs. anterior</small></div>
      <div><span>R$/h</span><strong>{money(summary?.average_per_hour)}</strong><small>{delta(data?.comparison?.delta?.average_per_hour_pct)} vs. anterior</small></div>
      <div><span>Boas</span><strong>{summary?.verdicts.boa ?? "—"}</strong><small>pela sua estratégia</small></div>
    </section>

    <section className="srGrid2 srSectionGap">
      <article className="srPanel">
        <div className="srPanelHead"><h2>Ofertas por dia</h2><span className="srMutedPill">SQL/TypeScript</span></div>
        {data?.daily?.length ? <div className="srLiveBars">{data.daily.map((point) => <div className="srLiveBar" key={point.key}><span>{point.offer_count}</span><i style={{ height: `${Math.max(8, (point.offer_count / maxDaily) * 100)}%` }}/><small>{point.label}</small></div>)}</div> : <div className="srEmpty"><span>⌁</span><strong>Sem ofertas no filtro</strong><p>Troque o período ou os filtros.</p></div>}
      </article>

      <article className="srPanel">
        <div className="srPanelHead"><h2>Distribuição</h2><span className="srMutedPill">Sem IA</span></div>
        <div className="srDonut" style={{ background: `conic-gradient(var(--sr-good) 0 ${goodPct}%, var(--sr-warn) ${goodPct}% ${goodPct + regularPct}%, var(--sr-bad) ${goodPct + regularPct}% 100%)` }}><span>{summary?.offer_count ?? 0}<small>ofertas</small></span></div>
        <div className="srLegend"><b><i className="good"/>Boa {summary?.verdicts.boa ?? 0}</b><b><i className="warn"/>Atenção {summary?.verdicts.regular ?? 0}</b><b><i className="bad"/>Ruim {summary?.verdicts.ruim ?? 0}</b></div>
      </article>
    </section>

    {data?.services?.length ? <section className="srPanel srSectionGap">
      <div className="srPanelHead"><div><span className="srEyebrow">SERVIÇOS</span><h2>Comparativo do período</h2></div></div>
      <div className="srTableWrap"><table className="srTable"><thead><tr><th>Serviço</th><th>Ofertas</th><th>R$/km médio</th><th>R$/h médio</th></tr></thead><tbody>
        {data.services.map((item) => <tr key={item.service_type}><td>{serviceNames[item.service_type] || item.service_type}</td><td>{item.offer_count}</td><td>{money(item.average_per_km)}</td><td>{money(item.average_per_hour)}</td></tr>)}
      </tbody></table></div>
    </section> : null}

    <section className="srPanel srSectionGap">
      <div className="srPanelHead"><div><span className="srEyebrow">ÚLTIMAS JORNADAS</span><h2>Abra uma jornada para ver as ofertas</h2></div></div>
      {data?.journeys?.length ? <div className="srTableWrap"><table className="srTable srClickableTable"><thead><tr><th>Início</th><th>Duração</th><th>Ofertas</th><th>Boas</th><th>R$/km</th><th>R$/h</th><th></th></tr></thead><tbody>
        {data.journeys.slice(0, 20).map((journey) => <tr key={journey.id}>
          <td>{new Date(journey.started_at).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })}</td>
          <td>{typeof journey.duration_minutes === "number" ? `${journey.duration_minutes} min` : "Em andamento"}</td>
          <td>{journey.offer_count}</td><td>{journey.good_count}</td><td>{money(journey.average_per_km)}</td><td>{money(journey.average_per_hour)}</td>
          <td><Link className="srRowLink" href={`/app/historico/${journey.id}`}>Detalhes →</Link></td>
        </tr>)}
      </tbody></table></div> : <div className="srEmpty"><span>↗</span><strong>Nenhuma jornada neste período</strong><p>As jornadas sincronizadas aparecem aqui automaticamente.</p></div>}
      <p className="srDataNote">{data?.note || "Os números representam ofertas observadas, não corridas aceitas ou concluídas."}</p>
    </section>
  </>;
}
