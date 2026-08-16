import { authenticateDevice } from "@/src/device-auth";
import { askDriver } from "@/src/ai";

export const runtime = "nodejs";
export const maxDuration = 60;

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => ({}));
  const question = String(body?.question ?? "").trim();
  if (question.length < 3) return Response.json({ error: "question_required" }, { status: 400 });
  try {
    const answer = await askDriver(
      auth.driverId,
      question,
      body?.from ? String(body.from) : undefined,
      body?.to ? String(body.to) : undefined,
    );
    return Response.json(answer);
  } catch (error) {
    const message = error instanceof Error ? error.message : "ask_failed";
    return Response.json({ error: message }, { status: message === "openai_not_configured" ? 503 : 500 });
  }
}
