import { serverEnv } from "@/src/env";
import {
  ingestRadarEvents0261,
  listRadarEvents0261,
  setRadarEventStatus0261,
  updateRadarEvent0261,
} from "@/src/radar-ingest-0261";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function authorized(request: Request) {
  const secret = serverEnv().radarIngestSecret;
  return Boolean(secret) && request.headers.get("authorization") === `Bearer ${secret}`;
}
function denied() {
  return Response.json({ error: "unauthorized" }, { status: 401 });
}

export async function GET(request: Request) {
  if (!authorized(request)) return denied();
  const url = new URL(request.url);
  try {
    return Response.json({ events: await listRadarEvents0261(Number(url.searchParams.get("limit") || 200)) });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "radar_admin_failed" }, { status: 500 });
  }
}

export async function POST(request: Request) {
  if (!authorized(request)) return denied();
  try {
    const body = await request.json();
    const event = { ...body, source: body.source || "manual", status: body.status || "active" };
    return Response.json(await ingestRadarEvents0261({ events: [event] }));
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "radar_admin_create_failed" }, { status: 400 });
  }
}

export async function PATCH(request: Request) {
  if (!authorized(request)) return denied();
  try {
    const body = await request.json();
    const id = String(body.id ?? "").trim();
    if (!id) return Response.json({ error: "id_required" }, { status: 400 });
    const { id: _id, ...patch } = body;
    return Response.json({ event: await updateRadarEvent0261(id, patch) });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "radar_admin_update_failed" }, { status: 400 });
  }
}

export async function DELETE(request: Request) {
  if (!authorized(request)) return denied();
  const url = new URL(request.url);
  const id = String(url.searchParams.get("id") ?? "").trim();
  if (!id) return Response.json({ error: "id_required" }, { status: 400 });
  try {
    return Response.json({ event: await setRadarEventStatus0261(id, "cancelled") });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "radar_admin_cancel_failed" }, { status: 400 });
  }
}
