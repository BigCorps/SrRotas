import { authenticateDevice } from "@/src/device-auth";
import { askDriver } from "@/src/ai";

export const runtime = "nodejs";
export const maxDuration = 60;

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => ({}));
  const question = String(body?.question ?? "").trim();
  if (question.length < 3 || question.length > 800) return Response.json({ error: "question_invalid" }, { status: 400 });
  const days = Math.max(1, Math.min(Number(body?.days ?? 7) || 7, 90));
  try {
    const answer = await askDriver(
      auth.driverId,
      question,
      body?.from ? String(body.from) : undefined,
      body?.to ? String(body.to) : undefined,
      days,
    );
    return Response.json(answer);
  } catch (error) {
    const message = error instanceof Error ? error.message : "ask_failed";
    const status = message === "openai_not_configured" ? 503 : ["subscription_required","ai_credits_required"].includes(message) ? 402 : 500;
    return Response.json({ error: message }, { status });
  }
}
