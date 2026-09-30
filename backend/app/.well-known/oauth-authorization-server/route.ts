import { authorizationServerMetadata, oauthNoStoreHeaders } from "../../../src/mcp/oauth";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  return Response.json(authorizationServerMetadata(request), {
    headers: oauthNoStoreHeaders(),
  });
}

export async function OPTIONS() {
  return new Response(null, {
    status: 204,
    headers: oauthNoStoreHeaders({
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    }),
  });
}
