import {
  authorizationDetails,
  decideAuthorization,
} from "@/src/mcp/oauth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  return authorizationDetails(request);
}

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  return decideAuthorization(
    request,
    body && typeof body === "object" && !Array.isArray(body)
      ? body as Record<string, unknown>
      : {},
  );
}
