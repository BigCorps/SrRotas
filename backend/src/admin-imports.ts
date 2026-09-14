import { authenticateImportWeb } from "./admin-import-auth";
import { authenticateBillingWeb } from "./billing-auth";
import { adminSupabase } from "./supabase";
import { sha256 } from "./security";

export const IMPORT_OWNER_EMAIL = "contato@bigcorps.com.br";
export const IMPORT_CHUNK_LIMIT = 200;
export const HISTORICAL_SCHEMA_V1 = "srrotas-historical-offer-v1";

type ImportActor = {
  authUserId: string;
  email: string;
  isOwner: boolean;
  allowed: boolean;
  source: "admin" | "driver";
};

type HistoricalImportPayload = Record<string, unknown>;
type Quality = {
  demand_temporal_ready: boolean;
  route_flow_ready: boolean;
  financial_ready: boolean;
  fully_ready: boolean;
};

export type ValidatedImportRow = {
  rowIndex: number;
  sourceFileName: string | null;
  sourceFileSha256: string | null;
  schemaVersion: string;
  extractorVersion: string | null;
  recordId: string;
  offerIndex: number | null;
  semanticKey: string;
  validationStatus: "valid" | "partial" | "invalid" | "duplicate";
  validationErrors: string[];
  qualityFlags: string[];
  quality: Quality;
  originalPayload: HistoricalImportPayload;
  normalizedPayload: HistoricalImportPayload;
};

function normalizeEmail(value: unknown) {
  return String(value ?? "").trim().toLowerCase().slice(0, 180);
}

function text(value: unknown, max = 500) {
  const normalized = String(value ?? "").trim();
  return normalized ? normalized.slice(0, max) : null;
}

function numberOrNull(value: unknown) {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  const normalized = String(value).trim().replace(/\s/g, "").replace(/\.(?=\d{3}(?:\D|$))/g, "").replace(",", ".");
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function integerOrNull(value: unknown) {
  const n = numberOrNull(value);
  return n === null ? null : Math.round(n);
}

function confidenceOrNull(value: unknown) {
  const n = numberOrNull(value);
  if (n === null) return null;
  const normalized = n > 1 && n <= 100 ? n / 100 : n;
  return Math.max(0, Math.min(1, normalized));
}

function isoOrNull(value: unknown) {
  const raw = text(value, 100);
  if (!raw) return null;
  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function first(payload: HistoricalImportPayload, keys: string[]) {
  for (const key of keys) {
    const value = payload[key];
    if (value !== undefined && value !== null && value !== "") return value;
  }
  return null;
}

function hashOrNull(value: unknown) {
  const candidate = text(value, 128)?.toLowerCase() ?? null;
  return candidate && /^[a-f0-9]{64}$/.test(candidate) ? candidate : null;
}

function normalizedKey(value: string | null) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

const GENERIC_REGION_KEYS = new Set([
  "area", "regiao", "destino", "origem", "retirada", "embarque", "buscar", "aceitar", "escolher",
  "desloque-se-ate", "desloque-se", "entrada-principal", "viagem-longa", "como-foi-a-viagem",
  "area-semi", "semi-coberta", "uberx", "comfort", "black", "99pop", "99plus", "99moto",
]);

function usablePlace(value: string | null) {
  const key = normalizedKey(value);
  if (!key || key.length < 3) return false;
  if (GENERIC_REGION_KEYS.has(key)) return false;
  if (/^(area|regiao|destino|origem|retirada|embarque|buscar|aceitar|escolher)(-|$)/.test(key)) return false;
  if (/^desloque-se(-|$)/.test(key)) return false;
  return true;
}


function positive(value: number | null) {
  return value !== null && value > 0;
}

function closeEnough(actual: number | null, expected: number, absolute: number, relative: number) {
  if (actual === null) return true;
  return Math.abs(actual - expected) <= Math.max(absolute, Math.abs(expected) * relative);
}

function unique(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)));
}

function stringArray(value: unknown, maxItems = 80, maxLength = 100) {
  if (!Array.isArray(value)) return [] as string[];
  return value
    .map((item) => text(item, maxLength))
    .filter((item): item is string => Boolean(item))
    .slice(0, maxItems);
}

export async function importActor(request: Request): Promise<ImportActor | null> {
  const adminSession = await authenticateImportWeb(request);
  if (adminSession) {
    const email = normalizeEmail(adminSession.email);
    const isOwner = email === IMPORT_OWNER_EMAIL;
    if (isOwner) {
      return { authUserId: adminSession.authUserId, email, isOwner: true, allowed: true, source: "admin" };
    }
    const access = await adminSupabase()
      .from("historical_import_access")
      .select("enabled")
      .eq("email", email)
      .maybeSingle();
    return {
      authUserId: adminSession.authUserId,
      email,
      isOwner: false,
      allowed: Boolean(!access.error && access.data?.enabled),
      source: "admin",
    };
  }

  const driverSession = await authenticateBillingWeb(request);
  if (!driverSession) return null;
  const driver = await adminSupabase()
    .from("drivers")
    .select("auth_user_id,email")
    .eq("id", driverSession.driverId)
    .maybeSingle();
  if (driver.error || !driver.data?.auth_user_id) return null;

  const email = normalizeEmail(driver.data.email);
  const isOwner = email === IMPORT_OWNER_EMAIL;
  const access = isOwner
    ? { data: { enabled: true }, error: null }
    : await adminSupabase().from("historical_import_access").select("enabled").eq("email", email).maybeSingle();

  return {
    authUserId: String(driver.data.auth_user_id),
    email,
    isOwner,
    allowed: Boolean(isOwner || (!access.error && access.data?.enabled)),
    source: "driver",
  };
}

export async function requireImportAccess(request: Request) {
  const actor = await importActor(request);
  if (!actor) return { actor: null, response: Response.json({ error: "unauthorized" }, { status: 401 }) };
  if (!actor.allowed) return { actor, response: Response.json({ error: "forbidden", email: actor.email }, { status: 403 }) };
  return { actor, response: null };
}

export async function requireImportOwner(request: Request) {
  const checked = await requireImportAccess(request);
  if (checked.response) return checked;
  if (!checked.actor?.isOwner) {
    return { actor: checked.actor, response: Response.json({ error: "owner_required" }, { status: 403 }) };
  }
  return checked;
}

export function validateHistoricalImportRow(rowIndex: number, input: unknown): ValidatedImportRow {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    const original = { value: input } as HistoricalImportPayload;
    const key = sha256(`invalid|${rowIndex}|${JSON.stringify(input)}`);
    return {
      rowIndex,
      sourceFileName: null,
      sourceFileSha256: null,
      schemaVersion: "legacy",
      extractorVersion: null,
      recordId: key,
      offerIndex: null,
      semanticKey: key,
      validationStatus: "invalid",
      validationErrors: ["registro_nao_e_objeto"],
      qualityFlags: ["invalid_structure"],
      quality: { demand_temporal_ready: false, route_flow_ready: false, financial_ready: false, fully_ready: false },
      originalPayload: original,
      normalizedPayload: {},
    };
  }

  const payload = input as HistoricalImportPayload;
  const schemaVersion = text(first(payload, ["schema_version"]), 100) ?? "legacy";
  const extractorVersion = text(first(payload, ["extractor_version", "parser_version"]), 80);
  const fare = numberOrNull(first(payload, ["fare", "valor", "price"]));
  const observedAt = isoOrNull(first(payload, ["observed_at", "captured_at", "datetime", "timestamp", "date_time"]));
  const observedTimezone = text(first(payload, ["observed_timezone", "timezone"]), 80) ?? "America/Sao_Paulo";
  const timeSource = text(first(payload, ["time_source"]), 80) ?? (observedAt ? "legacy_timestamp" : "unknown");
  const timeConfidence = confidenceOrNull(first(payload, ["time_confidence"]));
  const pickupText = text(first(payload, ["pickup_text", "pickup_label", "pickup", "origin", "origin_text", "retirada"]));
  const destinationText = text(first(payload, ["destination_text", "destination_label", "destination", "destino"]));
  const pickupRegionCandidate = text(first(payload, ["pickup_region_candidate"]), 120);
  const destinationRegionCandidate = text(first(payload, ["destination_region_candidate"]), 120);
  const driverRegionCandidate = text(first(payload, ["driver_region_candidate"]), 120);
  const driverLocationText = text(first(payload, ["driver_location_text", "driver_location"]));
  const sourceFileName = text(first(payload, ["source_file_name", "file_name", "filename", "image_name"]), 260);
  const sourceFileSha256 = hashOrNull(first(payload, ["source_file_sha256", "file_sha256", "file_id", "sha256"]));
  const offerIndexRaw = integerOrNull(first(payload, ["offer_index"]));
  const offerIndex = offerIndexRaw !== null && offerIndexRaw >= 0 ? offerIndexRaw : null;

  const pickupKm = numberOrNull(first(payload, ["pickup_km"]));
  const tripKm = numberOrNull(first(payload, ["trip_km"]));
  const suppliedTotalKm = numberOrNull(first(payload, ["total_km"]));
  const pickupMinutes = integerOrNull(first(payload, ["pickup_minutes"]));
  const tripMinutes = integerOrNull(first(payload, ["trip_minutes"]));
  const suppliedTotalMinutes = integerOrNull(first(payload, ["total_minutes"]));
  const advertisedPerKm = numberOrNull(first(payload, ["advertised_per_km"]));

  const flags: string[] = stringArray(first(payload, ["quality_flags"]));
  const errors: string[] = [];

  if (fare === null) { flags.push("missing_fare"); }
  else if (fare <= 0 || fare > 5000) { errors.push("fare_invalido"); flags.push("invalid_fare"); }
  if (!observedAt) { errors.push("data_hora_ausente_ou_invalida"); flags.push("missing_observed_at"); }
  if (schemaVersion === HISTORICAL_SCHEMA_V1 && (timeConfidence === null || timeConfidence < 0.75)) flags.push("low_time_confidence");
  if (!pickupText) { errors.push("retirada_ausente"); flags.push("missing_pickup_text"); }
  if (!destinationText) { errors.push("destino_ausente"); flags.push("missing_destination_text"); }
  if (pickupText && !usablePlace(pickupText)) flags.push("generic_pickup_region");
  if (destinationText && !usablePlace(destinationText)) flags.push("generic_destination_region");
  if (pickupRegionCandidate && !usablePlace(pickupRegionCandidate)) flags.push("generic_pickup_region_candidate");
  if (destinationRegionCandidate && !usablePlace(destinationRegionCandidate)) flags.push("generic_destination_region_candidate");
  if (!sourceFileSha256 && schemaVersion === HISTORICAL_SCHEMA_V1) flags.push("missing_source_file_sha256");
  if (offerIndex === null && schemaVersion === HISTORICAL_SCHEMA_V1) flags.push("missing_offer_index");

  if (!positive(pickupKm)) flags.push("missing_pickup_km");
  if (!positive(tripKm)) flags.push("missing_trip_km");
  if (pickupMinutes === null || pickupMinutes <= 0) flags.push("missing_pickup_minutes");
  if (tripMinutes === null || tripMinutes <= 0) flags.push("missing_trip_minutes");

  const completeLegs = positive(pickupKm) && positive(tripKm) &&
    pickupMinutes !== null && pickupMinutes > 0 && tripMinutes !== null && tripMinutes > 0;
  const computedTotalKm = completeLegs ? Number(((pickupKm as number) + (tripKm as number)).toFixed(3)) : null;
  const computedTotalMinutes = completeLegs ? (pickupMinutes as number) + (tripMinutes as number) : null;

  let geometryConflict = false;
  if (computedTotalKm !== null && !closeEnough(suppliedTotalKm, computedTotalKm, 0.2, 0.04)) geometryConflict = true;
  if (computedTotalMinutes !== null && suppliedTotalMinutes !== null && Math.abs(suppliedTotalMinutes - computedTotalMinutes) > 1) geometryConflict = true;
  if (geometryConflict) flags.push("geometry_conflict");

  if (advertisedPerKm !== null && fare !== null && computedTotalKm !== null && computedTotalKm > 0) {
    const calculated = fare / computedTotalKm;
    if (Math.abs(advertisedPerKm - calculated) > Math.max(0.20, advertisedPerKm * 0.15)) flags.push("advertised_rate_mismatch");
  }

  const platformRaw = text(first(payload, ["platform"]), 40)?.toLowerCase() ?? "unknown";
  const platform = platformRaw === "99" || platformRaw === "uber" ? platformRaw : "unknown";
  if (platform === "unknown") flags.push("platform_ambiguous");

  const effectiveTimeConfidence = timeConfidence ?? (schemaVersion === HISTORICAL_SCHEMA_V1 ? 0 : 0.8);
  const pickupPlace = usablePlace(pickupText) ? pickupText : usablePlace(pickupRegionCandidate) ? pickupRegionCandidate : null;
  const destinationPlace = usablePlace(destinationText) ? destinationText : usablePlace(destinationRegionCandidate) ? destinationRegionCandidate : null;
  const demandTemporalReady = Boolean(observedAt && effectiveTimeConfidence >= 0.75 && pickupPlace);
  const routeFlowReady = Boolean(demandTemporalReady && destinationPlace);
  const financialReady = Boolean(
    fare !== null && fare > 0 && fare <= 5000 && completeLegs && !geometryConflict,
  );
  const fullyReady = demandTemporalReady && routeFlowReady && financialReady;
  const quality: Quality = {
    demand_temporal_ready: demandTemporalReady,
    route_flow_ready: routeFlowReady,
    financial_ready: financialReady,
    fully_ready: fullyReady,
  };

  const totalKm = computedTotalKm ?? (schemaVersion === HISTORICAL_SCHEMA_V1 ? null : suppliedTotalKm);
  const totalMinutes = computedTotalMinutes ?? (schemaVersion === HISTORICAL_SCHEMA_V1 ? null : suppliedTotalMinutes);
  const normalized: HistoricalImportPayload = {
    schema_version: schemaVersion,
    extractor_version: extractorVersion,
    source: text(first(payload, ["source"])) ?? "historical_screenshot",
    source_file_name: sourceFileName,
    source_file_sha256: sourceFileSha256,
    offer_index: offerIndex,
    crop_sha256: hashOrNull(first(payload, ["crop_sha256"])),
    observed_at: observedAt,
    observed_timezone: observedTimezone,
    time_source: timeSource,
    time_confidence: timeConfidence,
    fare,
    pickup_km: pickupKm,
    trip_km: tripKm,
    total_km: totalKm,
    pickup_minutes: pickupMinutes,
    trip_minutes: tripMinutes,
    total_minutes: totalMinutes,
    pickup_text: pickupText,
    destination_text: destinationText,
    driver_location_text: driverLocationText,
    pickup_region_candidate: pickupRegionCandidate,
    destination_region_candidate: destinationRegionCandidate,
    driver_region_candidate: driverRegionCandidate,
    pickup_region_confidence: confidenceOrNull(first(payload, ["pickup_region_confidence"])),
    destination_region_confidence: confidenceOrNull(first(payload, ["destination_region_confidence"])),
    driver_region_confidence: confidenceOrNull(first(payload, ["driver_region_confidence"])),
    platform,
    platform_confidence: confidenceOrNull(first(payload, ["platform_confidence"])),
    service_type: text(first(payload, ["service_type", "service"]), 80)?.toLowerCase(),
    offer_type: text(first(payload, ["offer_type", "type"]), 40)?.toLowerCase(),
    passenger_rating: numberOrNull(first(payload, ["passenger_rating", "rating"])),
    advertised_per_km: advertisedPerKm,
    dynamic_signal: text(first(payload, ["dynamic_signal"]), 40)?.toLowerCase(),
    bonus_amount: numberOrNull(first(payload, ["bonus_amount"])),
    surge_multiplier: numberOrNull(first(payload, ["surge_multiplier"])),
    ocr_confidence: confidenceOrNull(first(payload, ["ocr_confidence", "confidence"])),
    context_confidence: confidenceOrNull(first(payload, ["context_confidence"])),
    field_confidence: first(payload, ["field_confidence"]),
    quality,
    quality_flags: unique(flags),
  };

  const semanticMaterial = [
    observedAt ?? "?", fare ?? "?", pickupKm ?? "?", tripKm ?? "?", pickupMinutes ?? "?", tripMinutes ?? "?",
    pickupPlace?.toLowerCase() ?? "?", destinationPlace?.toLowerCase() ?? "?",
    normalized.service_type ?? "?", normalized.offer_type ?? "?",
  ].join("|");
  const semanticKey = sha256(semanticMaterial);
  const suppliedRecordId = hashOrNull(first(payload, ["record_id"]));
  const recordMaterial = [
    sourceFileSha256 ?? "?", offerIndex ?? "?", observedAt ?? "?", fare ?? "?", pickupKm ?? "?", tripKm ?? "?",
    pickupMinutes ?? "?", tripMinutes ?? "?", normalizedKey(pickupPlace), normalizedKey(destinationPlace),
  ].join("|");
  const calculatedRecordId = sha256(recordMaterial);
  const recordId = suppliedRecordId ?? calculatedRecordId;
  normalized.record_id = recordId;
  normalized.quality_flags = unique(flags);

  const criticalIdentityMissing = schemaVersion === HISTORICAL_SCHEMA_V1 && (!sourceFileSha256 || offerIndex === null);
  const noUsableDimension = !demandTemporalReady && !routeFlowReady && !financialReady;
  const status: ValidatedImportRow["validationStatus"] =
    errors.includes("fare_invalido") ? "invalid" :
      schemaVersion === HISTORICAL_SCHEMA_V1
        ? (criticalIdentityMissing || noUsableDimension || geometryConflict || errors.length > 0 || !fullyReady ? "partial" : "valid")
        : (errors.length > 0 ? "partial" : "valid");

  return {
    rowIndex,
    sourceFileName,
    sourceFileSha256,
    schemaVersion,
    extractorVersion,
    recordId,
    offerIndex,
    semanticKey,
    validationStatus: status,
    validationErrors: unique(errors),
    qualityFlags: unique(flags),
    quality,
    originalPayload: payload,
    normalizedPayload: normalized,
  };
}
