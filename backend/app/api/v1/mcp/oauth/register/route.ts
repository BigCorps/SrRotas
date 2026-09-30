import { oauthNoStoreHeaders, registerDynamicClient } from "@/src/mcp/oauth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  return registerDynamicClient(
    request,
    body && typeof body === "object" && !Array.isArray(body)
      ? body as Record<string, unknown>
      : {},
  );
}

export async function OPTIONS() {
  return new Response(null, {
    status: 204,
    headers: oauthNoStoreHeaders({
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
    }),
  });
}
