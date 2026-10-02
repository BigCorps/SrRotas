import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { askNaturalQuestion } from "@/src/intelligence/engine";

export const runtime = "nodejs";
export const maxDuration = 60;

export async function POST(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const denied = accessDeniedResponse(auth.access, "can_ai");
  if (denied) return denied;

  const body = await request.json().catch(() => ({}));
  const question = String(body?.question ?? "").trim();
  if (question.length < 3 || question.length > 800) {
    return Response.json({ error: "question_invalid" }, { status: 400 });
  }

  try {
    return Response.json(
      await askNaturalQuestion(auth.driverId, question, body?.context, {
        days: Number(body?.days ?? 7) || 7,
        from: body?.from ? String(body.from) : undefined,
        to: body?.to ? String(body.to) : undefined,
      }),
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "ask_failed";
    return Response.json({ error: message }, { status: 500, headers: { "Cache-Control": "no-store" } });
  }
}
