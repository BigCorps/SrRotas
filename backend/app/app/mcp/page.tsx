"use client";

import { useEffect, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";

type Token = { id: string; name: string; token_prefix: string; last_used_at: string | null; created_at: string };
type Created = Token & { token: string; endpoint: string };

export default function McpPage() {
  const [tokens, setTokens] = useState<Token[]>([]);
  const [created, setCreated] = useState<Created | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function load() {
    const response = await fetch("/api/v1/mcp/tokens", { cache: "no-store" });
    if (!response.ok) return;
    const data = await response.json();
    setTokens(data.tokens ?? []);
  }

  useEffect(() => { load().catch(() => undefined); }, []);

  async function createToken() {
    const name = window.prompt("Nome desta integração:", "Meu ChatGPT")?.trim();
    if (!name) return;
    setBusy(true);
    try {
      const response = await fetch("/api/v1/mcp/tokens", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) });
      const data = await response.json();
      if (!response.ok) throw new Error(data?.message || data?.error || "Não foi possível gerar a chave.");
      setCreated(data); await load();
      setMessage("Chave criada. Copie agora: por segurança, o segredo completo não será mostrado novamente.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível gerar a chave.");
    } finally { setBusy(false); }
  }

  async function revoke(id: string) {
    if (!window.confirm("Revogar esta chave MCP?")) return;
    const response = await fetch(`/api/v1/mcp/tokens?id=${encodeURIComponent(id)}`, { method: "DELETE" });
    if (response.ok) { setMessage("Chave revogada."); setCreated(null); await load(); }
  }

  async function copyValue(value: string) {
    await navigator.clipboard.writeText(value);
    setMessage("Copiado.");
  }

  return <div className="sr023Page srAccountSubpage">
    <AccountPageHeader
      title="Segurança MCP"
      subtitle="Conecte ChatGPT, Claude, Cursor e outros clientes compatíveis em modo somente leitura."
    />

    <section className="srGrid2">
      <article className="srPanel"><span className="srEyebrow">ENDPOINT OFICIAL</span><div className="srCode">https://srrotas.com/mcp</div><button className="srSecondaryButton" onClick={() => copyValue("https://srrotas.com/mcp")}>Copiar endpoint</button><div className="srCallout"><b>Somente leitura</b><span>O MCP não aceita/recusa corridas e não controla aplicativos de mobilidade.</span></div></article>
      <article className="srPanel"><span className="srEyebrow">O QUE PODE CONSULTAR</span><div className="srCheckGrid"><span>✓ Jornadas</span><span>✓ Ofertas</span><span>✓ Estratégia</span><span>✓ Métricas</span><span>✓ Comparações</span><span>✓ Resumos</span></div></article>
    </section>

    {created ? <section className="srPanel srSectionGap srSecretPanel"><span className="srEyebrow">CHAVE CRIADA AGORA</span><h2>Copie antes de sair desta tela</h2><div className="srCode">{created.token}</div><button className="srPrimary srInlineButton" onClick={() => copyValue(created.token)}>Copiar chave</button></section> : null}

    <section className="srPanel srSectionGap">
      <div className="srPanelHead"><div><span className="srEyebrow">CHAVES DE ACESSO</span><h2>Gerencie seus clientes MCP</h2></div><button className="srPrimary srInlineButton" onClick={createToken} disabled={busy}>{busy ? "Gerando..." : "Gerar chave"}</button></div>
      {tokens.length ? <div className="srTokenList">{tokens.map(token => <div className="srTokenRow" key={token.id}><div><strong>{token.name}</strong><small>{token.token_prefix}… · criada {new Date(token.created_at).toLocaleDateString("pt-BR")}{token.last_used_at ? ` · usada ${new Date(token.last_used_at).toLocaleDateString("pt-BR")}` : ""}</small></div><button onClick={() => revoke(token.id)}>Revogar</button></div>)}</div> : <div className="srEmpty"><span>&lt;/&gt;</span><strong>Nenhuma chave ativa</strong><p>Gere uma chave somente quando for conectar um cliente MCP.</p></div>}
      {message ? <p className="srDataNote">{message}</p> : null}
    </section>
  </div>;
}
