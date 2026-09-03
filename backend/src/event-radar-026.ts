import { adminSupabase } from "./supabase";
import { serverEnv } from "./env";

export type RadarOpportunity026 = {
  id?: string;
  source: string;
  external_id: string;
  event_type: string;
  name: string;
  venue_name: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  country_code: string | null;
  lat: number;
  lng: number;
  starts_at: string;
  expected_end_at: string;
  egress_start_at: string;
  egress_end_at: string;
  source_url: string | null;
  confidence: number;
  end_time_source: "source" | "estimated";
  status: "active" | "expired" | "cancelled";
  last_verified_at: string;
  updated_at: string;
  metadata: Record<string, unknown>;
};

type Center = { lat: number; lng: number; weight: number };

type NearbyInput = {
  lat: number;
  lng: number;
  radiusKm: number;
  hours: number;
};

const TM_ENDPOINT = "https://app.ticketmaster.com/discovery/v2/events.json";
const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

function finite(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function round(value: number, digits = 2) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

function addMinutes(value: string, minutes: number) {
  return new Date(new Date(value).getTime() + minutes * 60_000).toISOString();
}

function durationFor(type: string) {
  switch (type) {
    case "sports": return 180;
    case "theatre": return 150;
    case "fair_convention": return 240;
    case "family": return 150;
    case "music": return 180;
    default: return 180;
  }
}

function classify(segmentRaw: unknown, genreRaw: unknown, nameRaw: unknown) {
  const segment = String(segmentRaw ?? "").toLowerCase();
  const genre = String(genreRaw ?? "").toLowerCase();
  const name = String(nameRaw ?? "").toLowerCase();
  const text = `${segment} ${genre} ${name}`;
  if (/sport|futebol|soccer|basket|tennis|corrida/.test(text)) return "sports";
  if (/music|música|concert|show|festival/.test(text)) return "music";
  if (/theatre|theater|teatro|arts|cultura|comedy|comédia/.test(text)) return "theatre";
  if (/fair|feira|expo|convention|convenção|congresso|conference/.test(text)) return "fair_convention";
  if (/family|família|children|kids|infantil/.test(text)) return "family";
  return "event";
}

/** Geohash compacto para usar o parâmetro geoPoint recomendado pela Discovery API. */
export function geohash026(lat: number, lng: number, precision = 6) {
  let latMin = -90, latMax = 90, lngMin = -180, lngMax = 180;
  let even = true, bit = 0, ch = 0, output = "";
  while (output.length < precision) {
    if (even) {
      const mid = (lngMin + lngMax) / 2;
      if (lng >= mid) { ch = (ch << 1) | 1; lngMin = mid; }
      else { ch <<= 1; lngMax = mid; }
    } else {
      const mid = (latMin + latMax) / 2;
      if (lat >= mid) { ch = (ch << 1) | 1; latMin = mid; }
      else { ch <<= 1; latMax = mid; }
    }
    even = !even;
    bit++;
    if (bit === 5) {
      output += BASE32[ch];
      bit = 0;
      ch = 0;
    }
  }
  return output;
}

export function haversineKm026(lat1: number, lng1: number, lat2: number, lng2: number) {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLng = (lng2 - lng1) * rad;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function normalizeTicketmasterEvent(event: any, verifiedAt: string): RadarOpportunity026 | null {
  const id = String(event?.id ?? "").trim();
  const name = String(event?.name ?? "").trim();
  const start = String(event?.dates?.start?.dateTime ?? "").trim();
  const venue = event?._embedded?.venues?.[0];
  const lat = finite(venue?.location?.latitude);
  const lng = finite(venue?.location?.longitude);
  if (!id || !name || !start || lat === null || lng === null) return null;
  if (!Number.isFinite(new Date(start).getTime())) return null;

  const statusCode = String(event?.dates?.status?.code ?? "").toLowerCase();
  if (/cancel/.test(statusCode)) return null;

  const classification = event?.classifications?.[0] ?? {};
  const segment = classification?.segment?.name;
  const genre = classification?.genre?.name;
  const eventType = classify(segment, genre, name);
  const explicitEnd = String(event?.dates?.end?.dateTime ?? "").trim();
  const hasExplicitEnd = Boolean(explicitEnd && Number.isFinite(new Date(explicitEnd).getTime()));
  const expectedEnd = hasExplicitEnd ? new Date(explicitEnd).toISOString() : addMinutes(start, durationFor(eventType));
  const confidence = round(
    Math.max(0.5, Math.min(0.98,
      (hasExplicitEnd ? 0.92 : 0.74) - (eventType === "event" ? 0.06 : 0),
    )),
    2,
  );
  const address = [venue?.address?.line1, venue?.postalCode]
    .map((v) => String(v ?? "").trim())
    .filter(Boolean)
    .join(" · ") || null;

  return {
    source: "ticketmaster",
    external_id: id,
    event_type: eventType,
    name: name.slice(0, 240),
    venue_name: String(venue?.name ?? "").trim().slice(0, 240) || null,
    address,
    city: String(venue?.city?.name ?? "").trim().slice(0, 120) || null,
    state: String(venue?.state?.stateCode ?? venue?.state?.name ?? "").trim().slice(0, 80) || null,
    country_code: String(venue?.country?.countryCode ?? "").trim().slice(0, 3) || null,
    lat,
    lng,
    starts_at: new Date(start).toISOString(),
    expected_end_at: expectedEnd,
    egress_start_at: addMinutes(expectedEnd, -20),
    egress_end_at: addMinutes(expectedEnd, 75),
    source_url: String(event?.url ?? "").trim().slice(0, 1200) || null,
    confidence,
    end_time_source: hasExplicitEnd ? "source" : "estimated",
    status: "active",
    last_verified_at: verifiedAt,
    updated_at: verifiedAt,
    metadata: {
      segment: String(segment ?? "").slice(0, 120) || null,
      genre: String(genre ?? "").slice(0, 120) || null,
      ticketmaster_status: statusCode || null,
    },
  };
}

async function recentCenters(): Promise<Center[]> {
  const since = new Date(Date.now() - 7 * 86_400_000).toISOString();
  const { data, error } = await adminSupabase()
    .from("ride_offers")
    .select("pickup_lat,pickup_lng,destination_lat,destination_lng")
    .gte("observed_at", since)
    .order("observed_at", { ascending: false })
    .limit(3000);
  if (error) throw new Error(error.message);

  const buckets = new Map<string, Center>();
  for (const row of data ?? []) {
    const points = [
      [finite(row.pickup_lat), finite(row.pickup_lng)],
      [finite(row.destination_lat), finite(row.destination_lng)],
    ];
    for (const [lat, lng] of points) {
      if (lat === null || lng === null) continue;
      const centerLat = Math.round(lat * 4) / 4;
      const centerLng = Math.round(lng * 4) / 4;
      const key = `${centerLat.toFixed(2)},${centerLng.toFixed(2)}`;
      const previous = buckets.get(key);
      buckets.set(key, {
        lat: centerLat,
        lng: centerLng,
        weight: (previous?.weight ?? 0) + 1,
      });
    }
  }
  return [...buckets.values()]
    .sort((a, b) => b.weight - a.weight)
    .slice(0, 12);
}

async function ticketmasterBatch(apiKey: string, center: Center | null, verifiedAt: string) {
  const start = new Date().toISOString().replace(/\.\d{3}Z$/, "Z");
  const end = new Date(Date.now() + 7 * 86_400_000).toISOString().replace(/\.\d{3}Z$/, "Z");
  const url = new URL(TM_ENDPOINT);
  url.searchParams.set("apikey", apiKey);
  url.searchParams.set("countryCode", "BR");
  url.searchParams.set("startDateTime", start);
  url.searchParams.set("endDateTime", end);
  url.searchParams.set("size", center ? "100" : "200");
  url.searchParams.set("sort", "date,asc");
  url.searchParams.set("locale", "*");
  if (center) {
    url.searchParams.set("geoPoint", geohash026(center.lat, center.lng, 6));
    url.searchParams.set("radius", "60");
    url.searchParams.set("unit", "km");
  }

  const response = await fetch(url, {
    headers: { Accept: "application/json", "User-Agent": "SrRotas-Radar/0.26" },
    signal: AbortSignal.timeout(12_000),
  });
  if (!response.ok) throw new Error(`ticketmaster_http_${response.status}`);
  const json = await response.json() as any;
  return (json?._embedded?.events ?? [])
    .map((event: any) => normalizeTicketmasterEvent(event, verifiedAt))
    .filter(Boolean) as RadarOpportunity026[];
}

async function recordRun(input: {
  status: string;
  centers: number;
  fetched: number;
  saved: number;
  error?: string | null;
}) {
  await adminSupabase().from("sr_radar_source_runs").insert({
    source: "ticketmaster",
    status: input.status,
    centers_checked: input.centers,
    fetched_count: input.fetched,
    saved_count: input.saved,
    error_message: input.error?.slice(0, 800) ?? null,
  });
}

export async function refreshEventRadar026() {
  const env = serverEnv();
  if (!env.ticketmasterApiKey) {
    return {
      ok: true,
      skipped: true,
      reason: "ticketmaster_api_key_missing",
      fetched: 0,
      saved: 0,
    };
  }

  const verifiedAt = new Date().toISOString();
  let centers: Center[] = [];
  let fetched = 0;
  let saved = 0;
  try {
    centers = await recentCenters();
    const batches = centers.length ? centers.map((center) => center as Center | null) : [null];
    const unique = new Map<string, RadarOpportunity026>();
    for (const center of batches) {
      const rows = await ticketmasterBatch(env.ticketmasterApiKey, center, verifiedAt);
      fetched += rows.length;
      for (const row of rows) unique.set(`${row.source}:${row.external_id}`, row);
    }

    const values = [...unique.values()];
    if (values.length) {
      const result = await adminSupabase()
        .from("sr_event_opportunities")
        .upsert(values, { onConflict: "source,external_id" })
        .select("id");
      if (result.error) throw new Error(result.error.message);
      saved = result.data?.length ?? values.length;
    }

    await adminSupabase()
      .from("sr_event_opportunities")
      .update({ status: "expired" })
      .eq("status", "active")
      .lt("egress_end_at", new Date(Date.now() - 2 * 60 * 60_000).toISOString());

    await recordRun({ status: "ok", centers: batches.length, fetched, saved });
    return { ok: true, skipped: false, centers: batches.length, fetched, saved };
  } catch (error) {
    const message = error instanceof Error ? error.message : "radar_refresh_failed";
    await recordRun({ status: "error", centers: centers.length, fetched, saved, error: message }).catch(() => undefined);
    throw error;
  }
}

export async function nearbyEventRadar026(input: NearbyInput) {
  const now = new Date();
  const maxStart = new Date(now.getTime() + input.hours * 60 * 60_000).toISOString();
  const minEgress = new Date(now.getTime() - 30 * 60_000).toISOString();
  const { data, error } = await adminSupabase()
    .from("sr_event_opportunities")
    .select("id,source,event_type,name,venue_name,address,lat,lng,starts_at,expected_end_at,egress_start_at,egress_end_at,source_url,confidence,last_verified_at")
    .eq("status", "active")
    .gte("egress_end_at", minEgress)
    .lte("starts_at", maxStart)
    .order("egress_start_at", { ascending: true })
    .limit(500);
  if (error) throw new Error(error.message);

  const opportunities = (data ?? []).map((row: any) => {
    const lat = Number(row.lat);
    const lng = Number(row.lng);
    const distanceKm = haversineKm026(input.lat, input.lng, lat, lng);
    const minutesToEgress = (new Date(row.egress_start_at).getTime() - now.getTime()) / 60_000;
    const temporalBoost = minutesToEgress <= 15 ? 28 : minutesToEgress <= 60 ? 20 : minutesToEgress <= 180 ? 10 : 0;
    const score = Number(row.confidence ?? 0) * 100 + temporalBoost - distanceKm * 3.2;
    return {
      ...row,
      distance_km: round(distanceKm, 1),
      score: round(score, 2),
    };
  })
    .filter((row: any) => row.distance_km <= input.radiusKm)
    .sort((a: any, b: any) => b.score - a.score)
    .slice(0, 12)
    .map(({ score: _score, ...row }: any) => row);

  const last = await adminSupabase()
    .from("sr_radar_source_runs")
    .select("status,created_at")
    .eq("source", "ticketmaster")
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  const sourceStatus = !serverEnv().ticketmasterApiKey
    ? "source_not_configured"
    : (last.data?.status ?? "configured_no_refresh");

  return {
    opportunities,
    source_status: sourceStatus,
    refreshed_at: last.data?.created_at ?? null,
    note: "Radar usa eventos estruturados e uma janela estimada de saída. Não garante demanda nem corrida.",
  };
}
