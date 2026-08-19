"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import NavIcon from "./_components/NavIcon";

type Me = { display_name?: string; email?: string };

type Summary = {
  offer_count: number;
  average_per_km: number | null;
  average_per_hour: number | null;
  average_estimated_profit?: number | null;
  verdicts: { boa: number; regular: number; ruim: number };
};

type ServiceRow = {
  service_type: string;
  offer_count: number;
  average_per_km: number | null;
  average_per_hour: number | null;
};

type TopOffer = {
  observed_at: string;
  fare: number;
  service_type: string;
  verdict: string;
  per_km: number | null;
  per_hour: number | null;
};

type Dashboard = {
  summary: Summary;
  services: ServiceRow[];
  top_offers: TopOffer[];
  journeys: Array<{ id: string; started_at: string; offer_count: number }>;
  comparison?: {
    delta?: {
      offer_count_pct: number | null;
      average_per_km_pct: number | null;
      average_per_hour_pct: number | null;
    };
  };
  note?: string;
};

type CurrentJourney = {
  id: string;
  started_at: string;
  platform: string;
  ended_at: string | null;
};

const cards = [
  { title: "Histórico", text: "Jornadas, ofertas, filtros e comparações estruturadas.", href: "/app/historico", icon: "history" as const },
  { title: "Pesquisa IA", text: "Pergunte sobre seus dados sincronizados usando a IA do Sr. Rotas.", href: "/app/ia", icon: "ai" as const },
  { title: "MCP", text: "Gere e revogue chaves para assistentes compatíveis, sempre em leitura.", href: "/app/mcp", icon: "mcp" as const },
  { title: "Plano", text: "Assinatura, créditos e preparação do trial oficial da 1.0.", href: "/app/plano", icon: "plan" as const },
];

const serviceNames: Record<string, string> = {
  uberx: "UberX",
  comfort: "Comfort",
  black: "Black",
  electric: "Electric",
  priority: "Priority",
  moto: "Moto",
  unknown: "Não identificado",
};

function money(value: number | null | undefined) {
  return typeof value === "number"
    ? value.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : "—";
}

function delta(value: number | null | undefined) {
  if (typeof value !== "number" || !Number.isFinite(value)) return "sem comparação";
  if (value === 0) return "igual ao período anterior";
  return `${value > 0 ? "+" : ""}${value.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% vs. período anterior`;
}

export default function AppHome() {
  const [me, setMe] = useState<Me | null>(null);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [current, setCurrent] = useState<CurrentJourney | null>(null);
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    Promise.all([
      fetch("/api/v1/account/me", { cache: "no-store" }).then((r) => r.ok ? r.json() : null),
      fetch("/api/v1/analytics?days=7", { cache: "no-store" }).then((r) => r.ok ? r.json() : null),
      fetch("/api/v1/journeys?current=1", { cache: "no-store" }).then((r) => r.ok ? r.json() : null),
    ]).then(([account, analytics, journey]) => {
      setMe(account);
      setDashboard(analytics);
      setCurrent(journey?.journey ?? null);
    }).catch(() => undefined).finally(() => setBusy(false));
  }, []);

  const summary = dashboard?.summary;
  const bestService = useMemo(() => {
    return [...(dashboard?.services ?? [])]
      .filter((service) => service.offer_count > 0)
      .sort((a, b) => (b.average_per_km ?? -1) - (a.average_per_km ?? -1))[0] ?? null;
  }, [dashboard]);

  return <>
    <section className="srPageHead">
      <div>
        <span className="srEyebrow">PAINEL WEB</span>
        <h1>{me?.display_name ? `Olá, ${me.display_name}.` : "Seu Sr. Rotas, também fora da rua."}</h1>
        <p>O motor de oferta continua no Android. Aqui você acompanha histórico, comparações, IA, MCP e conta sem depender de atualização do APK.</p>
      </div>
      <div className="srStatusCard">
        <span className={current ? "srStatusDot live" : "srStatusDot"}/>
        <div>
          <strong>{current ? "Jornada em andamento" : busy ? "Carregando sua conta..." : "Painel sincronizado"}</strong>
          <small>{current ? `Iniciada ${new Date(current.started_at).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })}` : me?.email || "Dados privados liberados somente para sua conta."}</small>
        </div>
      </div>
    </section>

    <section className="srGrid4">
      {cards.map((card) => <Link className="srFeature" href={card.href} key={card.href}>
        <span className="srFeatureIcon"><NavIcon name={card.icon}/></span>
        <h2>{card.title}</h2>
        <p>{card.text}</p>
        <b>Abrir →</b>
      </Link>)}
    </section>

    <section className="srPanel srSectionGap">
      <div className="srPanelHead">
        <div><span className="srEyebrow">ÚLTIMOS 7 DIAS</span><h2>Resumo das ofertas observadas</h2></div>
        <Link href="/app/historico" className="srSecondary">Explorar histórico →</Link>
      </div>
      <div className="srMetrics">
        <div><span>Ofertas</span><strong>{summary?.offer_count ?? "—"}</strong><small>{delta(dashboard?.comparison?.delta?.offer_count_pct)}</small></div>
        <div><span>Boas</span><strong>{summary?.verdicts?.boa ?? "—"}</strong><small>pela sua estratégia</small></div>
        <div><span>R$/km médio</span><strong>{money(summary?.average_per_km)}</strong><small>{delta(dashboard?.comparison?.delta?.average_per_km_pct)}</small></div>
        <div><span>R$/h médio</span><strong>{money(summary?.average_per_hour)}</strong><small>{delta(dashboard?.comparison?.delta?.average_per_hour_pct)}</small></div>
      </div>
      {!summary?.offer_count ? <div className="srEmpty"><span>⌁</span><strong>Ainda não há ofertas neste período</strong><p>Quando o Android sincronizar ofertas válidas, os dados aparecem aqui automaticamente.</p></div> : null}
    </section>

    <section className="srGrid2 srSectionGap">
      <article className="srPanel">
        <div className="srPanelHead"><div><span className="srEyebrow">SERVIÇOS</span><h2>Onde apareceram as ofertas</h2></div><span className="srMutedPill">Dados reais</span></div>
        {dashboard?.services?.length ? <div className="srServiceList">
          {dashboard.services.slice(0, 6).map((service) => {
            const pct = summary?.offer_count ? Math.round((service.offer_count / summary.offer_count) * 100) : 0;
            return <div className="srServiceRow" key={service.service_type}>
              <div><strong>{serviceNames[service.service_type] || service.service_type}</strong><small>{service.offer_count} ofertas · R$ {money(service.average_per_km)}/km</small></div>
              <span>{pct}%</span>
              <i><b style={{ width: `${Math.min(100, Math.max(2, pct))}%` }}/></i>
            </div>;
          })}
        </div> : <div className="srEmpty compact"><span>◎</span><strong>Sem serviços no período</strong></div>}
      </article>

      <article className="srPanel">
        <div className="srPanelHead"><div><span className="srEyebrow">DESTAQUE</span><h2>Melhor contexto observado</h2></div></div>
        {bestService ? <div className="srHighlight">
          <span className="srHighlightIcon">↗</span>
          <div><small>Maior R$/km médio por serviço</small><strong>{serviceNames[bestService.service_type] || bestService.service_type}</strong><p>R$ {money(bestService.average_per_km)}/km · R$ {money(bestService.average_per_hour)}/h · {bestService.offer_count} ofertas observadas.</p></div>
        </div> : <div className="srEmpty compact"><span>↗</span><strong>Aguardando mais dados</strong><p>O destaque aparece quando houver ofertas suficientes no período.</p></div>}
        <p className="srDataNote">Isso resume ofertas observadas e não prova aceite, conclusão ou ganho realizado.</p>
      </article>
    </section>

    {dashboard?.top_offers?.length ? <section className="srPanel srSectionGap">
      <div className="srPanelHead"><div><span className="srEyebrow">OFERTAS EM DESTAQUE</span><h2>Melhores leituras recentes</h2></div><Link href="/app/historico" className="srSecondary">Ver todas →</Link></div>
      <div className="srTableWrap"><table className="srTable"><thead><tr><th>Quando</th><th>Serviço</th><th>Valor</th><th>R$/km</th><th>R$/h</th><th>Leitura</th></tr></thead>
      <tbody>{dashboard.top_offers.slice(0, 6).map((offer, index) => <tr key={`${offer.observed_at}-${index}`}><td>{new Date(offer.observed_at).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })}</td><td>{serviceNames[offer.service_type] || offer.service_type}</td><td>R$ {money(offer.fare)}</td><td>{money(offer.per_km)}</td><td>{money(offer.per_hour)}</td><td><span className={`srVerdict ${offer.verdict}`}>{offer.verdict === "boa" ? "Boa" : offer.verdict === "regular" ? "Atenção" : "Ruim"}</span></td></tr>)}</tbody></table></div>
    </section> : null}
  </>;
}
