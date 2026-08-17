import { bearerToken, sha256 } from "../security";
import { adminSupabase } from "../supabase";

export type McpContext = { driverId: string; clientId: string; tokenId: string };

export async function authenticateMcp(request: Request): Promise<McpContext | null> {
  const token = bearerToken(request);
  if (!token || !token.startsWith("srmcp_")) return null;

  const supabase = adminSupabase();
  const { data, error } = await supabase
    .from("mcp_access_tokens")
    .select("id,driver_id")
    .eq("token_hash", sha256(token))
    .eq("revoked", false)
    .maybeSingle();

  if (error || !data) return null;

  // Best effort: falha aqui não invalida uma chave que acabou de autenticar.
  void supabase.from("mcp_access_tokens").update({ last_used_at: new Date().toISOString() }).eq("id", data.id);

  return {
    driverId: String(data.driver_id),
    tokenId: String(data.id),
    clientId: request.headers.get("user-agent")?.slice(0, 160) || "mcp-client",
  };
}
