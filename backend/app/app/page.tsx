"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import NavIcon from "./_components/NavIcon";

type Me = { display_name?: string; email?: string };
type Summary = { offer_count: number; average_per_km: number | null; average_per_hour: number | null; verdicts: { boa: number; regular: number; ruim: number } };
type Dashboard = { summary: Summary; journeys: Array<{ id: string }>; note?: string };

const cards = [
  { title: "Histórico", text: "Jornadas, ofertas, filtros e comparações estruturadas.", href: "/app/historico", icon: "history" as const },
  { title: "Pesquisa IA", text: "Pergunte sobre seus dados sincronizados usando a IA do Sr. Rotas.", href: "/app/ia", icon: "ai" as const },
  { title: "MCP", text: "Gere e revogue chaves para assistentes compatíveis, sempre em leitura.", href: "/app/mcp", icon: "mcp" as const },
  { title: "Plano", text: "Assinatura, créditos e preparação do trial oficial da 1.0.", href: "/app/plano", icon: "plan" as const },
];

function money(value: number | null | undefined) {
  return typeof value === "number" ? value.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "—";
}

export default function AppHome() {
  const [me, setMe] = useState<Me | null>(null);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);

  useEffect(() => {
    Promise.all([
      fetch("/api/v1/account/me", { cache: "no-store" }).then((r) => r.ok ? r.json() : null),
      fetch("/api/v1/analytics?days=7", { cache: "no-store" }).then((r) => r.ok ? r.json() : null),
    ]).then(([account, analytics]) => {
      setMe(account);
      setDashboard(analytics);
    }).catch(() => undefined);
  }, []);

  const summary = dashboard?.summary;

  return <>
    <section className="srPageHead">
      <div>
        <span className="srEyebrow">PAINEL WEB</span>
        <h1>{me?.display_name ? `Olá, ${me.display_name}.` : "Seu Sr. Rotas, também fora da rua."}</h1>
        <p>O motor de oferta continua no Android. Aqui você acompanha dados sincronizados, IA, MCP e sua conta sem depender de atualização do APK.</p>
      </div>
      <div className="srStatusCard"><span className="srStatusDot"/><div><strong>Sessão conectada</strong><small>{me?.email || "Dados privados liberados somente para sua conta."}</small></div></div>
    </section>

    <section className="srGrid4">
      {cards.map(card => <Link className="srFeature" href={card.href} key={card.href}>
        <span className="srFeatureIcon"><NavIcon name={card.icon}/></span><h2>{card.title}</h2><p>{card.text}</p><b>Abrir →</b>
      </Link>)}
    </section>

    <section className="srGrid2 srSectionGap">
      <article className="srPanel">
        <div className="srPanelHead"><div><span className="srEyebrow">ÚLTIMOS 7 DIAS</span><h2>Resumo das ofertas observadas</h2></div><Link href="/app/historico" className="srSecondary">Ver histórico</Link></div>
        <div className="srMetrics">
          <div><span>Ofertas</span><strong>{summary?.offer_count ?? "—"}</strong></div>
          <div><span>Boas</span><strong>{summary?.verdicts?.boa ?? "—"}</strong></div>
          <div><span>R$/km médio</span><strong>{money(summary?.average_per_km)}</strong></div>
          <div><span>R$/h médio</span><strong>{money(summary?.average_per_hour)}</strong></div>
        </div>
        {!summary?.offer_count ? <div className="srEmpty"><span>⌁</span><strong>Ainda não há ofertas neste período</strong><p>Quando o Android sincronizar ofertas válidas, os dados aparecem aqui automaticamente.</p></div> : null}
      </article>

      <article className="srPanel srDarkPanel">
        <span className="srEyebrow light">ARQUITETURA 1.0</span><h2>O app faz o trabalho pesado. O Web mostra o valor.</h2>
        <p>MediaProjection, OCR, parser e HUD continuam locais. O painel recebe somente dados estruturados já autorizados e sincronizados.</p>
        <ul><li><b>Native</b><span>OCR + HUD + jornada</span></li><li><b>Web</b><span>Histórico + IA + MCP + conta</span></li><li><b>Backend</b><span>Trial + acesso + Pix + créditos</span></li></ul>
      </article>
    </section>
  </>;
}
