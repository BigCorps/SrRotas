import { bearerToken, safeEqual } from "../security";
import { serverEnv } from "../env";
import { adminSupabase } from "../supabase";

export type McpContext = { driverId: string; clientId: string };

export async function authenticateMcp(request: Request): Promise<McpContext | null> {
  const token = bearerToken(request);
  const env = serverEnv();
  if (!token || !safeEqual(token, env.mcpApiToken)) return null;
  const { data, error } = await adminSupabase()
    .from("drivers")
    .select("id")
    .order("created_at", { ascending: true })
    .limit(1)
    .maybeSingle();
  if (error || !data) return null;
  return {
    driverId: String(data.id),
    clientId: request.headers.get("user-agent")?.slice(0, 160) || "mcp-client",
  };
}
