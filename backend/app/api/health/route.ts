export const runtime = "nodejs";

export async function GET() {
  return Response.json({
    ok: true,
    service: "sr-rotas-backend",
    version: "0.4.0-alpha",
    time: new Date().toISOString(),
  });
}
