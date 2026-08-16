export const runtime = "nodejs";

export async function GET() {
  return Response.json({ ok: true, service: "driver-ai-backend", version: "0.1.0" });
}
