import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "Privacidade do MCP Sr. Rotas" };

export default function McpPrivacyPage() {
  return (
    <main style={{ minHeight: "100vh", padding: "48px 20px", background: "#f4f7f4" }}>
      <article style={{ maxWidth: 760, margin: "0 auto", padding: 30, border: "1px solid #d9e5dc", borderRadius: 20, background: "#fff", color: "#173125", lineHeight: 1.75 }}>
        <Link href="/integracoes/mcp">← MCP Sr. Rotas</Link>
        <h1>Privacidade do conector MCP</h1>
        <p>O conector acessa somente os dados da conta Sr. Rotas autorizada e somente enquanto o Access Resolver permitir MCP.</p>
        <p>O escopo público é somente leitura. O cliente pode consultar histórico, jornadas, ofertas observadas, estratégia, métricas, comparações e custos estimados.</p>
        <p>O Sr. Rotas registra auditoria técnica com ferramenta, cliente, horário, duração, status e hash dos argumentos. O conteúdo integral retornado não é persistido nessa auditoria.</p>
        <p>Códigos OAuth, access tokens e refresh tokens são armazenados somente como SHA-256. O usuário pode revogar a conexão em Usuário → Sua IA (MCP).</p>
        <p>O MCP não consome os créditos da IA interna do Sr. Rotas.</p>
      </article>
    </main>
  );
}
