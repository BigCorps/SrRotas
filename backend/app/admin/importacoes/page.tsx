"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import styles from "./page.module.css";

type Viewer = { email: string; is_owner: boolean; allowed: boolean };
type AccessRow = { id: string; email: string; enabled: boolean; created_at: string; updated_at: string };
type Batch = {
  id: string;
  created_by_email: string;
  source_name: string;
  original_filename: string;
  file_size_bytes: number;
  format: string;
  status: string;
  received_count: number;
  valid_count: number;
  partial_count: number;
  invalid_count: number;
  duplicate_count: number;
  created_at: string;
};
type ChunkResult = { received: number; counts: { valid: number; partial: number; invalid: number; duplicate: number } };

type JsonObject = Record<string, unknown>;

const CHUNK_SIZE = 150;

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

async function uploadChunk(batchId: string, rows: Array<{ row_index: number; payload: JsonObject }>) {
  return api(`/api/v1/admin/imports/batches/${batchId}/rows`, {
    method: "POST",
    body: JSON.stringify({ rows }),
  }) as Promise<ChunkResult>;
}

async function uploadJsonl(file: File, batchId: string, onProgress: (rows: number, counts: ChunkResult["counts"]) => void) {
  const reader = file.stream().getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let rowIndex = 0;
  let chunk: Array<{ row_index: number; payload: JsonObject }> = [];
  const totals = { valid: 0, partial: 0, invalid: 0, duplicate: 0 };

  async function flush() {
    if (!chunk.length) return;
    const result = await uploadChunk(batchId, chunk);
    totals.valid += result.counts.valid;
    totals.partial += result.counts.partial;
    totals.invalid += result.counts.invalid;
    totals.duplicate += result.counts.duplicate;
    rowIndex += chunk.length;
    chunk = [];
    onProgress(rowIndex, { ...totals });
  }

  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      let parsed: JsonObject;
      try { parsed = JSON.parse(trimmed) as JsonObject; }
      catch { parsed = { __parse_error: true, raw_line: trimmed.slice(0, 5000) }; }
      chunk.push({ row_index: rowIndex + chunk.length, payload: parsed });
      if (chunk.length >= CHUNK_SIZE) await flush();
    }

    if (done) break;
  }

  if (buffer.trim()) {
    let parsed: JsonObject;
    try { parsed = JSON.parse(buffer.trim()) as JsonObject; }
    catch { parsed = { __parse_error: true, raw_line: buffer.trim().slice(0, 5000) }; }
    chunk.push({ row_index: rowIndex + chunk.length, payload: parsed });
  }
  await flush();
  return { rows: rowIndex, counts: totals };
}

async function uploadJson(file: File, batchId: string, onProgress: (rows: number, counts: ChunkResult["counts"]) => void) {
  const parsed = JSON.parse(await file.text()) as unknown;
  const records = Array.isArray(parsed)
    ? parsed
    : parsed && typeof parsed === "object" && Array.isArray((parsed as { records?: unknown[] }).records)
      ? (parsed as { records: unknown[] }).records
      : [parsed];

  const totals = { valid: 0, partial: 0, invalid: 0, duplicate: 0 };
  let sent = 0;
  for (let start = 0; start < records.length; start += CHUNK_SIZE) {
    const slice = records.slice(start, start + CHUNK_SIZE).map((payload, offset) => ({
      row_index: start + offset,
      payload: payload && typeof payload === "object" && !Array.isArray(payload) ? payload as JsonObject : { value: payload },
    }));
    const result = await uploadChunk(batchId, slice);
    totals.valid += result.counts.valid;
    totals.partial += result.counts.partial;
    totals.invalid += result.counts.invalid;
    totals.duplicate += result.counts.duplicate;
    sent += slice.length;
    onProgress(sent, { ...totals });
  }
  return { rows: sent, counts: totals };
}

function formatBytes(bytes: number) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return `${(bytes / 1024 ** index).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} ${units[index]}`;
}

export default function AdminImportacoesPage() {
  const [viewer, setViewer] = useState<Viewer | null>(null);
  const [authState, setAuthState] = useState<"loading" | "login" | "denied" | "ready">("loading");
  const [email, setEmail] = useState("contato@bigcorps.com.br");
  const [password, setPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [access, setAccess] = useState<AccessRow[]>([]);
  const [newEmail, setNewEmail] = useState("");
  const [batches, setBatches] = useState<Batch[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [sourceName, setSourceName] = useState("historical_screenshot_gpt");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [progress, setProgress] = useState({ rows: 0, valid: 0, partial: 0, invalid: 0, duplicate: 0 });

  const loadBatches = useCallback(async () => {
    const data = await api("/api/v1/admin/imports/batches");
    setBatches(data.batches || []);
  }, []);

  const loadAccess = useCallback(async () => {
    const data = await api("/api/v1/admin/imports/access");
    setAccess(data.access || []);
  }, []);

  const checkSession = useCallback(async () => {
    try {
      const response = await fetch("/api/v1/admin/imports/me", { cache: "no-store" });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401) { setViewer(null); setAuthState("login"); return; }
      if (response.status === 403) { setViewer({ email: data.email || "", is_owner: false, allowed: false }); setAuthState("denied"); return; }
      if (!response.ok) throw new Error(data?.error || "Falha ao validar acesso.");
      const nextViewer = data as Viewer;
      setViewer(nextViewer);
      setAuthState("ready");
      await loadBatches();
      if (nextViewer.is_owner) await loadAccess();
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Falha ao validar acesso.");
      setAuthState("login");
    }
  }, [loadAccess, loadBatches]);

  useEffect(() => { void checkSession(); }, [checkSession]);

  async function login(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setAuthError("");
    try {
      await api("/api/v1/admin/imports/login", { method: "POST", body: JSON.stringify({ email, password }) });
      setPassword("");
      await checkSession();
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Não foi possível entrar.");
    } finally { setBusy(false); }
  }

  async function logout() {
    await fetch("/api/v1/admin/imports/logout", { method: "POST" }).catch(() => undefined);
    setViewer(null); setAuthState("login"); setAccess([]); setBatches([]);
  }

  async function addAccess(event: FormEvent) {
    event.preventDefault();
    if (!newEmail.trim()) return;
    setBusy(true); setMessage("");
    try {
      await api("/api/v1/admin/imports/access", { method: "POST", body: JSON.stringify({ email: newEmail }) });
      setNewEmail("");
      await loadAccess();
      setMessage("E-mail autorizado.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Falha ao autorizar."); }
    finally { setBusy(false); }
  }

  async function disableAccess(targetEmail: string) {
    if (!confirm(`Remover acesso de ${targetEmail}?`)) return;
    setBusy(true); setMessage("");
    try {
      await api("/api/v1/admin/imports/access", { method: "DELETE", body: JSON.stringify({ email: targetEmail }) });
      await loadAccess();
      setMessage("Acesso removido.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Falha ao remover."); }
    finally { setBusy(false); }
  }

  const canUpload = useMemo(() => Boolean(file && !busy), [file, busy]);

  async function startImport() {
    if (!file) return;
    const lower = file.name.toLowerCase();
    const format = lower.endsWith(".jsonl") || lower.endsWith(".ndjson") ? "jsonl" : lower.endsWith(".json") ? "json" : "";
    if (!format) { setMessage("Use um arquivo .jsonl, .ndjson ou .json."); return; }

    setBusy(true); setMessage("Preparando lote...");
    setProgress({ rows: 0, valid: 0, partial: 0, invalid: 0, duplicate: 0 });
    try {
      const fileHash = await sha256File(file);
      const created = await api("/api/v1/admin/imports/batches", {
        method: "POST",
        body: JSON.stringify({
          original_filename: file.name,
          source_name: sourceName,
          file_size_bytes: file.size,
          file_sha256: fileHash,
          format,
        }),
      });
      const batchId = String(created.batch.id);
      const onProgress = (rows: number, counts: ChunkResult["counts"]) => {
        setProgress({ rows, ...counts });
        setMessage(`Processando ${rows.toLocaleString("pt-BR")} registros...`);
      };

      if (format === "jsonl") await uploadJsonl(file, batchId, onProgress);
      else await uploadJson(file, batchId, onProgress);

      const finalized = await api(`/api/v1/admin/imports/batches/${batchId}/finalize`, { method: "POST", body: "{}" });
      const batch = finalized.batch;
      setProgress({ rows: batch.received_count, valid: batch.valid_count, partial: batch.partial_count, invalid: batch.invalid_count, duplicate: batch.duplicate_count });
      setMessage(`Lote concluído: ${batch.received_count.toLocaleString("pt-BR")} registros em staging.`);
      setFile(null);
      await loadBatches();
    } catch (error) {
      setMessage(error instanceof Error ? `Falha: ${error.message}` : "Falha na importação.");
    } finally { setBusy(false); }
  }

  if (authState === "loading") return <main className={styles.page}><div className={styles.centerCard}><img src="/logo-srrotas.png" alt=""/><strong>Validando acesso...</strong></div></main>;

  if (authState === "login") return <main className={styles.page}>
    <section className={styles.loginCard}>
      <img src="/logo-srrotas.png" alt="Sr. Rotas"/>
      <span>BIGCORPS · FERRAMENTA INTERNA</span>
      <h1>Importação histórica</h1>
      <p>Área Web restrita para preparar a base estatística do Sr. Rotas.</p>
      <form onSubmit={login}>
        <label>E-mail<input type="email" value={email} onChange={(event: ChangeEvent<HTMLInputElement>) => setEmail(event.target.value)} autoComplete="username"/></label>
        <label>Senha<input type="password" value={password} onChange={(event: ChangeEvent<HTMLInputElement>) => setPassword(event.target.value)} autoComplete="current-password"/></label>
        {authError ? <div className={styles.error}>{authError}</div> : null}
        <button disabled={busy}>{busy ? "Entrando..." : "Entrar"}</button>
      </form>
      <small>O e-mail precisa existir no Supabase Auth e estar autorizado. Não precisa ser uma conta de motorista.</small>
    </section>
  </main>;

  if (authState === "denied") return <main className={styles.page}><section className={styles.loginCard}><img src="/logo-srrotas.png" alt="Sr. Rotas"/><span>ACESSO RESTRITO</span><h1>Este e-mail não está autorizado.</h1><p>{viewer?.email || "Sua conta"} possui login válido, mas não tem permissão para importar históricos.</p><button onClick={logout}>Sair</button></section></main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <div><img src="/logo-srrotas.png" alt="Sr. Rotas"/><div><span>BIGCORPS · FERRAMENTA INTERNA</span><strong>Importação histórica</strong></div></div>
      <div className={styles.user}><small>{viewer?.email}</small>{viewer?.is_owner ? <b>Administrador</b> : <b>Importador</b>}<button onClick={logout}>Sair</button></div>
    </header>

    <div className={styles.shell}>
      <section className={styles.hero}><span>WEB-P5</span><h1>Prepare os dados antes de alimentar a inteligência.</h1><p>JSONL/JSON entram primeiro em staging. Nada é gravado diretamente em <code>ride_offers</code> ou no Motor Estatístico.</p></section>

      <section className={styles.grid}>
        <article className={styles.card}>
          <div className={styles.cardHead}><div><span>NOVO LOTE</span><h2>Enviar JSONL / JSON</h2></div><b>Staging</b></div>
          <label className={styles.field}>Fonte<input value={sourceName} onChange={(event: ChangeEvent<HTMLInputElement>) => setSourceName(event.target.value)} /></label>
          <label className={styles.drop}>
            <input type="file" accept=".jsonl,.ndjson,.json,application/json" onChange={(event: ChangeEvent<HTMLInputElement>) => setFile(event.target.files?.[0] || null)}/>
            <strong>{file ? file.name : "Selecionar arquivo"}</strong>
            <small>{file ? `${formatBytes(file.size)} · pronto para validar` : "Preferência: JSONL, um registro por screenshot."}</small>
          </label>
          <button className={styles.primary} disabled={!canUpload} onClick={startImport}>{busy ? "Processando..." : "Validar e enviar para staging"}</button>
          {(progress.rows > 0 || message) ? <div className={styles.progress}>
            <strong>{message}</strong>
            <div><span>Recebidos<b>{progress.rows.toLocaleString("pt-BR")}</b></span><span className={styles.good}>Válidos<b>{progress.valid.toLocaleString("pt-BR")}</b></span><span className={styles.warn}>Parciais<b>{progress.partial.toLocaleString("pt-BR")}</b></span><span>Duplicados<b>{progress.duplicate.toLocaleString("pt-BR")}</b></span><span className={styles.bad}>Inválidos<b>{progress.invalid.toLocaleString("pt-BR")}</b></span></div>
          </div> : null}
        </article>

        <article className={styles.card}>
          <div className={styles.cardHead}><div><span>REGRAS</span><h2>O que acontece neste estágio</h2></div></div>
          <ol className={styles.steps}><li><b>1</b><div><strong>Validação</strong><small>Formato, valor, data/hora, retirada e destino.</small></div></li><li><b>2</b><div><strong>Deduplicação</strong><small>SHA-256 do screenshot quando disponível + fingerprint semântico.</small></div></li><li><b>3</b><div><strong>Staging</strong><small>Original e normalizado ficam separados para auditoria.</small></div></li><li><b>4</b><div><strong>Próxima fase</strong><small>Context Engine/geocoding decidirão o que pode alimentar a base final.</small></div></li></ol>
          <div className={styles.note}>Screenshots e OCR bruto não são enviados aqui. Este portal recebe os <strong>dados estruturados produzidos a partir deles</strong>.</div>
        </article>
      </section>

      {viewer?.is_owner ? <section className={styles.card}>
        <div className={styles.cardHead}><div><span>ACESSO</span><h2>E-mails autorizados</h2></div><b>Somente administrador</b></div>
        <form className={styles.accessForm} onSubmit={addAccess}><input type="email" placeholder="motorista@exemplo.com" value={newEmail} onChange={(event: ChangeEvent<HTMLInputElement>) => setNewEmail(event.target.value)}/><button disabled={busy}>Autorizar e-mail</button></form>
        <div className={styles.accessList}><div className={styles.accessRow}><div><strong>contato@bigcorps.com.br</strong><small>Administrador permanente</small></div><span className={styles.ownerTag}>OWNER</span></div>{access.filter((row) => row.email !== "contato@bigcorps.com.br").map((row) => <div className={styles.accessRow} key={row.id}><div><strong>{row.email}</strong><small>{row.enabled ? "Pode enviar JSON/JSONL" : "Acesso removido"}</small></div>{row.enabled ? <button onClick={() => disableAccess(row.email)}>Remover</button> : <span className={styles.disabledTag}>REMOVIDO</span>}</div>)}</div>
      </section> : null}

      <section className={styles.card}>
        <div className={styles.cardHead}><div><span>HISTÓRICO</span><h2>Lotes recentes</h2></div><button className={styles.textButton} onClick={() => void loadBatches()}>Atualizar</button></div>
        {batches.length ? <div className={styles.tableWrap}><table><thead><tr><th>Arquivo</th><th>Quem enviou</th><th>Status</th><th>Registros</th><th>Válidos</th><th>Parciais</th><th>Duplicados</th><th>Inválidos</th></tr></thead><tbody>{batches.map((batch) => <tr key={batch.id}><td><strong>{batch.original_filename}</strong><small>{formatBytes(batch.file_size_bytes)} · {new Date(batch.created_at).toLocaleString("pt-BR")}</small></td><td>{batch.created_by_email}</td><td><span className={styles.status}>{batch.status}</span></td><td>{batch.received_count.toLocaleString("pt-BR")}</td><td>{batch.valid_count.toLocaleString("pt-BR")}</td><td>{batch.partial_count.toLocaleString("pt-BR")}</td><td>{batch.duplicate_count.toLocaleString("pt-BR")}</td><td>{batch.invalid_count.toLocaleString("pt-BR")}</td></tr>)}</tbody></table></div> : <div className={styles.empty}>Nenhum lote enviado ainda.</div>}
      </section>
    </div>
  </main>;
}
