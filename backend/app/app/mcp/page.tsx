"use client";

import { useEffect, useState } from "react";
import AccountPageHeader from "../_components/AccountPageHeader";
import styles from "./mcp.module.css";

type Token = {
  id: string;
  name: string;
  token_prefix: string;
  last_used_at: string | null;
  created_at: string;
};
type Created = Token & { token: string; endpoint: string };
type Connection = {
  grant_id: string;
  client_id: string;
  client_name: string;
  client_uri: string | null;
  logo_uri: string | null;
  scopes: string[];
  approved_at: string;
  last_used_at: string | null;
};
type ConnectionsPayload = {
  endpoint: string;
  access_state: string;
  can_connect: boolean;
  connections: Connection[];
};

function when(value?: string | null) {
  if (!value) return "Nunca";
  return new Date(value).toLocaleString("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  });
}

export default function McpPage() {
  const [tokens, setTokens] = useState<Token[]>([]);
  const [connections, setConnections] = useState<Connection[]>([]);
  const [created, setCreated] = useState<Created | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const endpoint = "https://srrotas.com/mcp";

  async function load() {
    const [manualResponse, oauthResponse] = await Promise.all([
      fetch("/api/v1/mcp/tokens", { cache: "no-store" }),
      fetch("/api/v1/mcp/connections", { cache: "no-store" }),
    ]);

    if (manualResponse.ok) {
      const data = await manualResponse.json();
      setTokens(data.tokens ?? []);
    }
    if (oauthResponse.ok) {
      const data = (await oauthResponse.json()) as ConnectionsPayload;
      setConnections(data.connections ?? []);
    }
  }

  useEffect(() => {
    load().catch(() => undefined);
  }, []);

  async function createToken() {
    const name = window
      .prompt("Nome desta integração:", "Integração manual")
      ?.trim();
    if (!name) return;

    setBusy(true);
    try {
      const response = await fetch("/api/v1/mcp/tokens", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name }),
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(
          data?.message ||
            data?.error ||
            "Não foi possível gerar a chave.",
        );
      }
      setCreated(data);
      await load();
      setMessage(
        "Chave criada. Copie agora: o segredo completo não será mostrado novamente.",
      );
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Não foi possível gerar a chave.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function revokeLegacy(id: string) {
    if (!window.confirm("Revogar esta chave MCP manual?")) return;
    const response = await fetch(
      `/api/v1/mcp/tokens?id=${encodeURIComponent(id)}`,
      { method: "DELETE" },
    );
    if (response.ok) {
      setMessage("Chave manual revogada.");
      setCreated(null);
      await load();
    }
  }

  async function revokeOAuth(grantId: string, name: string) {
    if (!window.confirm(`Desconectar ${name} do Sr. Rotas?`)) return;
    const response = await fetch(
      `/api/v1/mcp/connections?grant_id=${encodeURIComponent(grantId)}`,
      { method: "DELETE" },
    );
    if (response.ok) {
      setMessage(`${name} foi desconectado.`);
      await load();
    }
  }

  async function copyValue(value: string) {
    await navigator.clipboard.writeText(value);
    setMessage("Copiado.");
  }

  return (
    <div className="sr023Page srAccountSubpage">
      <AccountPageHeader
        title="Sua IA (MCP)"
        subtitle="Conecte ChatGPT, Claude, Cursor ou outro cliente MCP para consultar seus dados do Sr. Rotas."
      />

      <section className={styles.connectHero}>
        <article>
          <span className={styles.oauthBadge}>OAuth 2.1 · conexão recomendada</span>
          <h2>Conecte sem copiar chave</h2>
          <p className={styles.muted}>
            No seu cliente de IA, adicione um servidor MCP remoto e informe
            somente o endpoint abaixo. O cliente descobre a autenticação e abre
            a tela do Sr. Rotas para você autorizar.
          </p>
          <div className={styles.endpoint}>{endpoint}</div>
          <button
            className="srPrimary srInlineButton"
            onClick={() => copyValue(endpoint)}
          >
            Copiar endpoint
          </button>
          <div className={styles.steps}>
            <span><b>1</b>Adicione um servidor/conector MCP no cliente de IA.</span>
            <span><b>2</b>Cole https://srrotas.com/mcp.</span>
            <span><b>3</b>Entre no Sr. Rotas e aprove o acesso somente leitura.</span>
          </div>
        </article>

        <article>
          <span className="srEyebrow">PRIVACIDADE</span>
          <h2>Somente leitura</h2>
          <div className="srCheckGrid">
            <span>✓ Jornadas</span>
            <span>✓ Ofertas observadas</span>
            <span>✓ Estratégia</span>
            <span>✓ Métricas</span>
            <span>✓ Comparações</span>
            <span>✓ Custos estimados</span>
          </div>
          <div className="srCallout">
            <b>Não usa créditos de IA</b>
            <span>
              O MCP não aceita/recusa corridas e não controla Uber, 99 ou
              qualquer aplicativo de mobilidade.
            </span>
          </div>
        </article>
      </section>

      <section className="srPanel srSectionGap">
        <div className="srPanelHead">
          <div>
            <span className="srEyebrow">CONEXÕES OAUTH</span>
            <h2>Clientes autorizados</h2>
          </div>
          <a href="/integracoes/mcp" className="srSecondaryButton">
            Ver documentação
          </a>
        </div>

        {connections.length ? (
          <div className={styles.connectionList}>
            {connections.map((connection) => (
              <div className={styles.connectionCard} key={connection.grant_id}>
                <div className={styles.connectionIcon}>AI</div>
                <div className={styles.connectionBody}>
                  <strong>{connection.client_name}</strong>
                  <small>
                    Autorizado {when(connection.approved_at)}
                    {connection.last_used_at
                      ? ` · último uso ${when(connection.last_used_at)}`
                      : " · ainda não utilizado"}
                  </small>
                  <small>Permissão: somente leitura</small>
                </div>
                <button
                  className={styles.revoke}
                  onClick={() =>
                    revokeOAuth(
                      connection.grant_id,
                      connection.client_name,
                    )
                  }
                >
                  Desconectar
                </button>
              </div>
            ))}
          </div>
        ) : (
          <div className="srEmpty">
            <span>◇</span>
            <strong>Nenhuma IA conectada por OAuth</strong>
            <p>
              Quando você autorizar ChatGPT, Claude, Cursor ou outro cliente,
              ele aparecerá aqui.
            </p>
          </div>
        )}
      </section>

      <details className={styles.advanced}>
        <summary>Modo avançado · chave MCP manual</summary>
        <div className={styles.advancedBody}>
          <p className={styles.muted}>
            Compatibilidade para clientes que ainda não suportam OAuth/DCR.
            Prefira OAuth sempre que disponível.
          </p>

          {created ? (
            <section className="srSecretPanel">
              <span className="srEyebrow">CHAVE CRIADA AGORA</span>
              <h2>Copie antes de sair desta tela</h2>
              <div className="srCode">{created.token}</div>
              <button
                className="srPrimary srInlineButton"
                onClick={() => copyValue(created.token)}
              >
                Copiar chave
              </button>
            </section>
          ) : null}

          <div className="srPanelHead">
            <div>
              <span className="srEyebrow">CHAVES LEGADAS</span>
              <h2>Gerenciar chaves manuais</h2>
            </div>
            <button
              className="srPrimary srInlineButton"
              onClick={createToken}
              disabled={busy}
            >
              {busy ? "Gerando..." : "Gerar chave"}
            </button>
          </div>

          {tokens.length ? (
            <div className="srTokenList">
              {tokens.map((token) => (
                <div className="srTokenRow" key={token.id}>
                  <div>
                    <strong>{token.name}</strong>
                    <small>
                      {token.token_prefix}… · criada{" "}
                      {new Date(token.created_at).toLocaleDateString("pt-BR")}
                      {token.last_used_at
                        ? ` · usada ${new Date(token.last_used_at).toLocaleDateString("pt-BR")}`
                        : ""}
                    </small>
                  </div>
                  <button onClick={() => revokeLegacy(token.id)}>
                    Revogar
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <p className={styles.muted}>Nenhuma chave manual ativa.</p>
          )}
        </div>
      </details>

      {message ? <p className="srDataNote">{message}</p> : null}
    </div>
  );
}
