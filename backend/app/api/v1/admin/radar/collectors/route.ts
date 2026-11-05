import { readRadarJsonV2 } from "@/src/radar-hardening-v2";
import {
  adminJsonV1,
  radarAdminErrorV1,
  radarAdminRateLimitV1,
  requireRadarAdminV1,
  validUuidV1,
} from "@/src/radar-admin-http-v1";
import {
  createCollectorV1,
  listCollectorsV1,
  revokeCollectorV1,
  rotateCollectorKeyV1,
  updateCollectorV1,
} from "@/src/radar-collector-store-v1";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  try {
    return adminJsonV1({
      generated_at: new Date().toISOString(),
      collectors: await listCollectorsV1(),
    });
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}

/** Create a collector. The plain key is returned ONLY in this response. */
export async function POST(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  const limited = await radarAdminRateLimitV1(
    request,
    "admin-radar-collector-keys",
    checked.actor!.email,
    30,
  );
  if (limited) return limited;
  try {
    const body = await readRadarJsonV2(request, 16384);
    const created = await createCollectorV1(
      body && typeof body === "object" ? body : {},
      checked.actor!.email,
    );
    console.info("sr_radar_collector_created", {
      admin: checked.actor!.email,
      collector_id: created.collector.id,
    });
    return adminJsonV1({ ok: true, ...created, key_shown_once: true }, 201);
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}

/** { id, action: "update"|"rotate"|"revoke", ...fields }. Rotate returns the new key once. */
export async function PATCH(request: Request) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked.response;
  const limited = await radarAdminRateLimitV1(
    request,
    "admin-radar-collector-keys",
    checked.actor!.email,
    30,
  );
  if (limited) return limited;
  try {
    const body = await readRadarJsonV2(request, 16384);
    const id = String(body?.id ?? "");
    if (!validUuidV1(id)) return adminJsonV1({ error: "id_invalid" }, 400);
    const version = Number(body.expected_version);
    if (!Number.isSafeInteger(version) || version < 1)
      return adminJsonV1({ error: "expected_version_required" }, 400);
    const email = checked.actor!.email;
    const action = String(body?.action ?? "");
    if (action === "rotate") {
      const rotated = await rotateCollectorKeyV1(
        id,
        email,
        version,
        body.expires_at,
      );
      console.info("sr_radar_collector_rotated", {
        admin: email,
        collector_id: id,
      });
      return adminJsonV1({ ok: true, ...rotated, key_shown_once: true });
    }
    if (action === "revoke") {
      const collector = await revokeCollectorV1(id, email, version);
      console.info("sr_radar_collector_revoked", {
        admin: email,
        collector_id: id,
      });
      return adminJsonV1({ ok: true, collector });
    }
    if (action === "update") {
      const { id: _id, action: _action, ...fields } = body ?? {};
      return adminJsonV1({
        ok: true,
        collector: await updateCollectorV1(id, fields, email, version),
      });
    }
    return adminJsonV1({ error: "action_invalid" }, 400);
  } catch (error) {
    return radarAdminErrorV1(error);
  }
}
