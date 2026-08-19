"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";

type LoginResult = {
  redirect?: string;
  is_driver?: boolean;
  can_import?: boolean;
};

function safeRequestedNext(value: string | null) {
  if (!value) return null;
  if (value === "/admin/importacoes" || value.startsWith("/admin/importacoes?")) return value;
  if (value === "/app" || value.startsWith("/app/")) return value;
  return null;
}

export default function EntrarPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("Entre com seu acesso Sr. Rotas.");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage("Entrando...");

    try {
      const response = await fetch("/api/v1/web/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      const data = await response.json() as LoginResult & { message?: string; error?: string };
      if (!response.ok) throw new Error(data?.message || data?.error || "Não foi possível entrar.");

      const requestedNext = safeRequestedNext(new URLSearchParams(window.location.search).get("next"));
      let next = data.redirect || "/app";

      if (requestedNext?.startsWith("/admin/importacoes") && data.can_import) {
        next = requestedNext;
      } else if (requestedNext?.startsWith("/app") && data.is_driver) {
        next = requestedNext;
      }

      router.replace(next);
      router.refresh();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível entrar.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="srLoginPage">
      <section className="srLoginCard">
        <a href="/" className="srLoginBrand">
          <img src="/logo-srrotas.png" alt="" />
          <span><strong>Sr. Rotas</strong><small>Seu copiloto de rentabilidade</small></span>
        </a>

        <span className="srEyebrow">ACESSO WEB</span>
        <h1>Entrar</h1>
        <p>Motoristas acessam o painel Sr. Rotas. Contas autorizadas pela BigCorps também podem acessar as ferramentas internas.</p>

        <form onSubmit={submit} className="srLoginForm">
          <label>
            E-mail
            <input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
          <label>
            Senha
            <input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label>
          <button disabled={busy}>{busy ? "Entrando..." : "Entrar"}</button>
        </form>

        <div className="srLoginMessage">{message}</div>
        <small className="srLoginNote">O destino é escolhido automaticamente conforme as permissões da conta. Motoristas continuam usando a mesma conta do Android.</small>
      </section>
    </main>
  );
}
