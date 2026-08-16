import { createMcpHandler } from "@modelcontextprotocol/server";
import { authenticateMcp } from "@/src/mcp/auth";
import { createDriverMcpServer } from "@/src/mcp/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 60;

const allowedHeaders = [
  "Authorization",
  "Content-Type",
  "Accept",
  "MCP-Protocol-Version",
  "MCP-Session-Id",
  "Last-Event-ID",
].join(", ");

function withCors(response: Response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  headers.set("Access-Control-Allow-Headers", allowedHeaders);
  headers.set("Access-Control-Expose-Headers", "WWW-Authenticate, MCP-Session-Id, MCP-Protocol-Version");
  headers.set("Cache-Control", "no-store");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

export async function OPTIONS() {
  return new Response(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": allowedHeaders,
      "Access-Control-Max-Age": "86400",
    },
  });
}

async function handle(request: Request) {
  const context = await authenticateMcp(request);
  if (!context) {
    return withCors(
      new Response(JSON.stringify({ error: "unauthorized" }), {
        status: 401,
        headers: {
          "content-type": "application/json",
          "WWW-Authenticate": 'Bearer realm="driver-ai-mcp"',
        },
      }),
    );
  }
  const handler = createMcpHandler(() => createDriverMcpServer(context), { legacy: "stateless" });
  return withCors(await handler.fetch(request));
}

export const GET = handle;
export const POST = handle;
export const DELETE = handle;
