import { passwordAuth } from "@/src/account";
import { IMPORT_OWNER_EMAIL } from "@/src/admin-imports";
import { createImportWebSession, importCookieHeader } from "@/src/admin-import-auth";
import { allowSecurityAction } from "@/src/security-rate-limit";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function normalizeEmail(value: unknown) {
  return String(value ?? "").trim().toLowerCase().slice(0, 180);
}

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const email = normalizeEmail(body?.email);
  const password = String(body?.password ?? "");

  if (!email || !password) {
    return Response.json({ error: "missing_credentials" }, { status: 400 });
  }

  const allowedAttempt = await allowSecurityAction(request, "admin-import-login", email, 8, 900);
  if (!allowedAttempt) {
    return Response.json(
      { error: "rate_limited", message: "Muitas tentativas. Tente novamente mais tarde." },
      { status: 429 },
    );
  }

  try {
    const authUser = await passwordAuth(email, password);
    const authenticatedEmail = normalizeEmail(authUser.email || email);
    const isOwner = authenticatedEmail === IMPORT_OWNER_EMAIL;

    if (!isOwner) {
      const access = await adminSupabase()
        .from("historical_import_access")
        .select("enabled")
        .eq("email", authenticatedEmail)
        .maybeSingle();

      if (access.error || !access.data?.enabled) {
        return Response.json(
          { error: "forbidden", message: "Este e-mail não está autorizado para importações." },
          { status: 403 },
        );
      }
    }

    const session = await createImportWebSession(authUser.id, authenticatedEmail);

    return Response.json(
      {
        ok: true,
        email: authenticatedEmail,
        is_owner: isOwner,
        expires_at: session.expiresAt,
      },
      { headers: { "Set-Cookie": importCookieHeader(session.token) } },
    );
  } catch {
    return Response.json(
      { error: "invalid_credentials", message: "E-mail ou senha incorretos." },
      { status: 401 },
    );
  }
}
