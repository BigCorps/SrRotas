"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";

type Days = 7 | 30 | 90;

type Summary = {
  offer_count: number;
  average_fare: number | null;
  average_per_km: number | null;
  average_per_hour: number | null;
  average_per_minute: number | null;
  estimated_total_profit: number | null;
  verdicts: { boa: number; regular: number; ruim: number };
  data_sources: { operational: number; historical_v7: number };
};

type Journey = {
  id: string;
  platform: string;
  started_at: string;
  ended_at: string | null;
  duration_minutes: number | null;
  offer_count: number;
  good_count: number;
  regular_count: number;
  bad_count: number;
  average_per_km: number | null;
  average_per_hour: number | null;
  estimated_profit_observed: number | null;
};

type Analytics = {
  summary: Summary;
  journeys: Journey[];
  note: string;
  truncated?: boolean;
};

function number(value: number | null | undefined, digits = 2) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toLocaleString("pt-BR", { minimumFractionDigits: digits, maximumFractionDigits: digits })
    : "—";
}

function duration(value: number | null) {
  if (value === null || !Number.isFinite(value)) return "aberta";
  const h = Math.floor(value / 60);
  const m = value % 60;
  return h > 0 ? `${h}h ${m}min` : `${m}min`;
}

export default function HistoricoPage() {
  const [days, setDays] = useState<Days>(30);
  const [data, setData] = useState<Analytics | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setBusy(true);
    setError("");
    fetch(`/api/v1/analytics?days=${days}`, { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body?.error || "analytics_failed");
        return body as Analytics;
      })
      .then((body) => { if (active) setData(body); })
      .catch(() => { if (active) setError("Não foi possível carregar o histórico."); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [days]);

  const journeys = useMemo(() => data?.journeys || [], [data]);
  const summary = data?.summary;

  return (
    <div className="sr023Page srHistoricoP501">
      <AccountPageHeader
        title="Histórico"
        subtitle="Ofertas observadas e jornadas reais permanecem separadas. Abra uma jornada para ver as leituras que realmente pertencem a ela."
      >
        <div className="srP501ModeSwitch" role="group" aria-label="Período do histórico">
          {[7, 30, 90].map((value) => (
            <button key={value} className={days === value ? "active" : ""} onClick={() => setDays(value as Days)}>{value} dias</button>
          ))}
        </div>
      </AccountPageHeader>

      {error ? <div className="srInlineError">{error}</div> : null}

      <section className="srMetrics srMetricsWide">
        <div><span>Ofertas no período</span><strong>{busy ? "…" : summary?.offer_count?.toLocaleString("pt-BR") ?? "0"}</strong><small>operacional + V7 quando pertencem à conta</small></div>
        <div><span>R$/km médio</span><strong>{busy ? "…" : number(summary?.average_per_km)}</strong><small>ofertas observadas</small></div>
        <div><span>R$/h médio</span><strong>{busy ? "…" : number(summary?.average_per_hour)}</strong><small>ofertas observadas</small></div>
        <div><span>Jornadas reais</span><strong>{busy ? "…" : journeys.length.toLocaleString("pt-BR")}</strong><small>criadas pelo app Android</small></div>
      </section>

      <section className="srPanel srSectionGap">
        <div className="srPanelHead">
          <div><span className="srEyebrow">JORNADAS</span><h2>Atividade operacional do período</h2></div>
          <span className="srMutedPill">{days} dias</span>
        </div>

        {journeys.length ? (
          <div className="srTableWrap">
            <table className="srTable srClickableTable srP501HistoryTable">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Estado</th>
                  <th>Duração</th>
                  <th>Ofertas</th>
                  <th>Boas</th>
                  <th>Atenção</th>
                  <th>Ruins</th>
                  <th>R$/km</th>
                  <th>R$/h</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {journeys.map((journey) => (
                  <tr key={journey.id}>
                    <td>
                      <strong>{new Date(journey.started_at).toLocaleDateString("pt-BR")}</strong>
                      <small className="srP501TableSub">{new Date(journey.started_at).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })} · {journey.platform}</small>
                    </td>
                    <td><span className={`srStateTag ${journey.ended_at ? "revoked" : "active"}`}>{journey.ended_at ? "Encerrada" : "Ativa"}</span></td>
                    <td>{duration(journey.duration_minutes)}</td>
                    <td>{journey.offer_count}</td>
                    <td>{journey.good_count}</td>
                    <td>{journey.regular_count}</td>
                    <td>{journey.bad_count}</td>
                    <td>{number(journey.average_per_km)}</td>
                    <td>{number(journey.average_per_hour)}</td>
                    <td><Link className="srRowLink" href={`/app/historico/${journey.id}`}>Detalhes →</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : busy ? (
          <div className="srEmpty compact"><strong>Carregando histórico…</strong></div>
        ) : (
          <div className="srEmpty"><span>⌁</span><strong>Nenhuma jornada neste período</strong><p>O histórico de ofertas pode existir sem uma jornada real; jornadas só são criadas pela operação do aplicativo.</p></div>
        )}
      </section>

      <section className="srGrid2 srSectionGap">
        <article className="srMiniPanel">
          <span>FONTES</span>
          <strong>{summary?.data_sources?.operational?.toLocaleString("pt-BR") ?? "0"} operacionais · {summary?.data_sources?.historical_v7?.toLocaleString("pt-BR") ?? "0"} V7</strong>
          <p>O V7 entra somente na conta proprietária do lote canônico e nunca cria outcome de corrida.</p>
        </article>
        <article className="srMiniPanel">
          <span>INTERPRETAÇÃO</span>
          <strong>Oferta observada ≠ faturamento</strong>
          <p>Tarifa, custo e lucro mostrados neste painel são métricas de oferta/estimativa quando disponíveis.</p>
        </article>
      </section>

      {data?.truncated ? <div className="srInlineError srSectionGap">O período excedeu o limite seguro de leitura do painel. Use uma janela menor para uma análise completa.</div> : null}
      <p className="srDataNote srP501FooterNote">{data?.note || ""}</p>
    </div>
  );
}
