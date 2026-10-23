/**
 * Future-write-only error classifier. NOT imported by the locked POST route.
 * Expose neither raw SQLSTATE messages nor constraint names to clients.
 */
export type ReconciliationPublicError = { status: number; error: string };
const conflicts = new Set([
  "reconciliation_already_reviewed",
  "reconciliation_offer_already_used",
  "reconciliation_index_exists_review_required",
  "reconciliation_duplicate_confirmations_preflight",
  "reconciliation_legacy_overload_review_required",
]);
const invalid = new Set([
  "reconciliation_decision_invalid", "reconciliation_candidate_invalid",
  "reconciliation_actor_or_import_required", "reconciliation_import_time_unverified",
  "reconciliation_offer_time_unverified", "reconciliation_offer_outside_time_window",
  "reconciliation_offer_platform_mismatch", "reconciliation_only_completed_rides",
]);
const unavailable = new Set([
  "reconciliation_import_not_found", "reconciliation_offer_not_found",
  "reconciliation_offer_without_owned_journey",
]);
export function classifyReconciliationWriteError(input: unknown): ReconciliationPublicError {
  if (!input || typeof input !== "object") return { status: 500, error: "reconciliation_write_failed" };
  const error = input as { code?: unknown; message?: unknown };
  if (error.code === "23505") return { status: 409, error: "reconciliation_conflict" };
  // Only fixed, known application messages are converted to public errors.
  if (typeof error.message === "string") {
    if (conflicts.has(error.message)) return { status: 409, error: "reconciliation_conflict" };
    if (invalid.has(error.message)) return { status: 422, error: "reconciliation_candidate_invalid" };
    if (unavailable.has(error.message)) return { status: 404, error: "reconciliation_candidate_unavailable" };
  }
  return { status: 500, error: "reconciliation_write_failed" };
}
