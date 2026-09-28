"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import styles from "./diagnosticos.module.css";

type RecordRow = {
  id: string;
  driver_id: string;
  driver: { display_name: string; email: string } | null;
  kind: string;
  category: string;
  severity: string;
  message: string;
  app_version: string | null;
  version_code: number | null;
  android_sdk: number | null;
  manufacturer: string | null;
  model: string | null;
  checklist_completed: number | null;
  checklist_total: number | null;
  event_id: string | null;
  exception_class: string | null;
  stack_trace: string | null;
  occurred_at: string | null;
  thread: string | null;
  triage_status: string;
  admin_note: string;
  triaged_at: string | null;
  triaged_by: string | null;
  created_at: string;
};

type Payload = {
  generated_at: string;
  summary: Record<string, number>;
  records: RecordRow[];
};

function date(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? "—"
    : d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

function triageLabel(value: string) {
  return ({
    new: "Novo",
    reviewing: "Analisando",
    resolved: "Resolvido",
    ignored: "Ignorado",
  } as Record<string, string>)[value] || value;
}

function severityClass(value: string) {
  if (value === "Bloqueador") return styles.danger;
  if (value === "Problema importante") return styles.warning;
  if (value === "Problema leve") return styles.info;
  return styles.neutral;
}

async function api(path: string, init?: RequestInit) {
  const response = await fetch(path, { cache: "no-store", ...init });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data?.error || `HTTP ${response.status}`);
  return data;
}

export default function AdminDiagnosticsPage() {
  const [auth, setAuth] = useState<"loading" | "ready" | "denied">("loading");
  const [data, setData] = useState<Payload | null>(null);
  const [selected, setSelected] = useState<RecordRow | null>(null);
  const [query, setQuery] = useState("");
  const [kind, setKind] = useState("");
  const [severity, setSeverity] = useState("");
  const [triage, setTriage] = useState("");
  const [editStatus, setEditStatus] = useState("new");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setBusy(true);
    setMessage("");
    try {
      const result = await api("/api/v1/admin/diagnostics");
      setData(result as Payload);
      setAuth("ready");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao carregar diagnósticos.");
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    fetch("/api/v1/admin/imports/me", { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!active) return;
        if (response.status === 401) {
          window.location.replace("/app/entrar?next=%2Fadmin%2Fdiagnosticos");
          return;
        }
        if (!response.ok || !body?.can_admin_ops) {
          setAuth("denied");
          return;
        }
        await load();
      })
      .catch(() => active && setAuth("denied"));
    return () => { active = false; };
  }, [load]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (data?.records || []).filter((row) => {
      if (kind && row.kind !== kind) return false;
      if (severity && row.severity !== severity) return false;
      if (triage && row.triage_status !== triage) return false;
      if (!q) return true;
      return [
        row.driver?.display_name,
        row.driver?.email,
        row.category,
        row.severity,
        row.message,
        row.app_version,
        row.model,
        row.exception_class,
      ].some((value) => String(value || "").toLowerCase().includes(q));
    });
  }, [data, query, kind, severity, triage]);

  function open(row: RecordRow) {
    setSelected(row);
    setEditStatus(row.triage_status || "new");
    setNote(row.admin_note || "");
    setMessage("");
  }

  async function saveTriage() {
    if (!selected) return;
    setBusy(true);
    setMessage("");
    try {
      await api("/api/v1/admin/diagnostics", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: selected.id,
          triage_status: editStatus,
          admin_note: note,
        }),
      });
      setMessage("Triagem salva.");
      await load();
      setSelected(null);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível salvar.");
    } finally {
      setBusy(false);
    }
  }

  if (auth === "loading") {
    return <main className={styles.access}><img src="/admin-icons/icon-192.png" alt="" /><p>Validando Admin…</p></main>;
  }
  if (auth === "denied") {
    return <main className={styles.access}><img src="/admin-icons/icon-192.png" alt="" /><h1>Acesso administrativo restrito</h1><a href="/app/entrar?next=%2Fadmin%2Fdiagnosticos">Entrar novamente</a></main>;
  }

  const s = data?.summary || {};
  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <span>ADMIN BIGCORPS · SUPORTE TÉCNICO</span>
          <h1>Diagnósticos</h1>
          <p>Feedbacks, crashes e contexto técnico enviado explicitamente pelo aplicativo.</p>
        </div>
        <button onClick={() => void load()} disabled={busy}>{busy ? "Atualizando…" : "Atualizar"}</button>
      </header>

      {message ? <div className={styles.message}>{message}</div> : null}

      <section className={styles.metrics}>
        <article><span>Total</span><strong>{s.total ?? 0}</strong><small>registros persistidos</small></article>
        <article><span>Novos</span><strong>{s.new ?? 0}</strong><small>aguardando triagem</small></article>
        <article><span>Analisando</span><strong>{s.reviewing ?? 0}</strong><small>em investigação</small></article>
        <article><span>Crashes</span><strong>{s.crashes ?? 0}</strong><small>eventos enviados</small></article>
        <article><span>Bloqueadores</span><strong>{s.blockers ?? 0}</strong><small>severidade máxima</small></article>
      </section>

      <section className={styles.card}>
        <div className={styles.filters}>
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar usuário, versão, aparelho, mensagem…" />
          <select value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="">Feedback + crash</option>
            <option value="feedback">Feedback</option>
            <option value="crash">Crash</option>
          </select>
          <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
            <option value="">Todas severidades</option>
            <option>Sugestão</option>
            <option>Problema leve</option>
            <option>Problema importante</option>
            <option>Bloqueador</option>
          </select>
          <select value={triage} onChange={(e) => setTriage(e.target.value)}>
            <option value="">Toda triagem</option>
            <option value="new">Novo</option>
            <option value="reviewing">Analisando</option>
            <option value="resolved">Resolvido</option>
            <option value="ignored">Ignorado</option>
          </select>
        </div>

        <div className={styles.list}>
          {filtered.map((row) => (
            <button key={row.id} className={styles.row} onClick={() => open(row)}>
              <div className={styles.rowLead}>
                <span className={`${styles.badge} ${severityClass(row.severity)}`}>{row.severity}</span>
                <strong>{row.kind === "crash" ? row.exception_class || "Crash" : row.category}</strong>
                <small>{row.message || "Sem mensagem"}</small>
              </div>
              <div><span>{row.driver?.display_name || "Motorista"}</span><small>{row.driver?.email || "conta removida"}</small></div>
              <div><span>{row.app_version || "versão —"}</span><small>{[row.manufacturer, row.model].filter(Boolean).join(" ") || "aparelho —"}</small></div>
              <div><span>{triageLabel(row.triage_status)}</span><small>{date(row.created_at)}</small></div>
            </button>
          ))}
          {!filtered.length ? <div className={styles.empty}>Nenhum diagnóstico neste filtro.</div> : null}
        </div>
      </section>

      <div className={styles.privacyNote}>
        Este módulo não exibe OCR bruto, screenshots, coordenadas, tokens ou o identificador HMAC do aparelho.
      </div>

      {selected ? (
        <div className={styles.backdrop} onMouseDown={(e) => e.currentTarget === e.target && setSelected(null)}>
          <section className={styles.drawer}>
            <header>
              <div><span>{selected.kind === "crash" ? "CRASH" : "FEEDBACK"}</span><h2>{selected.category}</h2><p>{selected.driver?.display_name} · {selected.driver?.email}</p></div>
              <button onClick={() => setSelected(null)}>Fechar</button>
            </header>

            <div className={styles.detailGrid}>
              <div><small>Severidade</small><strong>{selected.severity}</strong></div>
              <div><small>Recebido</small><strong>{date(selected.created_at)}</strong></div>
              <div><small>Versão</small><strong>{selected.app_version || "—"} {selected.version_code ? `· vc${selected.version_code}` : ""}</strong></div>
              <div><small>Android</small><strong>{selected.android_sdk ? `SDK ${selected.android_sdk}` : "—"}</strong></div>
              <div><small>Aparelho</small><strong>{[selected.manufacturer, selected.model].filter(Boolean).join(" ") || "—"}</strong></div>
              <div><small>Checklist</small><strong>{selected.checklist_total ? `${selected.checklist_completed || 0}/${selected.checklist_total}` : "—"}</strong></div>
            </div>

            <article className={styles.textBlock}><small>Relato</small><p>{selected.message || "Sem mensagem."}</p></article>

            {selected.kind === "crash" ? (
              <>
                <article className={styles.textBlock}><small>Exceção</small><p>{selected.exception_class || "—"}</p></article>
                <article className={styles.stack}><small>Stack técnico</small><pre>{selected.stack_trace || "Sem stack persistido."}</pre></article>
              </>
            ) : null}

            <section className={styles.triageBox}>
              <h3>Triagem BigCorps</h3>
              <label>Status
                <select value={editStatus} onChange={(e) => setEditStatus(e.target.value)}>
                  <option value="new">Novo</option>
                  <option value="reviewing">Analisando</option>
                  <option value="resolved">Resolvido</option>
                  <option value="ignored">Ignorado</option>
                </select>
              </label>
              <label>Nota administrativa
                <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={1600} placeholder="Conclusão, causa, ação tomada ou motivo para ignorar…" />
              </label>
              <button onClick={() => void saveTriage()} disabled={busy}>{busy ? "Salvando…" : "Salvar triagem"}</button>
              {selected.triaged_at ? <small>Última triagem: {date(selected.triaged_at)} · {selected.triaged_by || "Admin"}</small> : null}
            </section>
          </section>
        </div>
      ) : null}
    </main>
  );
}
