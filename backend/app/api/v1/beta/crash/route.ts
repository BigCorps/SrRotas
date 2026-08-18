import { authenticateDevice } from "@/src/device-auth";
import { saveBetaCrash } from "@/src/beta-feedback";
import { allowSecurityAction } from "@/src/security-rate-limit";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const allowed = await allowSecurityAction(request, "beta-crash", auth.driverId, 30, 86400);
  if (!allowed) return Response.json({ error: "rate_limited" }, { status: 429 });

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  try {
    return Response.json({ ok: true, crash: await saveBetaCrash(auth.driverId, body) }, { status: 201 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "crash_failed";
    return Response.json({ error: message }, { status: 400 });
  }
}
