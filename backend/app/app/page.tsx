"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import AccountPageHeader from "./_components/AccountPageHeader";

type Summary = {
  offer_count: number;
  average_per_km: number | null;
  average_per_hour: number | null;
  average_per_minute: number | null;
  average_estimated_profit: number | null;
  verdicts?: { boa?: number; regular?: number; ruim?: number };
  data_sources?: { operational?: number; historical_v7?: number };
};

type Analytics = {
  summary: Summary;
  comparison?: {
    delta?: {
      offer_count_pct?: number | null;
      average_per_km_pct?: number | null;
      average_per_hour_pct?: number | null;
    };
  };
  journeys?: Array<{
    id: string;
    platform: string;
    started_at: string;
    ended_at: string | null;
    offer_count: number;
    average_per_km: number | null;
    average_per_hour: number | null;
  }>;
  note?: string;
};

type Journey = {
  id: string;
  platform: string;
  started_at: string;
  ended_at: string | null;
  state: "ACTIVE" | "PAUSED" | "ENDED";
};

function number(value: number | null | undefined, digits = 2) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toLocaleString("pt-BR", { minimumFractionDigits: digits, maximumFractionDigits: digits })
    : "—";
}

function delta(value: number | null | undefined) {
  if (typeof value !== "number" || !Number.isFinite(value)) return "sem comparação";
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% vs. período anterior`;
}

export default function DashboardPage() {
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [journey, setJourney] = useState<Journey | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setBusy(true);
    Promise.all([
      fetch("/api/v1/analytics?days=7", { cache: "no-store" }).then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body?.error || "analytics_failed");
        return body as Analytics;
      }),
      fetch("/api/v1/journeys?current=1", { cache: "no-store" }).then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body?.error || "journey_failed");
        return (body?.journey || null) as Journey | null;
      }),
    ])
      .then(([nextAnalytics, nextJourney]) => {
        if (!active) return;
        setAnalytics(nextAnalytics);
        setJourney(nextJourney);
      })
      .catch(() => {
        if (active) setError("Não foi possível carregar todos os dados do painel agora.");
      })
      .finally(() => {
        if (active) setBusy(false);
      });

    return () => { active = false; };
  }, []);

  const summary = analytics?.summary;
  const latest = useMemo(() => analytics?.journeys?.[0] || null, [analytics]);

  return (
    <div className="sr023Page srDashboardP501">
      <AccountPageHeader
        title="Início"
        subtitle="Uma visão curta da sua operação. As métricas abaixo descrevem ofertas observadas; corrida concluída é um dado separado."
      >
        <div className="srP501JourneyState">
          <i className={journey ? "live" : ""} />
          <div>
            <strong>{journey ? "Jornada ativa" : "Sem jornada ativa"}</strong>
            <small>{journey ? `Desde ${new Date(journey.started_at).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}` : "Inicie pelo app Android quando for dirigir."}</small>
          </div>
        </div>
      </AccountPageHeader>

      {error ? <div className="srInlineError">{error}</div> : null}

      <section className="srMetrics srMetricsWide">
        <div>
          <span>Ofertas · 7 dias</span>
          <strong>{busy ? "…" : summary?.offer_count?.toLocaleString("pt-BR") ?? "0"}</strong>
          <small>{delta(analytics?.comparison?.delta?.offer_count_pct)}</small>
        </div>
        <div>
          <span>R$/km médio</span>
          <strong>{busy ? "…" : number(summary?.average_per_km)}</strong>
          <small>{delta(analytics?.comparison?.delta?.average_per_km_pct)}</small>
        </div>
        <div>
          <span>R$/h médio</span>
          <strong>{busy ? "…" : number(summary?.average_per_hour)}</strong>
          <small>{delta(analytics?.comparison?.delta?.average_per_hour_pct)}</small>
        </div>
        <div>
          <span>Leitura da semana</span>
          <strong>{busy ? "…" : `${summary?.verdicts?.boa ?? 0} boas`}</strong>
          <small>{summary ? `${summary.verdicts?.regular ?? 0} atenção · ${summary.verdicts?.ruim ?? 0} ruins` : "—"}</small>
        </div>
      </section>

      <section className="srP501QuickGrid">
        <Link href="/app/agora" className="srP501QuickCard primary">
          <span>AGORA</span>
          <strong>Onde seu histórico ajuda mais neste horário?</strong>
          <p>Compara região, faixa horária, categoria, tamanho de amostra e aderência às suas metas.</p>
          <b>Abrir inteligência →</b>
        </Link>
        <Link href="/app/historico" className="srP501QuickCard">
          <span>HISTÓRICO</span>
          <strong>Veja jornadas e ofertas observadas</strong>
          <p>Use os períodos de 7, 30 ou 90 dias e abra cada jornada para conferir as leituras estruturadas.</p>
          <b>Ver histórico →</b>
        </Link>
        <Link href="/app/perfil" className="srP501QuickCard">
          <span>CONTA</span>
          <strong>Plano, dispositivos e integrações</strong>
          <p>Mensagens rápidas, MCP, aparelhos, sessão Web e exclusão de conta continuam centralizados no perfil.</p>
          <b>Abrir usuário →</b>
        </Link>
      </section>

      <section className="srGrid2 srSectionGap">
        <article className="srPanel">
          <div className="srPanelHead">
            <div><span className="srEyebrow">BASE DOS DADOS</span><h2>O que entrou nesta leitura</h2></div>
          </div>
          <div className="srP501SourceRows">
            <div><span>Operacional</span><strong>{summary?.data_sources?.operational?.toLocaleString("pt-BR") ?? "0"}</strong></div>
            <div><span>Histórico V7</span><strong>{summary?.data_sources?.historical_v7?.toLocaleString("pt-BR") ?? "0"}</strong></div>
          </div>
          <p className="srDataNote">
            O V7 representa ofertas históricas observadas. Ele não cria corridas concluídas, aceites ou lucro realizado.
          </p>
        </article>

        <article className="srPanel">
          <div className="srPanelHead">
            <div><span className="srEyebrow">JORNADA MAIS RECENTE</span><h2>{latest ? new Date(latest.started_at).toLocaleDateString("pt-BR") : "Ainda sem jornada no período"}</h2></div>
            {latest ? <Link className="srRowLink" href={`/app/historico/${latest.id}`}>Abrir →</Link> : null}
          </div>
          {latest ? (
            <div className="srP501SourceRows">
              <div><span>Ofertas</span><strong>{latest.offer_count}</strong></div>
              <div><span>R$/km médio</span><strong>{number(latest.average_per_km)}</strong></div>
              <div><span>R$/h médio</span><strong>{number(latest.average_per_hour)}</strong></div>
            </div>
          ) : (
            <div className="srEmpty compact"><strong>Nenhuma jornada recente</strong><p>Quando o Android sincronizar uma jornada, ela aparecerá aqui.</p></div>
          )}
        </article>
      </section>

      {analytics?.note ? <p className="srDataNote srP501FooterNote">{analytics.note}</p> : null}
    </div>
  );
}
