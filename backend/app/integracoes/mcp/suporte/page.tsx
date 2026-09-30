import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "Suporte MCP Sr. Rotas" };

export default function McpSupportPage() {
  return (
    <main style={{ minHeight: "100vh", padding: "48px 20px", background: "#f4f7f4" }}>
      <article style={{ maxWidth: 760, margin: "0 auto", padding: 30, border: "1px solid #d9e5dc", borderRadius: 20, background: "#fff", color: "#173125", lineHeight: 1.75 }}>
        <Link href="/integracoes/mcp">← MCP Sr. Rotas</Link>
        <h1>Suporte do MCP</h1>
        <p>Para conectar, informe <strong>https://srrotas.com/mcp</strong> no cliente compatível com MCP remoto e OAuth.</p>
        <p>Se houver erro, informe o nome do cliente (ChatGPT, Claude, Cursor etc.), horário aproximado e mensagem apresentada. Não envie access token, refresh token, senha ou chave MCP manual.</p>
        <p>Contato: <strong>contato@bigcorps.com.br</strong>.</p>
      </article>
    </main>
  );
}
