"use client";

import { useRef, useState } from "react";
import { inspectRadarJsonFile, RADAR_LOCAL_FILE_LIMIT, type JsonInspection } from "@/src/radar-admin-file-inspection";
import styles from "./json-import.module.css";

/** No envelope assumptions, network upload, persistent payload, preview rows or commit without the contract. */
export default function JsonImportPreparation() {
  const [fileName, setFileName] = useState("");
  const [result, setResult] = useState<JsonInspection | null>(null);
  const [busy, setBusy] = useState(false);
  const [readError, setReadError] = useState(false);
  const generation = useRef(0);
  async function inspect(file?: File) {
    const token = ++generation.current;
    setResult(null); setReadError(false); setFileName(file?.name ?? "");
    if (!file) { setBusy(false); return; }
    if (file.size > RADAR_LOCAL_FILE_LIMIT) {
      setResult({ status: "too_large", message: "O arquivo ultrapassa o limite local de 2 MB." }); setBusy(false); return;
    }
    setBusy(true);
    try {
      const checked = inspectRadarJsonFile(new Uint8Array(await file.arrayBuffer()));
      if (generation.current === token) setResult(checked);
    } catch { if (generation.current === token) setReadError(true); }
    finally { if (generation.current === token) setBusy(false); }
  }
  return <section className={styles.panel} aria-labelledby="json-import-title">
    <div className={styles.heading}><div><span>CURADORIA MANUAL</span><h2 id="json-import-title">Importar JSON</h2><p>Prepare um arquivo de eventos ou locais para revisão administrativa.</p></div><strong className={styles.badge}>Aguardando contrato do formato</strong></div>
    <div className={styles.notice}><strong>A importação ainda não está disponível.</strong><p>O contrato Radar v1.3 completo é necessário para validar <code>radar.admin-import.v2</code> e a compatibilidade v1. Sem ele, nenhum registro será classificado como pronto ou enviado.</p></div>
    <div className={styles.columns}>
      <div className={styles.upload}><span className={styles.fileIcon} aria-hidden="true">{`{ }`}</span><h3>Verificar um arquivo</h3><p>Apenas sintaxe JSON e tamanho. O conteúdo permanece neste navegador e não é enviado nem salvo.</p>
        <label className={styles.fileLabel} htmlFor="radar-json-file">Escolher arquivo JSON</label><input id="radar-json-file" type="file" accept=".json,application/json" onChange={event => void inspect(event.target.files?.[0])} />
        <small>UTF-8 · limite local de 2 MB · nenhuma publicação</small>
        {fileName ? <div className={styles.fileName}>{fileName}</div> : null}
        <div role="status" aria-live="polite" className={result?.status === "syntax_valid" ? styles.valid : styles.result}>{busy ? "Verificando sintaxe…" : readError ? "Não foi possível ler o arquivo. Selecione-o novamente." : result?.message}</div>
      </div>
      <aside className={styles.next}><h3>Próximos passos</h3><ol><li><strong>Prévia por registro</strong><span>Classificação e motivos após validação do formato.</span></li><li><strong>Seleção e confirmação</strong><span>Somente os registros prontos selecionados.</span></li><li><strong>Recibo em Envios</strong><span>Consulta posterior pelo batch_id.</span></li><li><strong>Revisão humana</strong><span>Eventos pendentes e locais inativos até aprovação.</span></li></ol><p>Essas etapas dependem do contrato e ainda estão bloqueadas.</p></aside>
    </div>
    <div className={styles.counts} aria-label="Registros ainda não avaliados">{["Prontos", "Duplicados", "Pendentes", "Inválidos"].map(label => <div key={label}><span>{label}</span><strong>—</strong><small>Não avaliado</small></div>)}</div>
    <footer className={styles.footer}><p>Sem prévia por item, seleção, gravação ou recibo nesta etapa. Uma URL de fonte não comprova direitos de reutilização.</p><button disabled>Confirmar importação indisponível</button></footer>
  </section>;
}
