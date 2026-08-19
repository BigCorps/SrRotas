import { adminSupabase } from "./supabase";
import { newToken, sha256 } from "./security";

export const IMPORT_ADMIN_COOKIE = "sr_import_admin";
const IMPORT_SESSION_SECONDS = 8 * 60 * 60;

function cookieValue(request: Request, name: string) {
  const raw = request.headers.get("cookie") || "";
  for (const part of raw.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

function normalizeEmail(value: unknown) {
  return String(value ?? "").trim().toLowerCase().slice(0, 180);
}

export async function createImportWebSession(authUserId: string, email: string) {
  const token = `srimp_${newToken()}`;
  const expiresAt = new Date(Date.now() + IMPORT_SESSION_SECONDS * 1000).toISOString();
  const normalizedEmail = normalizeEmail(email);

  const supabase = adminSupabase();
  await supabase
    .from("historical_import_web_sessions")
    .delete()
    .lt("expires_at", new Date().toISOString());

  const { error } = await supabase
    .from("historical_import_web_sessions")
    .insert({
      auth_user_id: authUserId,
      email: normalizedEmail,
      token_hash: sha256(token),
      expires_at: expiresAt,
    });

  if (error) throw new Error(error.message);
  return { token, expiresAt };
}

export function importCookieHeader(token: string, maxAgeSeconds = IMPORT_SESSION_SECONDS) {
  return `${IMPORT_ADMIN_COOKIE}=${encodeURIComponent(token)}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${maxAgeSeconds}`;
}

export function clearImportCookieHeader() {
  return `${IMPORT_ADMIN_COOKIE}=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0`;
}

export async function authenticateImportWeb(request: Request) {
  const token = cookieValue(request, IMPORT_ADMIN_COOKIE);
  if (!token || !token.startsWith("srimp_")) return null;

  const { data, error } = await adminSupabase()
    .from("historical_import_web_sessions")
    .select("id,auth_user_id,email,expires_at")
    .eq("token_hash", sha256(token))
    .gt("expires_at", new Date().toISOString())
    .maybeSingle();

  if (error || !data) return null;

  return {
    sessionId: String(data.id),
    authUserId: String(data.auth_user_id),
    email: normalizeEmail(data.email),
  };
}

export async function deleteImportWebSession(request: Request) {
  const token = cookieValue(request, IMPORT_ADMIN_COOKIE);
  if (!token) return;
  await adminSupabase()
    .from("historical_import_web_sessions")
    .delete()
    .eq("token_hash", sha256(token));
}
