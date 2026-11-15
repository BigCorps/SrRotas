"use client";
import { useRef, useState } from "react";
import type { Preview } from "@/src/radar-admin-import/validation";
import { inspectRadarJsonFile } from "@/src/radar-admin-file-inspection";
import type { UploadReceipt } from "@/src/radar-json-inbox/types";
import JsonUploadReceipt from "./JsonUploadReceipt";
import styles from "./json-import.module.css";
const itemLabels = {
  ready: "Pronto",
  duplicate: "Duplicado",
  pending: "Pendente",
  invalid: "Inválido",
};
const labels = {
  ready: "Prontos",
  duplicate: "Duplicados",
  pending: "Pendentes",
  invalid: "Inválidos",
};
export default function JsonImportPreparation() {
  const [fileName, setFileName] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  const file = useRef<File | null>(null),
    key = useRef(""),
    sending = useRef(false);
  const [syntaxValid, setSyntaxValid] = useState(false);
  const [receipt, setReceipt] = useState<UploadReceipt | null>(null);
  async function inspect(selectedFile?: File) {
    const current = ++generation.current;
    file.current = selectedFile ?? null;
    key.current = crypto.randomUUID();
    setPreview(null);
    setSelected([]);
    setReceipt(null);
    setSyntaxValid(false);
    setMessage("");
    setFileName(selectedFile?.name ?? "");
    if (!selectedFile) return;
    if (selectedFile.size > 2_000_000) {
      setMessage("O arquivo ultrapassa o limite de 2 MB.");
      return;
    }
    setBusy(true);
    try {
      const result = inspectRadarJsonFile(
        new Uint8Array(await selectedFile.arrayBuffer()),
      );
      if (current !== generation.current) return;
      setSyntaxValid(result.status === "syntax_valid");
      setMessage(result.message + " Arquivo ainda não enviado.");
    } catch {
      if (current === generation.current)
        setMessage("Não foi possível ler o arquivo. Nenhum envio confirmado.");
    } finally {
      if (current === generation.current) setBusy(false);
    }
  }
  async function perform(action: "preview" | "upload") {
    if (sending.current || !file.current || !syntaxValid) return;
    sending.current = true;
    setBusy(true);
    const current = generation.current;
    try {
      const response = await fetch(
        action === "upload"
          ? "/api/v1/admin/radar/json-inbox"
          : "/api/v1/admin/radar/manual-import/preview",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(action === "upload"
              ? {
                  "Idempotency-Key": key.current,
                  "X-File-Name": encodeURIComponent(file.current.name),
                }
              : {}),
          },
          body: file.current,
          cache: "no-store",
        },
      );
      const result = await response.json();
      if (current !== generation.current) return;
      if (!response.ok) {
        setMessage(
          `Não concluído: ${typeof result.error === "string" ? result.error : (result.error?.code ?? response.status)}. Confira Envios antes de repetir. Nenhum evento ou local importado.`,
        );
        return;
      }
      if (action === "upload") {
        if (!result.receipt?.upload_id || !result.receipt?.sha256) {
          setMessage(
            "Recebimento não confirmado. Consulte Envios antes de repetir.",
          );
          return;
        }
        setReceipt(result.receipt);
        setMessage(
          "JSON recebido e guardado para triagem. Formato ainda não validado; nenhum evento ou local importado.",
        );
      } else {
        setPreview(result);
        setMessage(
          "Prévia concluída sem gravar eventos, locais ou lotes. A prévia não armazena o arquivo.",
        );
      }
    } catch {
      if (current === generation.current)
        setMessage(
          "Falha de rede ou leitura. Consulte Envios; repetir este envio usa a mesma chave e não duplica o recibo.",
        );
    } finally {
      sending.current = false;
      if (current === generation.current) setBusy(false);
    }
  }
  return (
    <section className={styles.panel} aria-labelledby="json-import-title">
      <div className={styles.heading}>
        <div>
          <span>CAIXA DE ENTRADA PRIVADA · TRIAGEM</span>
          <h2 id="json-import-title">Importar JSON</h2>
          <p>Envie o arquivo para análise, inclusive formatos antigos.</p>
        </div>
        <strong className={styles.badge}>Recebimento privado</strong>
      </div>
      <div className={styles.notice}>
        <strong>Confirmação bloqueada neste ambiente.</strong>
        <p>
          O recebimento guarda somente o arquivo original para triagem. Não
          cria, altera ou aprova eventos e locais. A importação definitiva
          permanece bloqueada até resolver a duplicidade entre fontes.
        </p>
      </div>
      <div className={styles.upload}>
        <h3>Enviar arquivo para triagem</h3>
        <label className={styles.fileLabel} htmlFor="radar-json-file">
          Escolher arquivo JSON
        </label>
        <input
          id="radar-json-file"
          type="file"
          disabled={busy}
          accept=".json,application/json"
          onChange={(e) => void inspect(e.target.files?.[0])}
        />
        <small>
          UTF-8 · até 2 MB · JSON válido, inclusive formatos antigos. Prévia 0.2
          opcional e separada.
        </small>
        <p className={styles.fileName}>{fileName}</p>
        <div className={styles.actions}>
          <button
            type="button"
            disabled={busy || !syntaxValid || receipt !== null}
            onClick={() => void perform("upload")}
          >
            Enviar JSON para triagem
          </button>
          <button
            type="button"
            disabled={busy || !syntaxValid}
            onClick={() => void perform("preview")}
          >
            Validar prévia (contrato 0.2)
          </button>
        </div>
        <div role="status" aria-live="polite">
          {busy ? "Processando arquivo…" : message}
        </div>
      </div>
      {receipt ? <JsonUploadReceipt receipt={receipt} /> : null}
      <div className={styles.counts} aria-label="Classificação dos registros">
        {Object.entries(labels).map(([key, label]) => (
          <div key={key}>
            <span>{label}</span>
            <strong>{preview ? preview.counts[key] : "—"}</strong>
            <small>{preview ? "Prévia sem gravação" : "Não avaliado"}</small>
          </div>
        ))}
      </div>
      <div className={styles.items}>
        {preview?.items.map((row, index) => (
          <article key={`${index}-${row.item_id}`} className={styles.item}>
            <label>
              <input
                type="checkbox"
                disabled={row.classification !== "ready"}
                checked={selected.includes(row.item_id)}
                onChange={(e) =>
                  setSelected((ids) =>
                    e.target.checked
                      ? [...ids, row.item_id]
                      : ids.filter((id) => id !== row.item_id),
                  )
                }
              />
              <strong>
                {row.item?.kind === "event"
                  ? row.item.data.name
                  : row.item?.kind === "poi"
                    ? row.item.data.canonical_name
                    : row.item_id}
              </strong>
            </label>
            <p>
              {row.item_id} · {row.kind} · {itemLabels[row.classification]}
              {row.classification === "ready"
                ? " para importar; ainda exige aprovação humana"
                : ""}
            </p>
            {row.item ? (
              <p>
                {row.item.data.city}/{row.item.data.state} ·{" "}
                {row.item.provenance.namespace} ·{" "}
                {row.item.provenance.official_url}
                {row.item.kind === "event"
                  ? ` · ${row.item.data.starts_at} a ${row.item.data.expected_end_at} (UTC)`
                  : ""}
              </p>
            ) : null}
            <ul>
              {row.reasons.map((r, i) => (
                <li key={i}>
                  {r.code}: {r.message}
                  {r.field ? ` (${r.field})` : ""}
                </li>
              ))}
            </ul>
          </article>
        ))}
      </div>
      <footer className={styles.footer}>
        <p>
          {selected.length} selecionado(s). Apenas prontos podem ser
          selecionados. O recebimento não altera entidades. A confirmação da
          importação definitiva está bloqueada por deduplicação entre fontes,
          sem endpoint publicado.
        </p>
        <button disabled>Confirmar importação indisponível</button>
      </footer>
    </section>
  );
}
