import type { QuestionContext, ResolvedPeriod } from "./contracts";

function localParts(date: Date, timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  }).formatToParts(date);
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return {
    year: Number(value("year")),
    month: Number(value("month")),
    day: Number(value("day")),
    weekday: value("weekday"),
  };
}

function zonedMidnightUtc(year: number, month: number, day: number, timeZone: string) {
  let candidate = Date.UTC(year, month - 1, day, 0, 0, 0, 0);
  for (let i = 0; i < 3; i += 1) {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    }).formatToParts(new Date(candidate));
    const get = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? 0);
    const asUtc = Date.UTC(get("year"), get("month") - 1, get("day"), get("hour") % 24, get("minute"), get("second"));
    const diff = asUtc - Date.UTC(year, month - 1, day, 0, 0, 0, 0);
    candidate -= diff;
  }
  return new Date(candidate);
}

function addLocalDays(base: { year: number; month: number; day: number }, days: number) {
  const date = new Date(Date.UTC(base.year, base.month - 1, base.day + days, 12));
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate() };
}

function labelDate(date: Date, timeZone: string) {
  return new Intl.DateTimeFormat("pt-BR", { timeZone, day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

function range(kind: ResolvedPeriod["kind"], label: string, from: Date, to: Date): ResolvedPeriod {
  return { kind, label, from: from.toISOString(), to: to.toISOString() };
}

function calendarDay(year: number, month: number, day: number, timeZone: string, kind: ResolvedPeriod["kind"] = "CALENDAR_DAY") {
  const start = zonedMidnightUtc(year, month, day, timeZone);
  const next = addLocalDays({ year, month, day }, 1);
  const end = zonedMidnightUtc(next.year, next.month, next.day, timeZone);
  return range(kind, labelDate(start, timeZone), start, end);
}

function parseCalendarDate(text: string, now: Date, timeZone: string) {
  const iso = text.match(/\b(20\d{2})-(\d{1,2})-(\d{1,2})\b/);
  if (iso) return calendarDay(Number(iso[1]), Number(iso[2]), Number(iso[3]), timeZone);
  const br = text.match(/\b(\d{1,2})[\/-](\d{1,2})(?:[\/-](20\d{2}))?\b/);
  if (!br) return null;
  const local = localParts(now, timeZone);
  const year = Number(br[3] || local.year);
  return calendarDay(year, Number(br[2]), Number(br[1]), timeZone);
}

export function resolveQuestionPeriod(
  normalizedQuestion: string,
  timeZone: string,
  context?: QuestionContext | null,
  now = new Date(),
  defaultDays = 7,
): { period: ResolvedPeriod; inherited: boolean } {
  const explicit = parseCalendarDate(normalizedQuestion, now, timeZone);
  if (explicit) return { period: explicit, inherited: false };

  const local = localParts(now, timeZone);
  const todayStart = zonedMidnightUtc(local.year, local.month, local.day, timeZone);
  const tomorrowLocal = addLocalDays(local, 1);
  const tomorrow = zonedMidnightUtc(tomorrowLocal.year, tomorrowLocal.month, tomorrowLocal.day, timeZone);

  if (/\bhoje\b/.test(normalizedQuestion)) return { period: range("TODAY", "Hoje", todayStart, tomorrow), inherited: false };
  if (/\bontem\b/.test(normalizedQuestion)) {
    const y = addLocalDays(local, -1);
    return { period: range("YESTERDAY", "Ontem", zonedMidnightUtc(y.year, y.month, y.day, timeZone), todayStart), inherited: false };
  }

  const lastDays = normalizedQuestion.match(/\bultimos?\s+(\d{1,3})\s+dias?\b/);
  if (lastDays) {
    const days = Math.max(1, Math.min(90, Number(lastDays[1])));
    return { period: range("LAST_N_DAYS", `Últimos ${days} dias`, new Date(now.getTime() - days * 86_400_000), now), inherited: false };
  }

  const weekdayIndex: Record<string, number> = { Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6, Sun: 7 };
  const weekday = weekdayIndex[local.weekday] ?? 1;
  const mondayLocal = addLocalDays(local, -(weekday - 1));
  const monday = zonedMidnightUtc(mondayLocal.year, mondayLocal.month, mondayLocal.day, timeZone);
  if (/\b(esta|essa)\s+semana\b/.test(normalizedQuestion)) return { period: range("THIS_WEEK", "Esta semana", monday, now), inherited: false };
  if (/\bsemana\s+passada\b/.test(normalizedQuestion)) {
    const previousMonday = addLocalDays(mondayLocal, -7);
    return { period: range("LAST_WEEK", "Semana passada", zonedMidnightUtc(previousMonday.year, previousMonday.month, previousMonday.day, timeZone), monday), inherited: false };
  }

  const monthStart = zonedMidnightUtc(local.year, local.month, 1, timeZone);
  if (/\b(este|esse)\s+mes\b/.test(normalizedQuestion)) return { period: range("THIS_MONTH", "Este mês", monthStart, now), inherited: false };
  if (/\bmes\s+passado\b/.test(normalizedQuestion)) {
    const prev = new Date(Date.UTC(local.year, local.month - 2, 1, 12));
    const previousMonth = zonedMidnightUtc(prev.getUTCFullYear(), prev.getUTCMonth() + 1, 1, timeZone);
    return { period: range("LAST_MONTH", "Mês passado", previousMonth, monthStart), inherited: false };
  }

  if (context?.period) return { period: { ...context.period, kind: "CONTEXT" }, inherited: true };
  const safeDefaultDays = Math.max(1, Math.min(90, Math.floor(defaultDays || 7)));
  return { period: range("LAST_N_DAYS", `Últimos ${safeDefaultDays} dias`, new Date(now.getTime() - safeDefaultDays * 86_400_000), now), inherited: false };
}

export function previousEquivalentPeriod(period: ResolvedPeriod): ResolvedPeriod {
  const from = new Date(period.from).getTime();
  const to = new Date(period.to).getTime();
  const duration = Math.max(1, to - from);
  return {
    kind: "CUSTOM",
    label: `Período anterior a ${period.label.toLowerCase()}`,
    from: new Date(from - duration).toISOString(),
    to: new Date(from).toISOString(),
  };
}
