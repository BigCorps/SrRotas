"use client";

import { useState } from "react";
import styles from "./statistics.module.css";
import {
  historicalTimeStatus, isValidCandidateChoice, pendingImportedRides,
  readableDate, readableFare,
  type DraftChoice, type ReviewDetail, type ReviewListing,
} from "./reconciliation-review-model";

/**
 * Safely prepares the human matching workflow. Read-only GET requests exclusively.
 * Previewing a choice never invokes POST or stores an approval anywhere.
 */
export default function ReconciliationReview() {
  const [listing, setListing] = useState<ReviewListing | null>(null);
  const [detail, setDetail] = useState<ReviewDetail | null>(null);
  const [draft, setDraft] = useState<DraftChoice>(null);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState("");
  const [detailError, setDetailError] = useState("");

  async function requestJson(url: string) {
    const response = await fetch(url, { cache: "no-store" });
    if (response.status === 401) throw new Error("Sessão encerrada. Entre novamente para revisar suas corridas.");
    if (response.status === 402 || response.status === 403) throw new Error("Seu plano não possui acesso à conciliação.");
    if (!response.ok) throw new Error("Não foi possível carregar esta revisão agora.");
    return response.json() as Promise<unknown>;
  }

  async function loadPending() {
    if (loadingList) return;
    setLoadingList(true);
    setListing(null);
    setDetail(null);
    setDraft(null);
    setError("");
    setDetailError("");
    try {
      const response = await requestJson("/api/v1/reconciliation?limit=10") as ReviewListing;
      if (!Array.isArray(response?.items)) throw new Error("O servidor não retornou uma lista válida.");
      setListing(response);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível carregar as pendências.");
    } finally {
      setLoadingList(false);
    }
  }

  async function loadDetail(importId: string) {
    if (loadingDetail) return;
    setLoadingDetail(true);
    setDetail(null);
    setDraft(null);
    setDetailError("");
    try {
      const response = await requestJson("/api/v1/reconciliation?import_id=" + encodeURIComponent(importId)) as ReviewDetail;
      if (!response?.import || !Array.isArray(response.candidates)) throw new Error("Dados da corrida incompletos.");
      // A refreshed decision must never be presented as still awaiting approval.
      if (response.decision || response.import.decision || response.import.ride_status !== "completed") {
        throw new Error("Esta corrida não está disponível para revisão. Recarregue as pendências.");
      }
      setDetail(response);
    } catch (cause) {
      setDetailError(cause instanceof Error ? cause.message : "Não foi possível comparar as ofertas.");
    } finally {
      setLoadingDetail(false);
    }
  }

  const items = pendingImportedRides(listing);
  return (
    <section className={styles.comparison} aria-label="Prévia de revisão manual do histórico Uber">
      <div className={styles.comparisonHeader}>
        <div>
          <h2 className={styles.dashboardTitle}>Conferir correspondências manualmente</h2>
          <p className={styles.muted}>Compare cada corrida importada com ofertas candidatas. Nesta prévia, suas escolhas não são gravadas.</p>
        </div>
        <button className={styles.pill} type="button" disabled={loadingList || loadingDetail} onClick={loadPending}>
          {loadingList ? "Carregando…" : listing ? "Atualizar pendências" : "Carregar pendências"}
        </button>
      </div>
      <p className={styles.note}>
        Modo de preparação, somente leitura. Somente sugestões Uber dentro da janela de ±75 minutos, com horários válidos, aparecem para simular a escolha. A confirmação real permanece bloqueada.
        O valor importado é informação do histórico, não comprovante de recebimento bancário.
      </p>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      {listing && !loadingList ? (
        <div className={styles.reviewStack}>
          <p className={styles.muted}>Pendentes no histórico da conta: {listing.unresolved.toLocaleString("pt-BR")}. Exibindo até dez registros para conferência.</p>
          {items.length ? items.map((item) => (
            <div className={styles.reviewItem} key={item.id}>
              <div>
                <strong>{readableDate(item.occurred_at ?? item.captured_at)} · {item.service_type || "Categoria não identificada"}</strong>
                <p className={styles.muted}>Valor do histórico: {readableFare(item.fare)} · Origem: {item.pickup_label || "Não informada"} · Destino: {item.destination_label || "Não informado"}</p>
                {historicalTimeStatus(item.occurred_at, Date.now()) ? (
                  <p className={styles.reviewWarning}>{historicalTimeStatus(item.occurred_at, Date.now())}</p>
                ) : null}
              </div>
              <button type="button" className={styles.pill} disabled={loadingDetail} onClick={() => loadDetail(item.id)}>
                Comparar candidatas
              </button>
            </div>
          )) : (
            <p className={styles.empty}>{listing.unresolved === 0 ? "Não há revisões pendentes." : "Nenhuma pendência encontrada nos primeiros registros consultados. Atualize ou revise uma janela menor no histórico."}</p>
          )}
        </div>
      ) : null}
      {loadingDetail ? <p className={styles.empty} role="status">Buscando sugestões para a corrida selecionada…</p> : null}
      {detailError ? <p className={styles.error} role="alert">{detailError}</p> : null}
      {detail ? (
        <div className={styles.reviewStack}>
          <h3 className={styles.dashboardTitle}>Comparação — {readableDate(detail.import.occurred_at ?? detail.import.captured_at)}</h3>
          <p className={styles.muted}>
            Corrida no histórico: {readableFare(detail.import.fare)} · {detail.import.pickup_label || "Origem não informada"} → {detail.import.destination_label || "Destino não informado"}
          </p>
          {historicalTimeStatus(detail.import.occurred_at, Date.now()) ? (
            <p className={styles.reviewWarning}>{historicalTimeStatus(detail.import.occurred_at, Date.now())}. Candidatos podem não existir ou ser pouco confiáveis.</p>
          ) : null}
          {detail.excluded_candidates ? <p className={styles.reviewWarning}>{detail.excluded_candidates} sugestão(ões) excluída(s): fora dos critérios ou já revisada(s).</p> : null}
          {detail.candidates.length === 0 ? (
            <p className={styles.empty}>Nenhuma oferta Uber elegível encontrada. Verifique o horário da importação; a confirmação não pode ser simulada sem correspondência verificável.</p>
          ) : detail.candidates.map((candidate) => (
            <div className={styles.reviewItem} key={String(candidate.id)}>
              <div>
                <strong>{readableFare(candidate.fare)} · Oferta de {readableDate(candidate.observed_at)}</strong>
                <p className={styles.muted}>{candidate.service_type || "Categoria não identificada"} · {candidate.pickup_label || "Origem não informada"} → {candidate.destination_label || "Destino não informado"}</p>
                <p className={styles.muted}>Similaridade indicativa: {Number.isFinite(candidate.score) ? candidate.score.toLocaleString("pt-BR") + "%" : "—"} — não é confirmação.</p>
                {candidate.reasons?.length ? <ul className={styles.reviewReasons}>{candidate.reasons.map((reason, i) => <li key={i}>{reason}</li>)}</ul> : null}
              </div>
              <button className={styles.pill} type="button"
                aria-pressed={draft?.kind === "match" && draft.offerId === String(candidate.id)}
                onClick={() => {
                  if (isValidCandidateChoice(detail, String(candidate.id))) setDraft({ kind: "match", offerId: String(candidate.id) });
                }}>
                Simular escolha
              </button>
            </div>
          ))}
          <div className={styles.reviewActions}>
            <button className={styles.pill} type="button" aria-pressed={draft?.kind === "no_match"}
              onClick={() => setDraft({ kind: "no_match" })}>Simular sem correspondência</button>
            {draft ? <button className={styles.pill} type="button" onClick={() => setDraft(null)}>Limpar escolha</button> : null}
          </div>
          {draft ? (
            <p className={styles.note} role="status">
              {draft.kind === "match" ? "Você selecionou uma oferta candidata apenas nesta prévia." : "Você indicou ausência de correspondência apenas nesta prévia."}
              {" "}Nenhuma decisão foi enviada ao servidor; a escolha é descartada ao sair.
            </p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
