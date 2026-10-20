import { adminSupabase } from "./supabase";
import { summarizeReconciliationOverview } from "./reconciliation-overview";
import { reconciliationCandidateEligibility, RECONCILIATION_MAX_DELTA_MINUTES } from "./reconciliation-candidate-eligibility";

const RANKER_VERSION = "journey-reconciliation-v1";

type ImportedRide = {
  id: string;
  driver_id: string;
  device_id: string | null;
  source_key: string;
  captured_at: string;
  occurred_at: string | null;
  fare: number | string;
  service_type: string;
  pickup_label: string | null;
  destination_label: string | null;
  confidence: number | string;
  matched_ride_offer_id: number | string | null;
  duration_seconds: number | null;
  distance_km: number | string | null;
  ride_status: string;
};

type OfferCandidate = {
  id: number | string;
  driver_id: string;
  platform: string;
  journey_id: string | null;
  local_offer_id: string | null;
  observed_at: string;
  fare: number | string;
  service_type: string | null;
  pickup_label: string | null;
  destination_label: string | null;
  total_minutes: number | null;
  trip_minutes: number | null;
  total_km: number | string | null;
  trip_km: number | string | null;
  per_km: number | string | null;
  per_hour: number | string | null;
};

type ReconciliationRow = {
  import_id: string;
  decision: "confirmed" | "no_match";
  selected_ride_offer_id: number | string | null;
  candidate_score: number | string | null;
  decided_at: string;
};

function n(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function r2(value: number) {
  return Math.round(value * 100) / 100;
}

function normalize(value: unknown) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function labelSimilarity(a: unknown, b: unknown) {
  const left = new Set(normalize(a).split(" ").filter((x) => x.length >= 2));
  const right = new Set(normalize(b).split(" ").filter((x) => x.length >= 2));
  if (!left.size || !right.size) return null;
  let intersection = 0;
  for (const token of left) if (right.has(token)) intersection += 1;
  const union = new Set([...left, ...right]).size;
  return union ? intersection / union : null;
}

function closeness(delta: number, maxDelta: number) {
  if (!Number.isFinite(delta) || delta < 0 || maxDelta <= 0) return 0;
  return Math.max(0, 1 - Math.min(1, delta / maxDelta));
}

export function scoreReconciliationCandidate(imported: ImportedRide, offer: OfferCandidate) {
  let earned = 0;
  let available = 0;
  const reasons: string[] = [];

  const fareA = n(imported.fare);
  const fareB = n(offer.fare);
  if (fareA !== null && fareB !== null) {
    available += 30;
    const delta = Math.abs(fareA - fareB);
    const part = closeness(delta, 5);
    earned += part * 30;
    if (delta <= 0.02) reasons.push("valor praticamente idêntico");
    else if (delta <= 1) reasons.push("valor muito próximo");
  }

  if (imported.occurred_at) {
    const deltaMinutes = Math.abs(new Date(imported.occurred_at).getTime() - new Date(offer.observed_at).getTime()) / 60_000;
    available += 30;
    earned += closeness(deltaMinutes, 60) * 30;
    if (deltaMinutes <= 5) reasons.push("horário muito próximo");
    else if (deltaMinutes <= 20) reasons.push("horário compatível");
  }

  if (normalize(imported.service_type) && normalize(imported.service_type) !== "unknown" && normalize(offer.service_type)) {
    available += 8;
    if (normalize(imported.service_type) === normalize(offer.service_type)) {
      earned += 8;
      reasons.push("categoria compatível");
    }
  }

  const pickup = labelSimilarity(imported.pickup_label, offer.pickup_label);
  if (pickup !== null) {
    available += 8;
    earned += pickup * 8;
    if (pickup >= 0.65) reasons.push("origem semelhante");
  }

  const destination = labelSimilarity(imported.destination_label, offer.destination_label);
  if (destination !== null) {
    available += 8;
    earned += destination * 8;
    if (destination >= 0.65) reasons.push("destino semelhante");
  }

  const importedMinutes = imported.duration_seconds !== null ? imported.duration_seconds / 60 : null;
  const offerMinutes = offer.trip_minutes ?? offer.total_minutes;
  if (importedMinutes !== null && offerMinutes !== null) {
    available += 7;
    const delta = Math.abs(importedMinutes - offerMinutes);
    earned += closeness(delta, 25) * 7;
    if (delta <= 5) reasons.push("duração compatível");
  }

  const importedDistance = n(imported.distance_km);
  const offerDistance = n(offer.trip_km) ?? n(offer.total_km);
  if (importedDistance !== null && offerDistance !== null) {
    available += 7;
    const delta = Math.abs(importedDistance - offerDistance);
    earned += closeness(delta, 20) * 7;
    if (delta <= 2) reasons.push("distância compatível");
  }

  if (imported.matched_ride_offer_id !== null) {
    available += 10;
    if (String(imported.matched_ride_offer_id) === String(offer.id)) {
      earned += 10;
      reasons.push("pista legada do digitalizador");
    }
  }

  const score = available > 0 ? r2((earned / available) * 100) : 0;
  const confidence = score >= 80 ? "high" : score >= 60 ? "medium" : score >= 40 ? "low" : "insufficient";
  return { score, confidence, reasons: reasons.slice(0, 5), version: RANKER_VERSION };
}

async function loadImport(driverId: string, importId: string) {
  const found = await adminSupabase()
    .from("uber_completed_ride_imports")
    .select("id,driver_id,device_id,source_key,captured_at,occurred_at,fare,service_type,pickup_label,destination_label,confidence,matched_ride_offer_id,duration_seconds,distance_km,ride_status")
    .eq("driver_id", driverId)
    .eq("id", importId)
    .maybeSingle();
  if (found.error) throw new Error(found.error.message);
  if (!found.data) throw new Error("reconciliation_import_not_found");
  return found.data as ImportedRide;
}

async function loadDecision(driverId: string, importId: string) {
  const found = await adminSupabase()
    .from("sr_ride_reconciliations")
    .select("import_id,decision,selected_ride_offer_id,candidate_score,decided_at")
    .eq("driver_id", driverId)
    .eq("import_id", importId)
    .maybeSingle();
  if (found.error) throw new Error(found.error.message);
  return (found.data ?? null) as ReconciliationRow | null;
}

async function candidateOffers(driverId: string, imported: ImportedRide) {
  const byId = new Map<string, OfferCandidate>();
  if (imported.matched_ride_offer_id !== null) {
    const hint = await adminSupabase()
      .from("ride_offers")
      .select("id,driver_id,platform,journey_id,local_offer_id,observed_at,fare,service_type,pickup_label,destination_label,total_minutes,trip_minutes,total_km,trip_km,per_km,per_hour")
      .eq("driver_id", driverId)
      .eq("id", imported.matched_ride_offer_id)
      .maybeSingle();
    if (hint.error) throw new Error(hint.error.message);
    if (hint.data) byId.set(String(hint.data.id), hint.data as OfferCandidate);
  }

  const center = imported.occurred_at ? Date.parse(imported.occurred_at) : NaN;
  if (Number.isFinite(center) && center <= Date.now()) {
    const from = new Date(center - RECONCILIATION_MAX_DELTA_MINUTES * 60_000).toISOString();
    const to = new Date(center + RECONCILIATION_MAX_DELTA_MINUTES * 60_000).toISOString();
    const found = await adminSupabase()
      .from("ride_offers")
      .select("id,driver_id,platform,journey_id,local_offer_id,observed_at,fare,service_type,pickup_label,destination_label,total_minutes,trip_minutes,total_km,trip_km,per_km,per_hour")
      .eq("driver_id", driverId)
      .eq("platform", "uber")
      .gte("observed_at", from)
      .lte("observed_at", to)
      .not("journey_id", "is", null)
      .not("local_offer_id", "is", null)
      .order("observed_at", { ascending: true })
      .limit(80);
    if (found.error) throw new Error(found.error.message);
    for (const row of found.data ?? []) byId.set(String(row.id), row as OfferCandidate);
  }

  return [...byId.values()];
}

export async function reconciliationDetail(driverId: string, importId: string) {
  const [imported, decision] = await Promise.all([loadImport(driverId, importId), loadDecision(driverId, importId)]);
  const offers = imported.ride_status === "completed" ? await candidateOffers(driverId, imported) : [];
  const nowMs = Date.now();
  const eligibleOffers = offers.filter((offer) => reconciliationCandidateEligibility(imported, offer, nowMs).eligible);
  // Only a preview hint: future transactional uniqueness is the authority.
  const assigned = eligibleOffers.length
    ? await adminSupabase()
        .from("sr_ride_reconciliations")
        .select("selected_ride_offer_id")
        .eq("driver_id", driverId)
        .eq("decision", "confirmed")
        .in("selected_ride_offer_id", eligibleOffers.map((offer) => offer.id))
    : null;
  if (assigned?.error) throw new Error(assigned.error.message);
  const usedIds = new Set((assigned?.data ?? []).map((row: { selected_ride_offer_id: number | string | null }) => String(row.selected_ride_offer_id)));
  const candidates = eligibleOffers
    .filter((offer) => !usedIds.has(String(offer.id)))
    .map((offer) => ({ ...offer, ...scoreReconciliationCandidate(imported, offer) }))
    .filter((item) => item.score >= 30 || String(imported.matched_ride_offer_id ?? "") === String(item.id))
    .sort((a, b) => b.score - a.score || Math.abs(Number(a.fare) - Number(imported.fare)) - Math.abs(Number(b.fare) - Number(imported.fare)))
    .slice(0, 5);

  return {
    import: imported,
    decision,
    candidates,
    excluded_candidates: offers.length - eligibleOffers.length + usedIds.size,
    ranker_version: RANKER_VERSION,
    note: "Somente ofertas Uber elegíveis do mesmo motorista, com horário válido, dentro de ±75 minutos e ainda não revisadas. Sugestões não são confirmação nem pagamento.",
  };
}

/** Counts only; does not load names, addresses, fare amounts or unreviewed rows. */
export async function reconciliationOverview(driverId: string) {
  const now = new Date().toISOString();
  const [imports, reviewed, confirmed, noMatch, missingTime, futureTime] = await Promise.all([
    adminSupabase().from("uber_completed_ride_imports").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("ride_status", "completed"),
    adminSupabase().from("sr_ride_reconciliations").select("id", { count: "exact", head: true }).eq("driver_id", driverId),
    adminSupabase().from("sr_ride_reconciliations").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("decision", "confirmed"),
    adminSupabase().from("sr_ride_reconciliations").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("decision", "no_match"),
    adminSupabase().from("uber_completed_ride_imports").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("ride_status", "completed").is("occurred_at", null),
    adminSupabase().from("uber_completed_ride_imports").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("ride_status", "completed").gt("occurred_at", now),
  ]);
  for (const result of [imports, reviewed, confirmed, noMatch, missingTime, futureTime]) {
    if (result.error) throw new Error(result.error.message);
  }
  return summarizeReconciliationOverview({
    totalCompleted: imports.count ?? 0,
    reviewed: reviewed.count ?? 0,
    confirmed: confirmed.count ?? 0,
    noMatch: noMatch.count ?? 0,
    withoutOccurrence: missingTime.count ?? 0,
    futureOccurrence: futureTime.count ?? 0,
  });
}

export async function reconciliationSummary(driverId: string, limit = 40) {
  const safeLimit = Math.max(1, Math.min(100, Math.floor(limit || 40)));
  const [importsCount, decisionsCount, confirmedCount, noMatchCount, imports] = await Promise.all([
    adminSupabase().from("uber_completed_ride_imports").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("ride_status", "completed"),
    adminSupabase().from("sr_ride_reconciliations").select("id", { count: "exact", head: true }).eq("driver_id", driverId),
    adminSupabase().from("sr_ride_reconciliations").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("decision", "confirmed"),
    adminSupabase().from("sr_ride_reconciliations").select("id", { count: "exact", head: true }).eq("driver_id", driverId).eq("decision", "no_match"),
    adminSupabase()
      .from("uber_completed_ride_imports")
      .select("id,source_key,captured_at,occurred_at,fare,service_type,pickup_label,destination_label,confidence,matched_ride_offer_id,duration_seconds,distance_km,ride_status")
      .eq("driver_id", driverId)
      .eq("ride_status", "completed")
      .order("occurred_at", { ascending: false, nullsFirst: false })
      .order("captured_at", { ascending: false })
      .limit(Math.max(safeLimit * 3, 80)),
  ]);
  for (const result of [importsCount, decisionsCount, confirmedCount, noMatchCount, imports]) {
    if (result.error) throw new Error(result.error.message);
  }

  const importRows = imports.data ?? [];
  const importIds = importRows.map((row: any) => String(row.id));
  let decisions: ReconciliationRow[] = [];
  if (importIds.length) {
    const found = await adminSupabase()
      .from("sr_ride_reconciliations")
      .select("import_id,decision,selected_ride_offer_id,candidate_score,decided_at")
      .eq("driver_id", driverId)
      .in("import_id", importIds);
    if (found.error) throw new Error(found.error.message);
    decisions = (found.data ?? []) as ReconciliationRow[];
  }
  const decisionByImport = new Map(decisions.map((row) => [String(row.import_id), row]));
  const sorted = importRows
    .map((row: any) => ({ ...row, decision: decisionByImport.get(String(row.id)) ?? null }))
    .sort((a: any, b: any) => Number(Boolean(a.decision)) - Number(Boolean(b.decision)) || new Date(b.occurred_at ?? b.captured_at).getTime() - new Date(a.occurred_at ?? a.captured_at).getTime())
    .slice(0, safeLimit);

  const total = importsCount.count ?? 0;
  const reviewed = decisionsCount.count ?? 0;
  const coverage = total > 0 ? r2((reviewed / total) * 100) : 0;
  return {
    status: total > 0 && reviewed >= total ? "CONCILIADA" : "PARCIAL",
    total_completed_imports: total,
    reviewed,
    confirmed: confirmedCount.count ?? 0,
    no_match: noMatchCount.count ?? 0,
    unresolved: Math.max(0, total - reviewed),
    coverage_pct: coverage,
    items: sorted,
  };
}

// Legacy multi-write reconciliation mutations were removed intentionally.
// Human review currently uses GET only. A future transactional RPC needs
// independent financial/security review and a separately authorized DB change.
