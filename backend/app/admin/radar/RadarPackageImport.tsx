"use client";
import {useEffect,useMemo,useRef,useState} from "react";
import type {Preview,ItemResult} from "@/src/radar-admin-import/validation";
import styles from "./json-import.module.css";

type ResultRow={item_id:string;result:string;reason:string;target_id?:string|null};
type Receipt={batch_id:string;created:number;ignored:number;pending:number;items:ResultRow[];idempotent_replay?:boolean;published?:number};
const UUID=/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const names:Record<string,string>={
  ready:"Elegível",duplicate:"Duplicata comprovada",pending:"Pendente",invalid:"Inválido"
};
export default function RadarPackageImport(){
  const [preview,setPreview]=useState<Preview|null>(null);
  const [uploadId,setUploadId]=useState("");
  const [sourceName,setSourceName]=useState("");
  const [selected,setSelected]=useState<string[]>([]);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState("");
  const [dialog,setDialog]=useState(false);
  const [receipt,setReceipt]=useState<Receipt|null>(null);
  const [showOnlySelected,setShowOnlySelected]=useState(false);
  const [showExclusions,setShowExclusions]=useState(true);
  const fileKey=useRef("");
  const commitKey=useRef("");
  const requestVersion=useRef(0);
  useEffect(()=>{
    const q=new URLSearchParams(window.location.search).get("upload_id");
    if(q&&UUID.test(q))setUploadId(q);
  },[]);
  const items=preview?.items??[];
  const ready=useMemo(()=>items.filter(r=>r.kind==="poi"&&r.classification==="ready"),[items]);
  const groups=useMemo(()=>{
    const map=new Map<string,ItemResult[]>();
    for(const item of items){
      const region=item.region||"Sem região";
      map.set(region,[...(map.get(region)||[]),item]);
    }
    const ordered=preview?.package_manifest?.regions??[];
    return [...map.entries()].sort((a,b)=>{
      const pa=ordered.indexOf(a[0]),pb=ordered.indexOf(b[0]);
      return (pa<0?999:pa)-(pb<0?999:pb)||a[0].localeCompare(b[0],"pt-BR");
    });
  },[items,preview]);
  const chosen=useMemo(()=>new Set(selected),[selected]);
  const selectionRegions=useMemo(()=>groups.map(([region,rows])=>({
    region,count:rows.filter(r=>chosen.has(r.item_id)).length
  })).filter(r=>r.count>0),[groups,chosen]);
  function reset(){
    requestVersion.current++;
    setPreview(null);setSelected([]);setDialog(false);setReceipt(null);
    setMessage("");setSourceName("");
    fileKey.current=crypto.randomUUID();
    commitKey.current=crypto.randomUUID();
  }
  function selectItems(ids:string[],check=true){
    const allowed=new Set(ready.map(r=>r.item_id));
    setSelected(old=>{
      const merged=new Set(old);
      for(const id of ids)if(allowed.has(id)){
        if(check)merged.add(id);else merged.delete(id);
      }
      return [...merged];
    });
    commitKey.current=crypto.randomUUID();setReceipt(null);setDialog(false);
  }
  async function loadPackage(file:File|null){
    reset();
    if(!file)return;
    const version=requestVersion.current;
    setSourceName(file.name);
    if(file.size>2_000_000){setMessage("Pacote acima de 2 MB. Divida em regiões.");return;}
    setBusy(true);setMessage("Lendo, consolidando e verificando o pacote…");
    try{
      const response=await fetch("/api/v1/admin/radar/package",{
        method:"POST",
        headers:{"Content-Type":"application/octet-stream",
          "Idempotency-Key":fileKey.current,
          "X-File-Name":encodeURIComponent(file.name)},
        body:await file.arrayBuffer(),cache:"no-store"
      });
      const data=await response.json().catch(()=>({}));
      if(version!==requestVersion.current)return;
      if(!response.ok){
        setMessage("Pacote não aceito: "+(data.error?.code??response.status)+
          ". Nenhum local foi publicado.");
        return;
      }
      setUploadId(data.receipt.upload_id);
      setPreview(data.preview);
      setMessage("Pacote consolidado em Envios. Confira regiões, exclusões e duplicatas antes de selecionar.");
    }catch{
      if(version===requestVersion.current)
        setMessage("Falha de rede. Confira Envios antes de reenviar o mesmo arquivo.");
    }finally{if(version===requestVersion.current)setBusy(false);}
  }
  async function loadReceived(){
    if(!UUID.test(uploadId)){setMessage("Informe um upload_id UUID válido.");return;}
    resetPreviewOnly();
    const version=++requestVersion.current;
    setBusy(true);setMessage("Consultando arquivo armazenado em Envios…");
    try{
      const stored=await fetch("/api/v1/admin/radar/json-inbox/"+
        encodeURIComponent(uploadId)+"/file",{cache:"no-store"});
      if(!stored.ok)throw Error("HTTP "+stored.status);
      const body=await stored.arrayBuffer();
      const res=await fetch("/api/v1/admin/radar/manual-import/preview",{
        method:"POST",headers:{"Content-Type":"application/json"},
        body,cache:"no-store"});
      const data=await res.json().catch(()=>({}));
      if(!res.ok)throw Error(data.error?.code??"HTTP "+res.status);
      if(version!==requestVersion.current)return;
      setPreview(data);
      setSourceName("Envios · "+uploadId);
      setMessage("Lote existente recuperado. Não é necessário um novo upload.");
    }catch(e){
      if(version===requestVersion.current)
        setMessage("Não foi possível consultar o lote: "+
          (e instanceof Error?e.message:"falha de rede"));
    }finally{if(version===requestVersion.current)setBusy(false);}
  }
  function resetPreviewOnly(){
    setPreview(null);setSelected([]);setDialog(false);setReceipt(null);
    commitKey.current=crypto.randomUUID();
  }
  async function publish(){
    if(!preview?.commit_enabled||!selected.length||!UUID.test(uploadId)||busy)return;
    setDialog(false);setBusy(true);
    try{
      const res=await fetch("/api/v1/admin/radar/manual-import/commit",{
        method:"POST",cache:"no-store",
        headers:{"Content-Type":"application/json","Idempotency-Key":commitKey.current},
        body:JSON.stringify({
          upload_id:uploadId,selected_item_ids:[...selected].sort(),decisions:{}
        })
      });
      const body=await res.json().catch(()=>({}));
      if(!res.ok){
        setMessage("Publicação não confirmada: "+(body.error?.code??res.status)+
          ". Confira o recibo antes de repetir.");
        return;
      }
      setReceipt(body.receipt);
      setMessage("Operação concluída. Resultado por item abaixo; registros publicados já estão no catálogo.");
    }catch{
      setMessage("Resultado da rede desconhecido. Repita com a mesma seleção e chave; o servidor é idempotente.");
    }finally{setBusy(false);}
  }
  return (
    <section className={styles.packagePanel} aria-labelledby="radar-package-heading">
      <h3 id="radar-package-heading">Importação regional · JSON v2 ou ZIP</h3>
      <p>Leia o arquivo, revise as duplicatas, selecione por região e confirme a publicação em dois passos. O GeoJSON complementa os mesmos locais, sem contagem dupla.</p>
      <div className={styles.packageActions}>
        <label>
          <strong>Escolher JSON v2 ou ZIP regional</strong>
          <input aria-label="Escolher JSON v2 ou ZIP regional" type="file"
            accept=".json,.zip,application/json,application/zip"
            disabled={busy} onChange={e=>void loadPackage(e.currentTarget.files?.[0]??null)}/>
        </label>
        <label>
          <strong>Ou recuperar pelo upload_id em Envios</strong>
          <input aria-label="upload_id do pacote" value={uploadId}
            placeholder="UUID do recibo" disabled={busy}
            onChange={e=>setUploadId(e.target.value.trim())}/>
        </label>
        <button type="button" disabled={busy||!UUID.test(uploadId)}
          onClick={()=>void loadReceived()}>Carregar de Envios</button>
      </div>
      {sourceName?<p><strong>Origem:</strong> {sourceName}</p>:null}
      <p role="status" aria-live="polite">{busy?"Processando…":message}</p>
      {preview?(
        <>
          <div className={styles.packageCounts} aria-label="Contagem do pacote">
            {(["ready","duplicate","pending","invalid"] as const).map(k=>
              <div key={k}><strong>{preview.counts[k]??0}</strong><span>{names[k]}</span></div>)}
            {preview.package_manifest?.geojson_features?(
              <div><strong>{preview.package_manifest.geojson_matched}</strong><span>GeoJSON associado, sem duplicação</span></div>
            ):null}
          </div>
          <div className={styles.packageSelectionBar}>
            <button type="button" onClick={()=>selectItems(ready.map(r=>r.item_id))}
              disabled={busy||!ready.length}>Selecionar todos os válidos do pacote</button>
            <button type="button" onClick={()=>selectItems([...selected],false)}
              disabled={busy||!selected.length}>Limpar seleção</button>
            <label><input type="checkbox" checked={showOnlySelected}
              onChange={e=>setShowOnlySelected(e.target.checked)}/> Mostrar só selecionados</label>
            <strong>{selected.length} selecionados para publicação</strong>
          </div>
          {groups.map(([region,rows])=>{
            const eligible=rows.filter(r=>r.classification==="ready"&&r.kind==="poi");
            const visible=showOnlySelected?rows.filter(r=>chosen.has(r.item_id)):rows;
            return <section className={styles.packageRegion} key={region}
              aria-label={"Região "+region}>
              <div className={styles.packageRegionHeader}>
                <div><h4>{region}</h4><p>{rows.length} registros · {eligible.length} elegíveis · {rows.length-eligible.length} excluídos da seleção</p></div>
                <button type="button" disabled={!eligible.length||busy}
                  onClick={()=>selectItems(eligible.map(r=>r.item_id))}>
                  Selecionar válidos desta região
                </button>
              </div>
              {visible.map((row,index)=><article key={row.item_id+"-"+index}
                className={styles.packageRecord}>
                <label>
                  <input type="checkbox" disabled={busy||row.classification!=="ready"||row.kind!=="poi"}
                    checked={chosen.has(row.item_id)}
                    onChange={e=>selectItems([row.item_id],e.target.checked)}/>
                  <span><strong>{row.display_name??row.item_id}</strong>
                    <small>{row.display_type} · {row.display_location}</small></span>
                </label>
                <span className={styles.packageRecordStatus}>{names[row.classification]}</span>
                {row.reasons.length?(
                  <ul>{row.reasons.map((r,i)=>
                    <li key={i}>{r.message} ({r.code})</li>)}</ul>
                ):null}
                {row.candidates?.length?(
                  <div className={styles.packageComparisons}>
                    <div><strong>No pacote</strong><p>{row.display_name} · {row.display_location}</p></div>
                    {row.candidates.map(c=><div key={c.kind+":"+c.id}>
                      <strong>{c.match==="exact"?"Duplicata confirmada":"Possível duplicata"}</strong>
                      <p>{c.name} · {c.source} · {c.address??"Sem endereço"}</p><p>{c.reason}</p>
                    </div>)}
                  </div>
                ):null}
              </article>)}
            </section>;
          })}
          {preview.package_manifest?.excluded?.length?(
            <section className={styles.packageRegion}>
              <button type="button" onClick={()=>setShowExclusions(v=>!v)}>
                {showExclusions?"Ocultar":"Mostrar"} exclusões de arquivos ({preview.package_manifest.excluded.length})
              </button>
              {showExclusions?preview.package_manifest.excluded.map((e,i)=><p key={i}>
                {e.region} · {e.name||e.client_ref} · {e.reason} ({e.source_file})
              </p>):null}
            </section>
          ):null}
          <div className={styles.packageFooter}>
            <p>{selected.length} locais selecionados em {selectionRegions.length} região(ões).
              {preview.commit_enabled?" Prontos para revisão final e publicação.":
                " Publicação protegida: migração e confirmação ainda não ativadas."}</p>
            <button type="button" disabled={busy||!preview.commit_enabled||!selected.length}
              onClick={()=>setDialog(true)}>1. Revisar seleção para publicar</button>
          </div>
        </>
      ):null}
      {dialog?(
        <div className={styles.packageModalBackdrop} role="presentation">
          <section role="dialog" aria-modal="true" aria-labelledby="radar-publication-title"
            className={styles.packageModal}>
            <h3 id="radar-publication-title">2. Confirmar publicação no Radar</h3>
            <p><strong>{selected.length} locais</strong> serão publicados imediatamente no catálogo,
              sem aprovação posterior. Nenhum item duvidoso ou incompleto está selecionado.</p>
            <ul>{selectionRegions.map(r=><li key={r.region}>{r.region}: {r.count}</li>)}</ul>
            <p>O servidor verificará novamente as duplicatas e a validade de cada item.
              Registros repetidos ou que ficaram incompletos serão ignorados ou pendentes,
              sem bloquear os outros.</p>
            <div className={styles.actions}>
              <button type="button" onClick={()=>setDialog(false)}>Voltar à seleção</button>
              <button type="button" onClick={()=>void publish()} disabled={busy}>
                Confirmar e publicar {selected.length} locais
              </button>
            </div>
          </section>
        </div>
      ):null}
      {receipt?(
        <section className={styles.receipt} aria-label="Resultado da publicação">
          <h4>Recibo de publicação: {receipt.batch_id}</h4>
          <p>{receipt.published??receipt.created} publicados · {receipt.ignored} ignorados ·
            {receipt.pending} pendentes · repetição idempotente: {receipt.idempotent_replay?"sim":"não"}</p>
          <a href={"/admin/radar?tab=runs&batch_id="+encodeURIComponent(receipt.batch_id)}>
            Consultar em Envios
          </a>
          {receipt.items.map(item=><p key={item.item_id}>
            {item.item_id} · {item.result} · {item.reason} {item.target_id?"· "+item.target_id:""}
          </p>)}
          <button type="button" disabled={busy} onClick={()=>void publish()}>
            Repetir mesma confirmação (verificar idempotência)
          </button>
        </section>
      ):null}
    </section>
  );
}
