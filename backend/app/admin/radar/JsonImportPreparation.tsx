"use client";
import { useEffect, useRef, useState } from "react";
import type { Preview } from "@/src/radar-admin-import/validation";
import { inspectRadarJsonFile } from "@/src/radar-admin-file-inspection";
import type { UploadReceipt } from "@/src/radar-json-inbox/types";
import JsonUploadReceipt from "./JsonUploadReceipt";
import RadarPackageImport from "./RadarPackageImport";
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
  type Decision={choice:"same"|"distinct";reason:string};
  const [decisions,setDecisions]=useState<Record<string,Record<string,Decision>>>({});
  const [importReceipt,setImportReceipt]=useState<{
    batch_id:string; created:number; ignored:number; pending:number;
    items:Array<{item_id:string;result:string;target_id?:string|null;reason:string}>;
    idempotent_replay?:boolean;
  }|null>(null);
  const commitKey=useRef("");
  function changed(){commitKey.current=crypto.randomUUID();setImportReceipt(null);}
  function compareResolved(row:NonNullable<Preview["items"]>[number]){
    const matches=row.candidates?.filter(c=>c.match==="possible")??[];
    return matches.length>0 && matches.every(c=>{
      if(c.kind==="file")return false;
      const d=decisions[row.item_id]?.[c.id];
      return d && (d.choice==="same" || (d.choice==="distinct" && d.reason.trim().length>=10));
    });
  }
  function maySelect(row:NonNullable<Preview["items"]>[number]){
    if(row.classification==="ready")return true;
    if(row.classification!=="pending"||!compareResolved(row))return false;
    return row.reasons.every(r=>[
      "POSSIBLE_CROSS_SOURCE_DUPLICATE","REVIEW_BEFORE_PUBLICATION",
    ].includes(r.code));
  }
  function updateDecision(itemId:string,id:string,decision:Decision){
    changed();
    setDecisions(old=>({...old,[itemId]:{...(old[itemId]??{}),[id]:decision}}));
    setSelected(ids=>ids.filter(x=>x!==itemId));
  }
  const [message, setMessage] = useState("");
  const [needsAdminLogin, setNeedsAdminLogin] = useState(false);
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  const file = useRef<File | null>(null),
    key = useRef(""),
    sending = useRef(false);
  const [syntaxValid, setSyntaxValid] = useState(false);
  const [receipt, setReceipt] = useState<UploadReceipt | null>(null);
  const [storedUploadId, setStoredUploadId] = useState("");
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get("upload_id");
    if (id) setStoredUploadId(id);
  }, []);
  async function inspect(selectedFile?: File) {
    const current = ++generation.current;
    file.current = selectedFile ?? null;
    key.current = crypto.randomUUID();
    setPreview(null);
    setSelected([]);
    setDecisions({}); changed();
    setReceipt(null);
    setSyntaxValid(false);
    setMessage("");
    setNeedsAdminLogin(false);
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
        const code = typeof result.error === "string"
          ? result.error
          : result.error?.code;
        if (
          action === "upload" &&
          (code === "admin_session_required" || code === "unauthorized" ||
            code === "session_expired")
        ) {
          setNeedsAdminLogin(true);
          setMessage(
            "Sua conta abriu o painel com sessão de motorista ou a sessão administrativa expirou. Entre novamente com seu acesso Admin e selecione o JSON para concluir o envio.",
          );
          return;
        }
        setNeedsAdminLogin(false);
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
        setDecisions({});changed();
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
  async function previewStoredUpload() {
    if (sending.current || busy) return;
    const id = storedUploadId.trim();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)) {
      setMessage("Informe o upload_id UUID exibido no recibo ou em Envios.");
      return;
    }
    sending.current = true;
    const current = ++generation.current;
    setBusy(true);
    setPreview(null);
    setSelected([]);
    setDecisions({}); changed();
    setMessage("Consultando o JSON recebido em área privada…");
    try {
      const stored = await fetch(
        `/api/v1/admin/radar/json-inbox/${encodeURIComponent(id)}/file`,
        { cache: "no-store" },
      );
      if (!stored.ok) {
        setMessage(
          stored.status === 401 || stored.status === 403
            ? "Sessão administrativa ausente ou expirada. Entre novamente no Admin."
            : `Não foi possível ler o arquivo recebido (HTTP ${stored.status}).`,
        );
        return;
      }
      const bytes = new Uint8Array(await stored.arrayBuffer());
      if (current !== generation.current) return;
      const inspection = inspectRadarJsonFile(bytes);
      if (inspection.status !== "syntax_valid") {
        setMessage(inspection.message);
        return;
      }
      // A private download is audit-logged. Preview itself has no Radar writes.
      const response = await fetch("/api/v1/admin/radar/manual-import/preview", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: bytes,
        cache: "no-store",
      });
      const data = await response.json().catch(() => ({}));
      if (current !== generation.current) return;
      if (!response.ok) {
        setMessage(`Prévia não concluída: ${data.error?.code ?? response.status}. Nenhum registro foi importado.`);
        return;
      }
      setFileName(`Arquivo recebido — ${id}`);
      file.current = null;
      setSyntaxValid(false);
      setPreview(data);
      setMessage("Prévia informativa do arquivo recebido, sem conversão automática nem gravação de eventos e locais.");
    } catch {
      if (current === generation.current)
        setMessage("Falha de rede ao consultar a prévia do arquivo recebido.");
    } finally {
      sending.current = false;
      if (current === generation.current) setBusy(false);
    }
  }
  async function confirmImport() {
    if(busy || !preview || !preview.commit_enabled || !selected.length)return;
    const uploadId=receipt?.upload_id || storedUploadId.trim();
    if(!uploadId){setMessage("Envie o arquivo para triagem ou informe um upload_id antes da confirmação.");return;}
    if(selected.some(id=>!preview.items.some(row=>row.item_id===id && maySelect(row)))){
      setMessage("Há registros selecionados que precisam de análise antes de confirmar.");return;
    }
    setBusy(true);
    try{
      const response=await fetch("/api/v1/admin/radar/manual-import/commit",{
        method:"POST",headers:{"Content-Type":"application/json","Idempotency-Key":commitKey.current},
        body:JSON.stringify({upload_id:uploadId,selected_item_ids:[...selected],decisions}),
        cache:"no-store",
      });
      const data=await response.json().catch(()=>({}));
      if(!response.ok){setMessage(`Confirmação bloqueada: ${data.error?.code??response.status}. Nenhuma aprovação automática.`);return;}
      setImportReceipt(data.receipt);
      setMessage("Lote processado com recibo persistente; locais criados continuam inativos para revisão humana.");
    }catch{
      setMessage("Não foi possível confirmar o resultado da operação. Consulte Envios e repita com a mesma chave.");
    }finally{setBusy(false);}
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
      <RadarPackageImport />
      <div className={styles.notice}>
        <strong>Área legada: arquivos JSON individuais e recibos anteriores.</strong>
        <p>
          Use a seção acima para importar ZIP regional ou JSON v2.
          Esta área mantém a validação e os recibos antigos. A publicação de
          novos locais depende da confirmação em duas etapas acima.
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
            Validar prévia (v2 / contrato 0.2)
          </button>
        </div>
        <div role="status" aria-live="polite">
          {busy ? "Processando arquivo…" : message}
        </div>
        {needsAdminLogin ? (
          <a href="/app/entrar?next=%2Fadmin%2Fradar%3Ftab%3Dimport">
            Entrar novamente no Admin para enviar o JSON
          </a>
        ) : null}
      </div>
      <section className={styles.upload} aria-label="Conferir JSON já recebido">
        <h3>Validar um arquivo que já está em Envios</h3>
        <label htmlFor="radar-received-upload-id">upload_id do recibo</label>
        <input
          id="radar-received-upload-id"
          type="text"
          value={storedUploadId}
          onChange={(event) => setStoredUploadId(event.target.value)}
          placeholder="UUID do arquivo recebido"
        />
        <button type="button" disabled={busy} onClick={() => void previewStoredUpload()}>
          Validar arquivo já recebido
        </button>
      </section>
      {receipt ? <JsonUploadReceipt receipt={receipt} /> : null}
      {preview?.input_schema ? (
        <p role="status">
          Formato reconhecido: {preview.input_schema}.
          Lote de origem: {preview.original_batch_id}.
          Esta prévia não habilita importação nem confirma direitos ou pinos.
        </p>
      ) : null}
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
                disabled={!maySelect(row) || busy}
                checked={selected.includes(row.item_id)}
                onChange={(e) => {
                  changed();
                  setSelected((ids) =>
                    e.target.checked
                      ? [...ids, row.item_id]
                      : ids.filter((id) => id !== row.item_id),
                  );
                }}
              />
              <strong>
                {row.display_name ?? (
                  row.item?.kind === "event"
                    ? row.item.data.name
                    : row.item?.kind === "poi"
                      ? row.item.data.canonical_name
                      : row.item_id
                )}
              </strong>
            </label>
            <p>
              {row.item_id} · {row.kind} · {itemLabels[row.classification]}
              {row.classification === "ready"
                ? " para importar; ainda exige aprovação humana"
                : ""}
            </p>
            {row.candidates?.length ? (
              <div aria-label={`Comparações de ${row.display_name ?? row.item_id}`}
                   style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(210px,1fr))",gap:12}}>
                <div style={{padding:10,border:"1px solid #888",borderRadius:8}}>
                  <strong>Arquivo recebido</strong>
                  <p>{row.display_name} · {row.display_location}</p>
                </div>
                {row.candidates.map(c=>{
                  const decision=decisions[row.item_id]?.[c.id];
                  return <div key={`${row.item_id}-${c.source}-${c.id}`}
                    style={{padding:10,border:"1px solid #888",borderRadius:8}}>
                    <strong>{c.match==="exact"?"Duplicata confirmada":"Possível duplicata"}</strong>
                    <p>{c.name} · {c.source}</p>
                    <p>{c.address} · {c.reason}</p>
                    {c.match==="possible"&&c.kind!=="file" ? (
                      <>
                        <label>Decisão humana
                          <select aria-label={`Decisão sobre ${c.name}`}
                            value={decision?.choice??""}
                            onChange={e=>updateDecision(row.item_id,c.id,
                              {choice:e.target.value as Decision["choice"],reason:decision?.reason??""})}>
                            <option value="">Pendente de análise</option>
                            <option value="same">É a mesma entidade · ignorar</option>
                            <option value="distinct">São entidades distintas</option>
                          </select>
                        </label>
                        {decision?.choice==="distinct" ? (
                          <label>Justificativa obrigatória (10 caracteres)
                            <input value={decision.reason}
                              onChange={e=>updateDecision(row.item_id,c.id,
                                {choice:"distinct",reason:e.target.value})}/>
                          </label>
                        ) : null}
                      </>
                    ) : null}
                  </div>;
                })}
              </div>
            ) : null}
            {row.display_location ? (
              <p>{row.display_type} · {row.display_location}</p>
            ) : null}
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
        <button
          type="button"
          disabled={busy || !preview?.commit_enabled || selected.length===0 || importReceipt!==null}
          onClick={()=>void confirmImport()}
        >
          Confirmar importação para revisão humana
        </button>
        {!preview?.commit_enabled ? (
          <p>Confirmação protegida até a coordenação global de duplicidades ser validada no banco.</p>
        ) : null}
        {importReceipt ? (
          <div role="status">
            <strong>Recibo {importReceipt.batch_id}</strong>
            <p>{importReceipt.created} criados inativos · {importReceipt.ignored} ignorados ·
              {importReceipt.pending} pendentes · replay: {importReceipt.idempotent_replay?"sim":"não"}</p>
            <a href={`/admin/radar?tab=runs&batch_id=${encodeURIComponent(importReceipt.batch_id)}`}>
              Consultar lote em Envios
            </a>
            {importReceipt.items.map(item=><p key={item.item_id}>
              {item.item_id}: {item.result} · {item.reason}
            </p>)}
          </div>
        ) : null}
      </footer>
    </section>
  );
}
