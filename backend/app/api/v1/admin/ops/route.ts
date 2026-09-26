import { canAccessAdminOps } from "@/src/admin-access";
import { importActor } from "@/src/admin-imports";
import { adminDriverDetail, adminOpsOverview } from "@/src/admin-ops";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function statusFor(message: string) {
  if (message === "invalid_driver_id") return 400;
  if (message === "driver_not_found") return 404;
  return 500;
}

async function requireAdminOps(request: Request) {
  const actor = await importActor(request);
  if (!actor) {
    return { actor: null, response: Response.json({ error: "unauthorized" }, { status: 401 }) };
  }
  if (!actor.allowed || !canAccessAdminOps(actor.email)) {
    return {
      actor,
      response: Response.json(
        { error: "admin_ops_forbidden", email: actor.email },
        { status: 403 },
      ),
    };
  }
  return { actor, response: null };
}

export async function GET(request: Request) {
  const checked = await requireAdminOps(request);
  if (checked.response) return checked.response;

  const url = new URL(request.url);
  const view = (url.searchParams.get("view") || "overview").trim();

  try {
    const body =
      view === "driver"
        ? await adminDriverDetail(url.searchParams.get("driver_id") || "")
        : await adminOpsOverview();

    return Response.json(body, {
      headers: {
        "Cache-Control": "no-store",
        "X-Sr-Rotas-Admin": "dual-admin-read-only",
      },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "admin_ops_failed";
    return Response.json({ error: message }, { status: statusFor(message) });
  }
}
