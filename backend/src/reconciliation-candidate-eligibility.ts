/** Pure read-only candidate gate; mirrors the proposed SQL contract. */
export const RECONCILIATION_MAX_DELTA_MINUTES = 75;
const MAX_DELTA_MS = RECONCILIATION_MAX_DELTA_MINUTES * 60_000;
type Import = { driver_id: string; ride_status: string; occurred_at: string | null };
type Offer = { driver_id: string; platform: string | null; observed_at: string | null; journey_id: string | null; local_offer_id: string | null };
export type EligibilityFailure = "import_not_completed" | "driver_mismatch" | "import_time_unverified" | "offer_time_unverified" | "platform_not_uber" | "missing_journey" | "outside_time_window";
export function reconciliationCandidateEligibility(
  imported: Import, offer: Offer, nowMs: number = Date.now(),
): { eligible: true; reason: null } | { eligible: false; reason: EligibilityFailure } {
  const reject = (reason: EligibilityFailure) => ({ eligible: false as const, reason });
  if (imported.ride_status !== "completed") return reject("import_not_completed");
  if (!imported.driver_id || imported.driver_id !== offer.driver_id) return reject("driver_mismatch");
  const occurred = imported.occurred_at ? Date.parse(imported.occurred_at) : NaN;
  if (!Number.isFinite(occurred) || occurred > nowMs) return reject("import_time_unverified");
  const observed = offer.observed_at ? Date.parse(offer.observed_at) : NaN;
  if (!Number.isFinite(observed) || observed > nowMs) return reject("offer_time_unverified");
  if (offer.platform !== "uber") return reject("platform_not_uber");
  if (!offer.journey_id || !offer.local_offer_id) return reject("missing_journey");
  if (Math.abs(observed - occurred) > MAX_DELTA_MS) return reject("outside_time_window");
  return { eligible: true, reason: null };
}
