import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "MCP Sr. Rotas",
  description:
    "Conecte ChatGPT, Claude, Cursor e outros clientes MCP ao Sr. Rotas com OAuth 2.1 e acesso somente leitura.",
};

export default function McpIntegrationPage() {
  return (
    <main style={{
      minHeight: "100vh",
      padding: "48px 20px",
      background: "#f4f7f4",
      color: "#173125",
    }}>
      <article style={{
        maxWidth: 820,
        margin: "0 auto",
        background: "#fff",
        border: "1px solid #d9e5dc",
        borderRadius: 22,
        padding: 30,
        lineHeight: 1.7,
      }}>
        <span style={{ fontSize: 12, fontWeight: 900, color: "#4f8939" }}>
          INTEGRAÇÃO OFICIAL
        </span>
        <h1>MCP do Sr. Rotas</h1>
        <p>
          Use <strong>https://srrotas.com/mcp</strong> como servidor MCP remoto.
          Clientes compatíveis descobrem automaticamente o OAuth 2.1, registram
          o cliente por DCR e abrem a tela do Sr. Rotas para autorização.
        </p>

        <h2>Permissão</h2>
        <p>
          O escopo público é <code>srrotas.read</code>. As ferramentas são
          somente leitura e consultam jornadas, ofertas observadas, estratégia,
          métricas, comparações e custos estimados.
        </p>

        <h2>O que o MCP nunca faz</h2>
        <p>
          Não aceita ou recusa corridas, não inicia jornada, não altera
          estratégia, não controla Uber/99 e não executa ações no aparelho.
        </p>

        <h2>Autenticação</h2>
        <p>
          OAuth 2.1 com Authorization Code + PKCE S256, Dynamic Client
          Registration, access token de curta duração, refresh token rotativo e
          revogação pelo próprio motorista. Códigos e tokens são armazenados
          somente em hash no backend.
        </p>

        <h2>Endpoint</h2>
        <pre style={{
          whiteSpace: "pre-wrap",
          padding: 14,
          borderRadius: 12,
          background: "#eef4ef",
        }}>https://srrotas.com/mcp</pre>

        <p>
          <Link href="/integracoes/mcp/privacidade">Privacidade do MCP</Link>
          {" · "}
          <Link href="/integracoes/mcp/suporte">Suporte</Link>
          {" · "}
          <Link href="/app/mcp">Gerenciar conexões</Link>
        </p>
      </article>
    </main>
  );
}
