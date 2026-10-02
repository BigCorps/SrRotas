import { adminSupabase } from "./supabase";

const RANKER_VERSION = "journey-reconciliation-v1";
const LEGACY_AUTO_SOURCE = "uber_history_ocr";

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
      .select("id,journey_id,local_offer_id,observed_at,fare,service_type,pickup_label,destination_label,total_minutes,trip_minutes,total_km,trip_km,per_km,per_hour")
      .eq("driver_id", driverId)
      .eq("id", imported.matched_ride_offer_id)
      .maybeSingle();
    if (hint.error) throw new Error(hint.error.message);
    if (hint.data) byId.set(String(hint.data.id), hint.data as OfferCandidate);
  }

  if (imported.occurred_at) {
    const center = new Date(imported.occurred_at).getTime();
    const from = new Date(center - 75 * 60_000).toISOString();
    const to = new Date(center + 75 * 60_000).toISOString();
    const found = await adminSupabase()
      .from("ride_offers")
      .select("id,journey_id,local_offer_id,observed_at,fare,service_type,pickup_label,destination_label,total_minutes,trip_minutes,total_km,trip_km,per_km,per_hour")
      .eq("driver_id", driverId)
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
  const candidates = offers
    .map((offer) => ({ ...offer, ...scoreReconciliationCandidate(imported, offer) }))
    .filter((item) => item.score >= 30 || String(imported.matched_ride_offer_id ?? "") === String(item.id))
    .sort((a, b) => b.score - a.score || Math.abs(Number(a.fare) - Number(imported.fare)) - Math.abs(Number(b.fare) - Number(imported.fare)))
    .slice(0, 5);

  return {
    import: imported,
    decision,
    candidates,
    ranker_version: RANKER_VERSION,
    note: "Candidatos são sugestões explicáveis. Nenhum candidato é confirmado sem ação humana.",
  };
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

async function removeLegacyOutcomeForOffer(driverId: string, offerId: string | number | null) {
  if (offerId === null) return;
  const offer = await adminSupabase()
    .from("ride_offers")
    .select("id,local_offer_id")
    .eq("driver_id", driverId)
    .eq("id", offerId)
    .maybeSingle();
  if (offer.error) throw new Error(offer.error.message);
  if (!offer.data?.local_offer_id) return;
  const deleted = await adminSupabase()
    .from("ride_outcomes")
    .delete()
    .eq("driver_id", driverId)
    .eq("local_offer_id", offer.data.local_offer_id)
    .eq("source", LEGACY_AUTO_SOURCE);
  if (deleted.error) throw new Error(deleted.error.message);
}

export async function decideReconciliation(
  driverId: string,
  input: { importId: string; decision: "confirmed" | "no_match"; rideOfferId?: string | number | null },
) {
  const imported = await loadImport(driverId, input.importId);
  if (imported.ride_status !== "completed") throw new Error("reconciliation_only_completed_rides");

  if (input.decision === "no_match") {
    await removeLegacyOutcomeForOffer(driverId, imported.matched_ride_offer_id);
    const now = new Date().toISOString();
    const saved = await adminSupabase()
      .from("sr_ride_reconciliations")
      .upsert({
        driver_id: driverId,
        import_id: imported.id,
        decision: "no_match",
        selected_ride_offer_id: null,
        candidate_score: null,
        candidate_version: RANKER_VERSION,
        decided_at: now,
        updated_at: now,
      }, { onConflict: "driver_id,import_id" })
      .select("*")
      .single();
    if (saved.error) throw new Error(saved.error.message);
    const updatedImport = await adminSupabase()
      .from("uber_completed_ride_imports")
      .update({ matched_ride_offer_id: null })
      .eq("driver_id", driverId)
      .eq("id", imported.id);
    if (updatedImport.error) throw new Error(updatedImport.error.message);
    return { ok: true, reconciliation: saved.data };
  }

  if (input.rideOfferId === null || input.rideOfferId === undefined || String(input.rideOfferId).trim() === "") {
    throw new Error("reconciliation_candidate_required");
  }
  const offer = await adminSupabase()
    .from("ride_offers")
    .select("id,journey_id,local_offer_id,observed_at,fare,service_type,pickup_label,destination_label,total_minutes,trip_minutes,total_km,trip_km,per_km,per_hour")
    .eq("driver_id", driverId)
    .eq("id", input.rideOfferId)
    .maybeSingle();
  if (offer.error) throw new Error(offer.error.message);
  if (!offer.data) throw new Error("reconciliation_candidate_not_found");
  const candidate = offer.data as OfferCandidate;
  if (!candidate.journey_id || !candidate.local_offer_id) throw new Error("reconciliation_candidate_without_journey");

  const scoring = scoreReconciliationCandidate(imported, candidate);
  const now = new Date().toISOString();
  await removeLegacyOutcomeForOffer(driverId, imported.matched_ride_offer_id);

  const existing = await adminSupabase()
    .from("ride_outcomes")
    .select("revision")
    .eq("driver_id", driverId)
    .eq("local_offer_id", candidate.local_offer_id)
    .maybeSingle();
  if (existing.error) throw new Error(existing.error.message);
  const revision = Math.max(1, Number(existing.data?.revision ?? 0) + 1);
  const completedAt = imported.occurred_at ?? imported.captured_at;
  const startedAt = imported.duration_seconds !== null
    ? new Date(new Date(completedAt).getTime() - Math.max(0, imported.duration_seconds) * 1000).toISOString()
    : null;

  const outcome = await adminSupabase()
    .from("ride_outcomes")
    .upsert({
      driver_id: driverId,
      device_id: imported.device_id,
      journey_id: candidate.journey_id,
      ride_offer_id: candidate.id,
      local_offer_id: candidate.local_offer_id,
      status: "COMPLETED",
      started_at: startedAt,
      completed_at: completedAt,
      corrected_at: now,
      source: "reconciliation_v1",
      revision,
      updated_at: now,
    }, { onConflict: "driver_id,local_offer_id" });
  if (outcome.error) throw new Error(outcome.error.message);

  const updatedImport = await adminSupabase()
    .from("uber_completed_ride_imports")
    .update({ matched_ride_offer_id: candidate.id })
    .eq("driver_id", driverId)
    .eq("id", imported.id);
  if (updatedImport.error) throw new Error(updatedImport.error.message);

  const saved = await adminSupabase()
    .from("sr_ride_reconciliations")
    .upsert({
      driver_id: driverId,
      import_id: imported.id,
      decision: "confirmed",
      selected_ride_offer_id: candidate.id,
      candidate_score: scoring.score,
      candidate_version: RANKER_VERSION,
      decided_at: now,
      updated_at: now,
    }, { onConflict: "driver_id,import_id" })
    .select("*")
    .single();
  if (saved.error) throw new Error(saved.error.message);

  return { ok: true, reconciliation: saved.data, outcome_source: "reconciliation_v1", score: scoring.score };
}
