import { createHash } from "node:crypto";
import { adminSupabase } from "./supabase";

const EVENT_TYPES = new Set([
  "event", "music", "sports", "theatre", "fair_convention", "family",
  "airport", "bus_terminal", "mall", "cultural", "mobility_hub",
]);
const STATUSES = new Set(["active", "expired", "cancelled"]);

type RawEvent = Record<string, unknown>;

export type RadarIngestRequest0261 = {
  events: RawEvent[];
  snapshot_complete?: boolean;
};

function text(value: unknown, max = 240): string | null {
  const normalized = String(value ?? "").trim();
  return normalized ? normalized.slice(0, max) : null;
}

function finite(value: unknown): number | null {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function iso(value: unknown): string | null {
  const raw = String(value ?? "").trim();
  if (!raw) return null;
  const date = new Date(raw);
  return Number.isFinite(date.getTime()) ? date.toISOString() : null;
}

function addMinutes(value: string, minutes: number): string {
  return new Date(new Date(value).getTime() + minutes * 60_000).toISOString();
}

function durationMinutes(type: string): number {
  switch (type) {
    case "sports": return 180;
    case "theatre": return 150;
    case "fair_convention": return 240;
    case "family": return 150;
    case "airport":
    case "bus_terminal":
    case "mall":
    case "mobility_hub": return 120;
    default: return 180;
  }
}

function slug(value: unknown): string | null {
  const raw = text(value, 80)?.toLowerCase();
  if (!raw) return null;
  const normalized = raw
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9_-]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 80);
  return normalized || null;
}

function deterministicId(source: string, name: string, venue: string | null, startsAt: string): string {
  return createHash("sha256")
    .update([source, name.toLowerCase(), (venue ?? "").toLowerCase(), startsAt].join("|"))
    .digest("hex")
    .slice(0, 48);
}

export function normalizeRadarEvent0261(input: RawEvent, verifiedAt = new Date().toISOString()) {
  const source = slug(input.source);
  const name = text(input.name, 240);
  const venueName = text(input.venue_name, 240);
  const startsAt = iso(input.starts_at);
  const lat = finite(input.lat);
  const lng = finite(input.lng);
  if (!source) throw new Error("source_required");
  if (!name) throw new Error("name_required");
  if (!startsAt) throw new Error("starts_at_invalid");
  if (lat === null || lat < -90 || lat > 90) throw new Error("lat_invalid");
  if (lng === null || lng < -180 || lng > 180) throw new Error("lng_invalid");

  const requestedType = slug(input.event_type) ?? "event";
  const eventType = EVENT_TYPES.has(requestedType) ? requestedType : "event";
  const explicitEnd = iso(input.expected_end_at);
  const expectedEndAt = explicitEnd ?? addMinutes(startsAt, durationMinutes(eventType));
  if (new Date(expectedEndAt).getTime() < new Date(startsAt).getTime()) {
    throw new Error("expected_end_before_start");
  }
  const egressStartAt = iso(input.egress_start_at) ?? addMinutes(expectedEndAt, -20);
  const egressEndAt = iso(input.egress_end_at) ?? addMinutes(expectedEndAt, 75);
  if (new Date(egressEndAt).getTime() < new Date(egressStartAt).getTime()) {
    throw new Error("egress_end_before_start");
  }

  const confidenceRaw = finite(input.confidence);
  const confidence = Math.max(0, Math.min(1, confidenceRaw ?? (explicitEnd ? 0.82 : 0.70)));
  const statusRaw = String(input.status ?? "active").toLowerCase();
  const status = STATUSES.has(statusRaw) ? statusRaw : "active";
  const externalId = text(input.external_id, 160) ?? deterministicId(source, name, venueName, startsAt);
  const metadata = input.metadata && typeof input.metadata === "object" && !Array.isArray(input.metadata)
    ? input.metadata as Record<string, unknown>
    : {};

  return {
    source,
    external_id: externalId,
    event_type: eventType,
    name,
    venue_name: venueName,
    address: text(input.address, 500),
    city: text(input.city, 120),
    state: text(input.state, 80),
    country_code: (text(input.country_code, 3) ?? "BR").toUpperCase(),
    lat,
    lng,
    starts_at: startsAt,
    expected_end_at: expectedEndAt,
    egress_start_at: egressStartAt,
    egress_end_at: egressEndAt,
    source_url: text(input.source_url, 1200),
    confidence,
    end_time_source: explicitEnd ? "source" : "estimated",
    status,
    last_verified_at: verifiedAt,
    updated_at: verifiedAt,
    metadata,
  };
}

async function recordRun(source: string, status: "ok" | "error" | "skipped", fetched: number, saved: number, error?: string) {
  await adminSupabase().from("sr_radar_source_runs").insert({
    source,
    status,
    centers_checked: 0,
    fetched_count: fetched,
    saved_count: saved,
    error_message: error?.slice(0, 800) ?? null,
  });
}

export async function ingestRadarEvents0261(input: RadarIngestRequest0261) {
  if (!Array.isArray(input.events) || input.events.length === 0) {
    throw new Error("events_required");
  }
  if (input.events.length > 500) throw new Error("too_many_events");

  const verifiedAt = new Date().toISOString();
  const rows = input.events.map((event) => normalizeRadarEvent0261(event, verifiedAt));
  const sources = [...new Set(rows.map((row) => row.source))];
  if (input.snapshot_complete && sources.length !== 1) {
    throw new Error("snapshot_requires_single_source");
  }

  const { data, error } = await adminSupabase()
    .from("sr_event_opportunities")
    .upsert(rows, { onConflict: "source,external_id" })
    .select("id,source,external_id");
  if (error) {
    await recordRun(sources[0] ?? "external", "error", rows.length, 0, error.message).catch(() => undefined);
    throw new Error(error.message);
  }

  let expired = 0;

  // Limpeza temporal independente da fonte: eventos cuja janela de saída já
  // terminou há mais de duas horas deixam de ser candidatos mesmo quando um
  // scraper externo não envia snapshot_complete. O registro permanece para
  // auditoria; apenas o status muda para expired.
  const temporalExpiry = await adminSupabase()
    .from("sr_event_opportunities")
    .update({ status: "expired", updated_at: verifiedAt })
    .eq("status", "active")
    .lt("egress_end_at", new Date(Date.now() - 2 * 60 * 60_000).toISOString())
    .select("id");
  if (temporalExpiry.error) throw new Error(temporalExpiry.error.message);
  expired += temporalExpiry.data?.length ?? 0;

  if (input.snapshot_complete) {
    const source = sources[0];
    const ids = rows.map((row) => row.external_id);
    const { data: active, error: activeError } = await adminSupabase()
      .from("sr_event_opportunities")
      .select("id,external_id")
      .eq("source", source)
      .eq("status", "active");
    if (activeError) throw new Error(activeError.message);
    const keep = new Set(ids);
    const staleIds = (active ?? []).filter((row: any) => !keep.has(String(row.external_id))).map((row: any) => row.id);
    if (staleIds.length) {
      const updated = await adminSupabase()
        .from("sr_event_opportunities")
        .update({ status: "expired", updated_at: verifiedAt })
        .in("id", staleIds)
        .select("id");
      if (updated.error) throw new Error(updated.error.message);
      expired += updated.data?.length ?? staleIds.length;
    }
  }

  for (const source of sources) {
    const count = rows.filter((row) => row.source === source).length;
    await recordRun(source, "ok", count, count);
  }
  return { ok: true, received: rows.length, saved: data?.length ?? rows.length, expired, sources };
}

export async function listRadarEvents0261(limit = 200) {
  const { data, error } = await adminSupabase()
    .from("sr_event_opportunities")
    .select("id,source,external_id,event_type,name,venue_name,address,city,state,country_code,lat,lng,starts_at,expected_end_at,egress_start_at,egress_end_at,source_url,confidence,end_time_source,status,last_verified_at,updated_at")
    .order("starts_at", { ascending: false })
    .limit(Math.max(1, Math.min(limit, 500)));
  if (error) throw new Error(error.message);
  return data ?? [];
}

export async function updateRadarEvent0261(id: string, patch: RawEvent) {
  const { data: current, error: readError } = await adminSupabase()
    .from("sr_event_opportunities")
    .select("*")
    .eq("id", id)
    .maybeSingle();
  if (readError) throw new Error(readError.message);
  if (!current) throw new Error("event_not_found");

  const merged = normalizeRadarEvent0261({ ...current, ...patch, external_id: current.external_id, source: current.source });
  if (patch.expected_end_at === undefined) {
    merged.end_time_source = current.end_time_source === "source" ? "source" : "estimated";
  }
  const { data, error } = await adminSupabase()
    .from("sr_event_opportunities")
    .update(merged)
    .eq("id", id)
    .select("*")
    .single();
  if (error) throw new Error(error.message);
  return data;
}

export async function setRadarEventStatus0261(id: string, status: "active" | "expired" | "cancelled") {
  const { data, error } = await adminSupabase()
    .from("sr_event_opportunities")
    .update({ status, updated_at: new Date().toISOString() })
    .eq("id", id)
    .select("id,status")
    .single();
  if (error) throw new Error(error.message);
  return data;
}
