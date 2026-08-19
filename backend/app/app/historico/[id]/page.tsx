"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

type Summary = {
  offer_count: number;
  average_fare: number | null;
  average_per_km: number | null;
  average_per_hour: number | null;
  average_per_minute: number | null;
  average_estimated_profit: number | null;
  verdicts: { boa: number; regular: number; ruim: number };
  offer_types: { exclusive: number; radar: number };
};

type Offer = {
  id: string;
  observed_at: string;
  fare: number;
  total_km: number | null;
  total_minutes: number | null;
  per_km: number | null;
  per_hour: number | null;
  per_minute: number | null;
  estimated_profit: number | null;
  passenger_rating: number | null;
  service_type: string;
  verdict: string;
  offer_type: string;
};

type Detail = {
  journey: {
    id: string;
    platform: string;
    started_at: string;
    ended_at: string | null;
    end_reason: string | null;
  };
  summary: Summary;
  offers: Offer[];
  note: string;
};

const serviceNames: Record<string, string> = {
  uberx: "UberX", comfort: "Comfort", black: "Black", electric: "Electric",
  priority: "Priority", moto: "Moto", unknown: "Não identificado",
};

function money(value: number | null | undefined) {
  return typeof value === "number"
    ? value.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : "—";
}

export default function JourneyDetailPage() {
  const params = useParams<{ id: string }>();
  const id = String(params?.id || "");
  const [data, setData] = useState<Detail | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!id) return;
    setBusy(true);
    setError("");

    fetch(`/api/v1/journeys?id=${encodeURIComponent(id)}&include_offers=1`, { cache: "no-store" })
      .then(async (response) => {
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(payload?.error === "journey_not_found" ? "Jornada não encontrada." : "Não foi possível carregar esta jornada.");
        return payload;
      })
      .then(setData)
      .catch((cause) => setError(cause instanceof Error ? cause.message : "Não foi possível carregar esta jornada."))
      .finally(() => setBusy(false));
  }, [id]);

  if (busy) return <div className="srDetailState"><img src="/logo-srrotas.png" alt=""/><strong>Carregando jornada...</strong></div>;
  if (error || !data) return <><Link href="/app/historico" className="srBackLink">← Voltar ao histórico</Link><div className="srInlineError">{error || "Jornada não encontrada."}</div></>;

  const { journey, summary, offers } = data;

  return <>
    <Link href="/app/historico" className="srBackLink">← Voltar ao histórico</Link>

    <section className="srPageHead compact">
      <div><span className="srEyebrow">DETALHE DA JORNADA</span><h1>{new Date(journey.started_at).toLocaleDateString("pt-BR", { weekday: "long", day: "2-digit", month: "long" })}</h1><p>Início {new Date(journey.started_at).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })} · {journey.ended_at ? `encerrada ${new Date(journey.ended_at).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}` : "jornada ainda aberta"}.</p></div>
      <span className="srMutedPill">{offers.length} ofertas carregadas</span>
    </section>

    <section className="srMetrics srMetricsWide">
      <div><span>Ofertas</span><strong>{summary.offer_count}</strong><small>{summary.verdicts.boa} boas · {summary.verdicts.regular} atenção · {summary.verdicts.ruim} ruins</small></div>
      <div><span>R$/km médio</span><strong>{money(summary.average_per_km)}</strong><small>ofertas observadas</small></div>
      <div><span>R$/h médio</span><strong>{money(summary.average_per_hour)}</strong><small>ofertas observadas</small></div>
      <div><span>Valor médio</span><strong>R$ {money(summary.average_fare)}</strong><small>não representa faturamento</small></div>
    </section>

    <section className="srGrid3 srSectionGap">
      <article className="srMiniPanel"><span>EXCLUSIVE</span><strong>{summary.offer_types.exclusive}</strong><p>ofertas identificadas como Exclusive.</p></article>
      <article className="srMiniPanel"><span>RADAR</span><strong>{summary.offer_types.radar}</strong><p>ofertas identificadas como Radar.</p></article>
      <article className="srMiniPanel"><span>LUCRO ESTIMADO MÉDIO</span><strong>R$ {money(summary.average_estimated_profit)}</strong><p>estimativa sobre ofertas, não lucro realizado.</p></article>
    </section>

    <section className="srPanel srSectionGap">
      <div className="srPanelHead"><div><span className="srEyebrow">OFERTAS</span><h2>Leituras estruturadas da jornada</h2></div></div>
      {offers.length ? <div className="srTableWrap"><table className="srTable srOffersTable"><thead><tr><th>Horário</th><th>Serviço</th><th>Tipo</th><th>Valor</th><th>Km</th><th>Min</th><th>R$/km</th><th>R$/h</th><th>Avaliação</th><th>Leitura</th></tr></thead><tbody>
        {offers.map((offer) => <tr key={offer.id}><td>{new Date(offer.observed_at).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}</td><td>{serviceNames[offer.service_type] || offer.service_type}</td><td>{offer.offer_type === "radar" ? "Radar" : "Exclusive"}</td><td>R$ {money(offer.fare)}</td><td>{money(offer.total_km)}</td><td>{money(offer.total_minutes)}</td><td>{money(offer.per_km)}</td><td>{money(offer.per_hour)}</td><td>{typeof offer.passenger_rating === "number" ? offer.passenger_rating.toLocaleString("pt-BR", { minimumFractionDigits: 1 }) : "—"}</td><td><span className={`srVerdict ${offer.verdict}`}>{offer.verdict === "boa" ? "Boa" : offer.verdict === "regular" ? "Atenção" : "Ruim"}</span></td></tr>)}
      </tbody></table></div> : <div className="srEmpty"><span>⌁</span><strong>Sem ofertas nesta jornada</strong><p>A jornada existe, mas não há ofertas sincronizadas associadas a ela.</p></div>}
      <p className="srDataNote">{data.note}</p>
    </section>
  </>;
}
