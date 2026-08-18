export function serviceAuthorized(request: Request, serviceRoleKey: string) {
  const authorization = request.headers.get("authorization") ?? "";
  const [scheme, token] = authorization.split(/\s+/, 2);
  if (scheme?.toLowerCase() !== "bearer" || !token) return false;
  if (serviceRoleKey && token === serviceRoleKey) return true;

  const payload = decodeJwtPayload(token);
  if (String(payload?.role ?? "") !== "service_role") return false;
  const expiresAt = Number(payload?.exp ?? 0);
  return !Number.isFinite(expiresAt) || expiresAt <= 0 || expiresAt > Math.floor(Date.now() / 1000);
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const payload = JSON.parse(atob(padded));
    return payload && typeof payload === "object" && !Array.isArray(payload) ? payload as Record<string, unknown> : null;
  } catch { return null; }
}
