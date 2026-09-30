"use client";

import { useEffect, useState } from "react";
import styles from "../mcp.module.css";

type Details = {
  request_id: string;
  client: {
    client_id: string;
    name: string;
    client_uri: string | null;
    logo_uri: string | null;
  };
  redirect_uri: string;
  scopes: string[];
  access_state: string;
  can_mcp: boolean;
  expires_at: string;
};

export default function McpAuthorizePage() {
  const [requestId, setRequestId] = useState("");
  const [details, setDetails] = useState<Details | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const id =
      new URLSearchParams(window.location.search).get("request_id") || "";
    setRequestId(id);
    if (!id) {
      setError("Pedido de autorização ausente.");
      return;
    }

    fetch(
      `/api/v1/mcp/oauth/authorization?request_id=${encodeURIComponent(id)}`,
      { cache: "no-store" },
    )
      .then(async (response) => {
        const data = await response.json();
        if (!response.ok) {
          throw new Error(
            data?.message ||
              data?.error ||
              "Não foi possível carregar a autorização.",
          );
        }
        setDetails(data);
      })
      .catch((reason) => {
        setError(
          reason instanceof Error
            ? reason.message
            : "Não foi possível carregar a autorização.",
        );
      });
  }, []);

  async function decide(decision: "approve" | "deny") {
    if (!requestId) return;
    setBusy(true);
    setError("");

    try {
      const response = await fetch("/api/v1/mcp/oauth/authorization", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          request_id: requestId,
          decision,
        }),
      });
      const data = await response.json();

      if (!response.ok) {
        throw new Error(
          data?.message ||
            data?.error ||
            "Não foi possível concluir a autorização.",
        );
      }

      if (!data?.redirect_to) {
        throw new Error("O cliente não informou um retorno válido.");
      }

      window.location.assign(String(data.redirect_to));
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Não foi possível concluir a autorização.",
      );
      setBusy(false);
    }
  }

  return (
    <div className={styles.consentWrap}>
      <section className={styles.consentCard}>
        <div className={styles.consentBrand}>
          <img src="/icons/icon-192.png" alt="" />
          <div>
            <strong>Sr. Rotas</strong>
            <small>Autorização MCP · OAuth 2.1</small>
          </div>
        </div>

        <span className="srEyebrow">CONECTAR SUA IA</span>
        <h1>
          {details
            ? `${details.client.name} quer consultar seu Sr. Rotas`
            : "Validando autorização…"}
        </h1>

        {details ? (
          <>
            <div className={styles.clientBox}>
              <strong>{details.client.name}</strong>
              {details.client.client_uri ? (
                <span>{details.client.client_uri}</span>
              ) : null}
              <small>Retorno autorizado: {details.redirect_uri}</small>
            </div>

            <div className={styles.permission}>
              <b>✓ Consultar seus dados do Sr. Rotas</b>
              <span>
                Jornadas, ofertas observadas, estratégia, métricas, custos
                estimados e comparações.
              </span>
            </div>

            <div className={styles.permission}>
              <b>✓ Somente leitura</b>
              <span>
                O cliente não pode aceitar ou recusar corrida, alterar sua
                estratégia, iniciar jornada nem controlar outros aplicativos.
              </span>
            </div>

            <div className={styles.permission}>
              <b>✓ Revogável</b>
              <span>
                Você poderá desconectar esta IA a qualquer momento em
                Usuário → Sua IA (MCP).
              </span>
            </div>

            {!details.can_mcp ? (
              <div className={styles.error}>
                O MCP não está disponível para sua conta neste momento.
                Estado: {details.access_state}.
              </div>
            ) : null}

            <div className={styles.actions}>
              <button
                className={styles.deny}
                disabled={busy}
                onClick={() => decide("deny")}
              >
                Não autorizar
              </button>
              <button
                className={styles.approve}
                disabled={busy || !details.can_mcp}
                onClick={() => decide("approve")}
              >
                {busy ? "Autorizando…" : "Autorizar somente leitura"}
              </button>
            </div>
          </>
        ) : null}

        {error ? <div className={styles.error}>{error}</div> : null}
      </section>
    </div>
  );
}
