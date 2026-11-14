"use client";
import { useEffect, useState } from "react";
import styles from "./json-import.module.css";
export default function AdminImportBatches() {
  const [id, setId] = useState("");
  const [receipt, setReceipt] = useState<any>(null);
  const [message, setMessage] = useState("");
  useEffect(() => {
    setId(new URLSearchParams(window.location.search).get("batch_id") ?? "");
  }, []);
  async function consult() {
    setReceipt(null);
    setMessage("Consultando recibo…");
    try {
      const r = await fetch(
        `/api/v1/admin/radar/manual-import/batches/${encodeURIComponent(id)}`,
        { cache: "no-store" },
      );
      if (!r.ok) {
        setMessage(
          "Recibo indisponível. Armazenamento administrativo ainda não ativado neste ambiente.",
        );
        return;
      }
      setReceipt(await r.json());
      setMessage("");
    } catch {
      setMessage("Falha de rede; não gere outro lote.");
    }
  }
  return (
    <section className={styles.panel}>
      <h2>Lotes administrativos</h2>
      <p>
        batch_id independente do coletor. Eventos seguem para Recebidos; locais,
        para Lugares. Publicação exige revisão humana.
      </p>
      <label>
        Consultar batch_id
        <input
          aria-label="batch_id"
          value={id}
          maxLength={36}
          onChange={(e) => setId(e.target.value)}
        />
      </label>
      <button
        disabled={!/^[a-f\d]{8}-(?:[a-f\d]{4}-){3}[a-f\d]{12}$/i.test(id)}
        onClick={() => void consult()}
      >
        Consultar recibo
      </button>
      <p role="status">{message}</p>
      {receipt ? (
        <div>
          <strong>Recibo: {receipt.batch_id}</strong>
          <p>
            {receipt.created_at} · {receipt.items?.length ?? 0} criados ·
            revisão inicial pendente
          </p>
          <p>Recibo original imutável; estado atual na revisão.</p>
          {receipt.items?.map((r: any) => (
            <p key={r.item_id}>
              {r.item_id} · {r.kind} · {r.target_id} · {r.initial_review}
            </p>
          ))}
          <a href="/admin/radar?tab=events&origin=manual">
            Recebidos administrativos
          </a>
          <p>Revise os POIs inativos em Lugares.</p>
        </div>
      ) : null}
    </section>
  );
}
