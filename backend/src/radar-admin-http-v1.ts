import { canAccessAdminOps } from "./admin-access";
import { importActor } from "./admin-imports";
import { allowSecurityAction } from "./security-rate-limit";
import {
  CollectorConfigError,
  CollectorStoreUnavailableError,
  collectorStoreUnavailableResponse,
  isCollectorBearer,
} from "./radar-collectors-v1";
import { ReviewInputError } from "./radar-review-v1";

/**
 * Admin session guard for /api/v1/admin/radar/**. Same realm as Diagnósticos:
 * importActor (admin web session) + canAccessAdminOps. Collector keys are refused outright.
 */

const NO_STORE = { "Cache-Control": "no-store" };

export function adminJsonV1(body: unknown, status = 200) {
  return Response.json(body, { status, headers: NO_STORE });
}

export async function requireRadarAdminV1(request: Request) {
  if (isCollectorBearer(request.headers.get("authorization"))) {
    return { actor: null, response: adminJsonV1({ error: "collector_key_not_allowed" }, 403) };
  }
  const actor = await importActor(request);
  if (!actor) return { actor: null, response: adminJsonV1({ error: "unauthorized" }, 401) };
  if (!actor.allowed || !canAccessAdminOps(actor.email)) {
    return { actor: null, response: adminJsonV1({ error: "admin_ops_forbidden" }, 403) };
  }
  return { actor, response: null };
}

export async function radarAdminRateLimitV1(request: Request, scope: string, email: string, max: number, windowSeconds = 900) {
  const allowed = await allowSecurityAction(request, scope, email, max, windowSeconds).catch(() => false);
  return allowed ? null : adminJsonV1({ error: "rate_limited" }, 429);
}

export function radarAdminErrorV1(error: unknown) {
  if (error instanceof CollectorStoreUnavailableError) return collectorStoreUnavailableResponse();
  const message = error instanceof Error ? error.message : "radar_admin_failed";
  if (error instanceof CollectorConfigError || error instanceof ReviewInputError) {
    const status = message === "review_conflict" ? 409 : /not_found/.test(message) ? 404 : 400;
    return adminJsonV1({ error: message }, status);
  }
  if (/not_found|revoked/.test(message)) return adminJsonV1({ error: message }, 404);
  if (message === "collector_patch_empty") return adminJsonV1({ error: message }, 400);
  console.error("sr_radar_admin_failed", { message: message.slice(0, 200) });
  return adminJsonV1({ error: "radar_admin_failed" }, 500);
}

export function validUuidV1(value: unknown) {
  return /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(String(value ?? ""));
}
