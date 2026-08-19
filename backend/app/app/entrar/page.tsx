"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";

export default function EntrarPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("Use a mesma conta cadastrada no aplicativo.");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage("Entrando...");
    try {
      const response = await fetch("/api/v1/billing/web-session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data?.message || data?.error || "Não foi possível entrar.");
      const nextRaw = new URLSearchParams(window.location.search).get("next") || "/app";
      const next = nextRaw.startsWith("/app") ? nextRaw : "/app";
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

        <span className="srEyebrow">PAINEL WEB</span>
        <h1>Entrar</h1>
        <p>Histórico, IA, MCP, plano e perfil usando a mesma conta do Android.</p>

        <form onSubmit={submit} className="srLoginForm">
          <label>
            E-mail
            <input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
          <label>
            Senha
            <input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label>
          <button disabled={busy}>{busy ? "Entrando..." : "Entrar no painel"}</button>
        </form>

        <div className="srLoginMessage">{message}</div>
        <small className="srLoginNote">Nesta etapa paralela usamos uma sessão Web temporária. Na 1.0 o app Kotlin fará o handoff automaticamente, sem pedir login novamente.</small>
      </section>
    </main>
  );
}
