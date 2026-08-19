import { importActor } from "@/src/admin-imports";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const actor = await importActor(request);
  if (!actor) return Response.json({ error: "unauthorized" }, { status: 401 });
  if (!actor.allowed) return Response.json({ ok: false, allowed: false, email: actor.email, is_owner: false }, { status: 403 });
  return Response.json({ ok: true, allowed: true, email: actor.email, is_owner: actor.isOwner });
}
