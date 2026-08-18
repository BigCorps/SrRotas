import { createHash } from "node:crypto";
import { adminSupabase } from "./supabase";

function requestIp(request: Request) {
  return (request.headers.get("x-forwarded-for")?.split(",")[0] || request.headers.get("x-real-ip") || "unknown").trim().slice(0, 120);
}

export async function allowSecurityAction(
  request: Request,
  scope: string,
  identity: string,
  maxAttempts: number,
  windowSeconds: number,
) {
  const material = `${scope}|${requestIp(request)}|${identity.trim().toLowerCase()}`;
  const keyHash = createHash("sha256").update(material).digest("hex");
  const result = await adminSupabase().rpc("sr_security_rate_limit", {
    p_key_hash: keyHash,
    p_window_seconds: windowSeconds,
    p_max_attempts: maxAttempts,
  });
  if (result.error) throw new Error(result.error.message);
  return Boolean(result.data);
}
