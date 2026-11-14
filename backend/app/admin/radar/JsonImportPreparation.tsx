"use client";
import { useRef, useState } from "react";
import type { Preview } from "@/src/radar-admin-import/validation";
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
  async function inspect(file?: File) {
    const current = ++generation.current;
    setPreview(null);
    setSelected([]);
    setFileName(file?.name ?? "");
    setMessage("");
    setBusy(false);
    if (!file) return;
    if (file.size > 2_000_000) {
      setMessage("O arquivo ultrapassa o limite de 2 MB.");
      return;
    }
    setBusy(true);
    try {
      const bytes = await file.arrayBuffer();
      if (generation.current !== current) return;
      const r = await fetch("/api/v1/admin/radar/manual-import/preview", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: bytes,
        cache: "no-store",
      });
      if (generation.current !== current) return;
      const result = await r.json();
      if (generation.current !== current) return;
      if (!r.ok) {
        setMessage(
          `Prévia indisponível: ${typeof result.error === "string" ? result.error : (result.error?.code ?? r.status)}. Nenhum registro importado.`,
        );
        return;
      }
      setPreview(result);
      setMessage("Prévia concluída sem gravar eventos, locais ou lotes.");
    } catch {
      if (generation.current === current)
        setMessage(
          "Falha de rede ou leitura. Selecione novamente; nenhum envio confirmado.",
        );
    } finally {
      if (generation.current === current) setBusy(false);
    }
  }
  return (
    <section className={styles.panel} aria-labelledby="json-import-title">
      <div className={styles.heading}>
        <div>
          <span>CURADORIA MANUAL · CONTRATO NOVO 0.2</span>
          <h2 id="json-import-title">Importar JSON</h2>
          <p>Eventos e locais para revisão humana.</p>
        </div>
        <strong className={styles.badge}>Desenvolvimento isolado</strong>
      </div>
      <div className={styles.notice}>
        <strong>Confirmação bloqueada neste ambiente.</strong>
        <p>
          Formato srrotas.radar-admin-import.draft.v0.2, sem compatibilidade
          antiga. Registro de fontes e coordenação entre todos os escritores
          exigem homologação. Importar nunca publica.
        </p>
      </div>
      <div className={styles.upload}>
        <h3>Validar arquivo e proveniência</h3>
        <label className={styles.fileLabel} htmlFor="radar-json-file">
          Escolher arquivo JSON
        </label>
        <input
          id="radar-json-file"
          type="file"
          accept=".json,application/json"
          onChange={(e) => void inspect(e.target.files?.[0])}
        />
        <small>
          UTF-8 · até 2 MB · até 200 itens · prévia administrativa autenticada
        </small>
        <p className={styles.fileName}>{fileName}</p>
        <div role="status" aria-live="polite">
          {busy ? "Validando registros…" : message}
        </div>
      </div>
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
          selecionados. Eventos pendentes e locais inativos. Commit restrito ao
          protótipo PostgreSQL descartável, sem endpoint publicado.
        </p>
        <button disabled>Confirmar importação indisponível</button>
      </footer>
    </section>
  );
}
