import { authenticateDevice } from "@/src/device-auth";
import { adminSupabase } from "@/src/supabase";
import { fetchOffers } from "@/src/analytics";

export const runtime = "nodejs";

function numberOrNull(value: unknown) {
  if (value === null || value === undefined || value === "") return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function textOrNull(value: unknown, max = 300) {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  return text ? text.slice(0, max) : null;
}

function coordinate(value: unknown, min: number, max: number) {
  const n = numberOrNull(value);
  return n !== null && n >= min && n <= max ? n : null;
}

function isoOrNull(value: unknown) {
  const text = textOrNull(value, 80);
  if (!text) return null;
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function contextFields(body: any) {
  const geocodeStatus = String(body?.geocode_status ?? "unresolved");
  return {
    pickup_label: textOrNull(body?.pickup_label),
    destination_label: textOrNull(body?.destination_label),
    pickup_lat: coordinate(body?.pickup_lat, -90, 90),
    pickup_lng: coordinate(body?.pickup_lng, -180, 180),
    destination_lat: coordinate(body?.destination_lat, -90, 90),
    destination_lng: coordinate(body?.destination_lng, -180, 180),
    pickup_cell: textOrNull(body?.pickup_cell, 80),
    destination_cell: textOrNull(body?.destination_cell, 80),
    estimated_arrival_at: isoOrNull(body?.estimated_arrival_at),
    context_confidence: Math.max(0, Math.min(1, numberOrNull(body?.context_confidence) ?? 0)),
    geocode_status: ["pending", "resolved", "partial", "unresolved"].includes(geocodeStatus)
      ? geocodeStatus
      : "unresolved",
    geocode_source: textOrNull(body?.geocode_source, 120),
    context_version: textOrNull(body?.context_version, 80) ?? "unknown",
    context_source_type: textOrNull(body?.context_source_type, 60) ?? "live_ocr",
    context_time_source: textOrNull(body?.context_time_source, 60) ?? "system_observed_at",
  };
}

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => null);
  if (!body) return Response.json({ error: "invalid_json" }, { status: 400 });

  const fare = numberOrNull(body.fare);
  const dedupeKey = String(body.dedupe_key ?? "").trim();
  if (fare === null || fare <= 0 || !dedupeKey) return Response.json({ error: "invalid_offer" }, { status: 400 });
  const shareRawText = body.share_raw_text === true;
  const journeyId = String(body.journey_id ?? "").trim() || null;
  if (journeyId) {
    const linked = await adminSupabase()
      .from("driver_journeys")
      .select("id")
      .eq("id", journeyId)
      .eq("driver_id", auth.driverId)
      .maybeSingle();
    if (linked.error) return Response.json({ error: linked.error.message }, { status: 500 });
    if (!linked.data) return Response.json({ error: "invalid_journey" }, { status: 400 });
  }

  const row = {
    driver_id: auth.driverId,
    device_id: auth.deviceId,
    local_offer_id: textOrNull(body.local_id, 100),
    journey_id: journeyId,
    platform: String(body.platform ?? "uber").slice(0, 30),
    observed_at: body.observed_at ? new Date(String(body.observed_at)).toISOString() : new Date().toISOString(),
    source_package: String(body.source_package ?? "").slice(0, 160),
    capture_method: String(body.capture_method ?? "unknown").slice(0, 60),
    raw_text: shareRawText ? String(body.raw_text ?? "").slice(0, 12000) : "",
    raw_text_shared: shareRawText,
    fare,
    pickup_km: numberOrNull(body.pickup_km),
    trip_km: numberOrNull(body.trip_km),
    total_km: numberOrNull(body.total_km),
    pickup_minutes: numberOrNull(body.pickup_minutes),
    trip_minutes: numberOrNull(body.trip_minutes),
    total_minutes: numberOrNull(body.total_minutes),
    per_km: numberOrNull(body.per_km),
    per_hour: numberOrNull(body.per_hour),
    per_minute: numberOrNull(body.per_minute),
    estimated_cost: numberOrNull(body.estimated_cost),
    estimated_profit: numberOrNull(body.estimated_profit),
    profit_per_hour: numberOrNull(body.profit_per_hour),
    profit_percent: numberOrNull(body.profit_percent),
    passenger_rating: numberOrNull(body.passenger_rating),
    advertised_per_km: numberOrNull(body.advertised_per_km),
    service_type: String(body.service_type ?? "unknown").slice(0, 40),
    verdict: ["boa", "regular", "ruim"].includes(String(body.verdict)) ? String(body.verdict) : "regular",
    confidence: Math.max(0, Math.min(1, numberOrNull(body.confidence) ?? 0.5)),
    offer_type: ["exclusive", "radar"].includes(String(body.offer_type)) ? String(body.offer_type) : "exclusive",
    parser_version: String(body.parser_version ?? "unknown").slice(0, 80),
    dedupe_key: dedupeKey.slice(0, 100),
    ...contextFields(body),
  };

  const { data, error } = await adminSupabase()
    .from("ride_offers")
    .upsert(row, { onConflict: "device_id,dedupe_key", ignoreDuplicates: true })
    .select("id")
    .maybeSingle();
  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true, id: data?.id ?? null });
}


export async function PATCH(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json().catch(() => null);
  if (!body) return Response.json({ error: "invalid_json" }, { status: 400 });

  const dedupeKey = String(body.dedupe_key ?? "").trim();
  if (!dedupeKey) return Response.json({ error: "dedupe_key_required" }, { status: 400 });

  const { data, error } = await adminSupabase()
    .from("ride_offers")
    .update(contextFields(body))
    .eq("driver_id", auth.driverId)
    .eq("device_id", auth.deviceId)
    .eq("dedupe_key", dedupeKey.slice(0, 100))
    .select("id")
    .maybeSingle();

  if (error) return Response.json({ error: error.message }, { status: 500 });
  if (!data) return Response.json({ error: "offer_not_found" }, { status: 404 });
  return Response.json({ ok: true, id: data.id });
}

export async function GET(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const url = new URL(request.url);
  const found = await fetchOffers(auth.driverId, {
    from: url.searchParams.get("from") || undefined,
    to: url.searchParams.get("to") || undefined,
    platform: url.searchParams.get("platform") || undefined,
    verdict: url.searchParams.get("verdict") || undefined,
    journeyId: url.searchParams.get("journey_id") || undefined,
    limit: Number(url.searchParams.get("limit") || 50),
  });
  return Response.json(found);
}
