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
/** Align by local clock, not elapsed milliseconds (DST transitions may be 23/25h). */
function localClock(instant: Date, timeZone: string) {
  const fields = new Intl.DateTimeFormat("en-GB", {
    timeZone, hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(instant);
  const get = (name: string) => Number(fields.find((part) => part.type === name)?.value ?? 0);
  return get("hour") * 60 + get("minute");
}
function alignLocalClock(day: ComparisonWindow, targetMinute: number, timeZone: string): ComparisonWindow {
  const start = new Date(day.from).getTime();
  const end = new Date(day.to).getTime();
  let selected: number | null = null;
  let firstLater: number | null = null;
  // Scan 30-minute segments; validate the exact candidate with Intl so DST folds
  // choose the first occurrence and missing local times fall forward safely.
  for (let probe = start; probe < end; probe += 30 * 60_000) {
    const minute = localClock(new Date(probe), timeZone);
    const delta = targetMinute - minute;
    if (delta >= 0 && delta < 30) {
      const candidate = probe + delta * 60_000;
      if (candidate < end && localClock(new Date(candidate), timeZone) === targetMinute) {
        selected = candidate;
        break;
      }
    }
    if (firstLater === null && minute >= targetMinute) firstLater = probe;
  }
  const until = selected ?? firstLater ?? end;
  return { ...day, to: new Date(Math.max(start + 1, Math.min(end, until))).toISOString() };
}
/** Do not expose a future cutoff, including the first seconds after local midnight. */
export function safeComparisonCutoff(nowMs: number, dayStartMs: number, graceMs: number): Date {
  if (![nowMs, dayStartMs, graceMs].every(Number.isFinite) || dayStartMs > nowMs || graceMs < 0) {
    throw new Error("comparison_cutoff_invalid");
  }
  return new Date(Math.min(nowMs, Math.max(dayStartMs + 1, nowMs - graceMs)));
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
  const clockMinute = localClock(now, timeZone);
  const current = { ...today, to: new Date(Math.max(new Date(today.from).getTime() + 1, now.getTime())).toISOString() };
  const aligned = (offset: number) => alignLocalClock(dayAtOffset(now, timeZone, offset), clockMinute, timeZone);
  if (kind === "today_yesterday") {
    return { kind, current, references: [aligned(-1)], aggregation: "single" };
  }
  if (kind === "today_personal_average") {
    return { kind, current, references: Array.from({ length: 7 }, (_, i) => aligned(-(i + 1))), aggregation: "personal_mean" };
  }
  if (kind === "same_weekday") {
    return { kind, current, references: Array.from({ length: 4 }, (_, i) => aligned(-7 * (i + 1))), aggregation: "personal_mean" };
  }
  if (kind === "week_previous") {
    const local = dayParts(now, timeZone);
    const weekday = weekdays[local.weekday];
    if (weekday == null) throw new Error("invalid_weekday");
    const daysSinceMonday = (weekday + 6) % 7;
    const monday = dayAtOffset(now, timeZone, -daysSinceMonday);
    const lastMonday = dayAtOffset(now, timeZone, -daysSinceMonday - 7);
    return {
      kind,
      current: { ...monday, to: new Date(Math.max(new Date(monday.from).getTime() + 1, now.getTime())).toISOString(), label: "Semana atual" },
      references: [{
        ...aligned(-7),
        from: lastMonday.from,
        label: "Semana anterior",
      }],
      aggregation: "single",
    };
  }
  throw new Error("comparison_kind_invalid");
}

/**
 * The current AnalyticsDomain bounds observed offers by timestamp but aggregates
 * realized rides and odometer totals by journey start. These cannot be represented
 * as exact partial-day/partial-week deltas until event-time slicing exists.
 */
export function supportsExactWindowComparison(semantic: string): boolean {
  return semantic === "OBSERVED_OFFER";
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
  const covered = (item: MetricEvidence) =>
    item.semantic === "OBSERVED_OFFER" || item.coverage === 1;
  const valid = current.status === "ok" && current.value !== null && Number.isFinite(current.value) &&
    covered(current) && previous.length > 0 &&
    previous.every((item) => item.status === "ok" && item.value !== null && Number.isFinite(item.value) &&
      covered(item) && item.semantic === current.semantic);
  if (!valid) return { current: current.value, reference: null, absolute: null, percentage: null, comparable: false };
  const reference = previous.reduce((sum, item) => sum + (item.value as number), 0) / previous.length;
  const difference = (current.value as number) - reference;
  return { current: current.value, reference, absolute: difference,
    percentage: reference === 0 ? null : difference / Math.abs(reference) * 100, comparable: true };
}
