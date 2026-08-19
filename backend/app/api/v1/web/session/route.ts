import { driverForAuthUser, normalizeEmail, passwordAuth } from "@/src/account";
import { createImportWebSession, importCookieHeader } from "@/src/admin-import-auth";
import { IMPORT_OWNER_EMAIL } from "@/src/admin-imports";
import { billingCookieHeader, createBillingWebSession } from "@/src/billing-auth";
import { allowSecurityAction } from "@/src/security-rate-limit";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const email = normalizeEmail(body?.email);
  const password = String(body?.password ?? "");

  if (!email || !password) {
    return Response.json({ error: "missing_credentials", message: "Informe e-mail e senha." }, { status: 400 });
  }

  try {
    const allowedAttempt = await allowSecurityAction(request, "web-login", email, 10, 900);
    if (!allowedAttempt) {
      return Response.json(
        { error: "rate_limited", message: "Muitas tentativas. Tente novamente mais tarde." },
        { status: 429 },
      );
    }
  } catch {
    return Response.json({ error: "login_guard_failed", message: "Não foi possível validar o login agora." }, { status: 503 });
  }

  let authUser;
  try {
    authUser = await passwordAuth(email, password);
  } catch {
    return Response.json(
      { error: "invalid_credentials", message: "E-mail ou senha incorretos." },
      { status: 401 },
    );
  }

  try {
    const authenticatedEmail = normalizeEmail(authUser.email || email);
    const driver = await driverForAuthUser(authUser.id);
    const isImportOwner = authenticatedEmail === IMPORT_OWNER_EMAIL;

    let canImport = isImportOwner;
    if (!canImport) {
      const access = await adminSupabase()
        .from("historical_import_access")
        .select("enabled")
        .eq("email", authenticatedEmail)
        .maybeSingle();
      canImport = Boolean(!access.error && access.data?.enabled);
    }

    if (!driver && !canImport) {
      return Response.json(
        {
          error: "account_without_access",
          message: "Esta conta não possui acesso ao painel de motorista nem às ferramentas autorizadas.",
        },
        { status: 403 },
      );
    }

    const headers = new Headers();
    let driverSessionExpiresAt: string | null = null;
    let adminSessionExpiresAt: string | null = null;

    if (driver) {
      const session = await createBillingWebSession(String(driver.id));
      driverSessionExpiresAt = session.expiresAt;
      headers.append("Set-Cookie", billingCookieHeader(session.token));
    } else if (canImport) {
      // Contas administrativas/importadores que não são motoristas.
      const session = await createImportWebSession(authUser.id, authenticatedEmail);
      adminSessionExpiresAt = session.expiresAt;
      headers.append("Set-Cookie", importCookieHeader(session.token));
    }

    return Response.json(
      {
        ok: true,
        email: authenticatedEmail,
        display_name: driver?.display_name ?? (isImportOwner ? "BigCorps" : "Importador"),
        is_driver: Boolean(driver),
        can_import: canImport,
        is_import_owner: isImportOwner,
        redirect: driver ? "/app" : "/admin/importacoes",
        expires_at: driverSessionExpiresAt || adminSessionExpiresAt,
      },
      { headers },
    );
  } catch (error) {
    return Response.json(
      {
        error: "web_session_failed",
        message: error instanceof Error ? error.message : "Não foi possível criar a sessão Web.",
      },
      { status: 500 },
    );
  }
}
