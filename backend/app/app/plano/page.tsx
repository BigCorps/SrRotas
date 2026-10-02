"use client";

import { useEffect, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";

type Billing = {
  plan: { name: string; amount_cents: number };
  subscription: null | {
    status: string;
    active: boolean;
    current_period_end: string | null;
  };
  trial: {
    trial_status: string;
    days_remaining: number | null;
    trial_ends_at: string | null;
  };
};

export default function PlanoPage() {
  const [billing, setBilling] = useState<Billing | null>(null);

  useEffect(() => {
    fetch("/api/v1/billing/status", { cache: "no-store" })
      .then((response) => (response.ok ? response.json() : null))
      .then(setBilling)
      .catch(() => undefined);
  }, []);

  const trialActive = billing?.trial?.trial_status === "active";

  return (
    <div className="sr023Page srAccountSubpage">
      <AccountPageHeader
        title="Planos"
        subtitle="O Copiloto é gratuito. A assinatura libera a camada de Inteligência."
      />

      <section className="srPlanHero srAccountPlanHero">
        <div>
          <span className="srEyebrow light">SR. ROTAS COPILOTO</span>
          <h2>Grátis para usar no dia a dia</h2>
          <p>
            Leitura de ofertas, HUD, R$/km, R$/min, R$/h, custos, análise
            financeira básica e histórico pessoal continuam disponíveis sem
            assinatura.
          </p>
          <div className="srTrialFlow">
            <span>Conta criada</span>
            <b>→</b>
            <span>Primeira oferta válida</span>
            <b>→</b>
            <span>7 dias de Inteligência</span>
          </div>
        </div>

        <div className="srPriceCard">
          <span>
            {billing?.subscription?.active
              ? "INTELIGÊNCIA ATIVA"
              : trialActive
                ? "TRIAL DE INTELIGÊNCIA"
                : "SR. ROTAS INTELIGÊNCIA"}
          </span>
          <strong>R$ 9,90</strong>
          <small>por 30 dias</small>
          <ul>
            <li>Estatísticas Premium</li>
            <li>Pergunte ao Sr. Rotas</li>
            <li>Inteligência regional, temporal e de destino</li>
            <li>MCP somente leitura</li>
          </ul>
          {billing?.subscription?.active ? (
            <p className="srPlanStatus">
              Ativo até{" "}
              <b>
                {billing.subscription.current_period_end
                  ? new Date(
                      billing.subscription.current_period_end,
                    ).toLocaleDateString("pt-BR")
                  : "—"}
              </b>
            </p>
          ) : (
            <a href="/conta">Ativar Inteligência via Pix</a>
          )}
        </div>
      </section>

      <section className="srGrid3 srSectionGap">
        <article className="srMiniPanel">
          <span>01</span>
          <strong>Copiloto não expira</strong>
          <p>
            Fim do trial ou da assinatura não bloqueia leitura de ofertas, HUD
            nem seu histórico básico.
          </p>
        </article>
        <article className="srMiniPanel">
          <span>02</span>
          <strong>7 dias de Inteligência</strong>
          <p>
            O período de teste começa somente na primeira oferta válida. Não
            existe cobrança automática.
          </p>
        </article>
        <article className="srMiniPanel">
          <span>03</span>
          <strong>Sem créditos por pergunta</strong>
          <p>
            O Pergunte é determinístico e não consome API externa ou carteira
            de créditos a cada consulta.
          </p>
        </article>
      </section>
    </div>
  );
}
