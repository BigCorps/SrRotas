"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import styles from "./radar.module.css";
import JsonImportPreparation from "./JsonImportPreparation";
import AdminImportBatches from "./AdminImportBatches";

type Tab = "import" | "events" | "pois" | "decisions" | "keys" | "runs";
type Flag = { code: string; label: string; related_ids?: string[] };
type Collector = {
  id: string;
  row_version: number;
  expires_at: string;
  name: string;
  status: string;
  allowed_sources: string[];
  scopes: string[];
  key_prefix: string;
  max_batch: number;
  rate_limit_per_hour: number;
  created_at: string | null;
  created_by: string | null;
  rotated_at: string | null;
  revoked_at: string | null;
  revoked_by: string | null;
  last_used_at: string | null;
};
type EventRow = {
  id: string;
  row_version: number;
  source: string;
  external_id: string;
  event_type: string;
  name: string;
  venue_name: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  lat: number;
  lng: number;
  starts_at: string;
  expected_end_at: string;
  egress_start_at: string;
  egress_end_at: string;
  source_url: string | null;
  confidence: number;
  end_time_source: string;
  status: string;
  review_status: string;
  review_note: string | null;
  reviewed_at: string | null;
  reviewed_by: string | null;
  collector_id: string | null;
  ingest_run_id: string | null;
  first_seen_at: string | null;
  maps_url: string | null;
  flags: Flag[];
};
type PoiRow = {
  id: string;
  row_version: number;
  canonical_name: string;
  poi_type: string;
  address: string | null;
  city: string | null;
  state: string | null;
  lat: number;
  lng: number;
  source: string;
  source_external_id: string | null;
  source_url: string | null;
  source_confidence: number;
  status: string;
  review: string;
  updated_at: string;
  maps_url: string | null;
  metadata: Record<string, unknown>;
};
type Decision = {
  id: string;
  target_type: string;
  target_id: string;
  decision: string;
  note: string | null;
  actor_email: string;
  previous_status: string | null;
  created_at: string;
  target_label: string | null;
};

const TABS: Array<{ id: Tab; label: string }> = [
  { id: "import", label: "Importar JSON" },
  { id: "events", label: "Recebidos" },
  { id: "pois", label: "Lugares" },
  { id: "decisions", label: "Histórico de decisões" },
  { id: "keys", label: "Chaves" },
  { id: "runs", label: "Envios" },
];
const SCOPES = ["events:ingest", "pois:propose"];

// Hoisted formatters (all event times shown in Brasília with explicit offset).
const TZ_FORMAT = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  year: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "America/Sao_Paulo",
  timeZoneName: "shortOffset",
});
const LOCAL_FORMAT = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

function tzDate(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? "—" : TZ_FORMAT.format(d);
}
function date(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? "—" : LOCAL_FORMAT.format(d);
}
function reviewLabel(value: string) {
  return (
    (
      {
        pending: "Pendente",
        approved: "Aprovado",
        rejected: "Rejeitado",
        changes_requested: "Correção solicitada",
        unpublished: "Despublicado",
      } as Record<string, string>
    )[value] || value
  );
}
function reviewClass(value: string) {
  if (value === "approved") return styles.bApproved;
  if (value === "rejected") return styles.bRejected;
  if (value === "changes_requested") return styles.bChanges;
  if (value === "pending") return styles.bPending;
  return styles.bNeutral;
}
function decisionLabel(value: string) {
  return (
    (
      {
        approve: "Aprovar",
        reject: "Rejeitar",
        request_changes: "Solicitar correção",
        unpublish: "Despublicar",
      } as Record<string, string>
    )[value] || value
  );
}
function safeHttpUrl(value?: string | null) {
  if (!value) return null;
  try {
    const u = new URL(value);
    return u.protocol === "https:" || u.protocol === "http:"
      ? u.toString()
      : null;
  } catch {
    return null;
  }
}

async function api(path: string, init?: RequestInit) {
  const response = await fetch(path, { cache: "no-store", ...init });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const code = data?.error || `HTTP ${response.status}`;
    throw new Error(
      code === "collector_store_unavailable"
        ? "Migration do Radar (coletores/revisão) ainda não aplicada no banco."
        : code,
    );
  }
  return data;
}
function jsonInit(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export default function AdminRadarPage() {
  const [history, setHistory] = useState<
    Array<{
      id: number;
      version: number;
      actor: string;
      created_at: string;
      after_data: unknown;
    }>
  >([]);
  const [expiry, setExpiry] = useState(
    new Date(Date.now() + 365 * 86400000).toISOString().slice(0, 10),
  );
  const [auth, setAuth] = useState<"loading" | "ready" | "denied">("loading");
  const [tab, setTab] = useState<Tab>("events");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const [collectors, setCollectors] = useState<Collector[]>([]);
  const [events, setEvents] = useState<EventRow[]>([]);
  const [pois, setPois] = useState<PoiRow[]>([]);
  const [decisions, setDecisions] = useState<Decision[]>([]);
  const [runs, setRuns] = useState<any[]>([]);
  const [usage, setUsage] = useState<any[]>([]);

  const [filters, setFilters] = useState({
    origin: "collectors",
    collector_id: "",
    source: "",
    review_status: "pending",
    status: "",
    from: "",
    to: "",
  });
  const [runsCollector, setRunsCollector] = useState("");
  const [selectedEvent, setSelectedEvent] = useState<EventRow | null>(null);
  const [selectedPoi, setSelectedPoi] = useState<PoiRow | null>(null);
  const [note, setNote] = useState("");
  const [revealedKey, setRevealedKey] = useState<{
    name: string;
    key: string;
  } | null>(null);
  const [form, setForm] = useState({
    name: "",
    sources: "",
    scopes: ["events:ingest"],
    max_batch: "200",
    rate_limit_per_hour: "30",
  });
  const [editing, setEditing] = useState<Collector | null>(null);
  const [editForm, setEditForm] = useState({
    sources: "",
    scopes: [] as string[],
    max_batch: "",
    rate_limit_per_hour: "",
  });

  const collectorName = useMemo(
    () => new Map(collectors.map((c) => [c.id, c.name])),
    [collectors],
  );

  const run = useCallback(async (fn: () => Promise<void>) => {
    setBusy(true);
    setMessage("");
    try {
      await fn();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha na operação.");
    } finally {
      setBusy(false);
    }
  }, []);

  const loadEvents = useCallback(
    () =>
      run(async () => {
        const qs = new URLSearchParams();
        for (const [k, v] of Object.entries(filters))
          if (v)
            qs.set(
              k,
              k === "from" || k === "to" ? new Date(v).toISOString() : v,
            );
        const data = await api(`/api/v1/admin/radar/events?${qs.toString()}`);
        setEvents(data.events || []);
        setCollectors(data.collectors || []);
      }),
    [filters, run],
  );
  const loadPois = useCallback(
    () =>
      run(async () => {
        setPois((await api("/api/v1/admin/radar/pois")).pois || []);
      }),
    [run],
  );
  const loadDecisions = useCallback(
    () =>
      run(async () => {
        setDecisions(
          (await api("/api/v1/admin/radar/decisions")).decisions || [],
        );
      }),
    [run],
  );
  const loadCollectors = useCallback(
    () =>
      run(async () => {
        setCollectors(
          (await api("/api/v1/admin/radar/collectors")).collectors || [],
        );
      }),
    [run],
  );
  const loadRuns = useCallback(
    () =>
      run(async () => {
        const qs = runsCollector
          ? `?collector_id=${encodeURIComponent(runsCollector)}`
          : "";
        const [activity, list] = await Promise.all([
          api(`/api/v1/admin/radar/runs${qs}`),
          api("/api/v1/admin/radar/collectors"),
        ]);
        setRuns(activity.runs || []);
        setUsage(activity.usage || []);
        setCollectors(list.collectors || []);
      }),
    [run, runsCollector],
  );

  useEffect(() => {
    const params=new URLSearchParams(window.location.search);
    if(params.get("origin")==="manual")setFilters(f=>({...f,origin:"manual"}));
    if(["import","events","pois","decisions","keys","runs"].includes(params.get("tab")??""))setTab(params.get("tab") as Tab);
    let active = true;
    fetch("/api/v1/admin/imports/me", { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!active) return;
        if (response.status === 401) {
          window.location.replace("/app/entrar?next=%2Fadmin%2Fradar");
          return;
        }
        setAuth(response.ok && body?.can_admin_ops ? "ready" : "denied");
      })
      .catch(() => active && setAuth("denied"));
    return () => {
      active = false;
    };
  }, []);

  function selectTab(next: Tab) {
    setTab(next);
    setMessage("");
    if (next === "events") void loadEvents();
    if (next === "pois") void loadPois();
    if (next === "decisions") void loadDecisions();
    if (next === "keys") void loadCollectors();
    if (next === "runs") void loadRuns();
  }

  useEffect(() => {
    if (auth === "ready") selectTab(tab);
    // First load only; later loads are explicit (filters button / tab switch), so filter
    // edits never trigger a request per keystroke.
  }, [auth]); // eslint-disable-line react-hooks/exhaustive-deps

  async function decideEvent(
    decision: "approve" | "reject" | "request_changes" | "unpublish",
  ) {
    if (!selectedEvent) return;
    if (note.trim().length < 3) {
      setMessage("Informe o motivo da decisão (mínimo 3 caracteres).");
      return;
    }
    await run(async () => {
      await api(
        "/api/v1/admin/radar/events",
        jsonInit("PATCH", {
          id: selectedEvent.id,
          expected_version: selectedEvent.row_version,
          decision,
          note,
        }),
      );
      setSelectedEvent(null);
      setNote("");
    });
    void loadEvents();
  }
  async function decidePoi(
    decision: "approve" | "reject" | "request_changes" | "unpublish",
  ) {
    if (!selectedPoi) return;
    if (note.trim().length < 3) {
      setMessage("Informe o motivo da decisão (mínimo 3 caracteres).");
      return;
    }
    await run(async () => {
      await api(
        "/api/v1/admin/radar/pois",
        jsonInit("PATCH", {
          id: selectedPoi.id,
          expected_version: selectedPoi.row_version,
          decision,
          note,
        }),
      );
      setSelectedPoi(null);
      setNote("");
    });
    void loadPois();
  }

  function parseSources(value: string) {
    return value
      .split(/[\s,;]+/)
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);
  }
  async function createCollector() {
    await run(async () => {
      const data = await api(
        "/api/v1/admin/radar/collectors",
        jsonInit("POST", {
          name: form.name,
          expires_at: new Date(expiry + "T23:59:59-03:00").toISOString(),
          allowed_sources: parseSources(form.sources),
          scopes: form.scopes,
          max_batch: Number(form.max_batch),
          rate_limit_per_hour: Number(form.rate_limit_per_hour),
        }),
      );
      setRevealedKey({
        name: data.collector?.name || form.name,
        key: data.key,
      });
      setForm({
        name: "",
        sources: "",
        scopes: ["events:ingest"],
        max_batch: "200",
        rate_limit_per_hour: "30",
      });
      setCollectors(
        (await api("/api/v1/admin/radar/collectors")).collectors || [],
      );
    });
  }
  async function rotate(c: Collector) {
    if (
      !window.confirm(
        `Rotacionar a chave de "${c.name}"? A chave atual deixa de funcionar imediatamente.`,
      )
    )
      return;
    const day = window.prompt(
      "Nova validade (AAAA-MM-DD)",
      new Date(Date.now() + 365 * 86400000).toISOString().slice(0, 10),
    );
    if (!day || !/^\d{4}-\d{2}-\d{2}$/.test(day)) return;
    await run(async () => {
      const data = await api(
        "/api/v1/admin/radar/collectors",
        jsonInit("PATCH", {
          id: c.id,
          expected_version: c.row_version,
          action: "rotate",
          expires_at: new Date(day + "T23:59:59-03:00").toISOString(),
        }),
      );
      setRevealedKey({ name: c.name, key: data.key });
      setCollectors(
        (await api("/api/v1/admin/radar/collectors")).collectors || [],
      );
    });
  }
  async function revoke(c: Collector) {
    if (
      !window.confirm(
        `Revogar "${c.name}"? O coletor perde acesso de forma definitiva.`,
      )
    )
      return;
    await run(async () => {
      await api(
        "/api/v1/admin/radar/collectors",
        jsonInit("PATCH", {
          id: c.id,
          expected_version: c.row_version,
          action: "revoke",
        }),
      );
      setCollectors(
        (await api("/api/v1/admin/radar/collectors")).collectors || [],
      );
    });
  }
  function openEdit(c: Collector) {
    setEditing(c);
    setEditForm({
      sources: c.allowed_sources.join(", "),
      scopes: c.scopes,
      max_batch: String(c.max_batch),
      rate_limit_per_hour: String(c.rate_limit_per_hour),
    });
  }
  async function saveEdit() {
    if (!editing) return;
    await run(async () => {
      await api(
        "/api/v1/admin/radar/collectors",
        jsonInit("PATCH", {
          id: editing.id,
          expected_version: editing.row_version,
          action: "update",
          allowed_sources: parseSources(editForm.sources),
          scopes: editForm.scopes,
          max_batch: Number(editForm.max_batch),
          rate_limit_per_hour: Number(editForm.rate_limit_per_hour),
        }),
      );
      setEditing(null);
      setCollectors(
        (await api("/api/v1/admin/radar/collectors")).collectors || [],
      );
    });
  }
  async function copyKey() {
    if (!revealedKey) return;
    try {
      await navigator.clipboard.writeText(revealedKey.key);
      setMessage("Chave copiada. Guarde-a em um cofre de segredos.");
    } catch {
      setMessage("Não foi possível copiar; selecione o texto manualmente.");
    }
  }

  if (auth === "loading") {
    return (
      <main className={styles.access}>
        <img src="/admin-icons/icon-192.png" alt="" />
        <p>Validando Admin…</p>
      </main>
    );
  }
  if (auth === "denied") {
    return (
      <main className={styles.access}>
        <img src="/admin-icons/icon-192.png" alt="" />
        <h1>Acesso administrativo restrito</h1>
        <a href="/app/entrar?next=%2Fadmin%2Fradar">Entrar novamente</a>
      </main>
    );
  }

  const toggleScope = (list: string[], scope: string) =>
    list.includes(scope) ? list.filter((s) => s !== scope) : [...list, scope];

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <span>ADMIN BIGCORPS · RADAR</span>
          <h1>Radar · coletores e revisão</h1>
          <p>
            Eventos e lugares enviados por coletores externos ficam pendentes e
            só chegam ao Radar do app depois de aprovados aqui.
          </p>
        </div>
        <button onClick={() => selectTab(tab)} disabled={busy}>
          {busy ? "Atualizando…" : "Atualizar"}
        </button>
      </header>

      <nav className={styles.tabs} role="tablist" aria-label="Seções do Radar">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => selectTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      {message ? <div className={styles.message}>{message}</div> : null}

      {revealedKey ? (
        <div className={styles.keyBox} role="alert">
          <strong>
            Chave de “{revealedKey.name}” — exibida somente agora.
          </strong>
          Copie e entregue ao coletor por um canal seguro. Ela não pode ser
          recuperada depois; se perder, rotacione.
          <code>{revealedKey.key}</code>
          <div className={styles.actions}>
            <button className={styles.ghost} onClick={() => void copyKey()}>
              Copiar
            </button>
            <button
              className={styles.ghost}
              onClick={() => setRevealedKey(null)}
            >
              Já guardei, ocultar
            </button>
          </div>
        </div>
      ) : null}

      {tab === "import" ? <JsonImportPreparation /> : null}

      {tab === "events" ? (
        <section className={styles.card}>
          <div className={styles.filters}>
            <label>
              Origem
              <select
                aria-label="Origem"
                value={filters.origin}
                onChange={(e) =>
                  setFilters({ ...filters, origin: e.target.value })
                }
              >
                <option value="collectors">Coletores externos</option>
                <option value="manual">Importação administrativa</option>
                <option value="all">Todas as fontes</option>
              </select>
            </label>
            <label>
              Coletor
              <select
                value={filters.collector_id}
                onChange={(e) =>
                  setFilters({ ...filters, collector_id: e.target.value })
                }
              >
                <option value="">Todos</option>
                {collectors.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Fonte
              <input
                value={filters.source}
                onChange={(e) =>
                  setFilters({ ...filters, source: e.target.value })
                }
                placeholder="slug"
              />
            </label>
            <label>
              Revisão
              <select
                aria-label="Revisão"
                value={filters.review_status}
                onChange={(e) =>
                  setFilters({ ...filters, review_status: e.target.value })
                }
              >
                <option value="">Todas</option>
                <option value="pending">Pendente</option>
                <option value="approved">Aprovado</option>
                <option value="rejected">Rejeitado</option>
                <option value="changes_requested">Correção solicitada</option>
                <option value="unpublished">Despublicado</option>
              </select>
            </label>
            <label>
              Status
              <select
                value={filters.status}
                onChange={(e) =>
                  setFilters({ ...filters, status: e.target.value })
                }
              >
                <option value="">Todos</option>
                <option value="active">Ativo</option>
                <option value="cancelled">Cancelado</option>
                <option value="expired">Expirado</option>
              </select>
            </label>
            <label>
              Início de
              <input
                type="datetime-local"
                value={filters.from}
                onChange={(e) =>
                  setFilters({ ...filters, from: e.target.value })
                }
              />
            </label>
            <label>
              Início até
              <input
                type="datetime-local"
                value={filters.to}
                onChange={(e) => setFilters({ ...filters, to: e.target.value })}
              />
            </label>
            <button
              className={styles.primary}
              onClick={() => void loadEvents()}
              disabled={busy}
            >
              Filtrar
            </button>
          </div>
          <div className={styles.list}>
            {events.map((row) => (
              <button
                key={row.id}
                className={styles.row}
                onClick={() => {
                  setSelectedEvent(row);
                  setNote(row.review_note || "");
                }}
              >
                <div>
                  <div className={styles.badges}>
                    <span
                      className={`${styles.badge} ${reviewClass(row.review_status)}`}
                    >
                      {reviewLabel(row.review_status)}
                    </span>
                    <span className={`${styles.badge} ${styles.bNeutral}`}>
                      {row.status}
                    </span>
                    {row.flags.map((f) => (
                      <span
                        key={f.code}
                        className={`${styles.badge} ${styles.bFlag}`}
                        title={f.label}
                      >
                        {f.label}
                      </span>
                    ))}
                  </div>
                  <strong>{row.name}</strong>
                  <small>
                    {[row.venue_name, row.address, row.city]
                      .filter(Boolean)
                      .join(" · ") || "Sem local textual"}
                  </small>
                </div>
                <div>
                  <span>{tzDate(row.starts_at)}</span>
                  <small>
                    até {tzDate(row.expected_end_at)} (
                    {row.end_time_source === "source" ? "fonte" : "estimado"})
                  </small>
                </div>
                <div>
                  <span>{row.source}</span>
                  <small>
                    {row.collector_id
                      ? collectorName.get(row.collector_id) || "coletor"
                      : "interno"}{" "}
                    · conf. {Number(row.confidence).toFixed(2)}
                  </small>
                </div>
                <div>
                  <span>Recebido</span>
                  <small>{date(row.first_seen_at)}</small>
                </div>
              </button>
            ))}
            {!events.length ? (
              <div className={styles.empty}>Nenhum evento neste filtro.</div>
            ) : null}
          </div>
        </section>
      ) : null}

      {tab === "pois" ? (
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2>Lugares inativos (candidatos)</h2>
            <small>Aprovar publica o lugar no Radar.</small>
          </div>
          <div className={styles.list}>
            {pois.map((p) => (
              <button
                key={p.id}
                className={styles.row}
                onClick={() => {
                  setSelectedPoi(p);
                  setNote("");
                }}
              >
                <div>
                  <div className={styles.badges}>
                    <span
                      className={`${styles.badge} ${reviewClass(p.review)}`}
                    >
                      {reviewLabel(p.review)}
                    </span>
                    <span className={`${styles.badge} ${styles.bNeutral}`}>
                      {p.poi_type}
                    </span>
                  </div>
                  <strong>{p.canonical_name}</strong>
                  <small>
                    {[p.address, p.city, p.state].filter(Boolean).join(" · ") ||
                      "Sem endereço"}
                  </small>
                </div>
                <div>
                  <span>{p.source}</span>
                  <small className={styles.mono}>
                    {p.source_external_id || "—"}
                  </small>
                </div>
                <div>
                  <span>
                    Confiança {Number(p.source_confidence).toFixed(2)}
                  </span>
                  <small>{String(p.metadata?.ingest_kind || "—")}</small>
                </div>
                <div>
                  <span>Atualizado</span>
                  <small>{date(p.updated_at)}</small>
                </div>
              </button>
            ))}
            {!pois.length ? (
              <div className={styles.empty}>
                Nenhum lugar aguardando revisão.
              </div>
            ) : null}
          </div>
        </section>
      ) : null}

      {tab === "decisions" ? (
        <section className={styles.card}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Quando</th>
                  <th>Alvo</th>
                  <th>Decisão</th>
                  <th>Anterior</th>
                  <th>Nota</th>
                  <th>Admin</th>
                </tr>
              </thead>
              <tbody>
                {decisions.map((d) => (
                  <tr key={d.id}>
                    <td>{date(d.created_at)}</td>
                    <td>
                      {d.target_type === "poi" ? "Lugar" : "Evento"} ·{" "}
                      {d.target_label || (
                        <span className={styles.mono}>{d.target_id}</span>
                      )}
                    </td>
                    <td>{decisionLabel(d.decision)}</td>
                    <td>
                      {d.previous_status ? reviewLabel(d.previous_status) : "—"}
                    </td>
                    <td>{d.note || "—"}</td>
                    <td>{d.actor_email}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!decisions.length ? (
              <div className={styles.empty}>Nenhuma decisão registrada.</div>
            ) : null}
          </div>
        </section>
      ) : null}

      {tab === "keys" ? (
        <>
          <section className={styles.card}>
            <div className={styles.cardHead}>
              <h2>Nova chave de coletor</h2>
              <small>A chave aparece uma única vez.</small>
            </div>
            <div className={styles.form}>
              <label>
                Válida até
                <input
                  type="date"
                  value={expiry}
                  onChange={(e) => setExpiry(e.target.value)}
                  required
                />
              </label>
              <label>
                Nome
                <input
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="Coletor semanal SP"
                />
              </label>
              <label>
                Fontes permitidas
                <input
                  value={form.sources}
                  onChange={(e) =>
                    setForm({ ...form, sources: e.target.value })
                  }
                  placeholder="prefeitura_sp, sympla"
                />
              </label>
              <label>
                Escopos
                <span className={styles.checks}>
                  {SCOPES.map((s) => (
                    <span key={s}>
                      <input
                        type="checkbox"
                        checked={form.scopes.includes(s)}
                        onChange={() =>
                          setForm({
                            ...form,
                            scopes: toggleScope(form.scopes, s),
                          })
                        }
                      />{" "}
                      {s}
                    </span>
                  ))}
                </span>
              </label>
              <label>
                Lote máx. (≤ 500)
                <input
                  type="number"
                  min={1}
                  max={500}
                  value={form.max_batch}
                  onChange={(e) =>
                    setForm({ ...form, max_batch: e.target.value })
                  }
                />
              </label>
              <label>
                Envios/hora
                <input
                  type="number"
                  min={1}
                  max={1000}
                  value={form.rate_limit_per_hour}
                  onChange={(e) =>
                    setForm({ ...form, rate_limit_per_hour: e.target.value })
                  }
                />
              </label>
              <button
                className={styles.primary}
                onClick={() => void createCollector()}
                disabled={busy || form.name.trim().length < 3}
              >
                Criar chave
              </button>
            </div>
          </section>
          <section className={styles.card}>
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Coletor</th>
                    <th>Prefixo</th>
                    <th>Escopos</th>
                    <th>Fontes</th>
                    <th>Limites</th>
                    <th>Status</th>
                    <th>Último uso</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {collectors.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <strong>{c.name}</strong>
                        <br />
                        <small>
                          criado {date(c.created_at)} · {c.created_by || "—"}
                        </small>
                      </td>
                      <td className={styles.mono}>{c.key_prefix}…</td>
                      <td>{c.scopes.join(", ")}</td>
                      <td>{c.allowed_sources.join(", ")}</td>
                      <td>
                        {c.max_batch}/lote · {c.rate_limit_per_hour}/h
                      </td>
                      <td>
                        <span
                          className={`${styles.badge} ${c.status === "active" ? styles.bApproved : styles.bRejected}`}
                        >
                          {c.status === "active" ? "Ativa" : "Revogada"}
                        </span>
                        {c.rotated_at ? (
                          <>
                            <br />
                            <small>rotacionada {date(c.rotated_at)}</small>
                          </>
                        ) : null}
                      </td>
                      <td>
                        {date(c.last_used_at)}
                        <small>Validade: {date(c.expires_at)}</small>
                      </td>
                      <td>
                        {c.status === "active" ? (
                          <span className={styles.badges}>
                            <button
                              className={styles.ghost}
                              onClick={() => openEdit(c)}
                              disabled={busy}
                            >
                              Editar
                            </button>
                            <button
                              className={styles.warn}
                              onClick={() => void rotate(c)}
                              disabled={busy}
                            >
                              Rotacionar
                            </button>
                            <button
                              className={styles.danger}
                              onClick={() => void revoke(c)}
                              disabled={busy}
                            >
                              Revogar
                            </button>
                          </span>
                        ) : (
                          <small>{c.revoked_by || ""}</small>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!collectors.length ? (
                <div className={styles.empty}>Nenhum coletor cadastrado.</div>
              ) : null}
            </div>
          </section>
          <div className={styles.note}>
            A chave completa nunca é armazenada (somente o hash SHA-256). Chaves
            de coletor não acessam nenhuma rota administrativa.
          </div>
        </>
      ) : null}

      {tab === "runs" ? (
        <>
          <AdminImportBatches />
          <section className={styles.card}>
            <div className={styles.cardHead}>
              <h2>Execuções</h2>
              <span className={styles.badges}>
                <select
                  value={runsCollector}
                  onChange={(e) => setRunsCollector(e.target.value)}
                >
                  <option value="">Todos os coletores</option>
                  {collectors.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
                <button
                  className={styles.ghost}
                  onClick={() => void loadRuns()}
                  disabled={busy}
                >
                  Filtrar
                </button>
              </span>
            </div>
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Quando</th>
                    <th>Coletor</th>
                    <th>Fonte</th>
                    <th>run_id</th>
                    <th>Status</th>
                    <th>Recebidos</th>
                    <th>Aceitos</th>
                    <th>Aprovados atualmente</th>
                    <th>Rejeitados na revisão</th>
                    <th>Rejeitados</th>
                    <th>Expirados</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.map((r) => (
                    <tr key={r.id}>
                      <td>{date(r.created_at)}</td>
                      <td>{collectorName.get(r.collector_id) || "—"}</td>
                      <td>{r.source}</td>
                      <td className={styles.mono}>{r.run_id || "—"}</td>
                      <td>
                        {r.status}
                        {r.error_message ? ` · ${r.error_message}` : ""}
                      </td>
                      <td>{r.fetched_count}</td>
                      <td>{r.saved_count}</td>
                      <td>{r.approved_count ?? 0}</td>
                      <td>{r.rejected_count_review ?? 0}</td>
                      <td>{r.rejected_count}</td>
                      <td>{r.expired_count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!runs.length ? (
                <div className={styles.empty}>Nenhuma execução de coletor.</div>
              ) : null}
            </div>
          </section>
          <section className={styles.card}>
            <div className={styles.cardHead}>
              <h2>Uso (auditoria)</h2>
              <small>Sem payload e sem IP bruto.</small>
            </div>
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Quando</th>
                    <th>Coletor</th>
                    <th>Endpoint</th>
                    <th>HTTP</th>
                    <th>Recebidos</th>
                    <th>Aceitos</th>
                    <th>Aprovados atualmente</th>
                    <th>Rejeitados na revisão</th>
                    <th>Rejeitados</th>
                    <th>run_id</th>
                  </tr>
                </thead>
                <tbody>
                  {usage.map((u) => (
                    <tr key={u.id}>
                      <td>{date(u.at)}</td>
                      <td>{collectorName.get(u.collector_id) || "—"}</td>
                      <td className={styles.mono}>{u.endpoint}</td>
                      <td>{u.http_status}</td>
                      <td>{u.received}</td>
                      <td>{u.accepted}</td>
                      <td>{u.rejected}</td>
                      <td className={styles.mono}>{u.run_id || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!usage.length ? (
                <div className={styles.empty}>Nenhum uso registrado.</div>
              ) : null}
            </div>
          </section>
        </>
      ) : null}

      {selectedEvent ? (
        <div
          className={styles.backdrop}
          onMouseDown={(e) =>
            e.currentTarget === e.target && setSelectedEvent(null)
          }
        >
          <section className={styles.drawer}>
            <header>
              <div>
                <span>EVENTO · {selectedEvent.source}</span>
                <h2>{selectedEvent.name}</h2>
                <p>
                  {reviewLabel(selectedEvent.review_status)} · status{" "}
                  {selectedEvent.status}
                </p>
              </div>
              <button
                className={styles.ghost}
                onClick={() => setSelectedEvent(null)}
              >
                Fechar
              </button>
            </header>
            {selectedEvent.flags.length ? (
              <div className={styles.message}>
                {selectedEvent.flags.map((f) => (
                  <div key={f.code}>
                    ⚑ {f.label}
                    {f.related_ids?.length
                      ? ` (${f.related_ids.length} registro(s) semelhantes)`
                      : ""}
                  </div>
                ))}
              </div>
            ) : null}
            <div className={styles.detailGrid}>
              <div>
                <small>Local</small>
                <strong>{selectedEvent.venue_name || "—"}</strong>
              </div>
              <div>
                <small>Endereço</small>
                <strong>
                  {[
                    selectedEvent.address,
                    selectedEvent.city,
                    selectedEvent.state,
                  ]
                    .filter(Boolean)
                    .join(" · ") || "—"}
                </strong>
              </div>
              <div>
                <small>Mapa</small>
                <strong>
                  {selectedEvent.maps_url ? (
                    <a
                      href={selectedEvent.maps_url}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Abrir no Google Maps
                    </a>
                  ) : (
                    "—"
                  )}
                </strong>
              </div>
              <div>
                <small>Início</small>
                <strong title={selectedEvent.starts_at}>
                  {tzDate(selectedEvent.starts_at)}
                </strong>
              </div>
              <div>
                <small>Término</small>
                <strong title={selectedEvent.expected_end_at}>
                  {tzDate(selectedEvent.expected_end_at)} (
                  {selectedEvent.end_time_source === "source"
                    ? "fonte"
                    : "estimado"}
                  )
                </strong>
              </div>
              <div>
                <small>Janela de saída</small>
                <strong>
                  {tzDate(selectedEvent.egress_start_at)} →{" "}
                  {tzDate(selectedEvent.egress_end_at)}
                </strong>
              </div>
              <div>
                <small>Fonte</small>
                <strong>
                  {safeHttpUrl(selectedEvent.source_url) ? (
                    <a
                      href={safeHttpUrl(selectedEvent.source_url)!}
                      target="_blank"
                      rel="noopener noreferrer nofollow"
                    >
                      {selectedEvent.source}
                    </a>
                  ) : (
                    `${selectedEvent.source} (sem link)`
                  )}
                </strong>
              </div>
              <div>
                <small>ID externo</small>
                <strong className={styles.mono}>
                  {selectedEvent.external_id}
                </strong>
              </div>
              <div>
                <small>Tipo · confiança</small>
                <strong>
                  {selectedEvent.event_type} ·{" "}
                  {Number(selectedEvent.confidence).toFixed(2)}
                </strong>
              </div>
              <div>
                <small>Coletor</small>
                <strong>
                  {selectedEvent.collector_id
                    ? collectorName.get(selectedEvent.collector_id) ||
                      selectedEvent.collector_id
                    : "interno"}
                </strong>
              </div>
              <div>
                <small>Recebido</small>
                <strong>{date(selectedEvent.first_seen_at)}</strong>
              </div>
              <div>
                <small>run_id</small>
                <strong className={styles.mono}>
                  {selectedEvent.ingest_run_id || "—"}
                </strong>
              </div>
            </div>
            <section className={styles.decisionBox}>
              <h3>Decisão</h3>
              <button
                onClick={() =>
                  void run(async () =>
                    setHistory(
                      (
                        await api(
                          "/api/v1/admin/radar/events?id=" + selectedEvent.id,
                        )
                      ).history,
                    ),
                  )
                }
              >
                Carregar histórico de alterações
              </button>
              {history.map((h) => (
                <details key={h.id}>
                  <summary>
                    {date(h.created_at)} · versão {h.version} · {h.actor}
                  </summary>
                  <pre>{JSON.stringify(h.after_data, null, 2)}</pre>
                </details>
              ))}
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                maxLength={1000}
                placeholder="Motivo da decisão (obrigatório)…"
              />
              <div className={styles.actions}>
                <button
                  className={styles.primary}
                  onClick={() => void decideEvent("approve")}
                  disabled={busy}
                >
                  Aprovar
                </button>
                <button
                  className={styles.warn}
                  onClick={() => void decideEvent("request_changes")}
                  disabled={busy}
                >
                  Solicitar correção
                </button>
                <button
                  className={styles.warn}
                  onClick={() => void decideEvent("unpublish")}
                  disabled={busy}
                >
                  Despublicar
                </button>
                <button
                  className={styles.danger}
                  onClick={() => void decideEvent("reject")}
                  disabled={busy}
                >
                  Rejeitar
                </button>
              </div>
              {selectedEvent.reviewed_at ? (
                <small>
                  Última revisão: {date(selectedEvent.reviewed_at)} ·{" "}
                  {selectedEvent.reviewed_by}
                </small>
              ) : null}
            </section>
          </section>
        </div>
      ) : null}

      {selectedPoi ? (
        <div
          className={styles.backdrop}
          onMouseDown={(e) =>
            e.currentTarget === e.target && setSelectedPoi(null)
          }
        >
          <section className={styles.drawer}>
            <header>
              <div>
                <span>LUGAR · {selectedPoi.source}</span>
                <h2>{selectedPoi.canonical_name}</h2>
                <p>
                  {reviewLabel(selectedPoi.review)} · {selectedPoi.poi_type}
                </p>
              </div>
              <button
                className={styles.ghost}
                onClick={() => setSelectedPoi(null)}
              >
                Fechar
              </button>
            </header>
            <div className={styles.detailGrid}>
              <div>
                <small>Endereço</small>
                <strong>
                  {[selectedPoi.address, selectedPoi.city, selectedPoi.state]
                    .filter(Boolean)
                    .join(" · ") || "—"}
                </strong>
              </div>
              <div>
                <small>Mapa</small>
                <strong>
                  {selectedPoi.maps_url ? (
                    <a
                      href={selectedPoi.maps_url}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Abrir no Google Maps
                    </a>
                  ) : (
                    "—"
                  )}
                </strong>
              </div>
              <div>
                <small>Fonte</small>
                <strong>
                  {safeHttpUrl(selectedPoi.source_url) ? (
                    <a
                      href={safeHttpUrl(selectedPoi.source_url)!}
                      target="_blank"
                      rel="noopener noreferrer nofollow"
                    >
                      {selectedPoi.source}
                    </a>
                  ) : (
                    selectedPoi.source
                  )}
                </strong>
              </div>
            </div>
            <section className={styles.decisionBox}>
              <h3>Decisão</h3>
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                maxLength={1000}
                placeholder="Motivo da decisão (obrigatório)…"
              />
              <div className={styles.actions}>
                <button
                  className={styles.primary}
                  onClick={() => void decidePoi("approve")}
                  disabled={busy}
                >
                  Aprovar e publicar
                </button>
                <button
                  className={styles.warn}
                  onClick={() => void decidePoi("request_changes")}
                  disabled={busy}
                >
                  Solicitar correção
                </button>
                <button
                  className={styles.warn}
                  onClick={() => void decidePoi("unpublish")}
                  disabled={busy}
                >
                  Despublicar
                </button>
                <button
                  className={styles.danger}
                  onClick={() => void decidePoi("reject")}
                  disabled={busy}
                >
                  Rejeitar
                </button>
              </div>
            </section>
          </section>
        </div>
      ) : null}

      {editing ? (
        <div
          className={styles.backdrop}
          onMouseDown={(e) => e.currentTarget === e.target && setEditing(null)}
        >
          <section className={styles.drawer}>
            <header>
              <div>
                <span>COLETOR</span>
                <h2>{editing.name}</h2>
                <p className={styles.mono}>{editing.key_prefix}…</p>
              </div>
              <button className={styles.ghost} onClick={() => setEditing(null)}>
                Fechar
              </button>
            </header>
            <section className={styles.decisionBox}>
              <div className={styles.form}>
                <label>
                  Fontes permitidas
                  <input
                    value={editForm.sources}
                    onChange={(e) =>
                      setEditForm({ ...editForm, sources: e.target.value })
                    }
                  />
                </label>
                <label>
                  Escopos
                  <span className={styles.checks}>
                    {SCOPES.map((s) => (
                      <span key={s}>
                        <input
                          type="checkbox"
                          checked={editForm.scopes.includes(s)}
                          onChange={() =>
                            setEditForm({
                              ...editForm,
                              scopes: toggleScope(editForm.scopes, s),
                            })
                          }
                        />{" "}
                        {s}
                      </span>
                    ))}
                  </span>
                </label>
                <label>
                  Lote máx.
                  <input
                    type="number"
                    min={1}
                    max={500}
                    value={editForm.max_batch}
                    onChange={(e) =>
                      setEditForm({ ...editForm, max_batch: e.target.value })
                    }
                  />
                </label>
                <label>
                  Envios/hora
                  <input
                    type="number"
                    min={1}
                    max={1000}
                    value={editForm.rate_limit_per_hour}
                    onChange={(e) =>
                      setEditForm({
                        ...editForm,
                        rate_limit_per_hour: e.target.value,
                      })
                    }
                  />
                </label>
              </div>
              <div className={styles.actions}>
                <button
                  className={styles.primary}
                  onClick={() => void saveEdit()}
                  disabled={busy}
                >
                  Salvar
                </button>
              </div>
            </section>
          </section>
        </div>
      ) : null}
    </main>
  );
}
