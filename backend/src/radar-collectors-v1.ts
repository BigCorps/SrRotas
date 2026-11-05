import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

/**
 * Radar external collector credentials (vc110) — PURE helpers only (no Supabase import).
 *
 * Key format: `srrk_<prefix>_<secret>`
 *  - prefix: 8 lowercase hex chars (public, shown in the admin list);
 *  - secret: base64url of 32 random bytes (43 chars) from crypto.randomBytes.
 * Only SHA-256 hex of the FULL key is stored (`key_hash`). The plain key is returned once.
 */

export const COLLECTOR_KEY_PREFIX = "srrk_";
export const COLLECTOR_SCOPES = ["events:ingest", "pois:propose"] as const;
export type CollectorScopeV1 = (typeof COLLECTOR_SCOPES)[number];

/** Sources owned by internal pipelines; a collector can never write them. */
export const RESERVED_COLLECTOR_SOURCES = new Set([
  "ticketmaster",
  "manual",
  "admin",
  "internal",
]);

export const COLLECTOR_LIMITS = {
  maxBatchDefault: 200,
  maxBatchCap: 500,
  ratePerHourDefault: 30,
  ratePerHourCap: 1000,
  maxSources: 50,
  poiBatchCap: 50,
} as const;

const KEY_RE = /^srrk_([0-9a-f]{8})_([A-Za-z0-9_-]{43,128})$/;
const SOURCE_RE = /^[a-z0-9][a-z0-9_-]{1,79}$/;

export type CollectorRecordV1 = {
  id: string;
  name: string;
  status: "active" | "revoked";
  allowed_sources: string[];
  scopes: CollectorScopeV1[];
  key_prefix: string;
  key_hash?: string;
  max_batch: number;
  rate_limit_per_hour: number;
  created_at?: string | null;
  created_by?: string | null;
  rotated_at?: string | null;
  revoked_at?: string | null;
  revoked_by?: string | null;
  last_used_at?: string | null;
  expires_at?: string | null;
  row_version?: number;
};

export function sha256Hex(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

export function hashCollectorKey(key: string): string {
  return sha256Hex(key);
}

export function generateCollectorKey(): {
  key: string;
  prefix: string;
  displayPrefix: string;
  hash: string;
} {
  const prefix = randomBytes(4).toString("hex");
  const secret = randomBytes(32).toString("base64url");
  const key = `${COLLECTOR_KEY_PREFIX}${prefix}_${secret}`;
  return {
    key,
    prefix,
    displayPrefix: `${COLLECTOR_KEY_PREFIX}${prefix}`,
    hash: hashCollectorKey(key),
  };
}

export function parseCollectorKey(
  raw: unknown,
): { key: string; prefix: string } | null {
  const key = String(raw ?? "").trim();
  const match = KEY_RE.exec(key);
  return match ? { key, prefix: match[1] } : null;
}

/** Constant-time comparison of two hex digests (lengths normalized by hashing). */
export function hexDigestEqual(a: string, b: string): boolean {
  const aa = Buffer.from(String(a), "utf8");
  const bb = Buffer.from(String(b), "utf8");
  if (aa.length !== bb.length || aa.length === 0) {
    // Still spend comparable time; never short-circuit on content.
    timingSafeEqual(Buffer.alloc(32), Buffer.alloc(32));
    return false;
  }
  return timingSafeEqual(aa, bb);
}

export function verifyCollectorKey(key: string, storedHash: string): boolean {
  return hexDigestEqual(
    hashCollectorKey(key),
    String(storedHash || "").toLowerCase(),
  );
}

/** Timing-safe secret comparison for arbitrary-length strings (hash both sides first). */
export function timingSafeSecretEqual(
  provided: string,
  expected: string,
): boolean {
  if (!expected) return false;
  const a = createHash("sha256")
    .update(String(provided ?? ""), "utf8")
    .digest();
  const b = createHash("sha256").update(String(expected), "utf8").digest();
  return timingSafeEqual(a, b);
}

/**
 * Legacy internal/admin path: `Authorization: Bearer <RADAR_INGEST_SECRET>`.
 * Same accept/deny semantics as the previous `===` check, now constant time.
 */
export function legacyRadarSecretAuthorized(
  authorizationHeader: string | null,
  secret: string,
): boolean {
  return (
    Boolean(secret) &&
    timingSafeSecretEqual(authorizationHeader ?? "", `Bearer ${secret}`)
  );
}

export function collectorBearer(
  authorizationHeader: string | null,
): string | null {
  const value = String(authorizationHeader ?? "").trim();
  const match = /^Bearer\s+(srrk_\S+)$/.exec(value);
  return match ? match[1] : null;
}

export function isCollectorBearer(authorizationHeader: string | null): boolean {
  return /^Bearer\s+srrk_/i.test(String(authorizationHeader ?? "").trim());
}

export function hasCollectorScope(
  collector: Pick<CollectorRecordV1, "scopes">,
  scope: CollectorScopeV1,
): boolean {
  return Array.isArray(collector.scopes) && collector.scopes.includes(scope);
}

export function collectorSourceAllowed(
  collector: Pick<CollectorRecordV1, "allowed_sources">,
  source: string | null,
): boolean {
  if (!source || RESERVED_COLLECTOR_SOURCES.has(source)) return false;
  return (
    Array.isArray(collector.allowed_sources) &&
    collector.allowed_sources.includes(source)
  );
}

export function effectiveMaxBatch(
  collector: Pick<CollectorRecordV1, "max_batch">,
): number {
  const n = Math.floor(Number(collector.max_batch));
  if (!Number.isFinite(n) || n < 1) return COLLECTOR_LIMITS.maxBatchDefault;
  return Math.min(n, COLLECTOR_LIMITS.maxBatchCap);
}

export class CollectorConfigError extends Error {}

function boundedInt(
  value: unknown,
  min: number,
  max: number,
  label: string,
): number {
  const n = Number(value);
  if (!Number.isInteger(n) || n < min || n > max)
    throw new CollectorConfigError(`${label}_invalid`);
  return n;
}

export function normalizeCollectorSource(value: unknown): string | null {
  const s = String(value ?? "")
    .trim()
    .toLowerCase();
  return SOURCE_RE.test(s) ? s : null;
}

/** Validates admin input for create (full) or update (partial). */
export function normalizeCollectorConfigV1(
  input: Record<string, unknown>,
  partial = false,
) {
  const out: {
    name?: string;
    allowed_sources?: string[];
    scopes?: CollectorScopeV1[];
    max_batch?: number;
    rate_limit_per_hour?: number;
    expires_at?: string;
  } = {};

  if (!partial || input.name !== undefined) {
    const name = String(input.name ?? "")
      .trim()
      .replace(/\s+/g, " ");
    if (name.length < 3 || name.length > 80)
      throw new CollectorConfigError("name_invalid");
    out.name = name;
  }
  if (!partial || input.allowed_sources !== undefined) {
    if (!Array.isArray(input.allowed_sources))
      throw new CollectorConfigError("allowed_sources_invalid");
    const sources = [
      ...new Set(
        input.allowed_sources.map((s) =>
          String(s ?? "")
            .trim()
            .toLowerCase(),
        ),
      ),
    ];
    if (sources.length < 1 || sources.length > COLLECTOR_LIMITS.maxSources)
      throw new CollectorConfigError("allowed_sources_invalid");
    for (const s of sources) {
      if (!SOURCE_RE.test(s))
        throw new CollectorConfigError("allowed_sources_invalid");
      if (RESERVED_COLLECTOR_SOURCES.has(s))
        throw new CollectorConfigError("allowed_sources_reserved");
    }
    out.allowed_sources = sources.sort();
  }
  if (!partial || input.scopes !== undefined) {
    if (!Array.isArray(input.scopes))
      throw new CollectorConfigError("scopes_invalid");
    const scopes = [
      ...new Set(input.scopes.map((s) => String(s ?? "").trim())),
    ];
    if (
      scopes.length < 1 ||
      scopes.some((s) => !(COLLECTOR_SCOPES as readonly string[]).includes(s))
    ) {
      throw new CollectorConfigError("scopes_invalid");
    }
    out.scopes = scopes as CollectorScopeV1[];
  }
  if (!partial || input.max_batch !== undefined) {
    out.max_batch =
      input.max_batch === undefined ||
      input.max_batch === null ||
      input.max_batch === ""
        ? COLLECTOR_LIMITS.maxBatchDefault
        : boundedInt(
            input.max_batch,
            1,
            COLLECTOR_LIMITS.maxBatchCap,
            "max_batch",
          );
  }
  if (!partial || input.rate_limit_per_hour !== undefined) {
    out.rate_limit_per_hour =
      input.rate_limit_per_hour === undefined ||
      input.rate_limit_per_hour === null ||
      input.rate_limit_per_hour === ""
        ? COLLECTOR_LIMITS.ratePerHourDefault
        : boundedInt(
            input.rate_limit_per_hour,
            1,
            COLLECTOR_LIMITS.ratePerHourCap,
            "rate_limit_per_hour",
          );
  }
  if (!partial || input.expires_at !== undefined) {
    const value =
      input.expires_at === undefined
        ? new Date(Date.now() + 365 * 86400000).toISOString()
        : String(input.expires_at);
    if (
      !/(Z|[+-]\d{2}:\d{2})$/.test(value) ||
      !Number.isFinite(Date.parse(value)) ||
      Date.parse(value) <= Date.now()
    )
      throw new CollectorConfigError("expires_at_invalid");
    out.expires_at = new Date(value).toISOString();
  }
  return out;
}

/** Public view of a collector: never includes key_hash. */
export function publicCollectorV1(row: any) {
  return {
    id: String(row?.id ?? ""),
    name: String(row?.name ?? ""),
    status: row?.status === "revoked" ? "revoked" : "active",
    allowed_sources: Array.isArray(row?.allowed_sources)
      ? row.allowed_sources
      : [],
    scopes: Array.isArray(row?.scopes) ? row.scopes : [],
    key_prefix: String(row?.key_prefix ?? ""),
    max_batch: Number(row?.max_batch ?? COLLECTOR_LIMITS.maxBatchDefault),
    rate_limit_per_hour: Number(
      row?.rate_limit_per_hour ?? COLLECTOR_LIMITS.ratePerHourDefault,
    ),
    created_at: row?.created_at ?? null,
    created_by: row?.created_by ?? null,
    rotated_at: row?.rotated_at ?? null,
    revoked_at: row?.revoked_at ?? null,
    revoked_by: row?.revoked_by ?? null,
    last_used_at: row?.last_used_at ?? null,
    expires_at: row?.expires_at ?? null,
    row_version: Number(row?.row_version ?? 1),
  };
}

export function requestIpHash(request: Request): string {
  const ip = (
    request.headers.get("x-forwarded-for")?.split(",")[0] ||
    request.headers.get("x-real-ip") ||
    "unknown"
  )
    .trim()
    .slice(0, 120);
  return sha256Hex(`radar-collector|${ip}`).slice(0, 16);
}

/**
 * Detects "schema not migrated yet" errors from PostgREST/Postgres so callers can degrade
 * safely: 42P01 undefined_table, 42703 undefined_column, PGRST204/PGRST205 schema cache misses.
 */
export function isMissingSchemaError(error: unknown): boolean {
  if (!error || typeof error !== "object") return false;
  const e = error as {
    code?: unknown;
    message?: unknown;
    details?: unknown;
    hint?: unknown;
  };
  const code = String(e.code ?? "");
  if (["42P01", "42703", "PGRST204", "PGRST205"].includes(code)) return true;
  const text =
    `${String(e.message ?? "")} ${String(e.details ?? "")}`.toLowerCase();
  return (
    /(relation|table|column) .*does not exist/.test(text) ||
    /could not find the .*(table|column)/.test(text) ||
    /schema cache/.test(text)
  );
}

export class CollectorStoreUnavailableError extends Error {
  constructor() {
    super("collector_store_unavailable");
  }
}

export function collectorStoreUnavailableResponse() {
  return Response.json(
    { error: "collector_store_unavailable" },
    {
      status: 503,
      headers: { "Cache-Control": "no-store" },
    },
  );
}
