"use client";

import { useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";
import IntelligenceNav from "../_components/IntelligenceNav";

type Metric = { key: string; label: string; formatted: string; semantic: string };
type Answer = {
  answer: string;
  intent: string;
  status: string;
  evidence: {
    semantic: string;
    source: string;
    sampleCount: number;
    confidence: string;
    dataQuality: string;
    completeness: string;
    coverage: { value: number | null; label: string };
    period: { label: string };
  };
  metrics: Metric[];
  alternatives: string[];
  limitations: string[];
  context: unknown;
};

const suggestions = [
  "Quais horários tiveram as melhores ofertas?",
  "Qual região pagou melhor nos últimos 30 dias?",
  "Quanto faturei e qual a cobertura da conciliação?",
  "Vale esperar ou me deslocar depois do destino?",
];

function pct(value: number | null | undefined) {
  return typeof value === "number" ? `${Math.round(value * 100)}%` : "—";
}

export default function PerguntePage() {
  const [question, setQuestion] = useState("");
  const [context, setContext] = useState<unknown>(null);
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState<"yes" | "no" | "">("");

  async function ask(text = question) {
    const clean = text.trim();
    if (clean.length < 3 || busy) return;
    setBusy(true);
    setError("");
    setFeedback("");
    try {
      const response = await fetch("/api/v1/ask", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: clean, context }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body?.error || "ask_failed");
      const next = body as Answer;
      setAnswer(next);
      setContext(next.context ?? null);
      setQuestion("");
    } catch {
      setError("Não foi possível consultar o Sr. Rotas agora.");
    } finally {
      setBusy(false);
    }
  }

  function dictate() {
    const w = window as any;
    const Recognition = w.SpeechRecognition || w.webkitSpeechRecognition;
    if (!Recognition) {
      setError("O ditado por voz não está disponível neste navegador.");
      return;
    }
    const recognition = new Recognition();
    recognition.lang = "pt-BR";
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;
    recognition.onresult = (event: any) => setQuestion(String(event.results?.[0]?.[0]?.transcript || ""));
    recognition.onerror = () => setError("Não foi possível reconhecer a voz.");
    recognition.start();
  }

  function listen() {
    if (!answer?.answer || !("speechSynthesis" in window)) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(answer.answer);
    utterance.lang = "pt-BR";
    window.speechSynthesis.speak(utterance);
  }

  async function share() {
    if (!answer?.answer) return;
    const text = `Sr. Rotas\n${answer.answer}`;
    if (navigator.share) await navigator.share({ title: "Sr. Rotas", text }).catch(() => undefined);
    else await navigator.clipboard.writeText(text).catch(() => undefined);
  }

  async function sendFeedback(useful: boolean) {
    if (!answer) return;
    setFeedback(useful ? "yes" : "no");
    await fetch("/api/v1/ask/feedback", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ useful, intent: answer.intent, semantic: answer.evidence.semantic }),
    }).catch(() => undefined);
  }

  return (
    <div className="sr023Page">
      <AccountPageHeader
        title="Pergunte"
        subtitle="Perguntas em linguagem natural sobre seus próprios dados. O motor é determinístico, usa a mesma Fundação Premium das Estatísticas e não consome uma API externa por pergunta."
      />
      <IntelligenceNav />

      <section className="srG3AskShell">
        {!answer ? (
          <div className="srG3AskIntro">
            <img src="/icons/icon-512.png" alt="Sr. Rotas" />
            <h2>Como posso ajudar?</h2>
            <p>Escolha uma sugestão ou pergunte do seu jeito. Respostas realizadas exibem cobertura e não transformam oferta observada em faturamento.</p>
            <div className="srG3Suggestions">
              {suggestions.map((item) => <button key={item} onClick={() => { setQuestion(item); void ask(item); }}>{item}</button>)}
            </div>
          </div>
        ) : (
          <article className="srG3Answer">
            <span className="srEyebrow">{answer.intent.replaceAll("_", " ")}</span>
            <h3>{answer.evidence.period.label}</h3>
            <p>{answer.answer}</p>
            <div className="srG3Evidence">
              <span>Amostra: {answer.evidence.sampleCount.toLocaleString("pt-BR")}</span>
              <span>Confiança: {answer.evidence.confidence}</span>
              <span>Cobertura: {pct(answer.evidence.coverage?.value)}</span>
              <span>{answer.evidence.completeness === "complete" ? "Completo" : "Parcial"}</span>
              <span>{answer.evidence.semantic.replaceAll("_", " ")}</span>
            </div>
            {answer.metrics?.length ? (
              <div className="srG3MetricGrid">
                {answer.metrics.map((item) => <div className="srG3Metric" key={item.key}><small>{item.label}</small><strong>{item.formatted}</strong><small>{item.semantic.replaceAll("_", " ")}</small></div>)}
              </div>
            ) : null}
            {answer.alternatives?.length ? <ul className="srG3Alternatives">{answer.alternatives.slice(0,3).map((item) => <li key={item}>{item}</li>)}</ul> : null}
            {answer.limitations?.map((item) => <div className="srG3Limit" key={item}>{item}</div>)}
            <div className="srG3AnswerTools">
              <button onClick={listen}>Ouvir</button>
              <button onClick={() => navigator.clipboard.writeText(answer.answer).catch(() => undefined)}>Copiar</button>
              <button onClick={() => void share()}>Compartilhar</button>
              <button onClick={() => void sendFeedback(true)}>{feedback === "yes" ? "Útil ✓" : "Útil"}</button>
              <button onClick={() => void sendFeedback(false)}>{feedback === "no" ? "Não útil ✓" : "Não útil"}</button>
            </div>
          </article>
        )}

        {error ? <div className="srInlineError srSectionGap">{error}</div> : null}
        <div className="srG3Composer">
          <button className="secondary" type="button" onClick={dictate} aria-label="Ditar pergunta">🎙</button>
          <textarea value={question} onChange={(e: any) => setQuestion(e.target.value)} placeholder={context ? "Continue: ‘E ontem?’, ‘E por hora?’…" : "Digite sua pergunta…"} />
          <button type="button" disabled={busy || question.trim().length < 3} onClick={() => void ask()}>{busy ? "…" : "Enviar"}</button>
        </div>
      </section>
    </div>
  );
}
