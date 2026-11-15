"use client";
import { useState } from "react";
import type { UploadReceipt } from "@/src/radar-json-inbox/types";
import styles from "./json-import.module.css";
export default function JsonUploadReceipt({
  receipt,
}: {
  receipt: UploadReceipt;
}) {
  const [message, setMessage] = useState("");
  return (
    <article className={styles.receipt} data-upload-id={receipt.upload_id}>
      <h3>
        {receipt.status === "quarantined"
          ? "Recebido — aguardando triagem"
          : "Arquivo eliminado após revisão de retenção"}
      </h3>
      <dl>
        <dt>upload_id</dt>
        <dd>
          <code>{receipt.upload_id}</code>
        </dd>
        <dt>Arquivo</dt>
        <dd>{receipt.original_name}</dd>
        <dt>SHA-256</dt>
        <dd>
          <code>{receipt.sha256}</code>
        </dd>
        <dt>Recebido em</dt>
        <dd>
          {new Date(receipt.received_at).toLocaleString("pt-BR", {
            timeZone: "America/Sao_Paulo",
            timeZoneName: "short",
          })}
        </dd>
        <dt>Tamanho</dt>
        <dd>{receipt.size_bytes} bytes</dd>
        <dt>Revisão de retenção</dt>
        <dd>
          {new Date(receipt.retention_review_at).toLocaleDateString("pt-BR")}
        </dd>
      </dl>
      <p>
        Nenhum evento ou local foi importado ou aprovado. Formato e conteúdo
        ainda aguardam análise.
      </p>
      <div className={styles.actions}>
        <button
          type="button"
          onClick={() =>
            void navigator.clipboard.writeText(receipt.upload_id).then(
              () => setMessage("upload_id copiado."),
              () => setMessage("Selecione o upload_id acima para copiar."),
            )
          }
        >
          Copiar upload_id
        </button>
        <a
          href={`/admin/radar?tab=runs&upload_id=${encodeURIComponent(receipt.upload_id)}`}
        >
          Ver arquivo em Envios
        </a>
        {receipt.status === "quarantined" ? (
          <a
            href={`/api/v1/admin/radar/json-inbox/${encodeURIComponent(receipt.upload_id)}/file`}
          >
            Baixar JSON original
          </a>
        ) : null}
      </div>
      <p role="status">{message}</p>
    </article>
  );
}
