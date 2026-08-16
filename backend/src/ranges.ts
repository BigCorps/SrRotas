export function resolveRange(from?: string, to?: string, defaultDays = 7) {
  const end = to ? new Date(to) : new Date();
  const start = from ? new Date(from) : new Date(end.getTime() - defaultDays * 86400000);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || start >= end) {
    throw new Error("invalid_date_range");
  }
  const maxMs = 366 * 86400000;
  if (end.getTime() - start.getTime() > maxMs) throw new Error("date_range_too_large");
  return { from: start.toISOString(), to: end.toISOString() };
}
