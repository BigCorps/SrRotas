import { ensurePreferences } from "./preferences";
import { continuityEstimate } from "./regional-intelligence";

const TTL_MS = 5 * 60_000;
const continuityCache = new Map<string, { at: number; value: any }>();
const optInCache = new Map<string, { at: number; value: Promise<boolean> }>();

function timeBucket(value: string) {
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) throw new Error("invalid_eta");
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "America/Sao_Paulo", weekday: "short", hour: "2-digit", hour12: false,
  }).formatToParts(d);
  const wd: Record<string, number> = { Mon:1,Tue:2,Wed:3,Thu:4,Fri:5,Sat:6,Sun:7 };
  const weekday = wd[parts.find((x) => x.type === "weekday")?.value || "Mon"] || 1;
  const hour = Number(parts.find((x) => x.type === "hour")?.value || 0) % 24;
  return `${weekday}:${Math.floor(hour / 3) * 3}`;
}

async function collectiveOptIn(driverId: string) {
  const now = Date.now();
  const cached = optInCache.get(driverId);
  if (cached && now - cached.at < TTL_MS) return cached.value;
  const value = ensurePreferences(driverId).then((p) => Boolean(p.collective_stats_opt_in));
  optInCache.set(driverId, { at: now, value });
  return value;
}

/**
 * Adapter do Radar sobre o motor canônico de continuidade.
 * Não recalcula P10: apenas aplica opt-in da Base Coletiva e cache curto,
 * pois continuidade histórica muda lentamente e o runtime pode atualizar a UI a cada minuto.
 */
export async function radarContinuityEstimateV1(
  driverId: string, cell: string, eta: string, days = 60,
) {
  const key = `${driverId}|${cell}|${timeBucket(eta)}|${days}`;
  const now = Date.now();
  const cached = continuityCache.get(key);
  if (cached && now - cached.at < TTL_MS) return cached.value;

  const [raw, optIn] = await Promise.all([
    continuityEstimate(driverId, cell, eta, days),
    collectiveOptIn(driverId),
  ]);
  const personalReady = raw?.personal?.p10?.probability_pct != null;
  const collectiveReady = optIn && raw?.collective?.p10?.probability_pct != null;
  const value = {
    ...raw,
    collective_opt_in: optIn,
    collective: optIn ? raw.collective : null,
    preferred_source: personalReady ? "personal" : collectiveReady ? "collective" : "insufficient",
  };
  continuityCache.set(key, { at: now, value });
  if (continuityCache.size > 500) {
    for (const [k, v] of continuityCache) if (now - v.at >= TTL_MS) continuityCache.delete(k);
  }
  return value;
}
