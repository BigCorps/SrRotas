"use client";

import { useEffect, useMemo, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";

type Mode = "now" | "today" | "week";
type Row = {
  region_key: string;
  region_label: string;
  weekday_iso: number;
  hour_bucket: number;
  service_profile: string;
  sample_count: number;
  average_fare: number | null;
  average_per_km: number | null;
  average_per_minute: number | null;
  average_per_hour: number | null;
  average_pickup_km: number | null;
  average_pickup_minutes: number | null;
  confidence: "high" | "medium" | "low" | "insufficient";
  score: number;
  wording: string;
  source: string;
};

type NowResponse = {
  mode: Mode;
  source: string;
  strategy_preset: string;
  selected_service_profile: string | null;
  collective_opt_in: boolean;
  time_zone: string;
  minimum_seed_samples: number;
  target?: Record<string, unknown>;
  scope: string;
  seed: Row[];
  personal: Row[];
  collective: Row[];
  preferred: "personal" | "collective" | "sr_rotas_seed";
  note: string;
};

const profileLabels: Record<string, string> = {
  popular: "Popular",
  comfort: "Comfort",
  premium: "Premium",
  unknown: "Não identificado",
};

const confidenceLabels: Record<string, string> = {
  high: "alta",
  medium: "média",
  low: "baixa",
  insufficient: "insuficiente",
};

function number(value: number | null | undefined, digits = 2) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toLocaleString("pt-BR", { minimumFractionDigits: digits, maximumFractionDigits: digits })
    : "—";
}

function preferredRows(data: NowResponse | null) {
  if (!data) return [];
  if (data.preferred === "personal") return data.personal || [];
  if (data.preferred === "collective") return data.collective || [];
  return data.seed || [];
}

function sourceLabel(source: NowResponse["preferred"] | undefined) {
  if (source === "personal") return "Base Pessoal";
  if (source === "collective") return "Base Coletiva";
  return "Base Sr. Rotas";
}

export default function AgoraPage() {
  const [mode, setMode] = useState<Mode>("now");
  const [data, setData] = useState<NowResponse | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setBusy(true);
    setError("");
    fetch(`/api/v1/intelligence/now?mode=${encodeURIComponent(mode)}&source=personal`, { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body?.error || "now_intelligence_failed");
        return body as NowResponse;
      })
      .then((body) => { if (active) setData(body); })
      .catch(() => { if (active) setError("Não foi possível carregar a inteligência deste horário."); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [mode]);

  const rows = useMemo(() => preferredRows(data), [data]);
  const best = rows[0] || null;

  return (
    <div className="sr023Page srAgoraP501">
      <AccountPageHeader
        title="Agora"
        subtitle="Inteligência histórica explicável para comparar regiões e horários. Não é mapa de demanda ao vivo e não garante uma nova corrida."
      >
        <div className="srP501ModeSwitch" role="group" aria-label="Período da inteligência">
          <button className={mode === "now" ? "active" : ""} onClick={() => setMode("now")}>Agora</button>
          <button className={mode === "today" ? "active" : ""} onClick={() => setMode("today")}>Hoje</button>
          <button className={mode === "week" ? "active" : ""} onClick={() => setMode("week")}>Semana</button>
        </div>
      </AccountPageHeader>

      {error ? <div className="srInlineError">{error}</div> : null}

      <section className="srP501NowHero">
        <div>
          <span className="srEyebrow light">FONTE PREFERIDA · {sourceLabel(data?.preferred).toUpperCase()}</span>
          {busy ? (
            <><h2>Carregando sua leitura…</h2><p>Comparando amostras do seu histórico com a faixa escolhida.</p></>
          ) : best ? (
            <>
              <h2>{best.region_label}</h2>
              <p>{best.wording}</p>
              <div className="srP501HeroMetrics">
                <span><small>Amostra</small><strong>{best.sample_count.toLocaleString("pt-BR")}</strong></span>
                <span><small>R$/km</small><strong>{number(best.average_per_km)}</strong></span>
                <span><small>R$/h</small><strong>{number(best.average_per_hour)}</strong></span>
                <span><small>Confiança</small><strong>{confidenceLabels[best.confidence] || best.confidence}</strong></span>
              </div>
            </>
          ) : (
            <><h2>Dados insuficientes nesta faixa</h2><p>O Sr. Rotas não encontrou amostra segura para destacar uma região agora.</p></>
          )}
        </div>
        <aside>
          <span>Como interpretar</span>
          <p>O ranking usa amostra histórica, horário, região, categoria e aderência às metas configuradas. Ele não lê o mapa de calor da Uber/99.</p>
          <strong>{data?.scope ? `Escopo: ${data.scope.replaceAll("_", " ")}` : "—"}</strong>
        </aside>
      </section>

      <section className="srPanel srSectionGap">
        <div className="srPanelHead">
          <div><span className="srEyebrow">COMPARAÇÃO</span><h2>Regiões com evidência nesta consulta</h2></div>
          <span className="srMutedPill">{rows.length} resultados</span>
        </div>

        {rows.length ? (
          <div className="srP501NowGrid">
            {rows.slice(0, 12).map((row, index) => (
              <article className="srP501NowCard" key={`${row.region_key}-${row.weekday_iso}-${row.hour_bucket}-${row.service_profile}-${index}`}>
                <div className="srP501Rank">
                  <b>{String(index + 1).padStart(2, "0")}</b>
                  <span className={`srP501Confidence ${row.confidence}`}>{confidenceLabels[row.confidence] || row.confidence}</span>
                </div>
                <h3>{row.region_label}</h3>
                <p>{row.wording}</p>
                <div className="srP501CardMetrics">
                  <span><small>Perfil</small><strong>{profileLabels[row.service_profile] || row.service_profile}</strong></span>
                  <span><small>Amostra</small><strong>{row.sample_count.toLocaleString("pt-BR")}</strong></span>
                  <span><small>R$/km</small><strong>{number(row.average_per_km)}</strong></span>
                  <span><small>R$/h</small><strong>{number(row.average_per_hour)}</strong></span>
                  <span><small>Busca km</small><strong>{number(row.average_pickup_km, 1)}</strong></span>
                  <span><small>Busca min</small><strong>{number(row.average_pickup_minutes, 1)}</strong></span>
                </div>
              </article>
            ))}
          </div>
        ) : busy ? (
          <div className="srEmpty compact"><strong>Carregando…</strong></div>
        ) : (
          <div className="srEmpty"><span>⌁</span><strong>Sem amostra segura</strong><p>Continue usando o Sr. Rotas normalmente. O sistema evita inventar recomendação quando a evidência é insuficiente.</p></div>
        )}

        <p className="srDataNote">{data?.note || "Resultados históricos; não representam demanda em tempo real."}</p>
      </section>
    </div>
  );
}
