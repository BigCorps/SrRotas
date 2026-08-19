import { authenticateImportWeb } from "./admin-import-auth";
import { authenticateBillingWeb } from "./billing-auth";
import { adminSupabase } from "./supabase";
import { sha256 } from "./security";

export const IMPORT_OWNER_EMAIL = "contato@bigcorps.com.br";
export const IMPORT_CHUNK_LIMIT = 200;

type ImportActor = {
  authUserId: string;
  email: string;
  isOwner: boolean;
  allowed: boolean;
  source: "admin" | "driver";
};

type HistoricalImportPayload = Record<string, unknown>;

export type ValidatedImportRow = {
  rowIndex: number;
  sourceFileName: string | null;
  sourceFileSha256: string | null;
  semanticKey: string;
  validationStatus: "valid" | "partial" | "invalid" | "duplicate";
  validationErrors: string[];
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

export async function importActor(request: Request): Promise<ImportActor | null> {
  // 1) Conta administrativa/importador sem driver: usa sessão própria do portal.
  const adminSession = await authenticateImportWeb(request);
  if (adminSession) {
    const email = normalizeEmail(adminSession.email);
    const isOwner = email === IMPORT_OWNER_EMAIL;

    if (isOwner) {
      return {
        authUserId: adminSession.authUserId,
        email,
        isOwner: true,
        allowed: true,
        source: "admin",
      };
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

  // 2) Motorista autorizado: reaproveita a sessão normal do dashboard.
  //    Não cria identidade paralela e não altera o registro em drivers.
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
    : await adminSupabase()
        .from("historical_import_access")
        .select("enabled")
        .eq("email", email)
        .maybeSingle();

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
    return {
      rowIndex,
      sourceFileName: null,
      sourceFileSha256: null,
      semanticKey: sha256(`invalid|${rowIndex}|${JSON.stringify(input)}`),
      validationStatus: "invalid",
      validationErrors: ["registro_nao_e_objeto"],
      originalPayload: original,
      normalizedPayload: {},
    };
  }

  const payload = input as HistoricalImportPayload;
  const fare = numberOrNull(first(payload, ["fare", "valor", "price"]));
  const observedAt = isoOrNull(first(payload, ["observed_at", "captured_at", "datetime", "timestamp", "date_time"]));
  const pickupText = text(first(payload, ["pickup_text", "pickup_label", "pickup", "origin", "origin_text", "retirada"]));
  const destinationText = text(first(payload, ["destination_text", "destination_label", "destination", "destino"]));
  const sourceFileName = text(first(payload, ["source_file_name", "file_name", "filename", "image_name"]), 260);
  const sourceFileSha256 = hashOrNull(first(payload, ["source_file_sha256", "file_sha256", "file_id", "sha256"]));

  const normalized: HistoricalImportPayload = {
    source: text(first(payload, ["source"])) ?? "historical_screenshot",
    source_file_name: sourceFileName,
    source_file_sha256: sourceFileSha256,
    observed_at: observedAt,
    time_source: text(first(payload, ["time_source"]), 80),
    time_confidence: confidenceOrNull(first(payload, ["time_confidence"])),
    fare,
    pickup_km: numberOrNull(first(payload, ["pickup_km"])),
    trip_km: numberOrNull(first(payload, ["trip_km"])),
    total_km: numberOrNull(first(payload, ["total_km"])),
    pickup_minutes: integerOrNull(first(payload, ["pickup_minutes"])),
    trip_minutes: integerOrNull(first(payload, ["trip_minutes"])),
    total_minutes: integerOrNull(first(payload, ["total_minutes"])),
    pickup_text: pickupText,
    destination_text: destinationText,
    service_type: text(first(payload, ["service_type", "service"]), 80)?.toLowerCase(),
    offer_type: text(first(payload, ["offer_type", "type"]), 40)?.toLowerCase(),
    passenger_rating: numberOrNull(first(payload, ["passenger_rating", "rating"])),
    advertised_per_km: numberOrNull(first(payload, ["advertised_per_km"])),
    ocr_confidence: confidenceOrNull(first(payload, ["ocr_confidence", "confidence"])),
    context_confidence: confidenceOrNull(first(payload, ["context_confidence"])),
  };

  const errors: string[] = [];
  if (fare === null || fare <= 0 || fare > 5000) errors.push("fare_invalido");
  if (!observedAt) errors.push("data_hora_ausente_ou_invalida");
  if (!pickupText) errors.push("retirada_ausente");
  if (!destinationText) errors.push("destino_ausente");

  const status: ValidatedImportRow["validationStatus"] =
    errors.includes("fare_invalido") ? "invalid" : errors.length ? "partial" : "valid";

  const semanticMaterial = [
    observedAt ?? "?",
    fare ?? "?",
    normalized.pickup_km ?? "?",
    normalized.trip_km ?? normalized.total_km ?? "?",
    normalized.pickup_minutes ?? "?",
    normalized.trip_minutes ?? normalized.total_minutes ?? "?",
    pickupText?.toLowerCase() ?? "?",
    destinationText?.toLowerCase() ?? "?",
    normalized.service_type ?? "?",
    normalized.offer_type ?? "?",
  ].join("|");

  return {
    rowIndex,
    sourceFileName,
    sourceFileSha256,
    semanticKey: sha256(semanticMaterial),
    validationStatus: status,
    validationErrors: errors,
    originalPayload: payload,
    normalizedPayload: normalized,
  };
}
