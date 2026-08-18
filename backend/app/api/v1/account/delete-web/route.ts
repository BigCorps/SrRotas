import { driverForAuthUser, normalizeEmail, passwordAuth } from "@/src/account";
import { deleteDriverAccount } from "@/src/account-deletion";
import { allowSecurityAction } from "@/src/security-rate-limit";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const email = normalizeEmail(body?.email);
  const password = String(body?.password ?? "");
  const confirmation = String(body?.confirmation ?? "").trim().toUpperCase();
  if (!email || !password || confirmation !== "EXCLUIR") {
    return Response.json({ error: "invalid_request", message: "Preencha e-mail, senha e a confirmação EXCLUIR." }, { status: 400 });
  }

  const allowed = await allowSecurityAction(request, "account-delete-web", email, 5, 3600);
  if (!allowed) return Response.json({ error: "rate_limited", message: "Muitas tentativas. Tente novamente mais tarde." }, { status: 429 });

  try {
    const user = await passwordAuth(email, password);
    const driver = await driverForAuthUser(user.id);
    if (!driver) return Response.json({ error: "account_not_found", message: "Conta não encontrada." }, { status: 404 });
    await deleteDriverAccount(String(driver.id));
    return Response.json({ ok: true, deleted: true });
  } catch (error) {
    const message = error instanceof Error ? error.message : "account_delete_failed";
    if (message === "invalid_credentials") {
      return Response.json({ error: "invalid_credentials", message: "E-mail ou senha incorretos." }, { status: 401 });
    }
    return Response.json({ error: message }, { status: 500 });
  }
}
