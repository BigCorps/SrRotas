/** A read-only status of human review, never evidence of final payment. */
export type ReconciliationCounts = {
  totalCompleted: number;
  reviewed: number;
  confirmed: number;
  noMatch: number;
  withoutOccurrence: number;
  futureOccurrence: number;
};

export function summarizeReconciliationOverview(input: ReconciliationCounts) {
  const valid = (n: number) => Number.isSafeInteger(n) && n >= 0 ? n : 0;
  const total = valid(input.totalCompleted);
  const reviewed = valid(input.reviewed);
  return {
    status: total === 0 ? "SEM_HISTORICO" : reviewed >= total ? "REVISADO" : "PENDENTE",
    total_completed_imports: total,
    reviewed,
    confirmed: valid(input.confirmed),
    no_match: valid(input.noMatch),
    unresolved: Math.max(0, total - reviewed),
    coverage_pct: total > 0 ? Math.round((Math.min(reviewed, total) / total) * 10000) / 100 : null,
    missing_occurrence_time: valid(input.withoutOccurrence),
    future_occurrence_time: valid(input.futureOccurrence),
    scope: "all_imported_completed_rides" as const,
    provenance: "uber_completed_ride_imports+human_reconciliation" as const,
    // Never expose a numeric payout estimate from unreviewed imports.
    final_payment_verified: false as const,
  };
}
