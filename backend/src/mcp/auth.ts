import { resolveDriverAccess } from "../access";
import { bearerToken, sha256 } from "../security";
import { adminSupabase } from "../supabase";
import { MCP_SCOPE } from "./oauth";

export type McpContext = {
  driverId: string;
  clientId: string;
  tokenId: string;
  authType: "oauth" | "legacy_key";
  scopes: string[];
};

async function authenticateLegacy(
  token: string,
  request: Request,
): Promise<McpContext | null> {
  const supabase = adminSupabase();
  const { data, error } = await supabase
    .from("mcp_access_tokens")
    .select("id,driver_id")
    .eq("token_hash", sha256(token))
    .eq("revoked", false)
    .maybeSingle();

  if (error || !data) return null;

  const access = await resolveDriverAccess(String(data.driver_id), null);
  if (!access.effective.can_mcp) return null;

  await supabase
    .from("mcp_access_tokens")
    .update({ last_used_at: new Date().toISOString() })
    .eq("id", data.id)
    .then(() => undefined)
    .catch(() => undefined);

  return {
    driverId: String(data.driver_id),
    tokenId: String(data.id),
    clientId:
      request.headers.get("user-agent")?.slice(0, 160) || "legacy-mcp-client",
    authType: "legacy_key",
    scopes: [MCP_SCOPE],
  };
}

async function authenticateOAuth(
  token: string,
): Promise<McpContext | null> {
  const supabase = adminSupabase();
  const now = new Date().toISOString();

  const { data, error } = await supabase
    .from("mcp_oauth_tokens")
    .select("id,driver_id,client_id,grant_id,scopes,access_expires_at,revoked_at")
    .eq("access_token_hash", sha256(token))
    .is("revoked_at", null)
    .gt("access_expires_at", now)
    .maybeSingle();

  if (error || !data) return null;

  const [grant, client] = await Promise.all([
    supabase
      .from("mcp_oauth_grants")
      .select("id,revoked_at")
      .eq("id", data.grant_id)
      .eq("driver_id", data.driver_id)
      .eq("client_id", data.client_id)
      .is("revoked_at", null)
      .maybeSingle(),
    supabase
      .from("mcp_oauth_clients")
      .select("client_id,revoked_at")
      .eq("client_id", data.client_id)
      .is("revoked_at", null)
      .maybeSingle(),
  ]);

  if (grant.error || !grant.data || client.error || !client.data) return null;

  const scopes = Array.isArray(data.scopes)
    ? data.scopes.map(String)
    : [MCP_SCOPE];
  if (!scopes.includes(MCP_SCOPE)) return null;

  const access = await resolveDriverAccess(String(data.driver_id), null);
  if (!access.effective.can_mcp) return null;

  const usedAt = new Date().toISOString();
  await Promise.all([
    supabase
      .from("mcp_oauth_tokens")
      .update({ last_used_at: usedAt })
      .eq("id", data.id),
    supabase
      .from("mcp_oauth_grants")
      .update({ last_used_at: usedAt, updated_at: usedAt })
      .eq("id", data.grant_id),
  ]).catch(() => undefined);

  return {
    driverId: String(data.driver_id),
    tokenId: String(data.id),
    clientId: String(data.client_id),
    authType: "oauth",
    scopes,
  };
}

export async function authenticateMcp(
  request: Request,
): Promise<McpContext | null> {
  const token = bearerToken(request);
  if (!token) return null;

  if (token.startsWith("srmcpo_")) {
    return authenticateOAuth(token);
  }

  if (token.startsWith("srmcp_")) {
    return authenticateLegacy(token, request);
  }

  return null;
}
