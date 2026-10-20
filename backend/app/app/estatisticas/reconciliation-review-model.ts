/** Read-only manual-review presentation. No backend writes and no payout inference. */
export type ImportedCompletedRide = {
  id: string;
  source_key?: string;
  captured_at: string | null;
  occurred_at: string | null;
  fare: number | string | null;
  service_type: string | null;
  pickup_label: string | null;
  destination_label: string | null;
  ride_status: string;
  decision?: { decision: "confirmed" | "no_match"; selected_ride_offer_id: string | number | null } | null;
};
export type OfferSuggestion = {
  id: string | number;
  observed_at: string;
  fare: number | string | null;
  service_type?: string | null;
  pickup_label: string | null;
  destination_label: string | null;
  score: number;
  reasons?: string[];
};
export type ReviewListing = {
  total_completed_imports: number;
  unresolved: number;
  items: ImportedCompletedRide[];
};
export type ReviewDetail = {
  import: ImportedCompletedRide;
  decision?: ImportedCompletedRide["decision"];
  candidates: OfferSuggestion[];
  excluded_candidates?: number;
  note: string;
};
export type DraftChoice = { kind: "match"; offerId: string } | { kind: "no_match" } | null;

export function pendingImportedRides(listing: ReviewListing | null): ImportedCompletedRide[] {
  return (listing?.items ?? []).filter((item) =>
    item.ride_status === "completed" && !item.decision,
  );
}

export function isValidCandidateChoice(detail: ReviewDetail | null, offerId: string): boolean {
  return Boolean(detail && !detail.decision && !detail.import.decision && detail.import.ride_status === "completed" &&
    detail.candidates.some((candidate) => String(candidate.id) === offerId));
}

export function historicalTimeStatus(value: string | null | undefined, nowMs: number) {
  if (!value) return "Horário da corrida não identificado";
  const ms = Date.parse(value);
  if (!Number.isFinite(ms)) return "Horário inválido — verificar";
  if (ms > nowMs) return "Data futura — verificar antes da conciliação";
  return null;
}

export function readableDate(value: string | null | undefined) {
  if (!value) return "Não informado";
  const parsed = new Date(value);
  if (!Number.isFinite(parsed.getTime())) return "Data não identificada";
  return parsed.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function readableFare(value: number | string | null | undefined) {
  if (value === null || value === undefined || typeof value === "string" && !value.trim()) return "—";
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0
    ? parsed.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })
    : "—";
}
