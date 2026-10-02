"use client";

import { useEffect, useMemo, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";
import IntelligenceNav from "../_components/IntelligenceNav";

type Decision = { decision: "confirmed" | "no_match"; selected_ride_offer_id: string | number | null; candidate_score: number | null; decided_at: string };
type ImportItem = {
  id: string;
  captured_at: string;
  occurred_at: string | null;
  fare: number;
  service_type: string;
  pickup_label: string | null;
  destination_label: string | null;
  duration_seconds: number | null;
  distance_km: number | null;
  matched_ride_offer_id: string | number | null;
  decision: Decision | null;
};
type Summary = {
  status: "CONCILIADA" | "PARCIAL";
  total_completed_imports: number;
  reviewed: number;
  confirmed: number;
  no_match: number;
  unresolved: number;
  coverage_pct: number;
  items: ImportItem[];
};
type Candidate = {
  id: string | number;
  journey_id: string | null;
  local_offer_id: string | null;
  observed_at: string;
  fare: number;
  service_type: string | null;
  pickup_label: string | null;
  destination_label: string | null;
  per_km: number | null;
  per_hour: number | null;
  score: number;
  confidence: string;
  reasons: string[];
};
type Detail = { import: ImportItem; decision: Decision | null; candidates: Candidate[]; note: string };

function money(value: unknown) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" }) : "—";
}
function number(value: unknown, suffix = "") {
  const n = Number(value);
  return Number.isFinite(n) ? `${n.toLocaleString("pt-BR", { maximumFractionDigits: 2 })}${suffix}` : "—";
}
function date(value: string | null | undefined) {
  if (!value) return "Horário não informado";
  return new Date(value).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

export default function ConciliacaoPage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [selected, setSelected] = useState<string>("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [busy, setBusy] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function refresh() {
    setBusy(true);
    setError("");
    try {
      const response = await fetch("/api/v1/reconciliation?limit=50", { cache: "no-store" });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body?.error || "reconciliation_failed");
      const next = body as Summary;
      setSummary(next);
      const preferred = next.items.find((item) => !item.decision)?.id || next.items[0]?.id || "";
      if (!selected && preferred) setSelected(preferred);
    } catch {
      setError("Não foi possível carregar a Conciliação.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void refresh(); }, []);
  useEffect(() => {
    if (!selected) { setDetail(null); return; }
    let active = true;
    fetch(`/api/v1/reconciliation?import_id=${encodeURIComponent(selected)}`, { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body?.error || "reconciliation_detail_failed");
        return body as Detail;
      })
      .then((body) => { if (active) setDetail(body); })
      .catch(() => { if (active) setError("Não foi possível calcular os candidatos desta corrida."); });
    return () => { active = false; };
  }, [selected]);

  const current = useMemo(() => summary?.items.find((item) => item.id === selected) || detail?.import || null, [summary, detail, selected]);

  async function decide(decision: "confirmed" | "no_match", rideOfferId?: string | number) {
    if (!selected || saving) return;
    setSaving(true);
    setError("");
    try {
      const response = await fetch("/api/v1/reconciliation", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ import_id: selected, decision, ride_offer_id: rideOfferId ?? null }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body?.error || "reconciliation_save_failed");
      setDetail(null);
      setSelected("");
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? `Não foi possível salvar: ${e.message}` : "Não foi possível salvar a decisão.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="sr023Page">
      <AccountPageHeader
        title="Conciliação"
        subtitle="Confira candidatos antes de transformar histórico importado em corrida realizada. O Sr. Rotas nunca confirma automaticamente uma correspondência."
      />
      <IntelligenceNav />

      {error ? <div className="srInlineError">{error}</div> : null}
      <div className="srG3ReconSummary">
        <div><span>Status</span><strong>{busy ? "…" : summary?.status || "—"}</strong></div>
        <div><span>Cobertura</span><strong>{busy ? "…" : `${summary?.coverage_pct ?? 0}%`}</strong></div>
        <div><span>Revisadas</span><strong>{summary?.reviewed ?? 0}</strong></div>
        <div><span>Confirmadas</span><strong>{summary?.confirmed ?? 0}</strong></div>
        <div><span>Pendentes</span><strong>{summary?.unresolved ?? 0}</strong></div>
      </div>

      <div className="srG3ReconLayout">
        <section className="srPanel">
          <div className="srPanelHead"><div><span className="srEyebrow">HISTÓRICO IMPORTADO</span><h2>Corridas para revisar</h2></div></div>
          <div className="srG3ReconList">
            {summary?.items?.map((item) => (
              <button className={`srG3ReconItem ${selected === item.id ? "active" : ""}`} key={item.id} onClick={() => setSelected(item.id)}>
                <strong>{money(item.fare)} · {item.service_type || "categoria não identificada"}</strong>
                <small>{date(item.occurred_at || item.captured_at)}</small>
                <small>{item.pickup_label || "origem não informada"} → {item.destination_label || "destino não informado"}</small>
                <small>{item.decision ? `Revisada: ${item.decision.decision === "confirmed" ? "confirmada" : "nenhum candidato"}` : "Pendente de revisão"}</small>
              </button>
            ))}
            {!busy && !summary?.items?.length ? <div className="srEmpty compact"><strong>Nenhuma corrida importada para revisar.</strong></div> : null}
          </div>
        </section>

        <section className="srPanel">
          <div className="srPanelHead"><div><span className="srEyebrow">CANDIDATOS</span><h2>{current ? `${money(current.fare)} · ${date(current.occurred_at || current.captured_at)}` : "Selecione uma corrida"}</h2></div></div>
          {current ? (
            <>
              <p className="srPanelText">Importada: {current.pickup_label || "origem não informada"} → {current.destination_label || "destino não informado"}. Distância {number(current.distance_km, " km")} · duração {current.duration_seconds ? number(current.duration_seconds / 60, " min") : "—"}.</p>
              {detail?.candidates?.map((candidate) => (
                <article className="srG3Candidate" key={String(candidate.id)}>
                  <div className="srG3CandidateHead">
                    <div><strong>{money(candidate.fare)} · {candidate.service_type || "categoria não identificada"}</strong><small className="srP501TableSub">{date(candidate.observed_at)}</small></div>
                    <span className="srG3Score">{candidate.score}% · {candidate.confidence}</span>
                  </div>
                  <p className="srPanelText">{candidate.pickup_label || "origem sem texto"} → {candidate.destination_label || "destino sem texto"} · R$/km {number(candidate.per_km)} · R$/h {number(candidate.per_hour)}</p>
                  <div className="srG3Reasons">{candidate.reasons.map((reason) => <span key={reason}>{reason}</span>)}</div>
                  <div className="srG3Actions"><button disabled={saving} onClick={() => void decide("confirmed", candidate.id)}>Confirmar esta corrida</button></div>
                </article>
              ))}
              {detail && !detail.candidates.length ? <div className="srEmpty compact"><strong>Nenhum candidato com evidência suficiente.</strong><p>Você pode marcar que nenhuma oferta corresponde. Isso não inventa uma corrida.</p></div> : null}
              <div className="srG3Actions"><button className="muted" disabled={saving} onClick={() => void decide("no_match")}>Nenhuma corresponde</button></div>
              <p className="srDataNote">{detail?.note || "O ranker compara horário, valor, categoria, origem, destino, duração e distância quando esses campos existem."}</p>
            </>
          ) : <div className="srEmpty"><strong>Selecione uma corrida à esquerda.</strong></div>}
        </section>
      </div>
    </div>
  );
}
