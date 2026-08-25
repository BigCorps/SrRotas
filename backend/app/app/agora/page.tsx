"use client";

import { useEffect, useMemo, useState } from "react";

type RegionRow = {
  region_key: string;
  region_label: string;
  weekday_iso: number;
  hour_bucket: number;
  service_profile: string;
  sample_count: number;
  median_per_km?: number | null;
  p25_per_km?: number | null;
  p75_per_km?: number | null;
  average_per_km?: number | null;
  median_per_hour?: number | null;
  p25_per_hour?: number | null;
  p75_per_hour?: number | null;
  average_per_hour?: number | null;
  average_pickup_km?: number | null;
  average_pickup_minutes?: number | null;
  confidence: string;
  wording: string;
  source: string;
  score: number;
};

type Data = {
  mode: string;
  source: string;
  strategy_preset?: string;
  selected_service_profile?: string | null;
  collective_opt_in: boolean;
  preferred?: string;
  seed: RegionRow[];
  personal: RegionRow[];
  collective: RegionRow[];
  note: string;
};

const profileNames: Record<string, string> = {
  popular: "Popular",
  comfort: "Conforto",
  premium: "Premium",
  unknown: "Todas",
};

function money(value?: number | null) {
  return typeof value === "number"
    ? value.toLocaleString("pt-BR", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    : "—";
}

function sourceName(value?: string) {
  if (value === "personal") return "Sua base";
  if (value === "collective") return "Comunidade";
  return "Base Sr. Rotas";
}

export default function AgoraPage() {
  const [mode, setMode] = useState("now");
  const [source, setSource] = useState("personal");
  const [region, setRegion] = useState("");
  const [profile, setProfile] = useState("");
  const [data, setData] = useState<Data | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    setBusy(true);
    const query = new URLSearchParams({ mode, source });
    if (region.trim()) query.set("region", region.trim());
    if (profile) query.set("profile", profile);
    try {
      const response = await fetch(`/api/v1/intelligence/now?${query}`, {
        cache: "no-store",
      });
      setData(response.ok ? await response.json() : null);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    load().catch(() => undefined);
    // region is intentionally submitted by the Consultar button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, source, profile]);

  const rows = useMemo(() => {
    if (!data) return [];
    if (source === "collective" && data.collective?.length) return data.collective;
    if (source === "personal" && data.personal?.length) return data.personal;
    return data.seed ?? [];
  }, [data, source]);

  return (
    <>
      <section className="srPageHead compact">
        <div>
          <span className="srEyebrow">INTELIGÊNCIA DE REGIÃO</span>
          <h1>Agora</h1>
          <p>
            Compare regiões e faixas de horário com o seu perfil. É histórico agregado,
            não demanda ao vivo e não garante corrida.
          </p>
        </div>
        {data ? (
          <div className="srStatusCard">
            <span className="srStatusDot" />
            <div>
              <strong>{sourceName(data.preferred)}</strong>
              <small>
                Perfil {profileNames[data.selected_service_profile || ""] ||
                  profileNames[data.strategy_preset || ""] ||
                  "personalizado"}
              </small>
            </div>
          </div>
        ) : null}
      </section>

      <section className="srNowToolbar">
        <div className="srSegment">
          {[
            ["now", "Agora"],
            ["today", "Hoje"],
            ["week", "Semana"],
            ["search", "Pesquisa"],
          ].map(([key, label]) => (
            <button
              key={key}
              className={mode === key ? "active" : ""}
              onClick={() => setMode(key)}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="srSegment">
          <button
            className={source === "personal" ? "active" : ""}
            onClick={() => setSource("personal")}
          >
            Base pessoal
          </button>
          <button
            className={source === "collective" ? "active" : ""}
            onClick={() => setSource("collective")}
          >
            Base coletiva
          </button>
        </div>
      </section>

      <section className="srPanel">
        <div className="srSearchRow">
          <input
            value={region}
            onChange={(event) => setRegion(event.target.value)}
            placeholder="Bairro ou região"
          />
          <select value={profile} onChange={(event) => setProfile(event.target.value)}>
            <option value="">Meu perfil</option>
            <option value="all">Todas as categorias</option>
            <option value="popular">Popular</option>
            <option value="comfort">Conforto</option>
            <option value="premium">Premium</option>
          </select>
          <button className="srPrimary" onClick={() => load()} disabled={busy}>
            {busy ? "Consultando..." : "Consultar"}
          </button>
        </div>
        {source === "collective" && data && !data.collective_opt_in ? (
          <div className="srCollectiveGate">
            <strong>Base coletiva exige participação</strong>
            <p>
              Ao autorizar dados anonimizados, você contribui e passa a consultar a
              comunidade. A Base Sr. Rotas histórica continua disponível como referência.
            </p>
          </div>
        ) : null}
      </section>

      <section className="srRegionGrid srSectionGap">
        {rows.length ? (
          rows.slice(0, 18).map((item, index) => (
            <article
              className="srRegionCard"
              key={`${item.region_key}-${item.weekday_iso}-${item.hour_bucket}-${index}`}
            >
              <div className="srRegionTop">
                <span>📍</span>
                <div>
                  <h2>{item.region_label}</h2>
                  <small>
                    {item.hour_bucket >= 0
                      ? `${String(item.hour_bucket).padStart(2, "0")}h–${String(Math.min(24, item.hour_bucket + 3)).padStart(2, "0")}h`
                      : "Várias faixas"} · {" "}
                    {profileNames[item.service_profile] || item.service_profile}
                  </small>
                </div>
                <b>{item.sample_count} amostras</b>
              </div>
              <p>{item.wording}</p>
              <div className="srRegionMetrics">
                <span>
                  R$/km
                  <strong>{money(item.median_per_km ?? item.average_per_km)}</strong>
                  <small>
                    {item.p25_per_km && item.p75_per_km
                      ? `${money(item.p25_per_km)}–${money(item.p75_per_km)}`
                      : "histórico"}
                  </small>
                </span>
                <span>
                  R$/h
                  <strong>{money(item.median_per_hour ?? item.average_per_hour)}</strong>
                  <small>
                    {item.p25_per_hour && item.p75_per_hour
                      ? `${money(item.p25_per_hour)}–${money(item.p75_per_hour)}`
                      : "histórico"}
                  </small>
                </span>
                <span>
                  Busca
                  <strong>
                    {item.average_pickup_minutes
                      ? `${money(item.average_pickup_minutes)} min`
                      : "—"}
                  </strong>
                  <small>
                    {item.average_pickup_km
                      ? `${money(item.average_pickup_km)} km`
                      : "histórico"}
                  </small>
                </span>
              </div>
              <footer>
                <span className={`srConfidence ${item.confidence}`}>
                  {item.confidence === "high"
                    ? "Confiança alta"
                    : item.confidence === "medium"
                      ? "Confiança média"
                      : "Histórico disponível"}
                </span>
                <small>{sourceName(item.source)}</small>
              </footer>
            </article>
          ))
        ) : (
          <div className="srEmpty">
            <span>⌁</span>
            <strong>Dados insuficientes</strong>
            <p>Experimente outra faixa, região ou categoria.</p>
          </div>
        )}
      </section>
      {data ? <p className="srDataNote">{data.note}</p> : null}
    </>
  );
}
