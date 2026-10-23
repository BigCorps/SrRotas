/**
 * A nullable fare is NOT a verified zero fare. Only an explicitly recorded
 * non-negative finite number (or numeric database string) is reconciled.
 * Shared by confirmed ride fares and supplemental session earnings.
 */
export function parseKnownFare(value: unknown): number | null {
  if (typeof value !== "number" && typeof value !== "string") return null;
  if (typeof value === "string" && value.trim() === "") return null;
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount < 0) return null;
  if (amount === 0) return 0; // Normalize explicit -0 to monetary zero.
  return Math.round(amount * 100) / 100;
}
