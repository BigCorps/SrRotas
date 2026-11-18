"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import AccountPageHeader from "../_components/AccountPageHeader";
import IntelligenceNav from "../_components/IntelligenceNav";
import styles from "./ia-conversational.module.css";

type Metric = { key: string; label: string; formatted: string; semantic: string };
type Evidence = {
  semantic: string; source: string; sampleCount: number;
  confidence: string; dataQuality: string; completeness: string;
  coverage?: { value: number | null; label: string };
  period?: { label: string };
};
type Answer = {
  answer: string; intent: string; status: "ok" | "insufficient" | "not_ready";
  evidence: Evidence; metrics: Metric[];
  alternatives: string[]; limitations: string[]; context: unknown;
};
type Turn = { id: number; question: string; answer: Answer; feedback: "yes" | "no" | "" };
type VoiceState = "idle" | "listening" | "draft" | "error";

const suggestions = [
  "Como foi meu dia?",
  "Quais horários tiveram melhores ofertas?",
  "Qual região pagou melhor?",
  "Quantas corridas fiz?",
];
const semanticNames: Record<string, string> = {
  OBSERVED_OFFER: "Ofertas observadas",
  REALIZED_RIDE: "Corridas realizadas",
  REALIZED_REVENUE: "Faturamento realizado",
  ESTIMATED_COST: "Custo estimado",
  ACTUAL_COST: "Custo realizado",
  ACTUAL_DISTANCE: "Distância percorrida",
  REGIONAL_EXPOSURE: "Observações regionais",
  HISTORICAL_CONTINUITY: "Histórico de continuidade",
  MARKET_OBSERVATION: "Observações de mercado",
  MIXED: "Dados combinados",
};
const confidenceNames: Record<string, string> = {
  insufficient: "Sem dados suficientes", low: "Baixa", medium: "Moderada", high: "Alta",
};
function percentage(value: number | null | undefined) {
  return typeof value === "number" && Number.isFinite(value) ? `${Math.round(value * 100)}%` : "Não disponível";
}
function destination(intent: string, status: Answer["status"]) {
  if (status !== "ok") return null;
  if (["WAIT_OR_MOVE", "DESTINATION_CONTINUITY", "CURRENT_REGION_ADVICE"].includes(intent)) {
    return { href: "/app/agora", label: "Abrir Agora" };
  }
  if (["PERIOD_SUMMARY","REALIZED_EARNINGS","ACTUAL_DISTANCE","OPERATING_COST",
    "PROFITABILITY","BEST_JOURNEY","COMPARE_PERIODS","REGION_PERFORMANCE",
    "COMPARE_REGIONS","HOUR_PERFORMANCE","CATEGORY_PERFORMANCE",
    "OFFER_QUALITY","COMPLETED_RIDES","MARKET_QUALITY","MISSED_OPPORTUNITIES"].includes(intent)) {
    return { href: "/app/estatisticas", label: "Abrir Estatísticas" };
  }
  return null;
}

export default function PerguntePage() {
  const [question, setQuestion] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [pendingQuestion, setPendingQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [voiceState, setVoiceState] = useState<VoiceState>("idle");
  const [voiceNotice, setVoiceNotice] = useState("");
  const contextRef = useRef<unknown>(null);
  const requestRef = useRef(0);
  const answerIdRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);
  const recognitionRef = useRef<{ stop(): void } | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const hasConversation = turns.length > 0 || pendingQuestion !== "";

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [turns, pendingQuestion]);
  useEffect(() => () => {
    requestRef.current += 1;
    abortRef.current?.abort();
    recognitionRef.current?.stop();
    if (typeof window !== "undefined" && "speechSynthesis" in window) window.speechSynthesis.cancel();
  }, []);

  const ask = useCallback(async (text: string) => {
    const clean = text.trim();
    if (clean.length < 3 || clean.length > 800 || busy || pendingQuestion) return;
    const request = ++requestRef.current;
    const controller = new AbortController();
    abortRef.current = controller;
    setPendingQuestion(clean);
    setError("");
    setBusy(true);
    setVoiceState("idle");
    try {
      const response = await fetch("/api/v1/ask", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        cache: "no-store",
        body: JSON.stringify({ question: clean, context: contextRef.current }),
        signal: controller.signal,
      });
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) throw new Error("SESSION");
        if (response.status === 429) throw new Error("LIMIT");
        if (response.status >= 500) throw new Error("SERVER");
        throw new Error("QUERY");
      }
      const data = await response.json() as Answer;
      if (!data || typeof data.answer !== "string" || !data.evidence) throw new Error("QUERY");
      if (request !== requestRef.current) return;
      contextRef.current = data.context ?? null;
      setTurns(current => [...current, {
        id: ++answerIdRef.current, question: clean, answer: data, feedback: "",
      }]);
      setQuestion("");
    } catch (cause) {
      if (request !== requestRef.current || controller.signal.aborted) return;
      const type = cause instanceof Error ? cause.message : "";
      setError(type === "SESSION"
        ? "Sua sessão expirou ou este recurso não está disponível na conta. Entre novamente."
        : type === "LIMIT"
          ? "Muitas perguntas em sequência. Aguarde um pouco e tente novamente."
          : type === "SERVER"
            ? "A consulta está temporariamente indisponível. Tente novamente."
            : type === "QUERY"
              ? "Não foi possível analisar esta pergunta. Revise o texto e tente novamente."
              : "Não foi possível conectar. Confira a internet e tente novamente.");
      // Keep the text for a deliberate retry; never lose a typed question.
    } finally {
      if (request === requestRef.current) {
        abortRef.current = null;
        setBusy(false);
        setPendingQuestion("");
      }
    }
  }, [busy, pendingQuestion]);

  function newConversation() {
    requestRef.current += 1;
    abortRef.current?.abort();
    recognitionRef.current?.stop();
    if ("speechSynthesis" in window) window.speechSynthesis.cancel();
    contextRef.current = null;
    setQuestion("");
    setTurns([]);
    setBusy(false);
    setPendingQuestion("");
    setError("");
    setVoiceState("idle");
    setVoiceNotice("");
  }

  function dictate() {
    const w = window as Window & {
      SpeechRecognition?: new () => any;
      webkitSpeechRecognition?: new () => any;
    };
    const SpeechRecognition = w.SpeechRecognition ?? w.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setVoiceState("error");
      setVoiceNotice("O reconhecimento de voz não está disponível aqui. Digite sua pergunta.");
      return;
    }
    if (voiceState === "listening") {
      recognitionRef.current?.stop();
      setVoiceState("idle");
      setVoiceNotice("Gravação interrompida. Você pode digitar normalmente.");
      return;
    }
    try {
      const recognition = new SpeechRecognition();
      recognition.lang = "pt-BR";
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.onresult = (event: any) => {
        const recognized = String(event.results?.[0]?.[0]?.transcript ?? "").trim();
        setQuestion(recognized);
        setVoiceState("draft");
        setVoiceNotice(recognized
          ? "Texto reconhecido. Confira e toque em Enviar."
          : "Nenhum texto reconhecido. Tente novamente ou digite.");
      };
      recognition.onerror = (event: { error?: string }) => {
        setVoiceState("error");
        setVoiceNotice(event.error === "not-allowed"
          ? "Permissão do microfone negada. Autorize no navegador ou digite."
          : "Não foi possível reconhecer a voz. Tente novamente ou digite.");
      };
      recognition.onend = () => {
        recognitionRef.current = null;
        setVoiceState(state => state === "listening" ? "idle" : state);
      };
      recognitionRef.current = recognition;
      recognition.start();
      setVoiceState("listening");
      setVoiceNotice("Ouvindo... fale sua pergunta.");
    } catch {
      setVoiceState("error");
      setVoiceNotice("Não foi possível ativar o microfone. Você pode digitar.");
    }
  }

  function listen(text: string) {
    if (!("speechSynthesis" in window)) {
      setVoiceNotice("Leitura em voz alta não está disponível neste navegador.");
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "pt-BR";
    window.speechSynthesis.speak(utterance);
  }

  async function share(text: string) {
    const value = `Sr. Rotas\n${text}`;
    if (navigator.share) await navigator.share({ title: "Sr. Rotas", text: value }).catch(() => undefined);
    else await navigator.clipboard.writeText(value).catch(() => setError("Não foi possível copiar esta resposta."));
  }

  async function sendFeedback(turn: Turn, useful: boolean) {
    setTurns(previous => previous.map(item => item.id === turn.id
      ? { ...item, feedback: useful ? "yes" : "no" } : item));
    await fetch("/api/v1/ask/feedback", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ useful, intent: turn.answer.intent, semantic: turn.answer.evidence.semantic }),
    }).catch(() => undefined);
  }

  return (
    <div className="sr023Page">
      <AccountPageHeader title="Pergunte" subtitle="Seu consultor de desempenho, com base nos dados disponíveis." />
      <IntelligenceNav />
      <section className={styles.shell} aria-label="Conversa com o Sr. Rotas">
        <div className={styles.topline}>
          <span>IA do Sr. Rotas</span>
          {hasConversation ? <button type="button" className={styles.newConversation}
            onClick={newConversation}>Nova conversa</button> : null}
        </div>
        <div className={styles.thread} aria-live="polite" aria-relevant="additions">
          {!hasConversation ? (
            <div className={styles.welcome}>
              <div className={styles.mascot} role="img" aria-label="Cabeça do mascote Sr. Rotas">
                <img src="/icons/sr-ai-head-light.png" alt="" />
              </div>
              <h2>Como posso ajudar hoje?</h2>
              <p>Pergunte sobre suas corridas, ofertas e regiões. Respondo somente com os dados disponíveis.</p>
              <div className={styles.suggestions}>
                {suggestions.map(item => (
                  <button type="button" key={item} disabled={busy} onClick={() => void ask(item)}>{item}</button>
                ))}
              </div>
            </div>
          ) : (
            <div className={styles.turns}>
              {turns.map(turn => {
                const answer = turn.answer;
                const action = destination(answer.intent, answer.status);
                return (
                  <div className={styles.turn} key={turn.id}>
                    <div className={styles.userBubble}><span>Você</span><p>{turn.question}</p></div>
                    <div className={styles.assistantRow}>
                      <div className={styles.smallMascot} aria-hidden="true">
                        <img src="/icons/sr-ai-head-light.png" alt="" />
                      </div>
                      <article className={styles.answerBubble}>
                        <strong>Sr. Rotas</strong>
                        <p className={styles.conclusion}>{answer.answer}</p>
                        {answer.status !== "ok" && (
                          <p className={styles.insufficient}>Dados insuficientes para uma conclusão segura neste recorte.</p>
                        )}
                        <p className={styles.evidenceSummary}>
                          {answer.evidence.period?.label ?? "Período consultado"} ·
                          {" "}{answer.evidence.sampleCount.toLocaleString("pt-BR")} registros ·
                          {" "}{semanticNames[answer.evidence.semantic] ?? "Dados analisados"}
                        </p>
                        <details className={styles.details}>
                          <summary>Entenda o resultado</summary>
                          <div className={styles.detailBody}>
                            <p><b>Origem:</b> {answer.evidence.source}</p>
                            <p><b>Confiança:</b> {confidenceNames[answer.evidence.confidence] ?? "Não disponível"}</p>
                            <p><b>Cobertura:</b> {percentage(answer.evidence.coverage?.value)}
                              {answer.evidence.coverage?.label ? ` · ${answer.evidence.coverage.label}` : ""}
                            </p>
                            <p><b>Dados:</b> {answer.evidence.completeness === "complete"
                              ? "Cobertura completa do recorte" : "Cobertura parcial do recorte"}</p>
                            {answer.metrics?.length ? (
                              <ul className={styles.metrics}>{answer.metrics.map(item => (
                                <li key={item.key}>
                                  <span>{item.label} · {semanticNames[item.semantic] ?? "Dado analisado"}</span>
                                  <strong>{item.formatted}</strong>
                                </li>
                              ))}</ul>
                            ) : null}
                            {answer.limitations?.length ? (
                              <div className={styles.limitations}>
                                {answer.limitations.map((item, i) => <p key={i}>{item}</p>)}
                              </div>
                            ) : null}
                          </div>
                        </details>
                        {action ? <Link className={styles.actionLink} href={action.href}>{action.label}</Link> : null}
                        {answer.status !== "ok" && answer.alternatives?.length ? (
                          <div className={styles.alternatives}>{answer.alternatives.slice(0,2).map((item,i) =>
                            <p key={i}>{item}</p>)}</div>
                        ) : null}
                        <div className={styles.answerTools}>
                          <button type="button" onClick={() => listen(answer.answer)}>Ouvir</button>
                          <button type="button" onClick={() =>
                            navigator.clipboard.writeText(answer.answer)
                              .catch(() => setError("Não foi possível copiar esta resposta."))}>Copiar</button>
                          <button type="button" onClick={() => void share(answer.answer)}>Compartilhar</button>
                          <button type="button" aria-pressed={turn.feedback === "yes"}
                            onClick={() => void sendFeedback(turn,true)}>
                            {turn.feedback === "yes" ? "Útil ✓" : "Útil"}
                          </button>
                          <button type="button" aria-pressed={turn.feedback === "no"}
                            onClick={() => void sendFeedback(turn,false)}>
                            {turn.feedback === "no" ? "Não útil ✓" : "Não útil"}
                          </button>
                        </div>
                      </article>
                    </div>
                  </div>
                );
              })}
              {pendingQuestion ? (
                <div className={styles.turn}>
                  <div className={styles.userBubble}><span>Você</span><p>{pendingQuestion}</p></div>
                  <div className={styles.waiting} role="status">Analisando seus dados…</div>
                </div>
              ) : null}
            </div>
          )}
          <div ref={bottomRef} />
        </div>
        <div className={styles.footer}>
          {voiceNotice ? <p className={styles.voiceNotice} role="status">{voiceNotice}</p> : null}
          {error ? <p className={styles.error} role="alert">{error}</p> : null}
          <div className={styles.composer}>
            <button type="button" className={styles.microphone} onClick={dictate}
              disabled={busy} aria-label={voiceState === "listening" ? "Parar ditado" : "Ditar pergunta"}
              aria-pressed={voiceState === "listening"} title="Ditar pergunta">🎙</button>
            <textarea aria-label="Sua pergunta" rows={1} value={question} maxLength={800}
              onChange={event => {
                setQuestion(event.target.value);
                if (voiceState === "draft") setVoiceState("idle");
              }}
              onKeyDown={event => {
                if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
                  event.preventDefault(); void ask(question);
                }
              }}
              placeholder={hasConversation ? "Pergunte algo relacionado, como “E ontem?”" : "Pergunte ao Sr. Rotas…"} />
            <button type="button" className={styles.send} disabled={busy || question.trim().length < 3}
              onClick={() => void ask(question)}>{busy ? "…" : "Enviar"}</button>
          </div>
          <p className={styles.composerHint}>Use Ctrl + Enter para enviar. Histórico disponível somente nesta conversa.</p>
        </div>
      </section>
    </div>
  );
}
