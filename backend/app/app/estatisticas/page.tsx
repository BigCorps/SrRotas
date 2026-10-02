"use client";

import { useEffect, useMemo, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";
import IntelligenceNav from "../_components/IntelligenceNav";

type Metric = { key: string; label: string; formatted: string; semantic: string };
type Evidence = {
  source: string;
  sampleCount: number;
  confidence: string;
  dataQuality: string;
  completeness: string;
  coverage?: { value: number | null; label: string };
  period: { label: string };
};
type Answer = {
  answer: string;
  intent: string;
  status: string;
  evidence: Evidence;
  metrics: Metric[];
  alternatives: string[];
  limitations: string[];
};
type ResponseBody = { section: string; preset: string; period: { label: string } | null; answers: Answer[] };

const sections = [
  ["resumo", "Resumo"],
  ["produtividade", "Produtividade"],
  ["horarios", "Horários"],
  ["regioes", "Regiões"],
  ["corridas", "Corridas"],
  ["oportunidades", "Oportunidades"],
  ["custos", "Custos"],
] as const;

type Preset = "yesterday" | "7" | "30" | "custom";

function pct(value: number | null | undefined) {
  return typeof value === "number" ? `${Math.round(value * 100)}%` : "—";
}

export default function EstatisticasPage() {
  const [section, setSection] = useState("resumo");
  const [preset, setPreset] = useState<Preset>("7");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [data, setData] = useState<ResponseBody | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  const query = useMemo(() => {
    const params = new URLSearchParams({ section, preset });
    if (preset === "custom" && from) params.set("from", new Date(`${from}T00:00:00`).toISOString());
    if (preset === "custom" && to) params.set("to", new Date(`${to}T23:59:59.999`).toISOString());
    return params.toString();
  }, [section, preset, from, to]);

  useEffect(() => {
    if (preset === "custom" && (!from || !to)) return;
    let active = true;
    setBusy(true);
    setError("");
    fetch(`/api/v1/statistics?${query}`, { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body?.error || "statistics_failed");
        return body as ResponseBody;
      })
      .then((body) => { if (active) setData(body); })
      .catch(() => { if (active) setError("Não foi possível carregar esta análise agora."); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [query, preset, from, to]);

  return (
    <div className="sr023Page">
      <AccountPageHeader
        title="Estatísticas"
        subtitle="A mesma Fundação Premium usada pelo Pergunte e pelo MCP. Oferta observada, corrida realizada, custo estimado e gasto real permanecem semanticamente separados."
      />
      <IntelligenceNav />

      <section className="srPanel">
        <div className="srPanelHead">
          <div><span className="srEyebrow">PERÍODO</span><h2>{data?.period?.label || "Escolha o recorte"}</h2></div>
          <div className="srG3Period">
            {([['yesterday','Ontem'],['7','7 dias'],['30','30 dias'],['custom','Personalizado']] as const).map(([value,label]) => (
              <button key={value} className={preset === value ? "active" : ""} onClick={() => setPreset(value)}>{label}</button>
            ))}
          </div>
        </div>
        {preset === "custom" ? (
          <div className="srG3Custom">
            <label>De <input type="date" value={from} onChange={(e: any) => setFrom(e.target.value)} /></label>
            <label>Até <input type="date" value={to} onChange={(e: any) => setTo(e.target.value)} /></label>
          </div>
        ) : null}
        <div className="srG3Tabs">
          {sections.map(([value,label]) => <button key={value} className={section === value ? "active" : ""} onClick={() => setSection(value)}>{label}</button>)}
        </div>
      </section>

      {error ? <div className="srInlineError srSectionGap">{error}</div> : null}
      {busy ? <div className="srEmpty srSectionGap"><strong>Calculando com a Fundação Premium…</strong></div> : null}

      {!busy && data?.answers?.map((answer, index) => (
        <article className="srG3Answer" key={`${answer.intent}-${index}`}>
          <span className="srEyebrow">{answer.intent.replaceAll("_", " ")}</span>
          <h3>{answer.evidence.period.label}</h3>
          <p>{answer.answer}</p>
          <div className="srG3Evidence">
            <span>Amostra: {answer.evidence.sampleCount.toLocaleString("pt-BR")}</span>
            <span>Confiança: {answer.evidence.confidence}</span>
            <span>Qualidade: {answer.evidence.dataQuality}</span>
            <span>Cobertura: {pct(answer.evidence.coverage?.value)}</span>
            <span>{answer.evidence.completeness === "complete" ? "Completo" : "Parcial"}</span>
          </div>
          {answer.metrics.length ? (
            <div className="srG3MetricGrid">
              {answer.metrics.map((item) => (
                <div className="srG3Metric" key={`${answer.intent}-${item.key}`}>
                  <small>{item.label}</small><strong>{item.formatted}</strong><small>{item.semantic.replaceAll("_", " ")}</small>
                </div>
              ))}
            </div>
          ) : null}
          {answer.alternatives?.length ? <ul className="srG3Alternatives">{answer.alternatives.slice(0,3).map((item) => <li key={item}>{item}</li>)}</ul> : null}
          {answer.limitations?.map((item) => <div className="srG3Limit" key={item}>{item}</div>)}
        </article>
      ))}
    </div>
  );
}
