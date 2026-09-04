import { adminSupabase } from "./supabase";

type OutcomeRow = {
  journey_id: string | null;
  ride_offer_id: number | string | null;
  local_offer_id: string | null;
  status: string;
};

type FareRow = {
  id: number | string;
  local_offer_id: string | null;
  fare: number | string | null;
};

type SessionRow = {
  journey_id: string | null;
  earnings: number | string | null;
  completed_trips: number | null;
  offered_trips: number | null;
  confidence: number | string | null;
  started_at: string | null;
  ended_at: string | null;
  captured_at: string;
};

function money(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : null;
}

function numeric(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function missingJourneyColumn(error: any) {
  const message = String(error?.message ?? "").toLowerCase();
  return message.includes("journey_id") &&
    (message.includes("column") || message.includes("schema cache") || error?.code === "PGRST204");
}

/**
 * Resultado realizado por jornada. Separado do dashboard de ofertas para não
 * mudar a semântica histórica de analytics.ts.
 */
export async function listJourneyRealized0262(driverId: string, days = 30) {
  const safeDays = Math.max(1, Math.min(days || 30, 90));
  const from = new Date(Date.now() - safeDays * 86_400_000).toISOString();
  const journeys = await adminSupabase()
    .from("driver_journeys")
    .select("id,started_at,ended_at")
    .eq("driver_id", driverId)
    .gte("started_at", from)
    .order("started_at", { ascending: false })
    .limit(150);
  if (journeys.error) throw new Error(journeys.error.message);

  const journeyIds: string[] = (journeys.data ?? []).map((row: any) => String(row.id)).filter(Boolean);
  if (!journeyIds.length) return { items: [] };

  const outcomesResult = await adminSupabase()
    .from("ride_outcomes")
    .select("journey_id,ride_offer_id,local_offer_id,status")
    .eq("driver_id", driverId)
    .eq("status", "COMPLETED")
    .in("journey_id", journeyIds);
  if (outcomesResult.error) throw new Error(outcomesResult.error.message);
  const outcomes = (outcomesResult.data ?? []) as OutcomeRow[];

  let sessionRows: SessionRow[] = [];
  const sessionsResult = await adminSupabase()
    .from("uber_session_imports")
    .select("journey_id,earnings,completed_trips,offered_trips,confidence,started_at,ended_at,captured_at")
    .eq("driver_id", driverId)
    .in("journey_id", journeyIds)
    .order("captured_at", { ascending: false });
  if (sessionsResult.error) {
    // O backend pode ser publicado alguns minutos antes da migration 0.26.2.
    // Nesse intervalo preservamos o dashboard de jornadas sem derrubar a tela.
    if (!missingJourneyColumn(sessionsResult.error)) throw new Error(sessionsResult.error.message);
  } else {
    sessionRows = (sessionsResult.data ?? []) as SessionRow[];
  }

  const offerIds = [...new Set(outcomes.map((row) => row.ride_offer_id).filter((v) => v !== null).map(String))];
  const localIds = [...new Set(outcomes.map((row) => row.local_offer_id).filter((v): v is string => !!v))];
  const fares: FareRow[] = [];

  if (offerIds.length) {
    const found = await adminSupabase()
      .from("ride_offers")
      .select("id,local_offer_id,fare")
      .eq("driver_id", driverId)
      .in("id", offerIds);
    if (found.error) throw new Error(found.error.message);
    fares.push(...((found.data ?? []) as FareRow[]));
  }
  if (localIds.length) {
    const found = await adminSupabase()
      .from("ride_offers")
      .select("id,local_offer_id,fare")
      .eq("driver_id", driverId)
      .in("local_offer_id", localIds);
    if (found.error) throw new Error(found.error.message);
    fares.push(...((found.data ?? []) as FareRow[]));
  }

  const fareById = new Map(fares.map((row) => [String(row.id), money(row.fare)]));
  const fareByLocal = new Map(
    fares.filter((row) => row.local_offer_id).map((row) => [String(row.local_offer_id), money(row.fare)]),
  );

  const grouped = new Map<string, { completed: number; revenue: number; matched: number }>();
  for (const outcome of outcomes) {
    const journeyId = String(outcome.journey_id ?? "");
    if (!journeyId) continue;
    const current = grouped.get(journeyId) ?? { completed: 0, revenue: 0, matched: 0 };
    current.completed += 1;
    const fareByOfferId = outcome.ride_offer_id !== null
      ? fareById.get(String(outcome.ride_offer_id))
      : undefined;
    const fare = fareByOfferId ?? (outcome.local_offer_id
      ? fareByLocal.get(outcome.local_offer_id)
      : null);
    if (typeof fare === "number") {
      current.revenue += fare;
      current.matched += 1;
    }
    grouped.set(journeyId, current);
  }

  const bestSession = new Map<string, SessionRow>();
  for (const session of sessionRows) {
    const journeyId = String(session.journey_id ?? "");
    if (!journeyId) continue;
    const current = bestSession.get(journeyId);
    const confidence = numeric(session.confidence) ?? 0;
    const currentConfidence = numeric(current?.confidence) ?? -1;
    if (!current || confidence > currentConfidence) bestSession.set(journeyId, session);
  }

  return {
    items: journeyIds.map((journeyId) => {
      const row = grouped.get(journeyId) ?? { completed: 0, revenue: 0, matched: 0 };
      const session = bestSession.get(journeyId);
      return {
        journey_id: journeyId,
        completed_trips: row.completed,
        realized_revenue: Math.round(row.revenue * 100) / 100,
        fare_matched_trips: row.matched,
        revenue_complete: row.completed > 0 && row.matched === row.completed,
        session_earnings: session ? money(session.earnings) : null,
        session_completed_trips: session?.completed_trips ?? null,
        session_offered_trips: session?.offered_trips ?? null,
        session_confidence: session ? numeric(session.confidence) : null,
        session_started_at: session?.started_at ?? null,
        session_ended_at: session?.ended_at ?? null,
      };
    }),
    note: "Faturamento realizado soma apenas outcomes COMPLETED com tarifa conhecida; quando existe Resumo da sessão Uber associado, ele é retornado em campos session_* como fonte oficial complementar da sessão.",
  };
}
