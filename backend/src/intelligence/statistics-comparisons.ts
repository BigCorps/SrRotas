import { resolveQuestionPeriod } from "./period-resolver";

/** Plans equal-duration, timezone-aware comparison windows without touching financial semantics. */
export type SmartComparisonKind = "today_yesterday" | "today_personal_average" | "same_weekday" | "week_previous";
export type ComparisonWindow = { from: string; to: string; label: string };
export type SmartComparisonPlan = { kind: SmartComparisonKind; current: ComparisonWindow; references: ComparisonWindow[]; aggregation: "single" | "personal_mean" };

function dayParts(now: Date, timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone, year: "numeric", month: "2-digit", day: "2-digit", weekday: "short",
  }).formatToParts(now);
  const get = (field: string) => parts.find((p) => p.type === field)?.value ?? "";
  return { year: Number(get("year")), month: Number(get("month")), day: Number(get("day")), weekday: get("weekday") };
}
function dayAtOffset(now: Date, timeZone: string, offsetDays: number): ComparisonWindow {
  const parts = dayParts(now, timeZone);
  const date = new Date(Date.UTC(parts.year, parts.month - 1, parts.day + offsetDays, 12));
  const iso = [date.getUTCFullYear(), String(date.getUTCMonth() + 1).padStart(2, "0"), String(date.getUTCDate()).padStart(2, "0")].join("-");
  const period = resolveQuestionPeriod(iso, timeZone, null, now).period;
  return { from: period.from, to: period.to, label: iso };
}
function partialWindow(day: ComparisonWindow, elapsedMs: number): ComparisonWindow {
  const start = new Date(day.from).getTime();
  const end = new Date(day.to).getTime();
  const to = Math.max(start + 1, Math.min(end, start + elapsedMs));
  return { ...day, to: new Date(to).toISOString() };
}
const weekdays: Record<string, number> = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };

/**
 * No look-ahead: "today" ends at now, not midnight tomorrow.
 * Prior days use equivalent elapsed time (clamped on daylight-saving transitions).
 * Weekly comparisons align Monday-to-Monday.
 * Personal mean samples the same elapsed daypart on seven previous calendar days.
 */
export function planSmartComparison(kind: SmartComparisonKind, timeZone: string, now = new Date()): SmartComparisonPlan {
  if (!Number.isFinite(now.getTime())) throw new Error("invalid_now");
  const today = dayAtOffset(now, timeZone, 0);
  const elapsed = Math.max(1, now.getTime() - new Date(today.from).getTime());
  if (kind === "today_yesterday") {
    return { kind, current: partialWindow(today, elapsed), references: [partialWindow(dayAtOffset(now, timeZone, -1), elapsed)], aggregation: "single" };
  }
  if (kind === "today_personal_average") {
    return { kind, current: partialWindow(today, elapsed), references: Array.from({ length: 7 }, (_, i) =>
      partialWindow(dayAtOffset(now, timeZone, -(i + 1)), elapsed)), aggregation: "personal_mean" };
  }
  if (kind === "same_weekday") {
    return { kind, current: partialWindow(today, elapsed), references: Array.from({ length: 4 }, (_, i) =>
      partialWindow(dayAtOffset(now, timeZone, -7 * (i + 1)), elapsed)), aggregation: "personal_mean" };
  }
  if (kind === "week_previous") {
    const local = dayParts(now, timeZone);
    const weekday = weekdays[local.weekday];
    if (weekday == null) throw new Error("invalid_weekday");
    const daysSinceMonday = (weekday + 6) % 7;
    const monday = dayAtOffset(now, timeZone, -daysSinceMonday);
    const lastMonday = dayAtOffset(now, timeZone, -daysSinceMonday - 7);
    const weekElapsed = now.getTime() - new Date(monday.from).getTime();
    return { kind, current: { ...monday, to: now.toISOString(), label: "Semana atual" },
      references: [{ ...partialWindow({ ...lastMonday, to: dayAtOffset(now, timeZone, -daysSinceMonday).from, label: "Semana anterior" }, weekElapsed),
        label: "Semana anterior" }], aggregation: "single" };
  }
  throw new Error("comparison_kind_invalid");
}

export type MetricEvidence = {
  value: number | null;
  semantic: string;
  status: "ok" | "insufficient" | "not_ready";
  coverage: number | null;
};
export type MetricDelta = { current: number | null; reference: number | null; absolute: number | null; percentage: number | null; comparable: boolean };

/** Null and partial coverage are not silently turned into zero or guaranteed revenue. */
export function comparableMetric(current: MetricEvidence, previous: MetricEvidence[]): MetricDelta {
  const valid = current.status === "ok" && current.value !== null && Number.isFinite(current.value) &&
    current.coverage === 1 && previous.length > 0 &&
    previous.every((item) => item.status === "ok" && item.value !== null && Number.isFinite(item.value) &&
      item.coverage === 1 && item.semantic === current.semantic);
  if (!valid) return { current: current.value, reference: null, absolute: null, percentage: null, comparable: false };
  const reference = previous.reduce((sum, item) => sum + (item.value as number), 0) / previous.length;
  const difference = (current.value as number) - reference;
  return { current: current.value, reference, absolute: difference,
    percentage: reference === 0 ? null : difference / Math.abs(reference) * 100, comparable: true };
}
