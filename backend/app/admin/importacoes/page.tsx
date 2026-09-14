"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import styles from "./page.module.css";

type Viewer = { email: string; is_owner: boolean; allowed: boolean; is_driver: boolean };
type AccessRow = { id: string; email: string; enabled: boolean; created_at: string; updated_at: string };
type QualitySummary = { quality_flags?: Record<string, number>; platforms?: Record<string, number>; months?: Record<string, number> };
type Batch = {
  id: string; created_by_email: string; source_name: string; original_filename: string; file_size_bytes: number;
  format: string; status: string; received_count: number; valid_count: number; partial_count: number; invalid_count: number;
  duplicate_count: number; created_at: string; finalized_at?: string | null; schema_version?: string | null; extractor_version?: string | null;
  supersedes_batch_id?: string | null; processing_manifest?: Record<string, unknown> | null; quality_summary?: QualitySummary | null;
  demand_temporal_ready_count?: number; route_flow_ready_count?: number; financial_ready_count?: number; fully_ready_count?: number;
};
type QualityCounts = { demand_temporal_ready: number; route_flow_ready: number; financial_ready: number; fully_ready: number };
type StatusCounts = { valid: number; partial: number; invalid: number; duplicate: number };
type ChunkResult = { received: number; counts: StatusCounts; quality_counts?: QualityCounts };
type JsonObject = Record<string, unknown>;
type DetectedContract = { schema_version: string; extractor_version: string | null };

const CHUNK_SIZE = 150;
const EMPTY_QUALITY: QualityCounts = { demand_temporal_ready: 0, route_flow_ready: 0, financial_ready: 0, fully_ready: 0 };

async function api(path: string, init?: RequestInit) {
  const response = await fetch(path, { cache: "no-store", ...init, headers: { "Content-Type": "application/json", ...(init?.headers || {}) } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error(data?.message || data?.error || `HTTP ${response.status}`), { status: response.status, data });
  return data;
}

async function sha256File(file: File) {
  if (!globalThis.crypto?.subtle || file.size > 64 * 1024 * 1024) return null;
  const bytes = await file.arrayBuffer();
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map((value) => value.toString(16).padStart(2, "0")).join("");
}

function asObject(value: unknown): JsonObject | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as JsonObject : null;
}

async function detectContract(file: File): Promise<DetectedContract> {
  const lower = file.name.toLowerCase();
  try {
    let record: JsonObject | null = null;
    if (lower.endsWith(".jsonl") || lower.endsWith(".ndjson")) {
      const head = await file.slice(0, Math.min(file.size, 256 * 1024)).text();
      const firstLine = head.split(/\r?\n/).map((line) => line.trim()).find(Boolean);
      record = firstLine ? asObject(JSON.parse(firstLine)) : null;
    } else {
      const parsed = JSON.parse(await file.text()) as unknown;
      const parsedObject = asObject(parsed);
      const records = parsedObject?.records;
      const first = Array.isArray(parsed) ? parsed[0] : Array.isArray(records) ? records[0] : parsed;
      record = asObject(first);
    }
    return {
      schema_version: String(record?.schema_version || "legacy").trim() || "legacy",
      extractor_version: record?.extractor_version ? String(record.extractor_version).trim() : null,
    };
  } catch {
    return { schema_version: "legacy", extractor_version: null };
  }
}

async function readManifest(file: File | null): Promise<JsonObject> {
  if (!file) return {};
  const parsed = JSON.parse(await file.text()) as unknown;
  const object = asObject(parsed);
  if (!object) throw new Error("O manifesto precisa ser um objeto JSON.");
  return object;
}

async function uploadChunk(batchId: string, rows: Array<{ row_index: number; payload: JsonObject }>) {
  return api(`/api/v1/admin/imports/batches/${batchId}/rows`, { method: "POST", body: JSON.stringify({ rows }) }) as Promise<ChunkResult>;
}

function accumulateQuality(target: QualityCounts, incoming?: QualityCounts) {
  if (!incoming) return;
  target.demand_temporal_ready += incoming.demand_temporal_ready || 0;
  target.route_flow_ready += incoming.route_flow_ready || 0;
  target.financial_ready += incoming.financial_ready || 0;
  target.fully_ready += incoming.fully_ready || 0;
}

async function uploadJsonl(file: File, batchId: string, onProgress: (rows: number, counts: StatusCounts, quality: QualityCounts) => void) {
  const reader = file.stream().getReader();
  const decoder = new TextDecoder();
  let buffer = ""; let rowIndex = 0; let chunk: Array<{ row_index: number; payload: JsonObject }> = [];
  const totals: StatusCounts = { valid: 0, partial: 0, invalid: 0, duplicate: 0 };
  const quality = { ...EMPTY_QUALITY };
  async function flush() {
    if (!chunk.length) return;
    const result = await uploadChunk(batchId, chunk);
    totals.valid += result.counts.valid; totals.partial += result.counts.partial; totals.invalid += result.counts.invalid; totals.duplicate += result.counts.duplicate;
    accumulateQuality(quality, result.quality_counts);
    rowIndex += chunk.length; chunk = []; onProgress(rowIndex, { ...totals }, { ...quality });
  }
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    const lines = buffer.split(/\r?\n/); buffer = lines.pop() ?? "";
    for (const line of lines) {
      const trimmed = line.trim(); if (!trimmed) continue;
      let parsed: JsonObject; try { parsed = JSON.parse(trimmed) as JsonObject; } catch { parsed = { __parse_error: true, raw_line: trimmed.slice(0, 5000) }; }
      chunk.push({ row_index: rowIndex + chunk.length, payload: parsed }); if (chunk.length >= CHUNK_SIZE) await flush();
    }
    if (done) break;
  }
  if (buffer.trim()) {
    let parsed: JsonObject; try { parsed = JSON.parse(buffer.trim()) as JsonObject; } catch { parsed = { __parse_error: true, raw_line: buffer.trim().slice(0, 5000) }; }
    chunk.push({ row_index: rowIndex + chunk.length, payload: parsed });
  }
  await flush(); return { rows: rowIndex, counts: totals, quality };
}

async function uploadJson(file: File, batchId: string, onProgress: (rows: number, counts: StatusCounts, quality: QualityCounts) => void) {
  const parsed = JSON.parse(await file.text()) as unknown;
  const records = Array.isArray(parsed) ? parsed : parsed && typeof parsed === "object" && Array.isArray((parsed as { records?: unknown[] }).records) ? (parsed as { records: unknown[] }).records : [parsed];
  const totals: StatusCounts = { valid: 0, partial: 0, invalid: 0, duplicate: 0 }; const quality = { ...EMPTY_QUALITY }; let sent = 0;
  for (let start = 0; start < records.length; start += CHUNK_SIZE) {
    const slice = records.slice(start, start + CHUNK_SIZE).map((payload, offset) => ({ row_index: start + offset, payload: asObject(payload) || { value: payload } }));
    const result = await uploadChunk(batchId, slice);
    totals.valid += result.counts.valid; totals.partial += result.counts.partial; totals.invalid += result.counts.invalid; totals.duplicate += result.counts.duplicate;
    accumulateQuality(quality, result.quality_counts); sent += slice.length; onProgress(sent, { ...totals }, { ...quality });
  }
  return { rows: sent, counts: totals, quality };
}

function formatBytes(bytes: number) {
  if (!bytes) return "0 B"; const units = ["B", "KB", "MB", "GB"]; const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return `${(bytes / 1024 ** index).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} ${units[index]}`;
}

function percent(value: number | undefined, total: number) {
  if (!total) return "0%"; return `${Math.round(((value || 0) / total) * 100)}%`;
}

function topIssues(batch: Batch) {
  const issues = batch.quality_summary?.quality_flags || {};
  const entries = Object.entries(issues).sort((a, b) => Number(b[1]) - Number(a[1])).slice(0, 3);
  return entries.length ? entries.map(([key, value]) => `${key}: ${Number(value).toLocaleString("pt-BR")}`).join(" · ") : "Sem flags críticas";
}

export default function AdminImportacoesPage() {
  const [viewer, setViewer] = useState<Viewer | null>(null);
  const [authState, setAuthState] = useState<"loading" | "denied" | "ready" | "error">("loading");
  const [authError, setAuthError] = useState(""); const [access, setAccess] = useState<AccessRow[]>([]); const [newEmail, setNewEmail] = useState("");
  const [batches, setBatches] = useState<Batch[]>([]); const [file, setFile] = useState<File | null>(null); const [manifestFile, setManifestFile] = useState<File | null>(null);
  const [sourceName, setSourceName] = useState("historical_screenshot_v7_1"); const [supersedesBatchId, setSupersedesBatchId] = useState("");
  const [detected, setDetected] = useState<DetectedContract | null>(null); const [busy, setBusy] = useState(false); const [message, setMessage] = useState("");
  const [progress, setProgress] = useState({ rows: 0, valid: 0, partial: 0, invalid: 0, duplicate: 0, ...EMPTY_QUALITY });

  const loadBatches = useCallback(async () => { const data = await api("/api/v1/admin/imports/batches"); setBatches(data.batches || []); }, []);
  const loadAccess = useCallback(async () => { const data = await api("/api/v1/admin/imports/access"); setAccess(data.access || []); }, []);
  const checkSession = useCallback(async () => {
    try {
      const response = await fetch("/api/v1/admin/imports/me", { cache: "no-store" }); const data = await response.json().catch(() => ({}));
      if (response.status === 401) { window.location.replace("/app/entrar?next=%2Fadmin%2Fimportacoes"); return; }
      if (response.status === 403) { setViewer({ email: data.email || "", is_owner: Boolean(data.is_owner), allowed: false, is_driver: Boolean(data.is_driver) }); setAuthState("denied"); return; }
      if (!response.ok) throw new Error(data?.error || "Falha ao validar acesso.");
      const nextViewer = data as Viewer; setViewer(nextViewer); setAuthState("ready"); await loadBatches(); if (nextViewer.is_owner) await loadAccess();
    } catch (error) { setAuthError(error instanceof Error ? error.message : "Falha ao validar acesso."); setAuthState("error"); }
  }, [loadAccess, loadBatches]);
  useEffect(() => { void checkSession(); }, [checkSession]);

  async function onOfferFileChanged(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0] || null; setFile(chosen); setDetected(null);
    if (chosen) { const contract = await detectContract(chosen); setDetected(contract); if (contract.schema_version === "srrotas-historical-offer-v1") setSourceName("historical_screenshot_v7_1"); }
  }
  async function logout() { if (viewer?.is_driver) { window.location.href = "/app"; return; } await fetch("/api/v1/admin/imports/logout", { method: "POST" }).catch(() => undefined); setViewer(null); setAccess([]); setBatches([]); window.location.replace("/app/entrar"); }
  async function addAccess(event: FormEvent) { event.preventDefault(); if (!newEmail.trim()) return; setBusy(true); setMessage(""); try { await api("/api/v1/admin/imports/access", { method: "POST", body: JSON.stringify({ email: newEmail }) }); setNewEmail(""); await loadAccess(); setMessage("E-mail autorizado."); } catch (error) { setMessage(error instanceof Error ? error.message : "Falha ao autorizar."); } finally { setBusy(false); } }
  async function disableAccess(targetEmail: string) { if (!confirm(`Remover acesso de ${targetEmail}?`)) return; setBusy(true); try { await api("/api/v1/admin/imports/access", { method: "DELETE", body: JSON.stringify({ email: targetEmail }) }); await loadAccess(); } finally { setBusy(false); } }
  async function archiveBatch(batch: Batch) { if (!confirm(`Arquivar ${batch.original_filename}? Os dados permanecem preservados e podem ser restaurados.`)) return; setBusy(true); try { await api(`/api/v1/admin/imports/batches/${batch.id}`, { method: "PATCH", body: JSON.stringify({ action: "archive" }) }); await loadBatches(); setMessage("Lote arquivado sem excluir dados."); } finally { setBusy(false); } }
  async function restoreBatch(batch: Batch) { setBusy(true); try { await api(`/api/v1/admin/imports/batches/${batch.id}`, { method: "PATCH", body: JSON.stringify({ action: "restore" }) }); await loadBatches(); setMessage("Lote restaurado para staging."); } finally { setBusy(false); } }
  async function deleteBatch(batch: Batch) { const label = `${batch.original_filename} (${batch.received_count.toLocaleString("pt-BR")} registros)`; if (!confirm(`Excluir o lote incompleto ${label}? Esta ação remove somente o staging e não pode ser desfeita.`)) return; setBusy(true); try { await api(`/api/v1/admin/imports/batches/${batch.id}`, { method: "DELETE", body: "{}" }); await loadBatches(); setMessage("Lote incompleto excluído."); } finally { setBusy(false); } }

  const canUpload = useMemo(() => Boolean(file && !busy), [file, busy]);
  async function startImport() {
    if (!file) return; const lower = file.name.toLowerCase(); const format = lower.endsWith(".jsonl") || lower.endsWith(".ndjson") ? "jsonl" : lower.endsWith(".json") ? "json" : "";
    if (!format) { setMessage("Use um arquivo .jsonl, .ndjson ou .json."); return; }
    setBusy(true); setMessage("Preparando lote..."); setProgress({ rows: 0, valid: 0, partial: 0, invalid: 0, duplicate: 0, ...EMPTY_QUALITY });
    try {
      const contract = detected || await detectContract(file); const manifest = await readManifest(manifestFile); const fileHash = await sha256File(file);
      const created = await api("/api/v1/admin/imports/batches", { method: "POST", body: JSON.stringify({ original_filename: file.name, source_name: sourceName, file_size_bytes: file.size, file_sha256: fileHash, format, schema_version: contract.schema_version, extractor_version: contract.extractor_version, supersedes_batch_id: supersedesBatchId || null, processing_manifest: manifest }) });
      const batchId = String(created.batch.id);
      const onProgress = (rows: number, counts: StatusCounts, quality: QualityCounts) => { setProgress({ rows, ...counts, ...quality }); setMessage(`Processando ${rows.toLocaleString("pt-BR")} ofertas...`); };
      if (format === "jsonl") await uploadJsonl(file, batchId, onProgress); else await uploadJson(file, batchId, onProgress);
      const finalized = await api(`/api/v1/admin/imports/batches/${batchId}/finalize`, { method: "POST", body: "{}" }); const batch = finalized.batch;
      setProgress({ rows: batch.received_count, valid: batch.valid_count, partial: batch.partial_count, invalid: batch.invalid_count, duplicate: batch.duplicate_count, demand_temporal_ready: batch.demand_temporal_ready_count || 0, route_flow_ready: batch.route_flow_ready_count || 0, financial_ready: batch.financial_ready_count || 0, fully_ready: batch.fully_ready_count || 0 });
      setMessage(`Lote concluído: ${batch.received_count.toLocaleString("pt-BR")} ofertas em staging. O lote anterior não foi apagado.`); setFile(null); setManifestFile(null); setDetected(null); setSupersedesBatchId(""); await loadBatches();
    } catch (error) { setMessage(error instanceof Error ? `Falha: ${error.message}` : "Falha na importação."); } finally { setBusy(false); }
  }

  if (authState === "loading") return <main className={styles.page}><div className={styles.centerCard}><img src="/logo-srrotas.png" alt=""/><strong>Validando acesso...</strong></div></main>;
  if (authState === "denied") return <main className={styles.page}><section className={styles.loginCard}><img src="/logo-srrotas.png" alt="Sr. Rotas"/><span>ACESSO RESTRITO</span><h1>Este e-mail não está autorizado.</h1><p>{viewer?.email || "Sua conta"} possui login válido, mas não tem permissão para importar históricos.</p><button onClick={logout}>{viewer?.is_driver ? "Voltar ao painel" : "Sair"}</button></section></main>;
  if (authState === "error") return <main className={styles.page}><section className={styles.loginCard}><img src="/logo-srrotas.png" alt="Sr. Rotas"/><span>ERRO DE ACESSO</span><h1>Não foi possível validar sua sessão.</h1><p>{authError || "Tente entrar novamente."}</p><button onClick={() => window.location.replace("/app/entrar?next=%2Fadmin%2Fimportacoes")}>Entrar novamente</button></section></main>;

  return <main className={styles.page}>
    <header className={styles.header}><div><img src="/logo-srrotas.png" alt="Sr. Rotas"/><div><span>BIGCORPS · FERRAMENTA INTERNA</span><strong>Importação histórica</strong></div></div><div className={styles.user}><small>{viewer?.email}</small>{viewer?.is_owner ? <b>Administrador</b> : <b>Importador</b>}<button onClick={logout}>{viewer?.is_driver ? "Voltar ao painel" : "Sair"}</button></div></header>
    <div className={styles.shell}>
      <section className={styles.hero}><span>V7.1 · CONTRATO DE DADOS</span><h1>Importe, compare e só depois substitua a base anterior.</h1><p>JSONL/JSON entram em staging. O backend recalcula a qualidade e <strong>não apaga o V7 antigo</strong>. Uma linha representa uma oferta; uma imagem pode gerar várias ofertas.</p></section>
      <section className={styles.grid}>
        <article className={styles.card}>
          <div className={styles.cardHead}><div><span>NOVO LOTE</span><h2>Enviar ofertas estruturadas</h2></div><b>Staging seguro</b></div>
          <label className={styles.field}>Fonte<input value={sourceName} onChange={(event: ChangeEvent<HTMLInputElement>) => setSourceName(event.target.value)} /></label>
          <label className={styles.drop}><input type="file" accept=".jsonl,.ndjson,.json,application/json" onChange={onOfferFileChanged}/><strong>{file ? file.name : "Selecionar srrotas_offers_v7_1.jsonl"}</strong><small>{file ? `${formatBytes(file.size)} · ${detected?.schema_version || "detectando contrato..."}${detected?.extractor_version ? ` · ${detected.extractor_version}` : ""}` : "Preferência: JSONL, uma oferta por linha."}</small></label>
          <label className={styles.drop}><input type="file" accept=".json,application/json" onChange={(event: ChangeEvent<HTMLInputElement>) => setManifestFile(event.target.files?.[0] || null)}/><strong>{manifestFile ? manifestFile.name : "Manifesto V7.1 (opcional, recomendado)"}</strong><small>Use srrotas_processing_manifest_v7_1.json para registrar auditoria da execução.</small></label>
          <label className={styles.field}>Substitui lote anterior (opcional)<select value={supersedesBatchId} onChange={(event: ChangeEvent<HTMLSelectElement>) => setSupersedesBatchId(event.target.value)}><option value="">Não vincular ainda</option>{batches.filter((batch) => batch.status !== "receiving" && batch.status !== "failed").map((batch) => <option key={batch.id} value={batch.id}>{batch.original_filename} · {batch.extractor_version || batch.schema_version || "legacy"}</option>)}</select></label>
          <button className={styles.primary} disabled={!canUpload} onClick={startImport}>{busy ? "Processando..." : "Validar e enviar para staging"}</button>
          {(progress.rows > 0 || message) ? <div className={styles.progress}><strong>{message}</strong><div><span>Ofertas<b>{progress.rows.toLocaleString("pt-BR")}</b></span><span className={styles.good}>Temporal<b>{progress.demand_temporal_ready.toLocaleString("pt-BR")}</b></span><span className={styles.good}>Rota<b>{progress.route_flow_ready.toLocaleString("pt-BR")}</b></span><span className={styles.good}>Financeiro<b>{progress.financial_ready.toLocaleString("pt-BR")}</b></span><span className={styles.good}>Completo<b>{progress.fully_ready.toLocaleString("pt-BR")}</b></span><span className={styles.warn}>Parciais<b>{progress.partial.toLocaleString("pt-BR")}</b></span><span>Duplicados<b>{progress.duplicate.toLocaleString("pt-BR")}</b></span><span className={styles.bad}>Inválidos<b>{progress.invalid.toLocaleString("pt-BR")}</b></span></div></div> : null}
        </article>
        <article className={styles.card}>
          <div className={styles.cardHead}><div><span>REGRAS V7.1</span><h2>O que o servidor confere</h2></div></div>
          <ol className={styles.steps}><li><b>1</b><div><strong>Identidade por oferta</strong><small>record_id + source_file_sha256 + offer_index; hash da imagem sozinho não elimina a segunda oferta do mesmo print.</small></div></li><li><b>2</b><div><strong>Duas pernas</strong><small>Busca e viagem são validadas separadamente. A perna de levar nunca vira total quando a busca falta.</small></div></li><li><b>3</b><div><strong>Quatro usos</strong><small>Temporal, rota, financeiro e completo são recalculados pelo backend.</small></div></li><li><b>4</b><div><strong>Preservação</strong><small>O lote antigo permanece disponível para comparação; depois pode ser arquivado, não apagado.</small></div></li></ol>
          <div className={styles.note}>Para o piloto, envie 500–1.000 imagens reprocessadas. Só depois de conferir as taxas abaixo rode as ~40 mil.</div>
        </article>
      </section>

      {viewer?.is_owner ? <section className={styles.card}><div className={styles.cardHead}><div><span>ACESSO</span><h2>E-mails autorizados</h2></div><b>Somente administrador</b></div><form className={styles.accessForm} onSubmit={addAccess}><input type="email" placeholder="motorista@exemplo.com" value={newEmail} onChange={(event: ChangeEvent<HTMLInputElement>) => setNewEmail(event.target.value)}/><button disabled={busy}>Autorizar e-mail</button></form><div className={styles.accessList}><div className={styles.accessRow}><div><strong>contato@bigcorps.com.br</strong><small>Administrador permanente</small></div><span className={styles.ownerTag}>OWNER</span></div>{access.filter((row) => row.email !== "contato@bigcorps.com.br").map((row) => <div className={styles.accessRow} key={row.id}><div><strong>{row.email}</strong><small>{row.enabled ? "Pode enviar JSON/JSONL" : "Acesso removido"}</small></div>{row.enabled ? <button onClick={() => disableAccess(row.email)}>Remover</button> : <span className={styles.disabledTag}>REMOVIDO</span>}</div>)}</div></section> : null}

      <section className={styles.card}>
        <div className={styles.cardHead}><div><span>COMPARAÇÃO</span><h2>Lotes recentes e qualidade</h2></div><button className={styles.textButton} onClick={() => void loadBatches()}>Atualizar</button></div>
        {batches.length ? <div className={styles.tableWrap}><table><thead><tr><th>Arquivo/contrato</th><th>Status</th><th>Ofertas</th><th>Temporal</th><th>Rota</th><th>Financeiro</th><th>Completo</th><th>Parciais</th><th>Duplic.</th><th>Principais flags</th><th>Ação</th></tr></thead><tbody>{batches.map((batch) => <tr key={batch.id}><td><strong>{batch.original_filename}</strong><small>{batch.extractor_version || batch.schema_version || "legacy"} · {new Date(batch.created_at).toLocaleString("pt-BR")}{batch.supersedes_batch_id ? " · substitui lote anterior" : ""}</small></td><td><span className={styles.status}>{batch.status}</span></td><td>{batch.received_count.toLocaleString("pt-BR")}</td><td>{(batch.demand_temporal_ready_count || 0).toLocaleString("pt-BR")}<small>{percent(batch.demand_temporal_ready_count, batch.received_count)}</small></td><td>{(batch.route_flow_ready_count || 0).toLocaleString("pt-BR")}<small>{percent(batch.route_flow_ready_count, batch.received_count)}</small></td><td>{(batch.financial_ready_count || 0).toLocaleString("pt-BR")}<small>{percent(batch.financial_ready_count, batch.received_count)}</small></td><td>{(batch.fully_ready_count || 0).toLocaleString("pt-BR")}<small>{percent(batch.fully_ready_count, batch.received_count)}</small></td><td>{batch.partial_count.toLocaleString("pt-BR")}</td><td>{batch.duplicate_count.toLocaleString("pt-BR")}</td><td><small>{topIssues(batch)}</small></td><td>{batch.status === "archived" ? <button onClick={() => void restoreBatch(batch)}>Restaurar</button> : (batch.status === "staged" || batch.status === "ready") ? <button onClick={() => void archiveBatch(batch)}>Arquivar</button> : <button className={styles.dangerButton} disabled={busy || !["receiving", "failed"].includes(batch.status)} onClick={() => void deleteBatch(batch)}>Excluir</button>}</td></tr>)}</tbody></table></div> : <div className={styles.empty}>Nenhum lote enviado ainda.</div>}
      </section>
    </div>
  </main>;
}
