"use client";

import { useEffect, useState } from "react";
import "./conta.css";

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
  pending_payment: any;
};

type Charge = {
  payment_id: string;
  amount_cents: number;
  status: string;
  txid: string;
  br_code: string;
  qr_code_image: string | null;
  expires_at: string | null;
  reused: boolean;
};

export default function ContaPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [billing, setBilling] = useState<Billing | null>(null);
  const [charge, setCharge] = useState<Charge | null>(null);
  const [message, setMessage] = useState(
    "Entre com a mesma conta usada no Sr. Rotas.",
  );
  const [busy, setBusy] = useState(false);

  async function loadStatus(silent = false) {
    const response = await fetch("/api/v1/billing/status", {
      cache: "no-store",
    });
    if (response.status === 401) {
      setBilling(null);
      if (!silent) setMessage("Entre com a mesma conta usada no aplicativo.");
      return false;
    }
    const data = await response.json();
    if (!response.ok) throw new Error(data?.error || "status_failed");
    setBilling(data);
    if (data.subscription?.active) {
      setCharge(null);
      setMessage("Pagamento confirmado. Sr. Rotas Inteligência ativo.");
    } else if (data.pending_payment?.pix_copy_paste && !charge) {
      setCharge({
        payment_id: data.pending_payment.id,
        amount_cents: data.pending_payment.amount_cents,
        status: data.pending_payment.status,
        txid: data.pending_payment.txid,
        br_code: data.pending_payment.pix_copy_paste,
        qr_code_image: data.pending_payment.pix_qrcode,
        expires_at: data.pending_payment.expires_at,
        reused: true,
      });
    }
    return true;
  }

  useEffect(() => {
    loadStatus(true).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!charge || billing?.subscription?.active) return;
    const id = window.setInterval(
      () => loadStatus(true).catch(() => undefined),
      5000,
    );
    return () => window.clearInterval(id);
  }, [charge, billing?.subscription?.active]);

  async function login(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage("Entrando...");
    try {
      const response = await fetch("/api/v1/billing/web-session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data?.message || data?.error || "login_failed");
      setPassword("");
      await loadStatus(true);
      setMessage(`Olá, ${data.display_name}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível entrar.");
    } finally {
      setBusy(false);
    }
  }

  async function checkout() {
    setBusy(true);
    setMessage("Gerando Pix da Inteligência no Banco Inter...");
    try {
      const response = await fetch("/api/v1/billing/checkout", { method: "POST" });
      const data = await response.json();
      if (!response.ok) throw new Error(data?.error || "checkout_failed");
      setCharge(data);
      setMessage(
        data.reused
          ? "Pix pendente recuperado."
          : "Pix criado. A confirmação será consultada automaticamente no Banco Inter.",
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível gerar o Pix.");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    await fetch("/api/v1/billing/web-logout", { method: "POST" }).catch(() => undefined);
    setBilling(null);
    setCharge(null);
    setMessage("Sessão encerrada.");
  }

  async function copyPix() {
    if (!charge?.br_code) return;
    await navigator.clipboard.writeText(charge.br_code);
    setMessage("Código Pix copiado.");
  }

  return (
    <main className="accountPage">
      <header className="accountHeader">
        <a href="/" className="accountBrand">
          <img src="/logo-srrotas.png" alt="" />
          <span>Sr. Rotas</span>
        </a>
        <span>Conta e Inteligência</span>
      </header>

      <section className="accountShell">
        <div className="accountIntro">
          <span className="eyebrow">COPILOTO GRÁTIS + INTELIGÊNCIA</span>
          <h1>
            Use o Copiloto grátis. Ative a Inteligência por{" "}
            <em>R$ 9,90/30 dias</em>.
          </h1>
          <p>
            O uso básico do Sr. Rotas não depende de assinatura. O pagamento
            libera Estatísticas Premium, Pergunte, inteligência regional e MCP.
          </p>
        </div>

        {!billing ? (
          <form className="accountCard loginCard" onSubmit={login}>
            <h2>Entrar</h2>
            <p>Use o e-mail e a senha da sua conta Sr. Rotas.</p>
            <label>
              E-mail
              <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" required />
            </label>
            <label>
              Senha
              <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete="current-password" required />
            </label>
            <button disabled={busy}>{busy ? "Aguarde..." : "Entrar"}</button>
          </form>
        ) : (
          <div className="accountGrid">
            <article className="accountCard creditCard">
              <span className="eyebrow">SR. ROTAS COPILOTO</span>
              <h2>Grátis</h2>
              <p>Continua disponível mesmo sem assinatura.</p>
              <ul>
                <li>Leitura de ofertas + HUD</li>
                <li>R$/km, R$/min e R$/h</li>
                <li>Custos e análise financeira básica</li>
                <li>Histórico pessoal</li>
              </ul>
            </article>

            <article className="accountCard planCard">
              <span className={billing.subscription?.active ? "status active" : "status"}>
                {billing.subscription?.active
                  ? "INTELIGÊNCIA ATIVA"
                  : billing.trial?.trial_status === "active"
                    ? "TRIAL ATIVO"
                    : "INTELIGÊNCIA"}
              </span>
              <h2>Sr. Rotas Inteligência</h2>
              <div className="price">
                <strong>R$ 9,90</strong>
                <span>/ 30 dias</span>
              </div>
              {billing.subscription?.active ? (
                <p>
                  Válido até{" "}
                  <strong>{new Date(billing.subscription.current_period_end || "").toLocaleDateString("pt-BR")}</strong>.
                </p>
              ) : (
                <>
                  <ul>
                    <li>Estatísticas Premium</li>
                    <li>Pergunte ao Sr. Rotas</li>
                    <li>Inteligência regional, temporal e de destino</li>
                    <li>MCP somente leitura</li>
                  </ul>
                  <button onClick={checkout} disabled={busy}>
                    {busy ? "Gerando..." : "Ativar via Pix Banco Inter"}
                  </button>
                </>
              )}
            </article>
          </div>
        )}

        {charge && !billing?.subscription?.active ? (
          <article className="accountCard pixCard">
            <span className="status pending">AGUARDANDO PIX</span>
            <h2>Finalize a ativação da Inteligência</h2>
            <div className="pixMerchant" aria-label="Identificação do recebedor do Pix">
              <strong>Intermediações de Pagamentos BigCorps</strong>
              <span>Sr.Rotas | Desenvolvido por BigCorps</span>
            </div>
            {charge.qr_code_image ? (
              <img className="qr" src={charge.qr_code_image} alt="QR Code Pix Banco Inter" />
            ) : null}
            <button onClick={copyPix}>Copiar código Pix</button>
            <p>
              A página consulta automaticamente a mesma cobrança no Banco Inter
              a cada poucos segundos.
            </p>
          </article>
        ) : null}

        <div className="message">{message}</div>
        {billing ? (
          <button className="logout" onClick={logout}>Sair desta conta</button>
        ) : null}
        <p className="legalNote">
          O Pix ativa 30 dias de Sr. Rotas Inteligência. A cobrança é intermediada por
          Intermediações de Pagamentos BigCorps. Não há cobrança automática. O Copiloto
          gratuito continua funcionando após o fim do trial ou da assinatura.
        </p>
      </section>
    </main>
  );
}
