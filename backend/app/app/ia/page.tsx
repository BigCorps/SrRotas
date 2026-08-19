"use client";

import { useEffect, useState } from "react";

const suggestions = ["Quais horários tiveram melhor R$/km?", "Compare minhas últimas 5 jornadas", "Qual serviço apareceu mais esta semana?", "Resuma meu padrão de ofertas boas"];
type Billing = { wallet?: { balance?: number } };
type Answer = { answer: string; offer_count: number; model: string };

export default function IaPage() {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [credits, setCredits] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    fetch("/api/v1/billing/status", { cache: "no-store" }).then(r => r.ok ? r.json() : null).then((data: Billing | null) => setCredits(data?.wallet?.balance ?? null)).catch(() => undefined);
  }, []);

  async function ask() {
    const q = question.trim();
    if (q.length < 3) return;
    setBusy(true); setMessage("Analisando seus dados...");
    try {
      const response = await fetch("/api/v1/ask", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ question: q, days: 30 }) });
      const data = await response.json();
      if (!response.ok) {
        if (response.status === 402) throw new Error("Sua conta está sem acesso/créditos para usar a IA própria.");
        throw new Error(data?.error || "Não foi possível consultar a IA.");
      }
      setAnswer(data); setMessage("");
      fetch("/api/v1/billing/status", { cache: "no-store" }).then(r => r.ok ? r.json() : null).then((billing: Billing | null) => setCredits(billing?.wallet?.balance ?? null)).catch(() => undefined);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível consultar a IA.");
    } finally { setBusy(false); }
  }

  return <>
    <section className="srPageHead compact"><div><span className="srEyebrow">PESQUISA IA</span><h1>Pergunte sobre o seu próprio histórico.</h1><p>A IA usa contexto estruturado. Média, soma e filtros simples continuam sendo resolvidos sem modelo.</p></div><div className="srCreditChip"><span>✦</span><div><small>Créditos disponíveis</small><strong>{credits ?? "—"}</strong></div></div></section>

    <section className="srAiShell">
      {!answer ? <div className="srAiWelcome"><img src="/logo-srrotas.png" alt=""/><h2>O que você quer entender sobre suas jornadas?</h2><p>Agora o painel Web já consegue usar a mesma conta e consultar os dados sincronizados.</p><div className="srSuggestions">{suggestions.map(s => <button key={s} onClick={() => setQuestion(s)}>{s}</button>)}</div></div> : <div className="srAiAnswer"><span className="srEyebrow">RESPOSTA</span><p>{answer.answer}</p><small>{answer.offer_count} ofertas observadas consideradas · {answer.model}</small><button onClick={() => setAnswer(null)}>Nova pergunta</button></div>}

      <div className="srComposer"><textarea aria-label="Pergunta" placeholder="Ex.: compare meus melhores horários..." value={question} onChange={e => setQuestion(e.target.value)} disabled={busy}/><button onClick={ask} disabled={busy || question.trim().length < 3}>{busy ? "..." : "Enviar"}</button></div>
      <small className="srComposerNote">{message || "1 pergunta concluída da IA própria = 1 crédito. MCP não consome crédito Sr. Rotas."}</small>
    </section>
  </>;
}
