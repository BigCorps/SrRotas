import { beginAuthorization } from "@/src/mcp/oauth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  return beginAuthorization(request);
}
