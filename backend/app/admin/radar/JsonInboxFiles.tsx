"use client";
import { useEffect, useState } from "react";
import type { UploadReceipt } from "@/src/radar-json-inbox/types";
import JsonUploadReceipt from "./JsonUploadReceipt";
import styles from "./json-import.module.css";
export default function JsonInboxFiles() {
  const [rows, setRows] = useState<UploadReceipt[]>([]),
    [message, setMessage] = useState("Carregando arquivos recebidos…"),
    [cursor, setCursor] = useState<string | null>(null),
    [busy, setBusy] = useState(false);
  const [page, setPage] = useState<string | null>(null),
    [reload, setReload] = useState(0),
    [selected, setSelected] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setBusy(true);
    const id = new URLSearchParams(window.location.search).get("upload_id");
    setSelected(Boolean(id));
    const query = id
      ? `?upload_id=${encodeURIComponent(id)}`
      : page
        ? `?cursor=${encodeURIComponent(page)}`
        : "";
    void fetch("/api/v1/admin/radar/json-inbox" + query, {
      cache: "no-store",
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw Error();
        const data = await response.json();
        if (controller.signal.aborted) return;
        setRows(data.uploads);
        setCursor(data.next_cursor);
        setMessage(
          data.uploads.length
            ? "Arquivos originais guardados em área privada."
            : "Nenhum arquivo recebido nesta consulta.",
        );
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setRows([]);
          setMessage("Não foi possível consultar os envios. Tente atualizar.");
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    return () => controller.abort();
  }, [page, reload]);
  return (
    <section className={styles.panel} aria-labelledby="json-inbox-title">
      <h2 id="json-inbox-title">Arquivos recebidos</h2>
      <p>
        Caixa privada de JSONs para triagem. Estes recibos não são lotes de
        importação nem execuções de coletores.
      </p>
      <p role="status">{message}</p>
      <div className={styles.actions}>
        <button
          type="button"
          disabled={busy}
          onClick={() => {
            setPage(null);
            setReload((n) => n + 1);
          }}
        >
          Atualizar arquivos recebidos
        </button>
        {selected ? (
          <a href="/admin/radar?tab=runs">Ver todos os arquivos</a>
        ) : null}
      </div>
      {rows.map((receipt) => (
        <JsonUploadReceipt key={receipt.upload_id} receipt={receipt} />
      ))}
      {cursor ? (
        <button type="button" disabled={busy} onClick={() => setPage(cursor)}>
          Próximos arquivos
        </button>
      ) : null}
    </section>
  );
}
