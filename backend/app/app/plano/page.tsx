"use client";

import { useEffect, useState } from "react";

type Billing = { plan: { name: string; amount_cents: number }; subscription: null | { status: string; active: boolean; current_period_end: string | null }; wallet: { balance: number; lifetime_granted: number; lifetime_spent: number } };

export default function PlanoPage() {
  const [billing, setBilling] = useState<Billing | null>(null);

  useEffect(() => {
    fetch("/api/v1/billing/status", { cache: "no-store" }).then(r => r.ok ? r.json() : null).then(setBilling).catch(() => undefined);
  }, []);

  return <>
    <section className="srPageHead compact"><div><span className="srEyebrow">PLANO</span><h1>Use primeiro. Assine quando fizer sentido.</h1><p>O trial comercial da 1.0 começará somente na primeira oferta válida — não no cadastro nem na instalação.</p></div></section>
    <section className="srPlanHero">
      <div><span className="srEyebrow light">TRIAL OFICIAL 1.0</span><h2>7 dias completos</h2><p>OCR, HUD, histórico, analytics e MCP liberados. A IA própria terá 5 créditos temporários para você experimentar.</p><div className="srTrialFlow"><span>Conta criada</span><b>→</b><span>Primeira oferta válida</span><b>→</b><span>7 dias começam</span></div></div>
      <div className="srPriceCard"><span>{billing?.subscription?.active ? "ASSINATURA ATIVA" : "PLANO SR. ROTAS"}</span><strong>R$ 9,90</strong><small>por 30 dias</small><ul><li>OCR + HUD</li><li>Histórico + analytics</li><li>MCP somente leitura</li><li>20 créditos na 1ª ativação paga</li></ul>{billing?.subscription?.active ? <p className="srPlanStatus">Ativo até <b>{billing.subscription.current_period_end ? new Date(billing.subscription.current_period_end).toLocaleDateString("pt-BR") : "—"}</b></p> : <a href="/conta">Abrir conta e cobrança</a>}</div>
    </section>
    <section className="srGrid3 srSectionGap"><article className="srMiniPanel"><span>01</span><strong>Créditos atuais: {billing?.wallet?.balance ?? "—"}</strong><p>A IA própria usa créditos; MCP e analytics não usam.</p></article><article className="srMiniPanel"><span>02</span><strong>Seu histórico fica</strong><p>Ao expirar, os dados continuam disponíveis em leitura.</p></article><article className="srMiniPanel"><span>03</span><strong>Trial entra depois do beta</strong><p>A interface está pronta, mas o bloqueio comercial ainda não é ativado durante os testes atuais.</p></article></section>
  </>;
}
