import {
  revokeUserOAuthConnection,
  userOAuthConnections,
} from "@/src/mcp/oauth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  return userOAuthConnections(request);
}

export async function DELETE(request: Request) {
  const grantId =
    new URL(request.url).searchParams.get("grant_id")?.trim() || "";
  return revokeUserOAuthConnection(request, grantId);
}
