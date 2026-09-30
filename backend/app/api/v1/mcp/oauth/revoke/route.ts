import { oauthNoStoreHeaders, revokeOAuthToken } from "@/src/mcp/oauth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  return revokeOAuthToken(request);
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
