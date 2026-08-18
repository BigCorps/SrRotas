import { authenticateDevice } from "@/src/device-auth";
import { saveBetaFeedback } from "@/src/beta-feedback";
import { allowSecurityAction } from "@/src/security-rate-limit";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const allowed = await allowSecurityAction(request, "beta-feedback", auth.driverId, 30, 3600);
  if (!allowed) return Response.json({ error: "rate_limited", message: "Muitos envios em pouco tempo." }, { status: 429 });

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  try {
    return Response.json({ ok: true, feedback: await saveBetaFeedback(auth.driverId, body) }, { status: 201 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "feedback_failed";
    return Response.json({ error: message, message: "Não foi possível registrar o feedback." }, { status: 400 });
  }
}
