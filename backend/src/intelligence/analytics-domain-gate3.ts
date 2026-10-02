import { adminSupabase } from "../supabase";
import { reconciliationSummary } from "../reconciliation-v1";
import type { AnswerMetric, DomainResult, QuestionPlan } from "./contracts";
import { coverageEnvelope } from "./evidence-policy";
import { queryAnalyticsDomain } from "./analytics-domain";

function n(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function r2(value: number) {
  return Math.round(value * 100) / 100;
}

function metric(key: string, label: string, value: number | string | null, formatted: string, semantic: AnswerMetric["semantic"]): AnswerMetric {
  return { key, label, value, formatted, semantic };
}

function money(value: number | null) {
  return value === null ? "—" : value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function number(value: number | null) {
  return value === null ? "—" : value.toLocaleString("pt-BR", { maximumFractionDigits: 2 });
}

async function missedOpportunities(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  const journeys = await adminSupabase()
    .from("driver_journeys")
    .select("id")
    .eq("driver_id", driverId)
    .gte("started_at", plan.period.from)
    .lt("started_at", plan.period.to)
    .limit(500);
  if (journeys.error) throw new Error(journeys.error.message);
  const journeyIds = (journeys.data ?? []).map((row: any) => String(row.id)).filter(Boolean);
  if (!journeyIds.length) {
    return {
      status: "insufficient",
      title: `Oportunidades não realizadas — ${plan.period.label}`,
      semantic: "MIXED",
      source: "ride_outcomes+reconciliation",
      scope: "personal",
      sampleCount: 0,
      metrics: [],
      limitations: ["Não há jornadas suficientes neste período para identificar ofertas explicitamente marcadas como não realizadas."],
    };
  }

  const outcomes = await adminSupabase()
    .from("ride_outcomes")
    .select("ride_offer_id,local_offer_id,status,source")
    .eq("driver_id", driverId)
    .in("journey_id", journeyIds)
    .in("status", ["NOT_COMPLETED", "CANCELLED"])
    .neq("source", "uber_history_ocr");
  if (outcomes.error) throw new Error(outcomes.error.message);
  const rows = outcomes.data ?? [];

  const offerIds = [...new Set(rows.map((row: any) => row.ride_offer_id).filter((value: unknown) => value !== null).map(String))];
  const localIds = [...new Set(rows.map((row: any) => row.local_offer_id).filter(Boolean).map(String))];
  const offers: any[] = [];
  if (offerIds.length) {
    const found = await adminSupabase()
      .from("ride_offers")
      .select("id,local_offer_id,fare,per_km,per_hour")
      .eq("driver_id", driverId)
      .in("id", offerIds);
    if (found.error) throw new Error(found.error.message);
    offers.push(...(found.data ?? []));
  }
  if (localIds.length) {
    const found = await adminSupabase()
      .from("ride_offers")
      .select("id,local_offer_id,fare,per_km,per_hour")
      .eq("driver_id", driverId)
      .in("local_offer_id", localIds);
    if (found.error) throw new Error(found.error.message);
    for (const row of found.data ?? []) if (!offers.some((item) => String(item.id) === String(row.id))) offers.push(row);
  }

  const fareValues = offers.map((row) => n(row.fare)).filter((value): value is number => value !== null);
  const perKmValues = offers.map((row) => n(row.per_km)).filter((value): value is number => value !== null);
  const perHourValues = offers.map((row) => n(row.per_hour)).filter((value): value is number => value !== null);
  const offeredFare = fareValues.length ? r2(fareValues.reduce((a, b) => a + b, 0)) : null;
  const avgPerKm = perKmValues.length ? r2(perKmValues.reduce((a, b) => a + b, 0) / perKmValues.length) : null;
  const avgPerHour = perHourValues.length ? r2(perHourValues.reduce((a, b) => a + b, 0) / perHourValues.length) : null;
  const reconciliation = await reconciliationSummary(driverId, 1);
  const reviewed = Number(reconciliation.reviewed || 0);
  const total = Number(reconciliation.total_completed_imports || 0);
  const coverage = coverageEnvelope(reviewed, total, "Histórico importado já revisado na Conciliação");

  return {
    status: rows.length ? "ok" : "insufficient",
    title: `Oportunidades não realizadas — ${plan.period.label}`,
    semantic: "MIXED",
    source: "ride_outcomes+ride_offers+reconciliation_v1",
    scope: "personal",
    sampleCount: rows.length,
    coverage,
    completeness: coverage.value !== null && coverage.value >= 0.999 ? "complete" : "partial",
    metrics: [
      metric("explicit_non_realized", "Ofertas explicitamente não realizadas", rows.length, number(rows.length), "REALIZED_RIDE"),
      metric("observed_fare_not_realized", "Valor ofertado nessas ofertas", offeredFare, money(offeredFare), "OBSERVED_OFFER"),
      metric("avg_per_km", "R$/km médio dessas ofertas", avgPerKm, avgPerKm === null ? "—" : `R$ ${number(avgPerKm)}`, "OBSERVED_OFFER"),
      metric("avg_per_hour", "R$/h médio dessas ofertas", avgPerHour, avgPerHour === null ? "—" : `R$ ${number(avgPerHour)}`, "OBSERVED_OFFER"),
    ],
    details: {
      statuses: rows.reduce((acc: Record<string, number>, row: any) => {
        const key = String(row.status || "UNKNOWN");
        acc[key] = (acc[key] ?? 0) + 1;
        return acc;
      }, {}),
      reconciliation_status: reconciliation.status,
      reconciliation_coverage_pct: reconciliation.coverage_pct,
    },
    limitations: rows.length
      ? [
          "O valor mostrado é o valor que apareceu nas ofertas explicitamente marcadas como não realizadas; não representa receita perdida nem ganho garantido.",
          ...(coverage.value !== null && coverage.value < 1 ? ["A Conciliação ainda está parcial; novas revisões podem alterar a cobertura das análises realizadas."] : []),
        ]
      : ["Não há oferta explicitamente marcada como não realizada neste recorte. Isso não prova que não existiram oportunidades perdidas."],
  };
}

export async function queryAnalyticsDomainGate3(driverId: string, plan: QuestionPlan): Promise<DomainResult> {
  if (plan.intent === "MISSED_OPPORTUNITIES") return missedOpportunities(driverId, plan);
  return queryAnalyticsDomain(driverId, plan);
}
