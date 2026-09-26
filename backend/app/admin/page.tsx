"use client";

import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import styles from "./admin.module.css";

type Tab = "dashboard" | "users" | "radar" | "finance" | "costs" | "data" | "system";

type Viewer = {
  email: string;
  is_owner: boolean;
  allowed: boolean;
  is_driver: boolean;
  can_admin_ops?: boolean;
};

type DriverRow = {
  id: string;
  display_name: string;
  email: string | null;
  onboarding_completed: boolean;
  created_at: string;
  last_login_at: string | null;
  devices_total: number;
  devices_active: number;
  latest_device_seen_at: string | null;
  operational_state: "driving" | "journey_stale" | "online" | "recent" | "offline" | string;
  offers_24h: number;
  last_offer_at: string | null;
  latest_offer: any | null;
  current_journey: any | null;
  trial: any | null;
  subscription: any | null;
  latest_payment: any | null;
  wallet: any | null;
  active_entitlements: string[];
  active_web_sessions: number;
  active_mcp_tokens: number;
  ai_calls_30d: number;
  ai_tokens_30d: number;
  last_notification_status: string | null;
  last_notification_at: string | null;
};

type Overview = {
  generated_at: string;
  deployment: { environment: string | null; commit_sha: string | null };
  counts: Record<string, number>;
  outcomes: Record<string, number>;
  drivers: DriverRow[];
  recent_journeys: any[];
  alerts: any[];
  finance: { subscriptions: any[]; payments: any[]; entitlements: any[]; confirmed_revenue_cents: number };
  costs: { ai: any; infrastructure: any };
  v7: { canonical_batch: any | null; recent_batches: any[] };
  system: any;
};

type DriverDetail = {
  generated_at: string;
  driver: any;
  health: any;
  counts: Record<string, number>;
  devices: any[];
  journeys: any[];
  trial: any | null;
  subscriptions: any[];
  payments: any[];
  entitlements: any[];
  wallet: any | null;
  credit_transactions: any[];
  vehicle_metrics: any[];
  energy_entries: any[];
  offers: any[];
  ai_usage: any[];
  notifications: any[];
  web_sessions: any[];
  mcp_tokens: any[];
  support_actions: any;
  privacy: any;
};

const tabLabels: Array<{ id: Tab; label: string; hint: string }> = [
  { id: "dashboard", label: "Painel", hint: "Visão executiva" },
  { id: "users", label: "Usuários", hint: "Contas e suporte" },
  { id: "radar", label: "Operação / Radar", hint: "Motoristas em campo" },
  { id: "finance", label: "Financeiro", hint: "Planos e pagamentos" },
  { id: "costs", label: "Custos", hint: "IA e infraestrutura" },
  { id: "data", label: "Dados / V7", hint: "Base de inteligência" },
  { id: "system", label: "Sistema", hint: "Sessões e integrações" },
];

function formatDate(value?: string | null, withTime = true) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("pt-BR", withTime
    ? { dateStyle: "short", timeStyle: "short" }
    : { dateStyle: "short" });
}

function money(cents: number) {
  return (Number(cents || 0) / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function number(value: unknown, digits = 0) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return "—";
  return parsed.toLocaleString("pt-BR", { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

function pct(value: number, total: number) {
  if (!total) return "0%";
  return `${Math.round((value / total) * 1000) / 10}%`;
}

function stateLabel(state?: string | null) {
  const value = String(state || "offline");
  const labels: Record<string, string> = {
    driving: "Em jornada",
    journey_stale: "Jornada sem contato",
    online: "Online",
    recent: "Atividade recente",
    offline: "Offline",
  };
  return labels[value] || value;
}

function stateClass(state?: string | null) {
  const value = String(state || "offline");
  if (value === "driving") return styles.live;
  if (value === "journey_stale") return styles.danger;
  if (value === "online" || value === "recent") return styles.good;
  return styles.neutral;
}

function statusClass(status?: string | null) {
  const value = String(status || "").toLowerCase();
  if (["active", "paid", "confirmed", "ready", "completed", "sent", "ok"].includes(value)) return styles.good;
  if (["pending", "manual_review", "receiving", "processing"].includes(value)) return styles.warning;
  if (["failed", "cancelled", "expired", "invalid", "revoked"].includes(value)) return styles.danger;
  return styles.neutral;
}

async function api(path: string, init?: RequestInit) {
  const response = await fetch(path, { cache: "no-store", ...init });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data?.error || `HTTP ${response.status}`) as Error & { status?: number };
    error.status = response.status;
    throw error;
  }
  return data;
}

export default function AdminControlCenter() {
  const [viewer, setViewer] = useState<Viewer | null>(null);
  const [auth, setAuth] = useState<"loading" | "ready" | "denied" | "error">("loading");
  const [tab, setTab] = useState<Tab>("dashboard");
  const [overview, setOverview] = useState<Overview | null>(null);
  const [selectedDriverId, setSelectedDriverId] = useState("");
  const [selectedDriver, setSelectedDriver] = useState<DriverDetail | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const loadOverview = useCallback(async () => {
    setBusy(true);
    setMessage("");
    try {
      setOverview(await api("/api/v1/admin/ops") as Overview);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao carregar Admin.");
    } finally {
      setBusy(false);
    }
  }, []);

  const loadDriver = useCallback(async (driverId: string) => {
    setSelectedDriverId(driverId);
    setSelectedDriver(null);
    try {
      setSelectedDriver(await api(`/api/v1/admin/ops?view=driver&driver_id=${encodeURIComponent(driverId)}`) as DriverDetail);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao abrir usuário.");
    }
  }, []);

  useEffect(() => {
    let active = true;
    fetch("/api/v1/admin/imports/me", { cache: "no-store" })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!active) return;
        if (response.status === 401) {
          window.location.replace("/app/entrar?next=%2Fadmin");
          return;
        }
        if (!response.ok || !body?.can_admin_ops) {
          setViewer(body as Viewer);
          setAuth("denied");
          return;
        }
        setViewer(body as Viewer);
        setAuth("ready");
        await loadOverview();
      })
      .catch(() => active && setAuth("error"));
    return () => { active = false; };
  }, [loadOverview]);

  async function logout() {
    await fetch("/api/v1/admin/imports/logout", { method: "POST" }).catch(() => undefined);
    window.location.replace("/app/entrar");
  }

  async function runAction(payload: Record<string, unknown>, question: string) {
    if (!window.confirm(question)) return;
    setBusy(true);
    setMessage("");
    try {
      await api("/api/v1/admin/actions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      setMessage("Ação concluída e dados atualizados.");
      await Promise.all([loadOverview(), selectedDriverId ? loadDriver(selectedDriverId) : Promise.resolve()]);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível concluir a ação.");
    } finally {
      setBusy(false);
    }
  }

  if (auth === "loading") return <AccessState title="Validando acesso administrativo…" />;
  if (auth === "denied") return <AccessState title="Admin exclusivo BigCorps" detail={`A conta ${viewer?.email || "atual"} não está autorizada.`} action="Sair" onAction={logout} />;
  if (auth === "error") return <AccessState title="Não foi possível validar a sessão." detail="Entre novamente para continuar." action="Entrar" onAction={() => window.location.replace("/app/entrar?next=%2Fadmin")} />;

  return (
    <main className={styles.page}>
      <aside className={styles.sidebar}>
        <a href="/admin" className={styles.brand}>
          <img src="/logo-srrotas.png" alt="" />
          <div><strong>Sr. Rotas</strong><span>BigCorps Control Center</span></div>
        </a>
        <nav className={styles.nav} aria-label="Módulos administrativos">
          {tabLabels.map((item) => (
            <button key={item.id} className={tab === item.id ? styles.navActive : ""} onClick={() => setTab(item.id)}>
              <strong>{item.label}</strong><small>{item.hint}</small>
            </button>
          ))}
        </nav>
        <div className={styles.sidebarBottom}>
          <a href="/admin/importacoes">Importações V7</a>
          <small>Admin autorizado</small>
          <strong>{viewer?.email}</strong>
          <button onClick={logout}>Sair</button>
        </div>
      </aside>

      <section className={styles.main}>
        <header className={styles.topbar}>
          <div><span>ADMINISTRAÇÃO SR. ROTAS</span><h1>{tabLabels.find((item) => item.id === tab)?.label}</h1></div>
          <div className={styles.topActions}>
            <div><small>Produção</small><strong>{overview?.deployment?.commit_sha?.slice(0, 9) || "—"}</strong></div>
            <button onClick={() => void loadOverview()} disabled={busy}>{busy ? "Atualizando…" : "Atualizar dados"}</button>
          </div>
        </header>

        {message ? <div className={message.startsWith("Ação concluída") ? styles.success : styles.message}>{message}</div> : null}

        {tab === "dashboard" ? <Dashboard overview={overview} onOpenUser={(id) => void loadDriver(id)} /> : null}
        {tab === "users" ? <Users overview={overview} onOpenUser={(id) => void loadDriver(id)} /> : null}
        {tab === "radar" ? <Radar overview={overview} onOpenUser={(id) => void loadDriver(id)} /> : null}
        {tab === "finance" ? <Finance overview={overview} /> : null}
        {tab === "costs" ? <Costs overview={overview} /> : null}
        {tab === "data" ? <DataV7 overview={overview} /> : null}
        {tab === "system" ? <System overview={overview} /> : null}

        <footer className={styles.footer}>
          <span>Atualizado em {formatDate(overview?.generated_at)}</span>
          <b>Admin BigCorps · sem raw OCR · sem coordenadas · ações sensíveis confirmadas</b>
        </footer>
      </section>

      {selectedDriverId ? (
        <div className={styles.drawerBackdrop} onMouseDown={(event) => { if (event.target === event.currentTarget) setSelectedDriverId(""); }}>
          <section className={styles.drawer}>
            <div className={styles.drawerHead}>
              <div><span>GESTÃO DE USUÁRIO</span><h2>{selectedDriver?.driver?.display_name || "Carregando…"}</h2><p>{selectedDriver?.driver?.email || ""}</p></div>
              <button onClick={() => { setSelectedDriverId(""); setSelectedDriver(null); }}>Fechar</button>
            </div>
            {selectedDriver ? (
              <DriverPanel
                detail={selectedDriver}
                busy={busy}
                runAction={runAction}
              />
            ) : <div className={styles.loading}>Carregando dados completos do usuário…</div>}
          </section>
        </div>
      ) : null}
    </main>
  );
}

function AccessState({ title, detail, action, onAction }: { title: string; detail?: string; action?: string; onAction?: () => void }) {
  return <main className={styles.accessPage}><section><img src="/logo-srrotas.png" alt="" /><span>BIGCORPS · SR. ROTAS</span><h1>{title}</h1>{detail ? <p>{detail}</p> : null}{action && onAction ? <button onClick={onAction}>{action}</button> : null}</section></main>;
}

function Metric({ label, value, note, tone }: { label: string; value: string | number; note?: string; tone?: string }) {
  return <article className={`${styles.metric} ${tone || ""}`}><span>{label}</span><strong>{value}</strong>{note ? <small>{note}</small> : null}</article>;
}

function SectionTitle({ eyebrow, title, aside }: { eyebrow: string; title: string; aside?: ReactNode }) {
  return <div className={styles.sectionTitle}><div><span>{eyebrow}</span><h2>{title}</h2></div>{aside}</div>;
}

function Dashboard({ overview, onOpenUser }: { overview: Overview | null; onOpenUser: (id: string) => void }) {
  const c = overview?.counts || {};
  const attention = (overview?.alerts || []).slice(0, 8);
  const activeDrivers = (overview?.drivers || []).filter((driver) => ["driving", "online", "journey_stale"].includes(driver.operational_state)).slice(0, 8);
  return <>
    <section className={styles.metrics}>
      <Metric label="Usuários" value={c.drivers ?? "—"} note={`${c.drivers_online ?? 0} online agora`} />
      <Metric label="Em jornada" value={c.drivers_driving ?? "—"} note={`${c.open_journeys ?? 0} jornadas abertas`} tone={styles.liveMetric} />
      <Metric label="Ofertas · 24h" value={number(c.offers_24h)} note={`${number(c.offers)} no total`} />
      <Metric label="Assinaturas" value={c.active_subscriptions ?? "—"} note={`${c.trials ?? 0} trials registrados`} />
      <Metric label="Receita confirmada" value={money(overview?.finance?.confirmed_revenue_cents || 0)} note={`${c.payments ?? 0} pagamentos`} />
      <Metric label="Atenções" value={c.admin_alerts ?? 0} note="operação, pagamento e integrações" tone={(c.admin_alerts ?? 0) ? styles.alertMetric : ""} />
    </section>

    <div className={styles.twoCols}>
      <article className={styles.card}>
        <SectionTitle eyebrow="OPERAÇÃO AGORA" title="Motoristas ativos" aside={<span className={styles.pill}>{c.drivers_online ?? 0} online</span>} />
        <div className={styles.rows}>
          {activeDrivers.map((driver) => <button className={styles.rowButton} key={driver.id} onClick={() => onOpenUser(driver.id)}>
            <div className={`${styles.statusDot} ${stateClass(driver.operational_state)}`} />
            <div className={styles.rowMain}><strong>{driver.display_name}</strong><small>{driver.email || "sem e-mail"}</small></div>
            <div className={styles.rowStat}><span>{stateLabel(driver.operational_state)}</span><small>{driver.offers_24h} ofertas · 24h</small></div>
          </button>)}
          {!activeDrivers.length ? <Empty text="Nenhum motorista ativo neste momento." /> : null}
        </div>
      </article>

      <article className={styles.card}>
        <SectionTitle eyebrow="CENTRAL DE ATENÇÃO" title="O que precisa ser visto" aside={<span className={styles.pill}>{attention.length}</span>} />
        <div className={styles.rows}>
          {attention.map((alert, index) => <button className={styles.alertRow} key={`${alert.kind}-${alert.at}-${index}`} onClick={() => alert.driver_id && onOpenUser(alert.driver_id)}>
            <span className={`${styles.alertMark} ${alert.severity === "critical" ? styles.danger : styles.warning}`} />
            <div><strong>{alert.title}</strong><small>{alert.driver_name} · {alert.detail}</small></div>
            <time>{formatDate(alert.at)}</time>
          </button>)}
          {!attention.length ? <Empty text="Nenhuma atenção operacional registrada." /> : null}
        </div>
      </article>
    </div>

    <div className={styles.threeCols}>
      <article className={styles.card}><SectionTitle eyebrow="CAPTURA" title="Radar operacional" /><div className={styles.bigNumber}>{number(c.offers_24h)}</div><p className={styles.note}>Ofertas reais recebidas nas últimas 24 horas. O Admin monitora atividade; não replica o HUD do motorista.</p></article>
      <article className={styles.card}><SectionTitle eyebrow="IA" title="Uso em 30 dias" /><div className={styles.bigNumber}>{number(c.ai_tokens_30d)}</div><p className={styles.note}>{c.ai_calls_30d ?? 0} chamadas · {c.ai_failures_30d ?? 0} falhas. A inteligência determinística do app não precisa chamar IA.</p></article>
      <article className={styles.card}><SectionTitle eyebrow="CANAIS" title="Acessos ativos" /><div className={styles.splitStats}><div><strong>{c.active_web_sessions ?? 0}</strong><span>Web</span></div><div><strong>{c.active_mcp_tokens ?? 0}</strong><span>MCP</span></div><div><strong>{c.notification_failures_24h ?? 0}</strong><span>Falhas push</span></div></div></article>
    </div>
  </>;
}

function Users({ overview, onOpenUser }: { overview: Overview | null; onOpenUser: (id: string) => void }) {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (overview?.drivers || []).filter((driver) => {
      const matchesText = !term || `${driver.display_name} ${driver.email || ""}`.toLowerCase().includes(term);
      const matchesFilter = filter === "all" || driver.operational_state === filter || (filter === "subscribed" && driver.subscription?.status === "active") || (filter === "trial" && driver.trial && driver.subscription?.status !== "active");
      return matchesText && matchesFilter;
    });
  }, [overview, search, filter]);

  return <article className={styles.card}>
    <SectionTitle eyebrow="GESTÃO DE CONTAS" title="Usuários do Sr. Rotas" aside={<span className={styles.pill}>{rows.length} exibidos</span>} />
    <div className={styles.filters}>
      <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nome ou e-mail" />
      <select value={filter} onChange={(event) => setFilter(event.target.value)}>
        <option value="all">Todos</option><option value="driving">Em jornada</option><option value="online">Online</option><option value="offline">Offline</option><option value="trial">Trial</option><option value="subscribed">Assinantes</option>
      </select>
    </div>
    <div className={styles.tableWrap}><table><thead><tr><th>Usuário</th><th>Operação</th><th>Último contato</th><th>Ofertas 24h</th><th>Aparelhos</th><th>Plano</th><th>Créditos</th><th>Acessos</th><th></th></tr></thead><tbody>
      {rows.map((driver) => <tr key={driver.id}>
        <td><strong>{driver.display_name}</strong><small>{driver.email || "sem e-mail"}</small></td>
        <td><span className={`${styles.badge} ${stateClass(driver.operational_state)}`}>{stateLabel(driver.operational_state)}</span></td>
        <td>{formatDate(driver.latest_device_seen_at)}</td>
        <td>{driver.offers_24h}</td>
        <td>{driver.devices_active}/{driver.devices_total}<small>ativos / total</small></td>
        <td>{driver.subscription?.status === "active" ? <span className={`${styles.badge} ${styles.good}`}>Assinante</span> : driver.trial ? <span className={`${styles.badge} ${styles.warning}`}>Trial</span> : "—"}</td>
        <td>{driver.wallet?.balance ?? 0}</td>
        <td>{driver.active_web_sessions} Web · {driver.active_mcp_tokens} MCP</td>
        <td><button className={styles.openButton} onClick={() => onOpenUser(driver.id)}>Gerenciar</button></td>
      </tr>)}
    </tbody></table></div>
  </article>;
}

function Radar({ overview, onOpenUser }: { overview: Overview | null; onOpenUser: (id: string) => void }) {
  const rows = [...(overview?.drivers || [])].sort((a, b) => {
    const order: Record<string, number> = { journey_stale: 0, driving: 1, online: 2, recent: 3, offline: 4 };
    return (order[a.operational_state] ?? 9) - (order[b.operational_state] ?? 9);
  });
  return <>
    <section className={styles.radarIntro}>
      <div><span>OPERAÇÃO / RADAR</span><h2>Quem está em campo e se a captura está chegando.</h2><p>Este radar administrativo mede contato do aparelho, jornada aberta e ofertas sincronizadas. Ele não tenta reproduzir a tela flutuante do Android.</p></div>
      <div className={styles.radarLegend}><span><i className={`${styles.statusDot} ${styles.live}`} /> Em jornada</span><span><i className={`${styles.statusDot} ${styles.good}`} /> Online</span><span><i className={`${styles.statusDot} ${styles.danger}`} /> Atenção</span></div>
    </section>
    <div className={styles.radarGrid}>
      {rows.map((driver) => <button className={styles.radarCard} key={driver.id} onClick={() => onOpenUser(driver.id)}>
        <div className={styles.radarCardHead}><div className={`${styles.statusDot} ${stateClass(driver.operational_state)}`} /><span className={`${styles.badge} ${stateClass(driver.operational_state)}`}>{stateLabel(driver.operational_state)}</span></div>
        <h3>{driver.display_name}</h3><small>{driver.email || "sem e-mail"}</small>
        <div className={styles.radarNumbers}><div><strong>{driver.offers_24h}</strong><span>ofertas 24h</span></div><div><strong>{driver.devices_active}</strong><span>aparelhos</span></div></div>
        <dl><div><dt>Último aparelho</dt><dd>{formatDate(driver.latest_device_seen_at)}</dd></div><div><dt>Última oferta</dt><dd>{formatDate(driver.last_offer_at)}</dd></div><div><dt>Plataforma</dt><dd>{driver.latest_offer?.platform || driver.current_journey?.platform || "—"}</dd></div><div><dt>Parser</dt><dd>{driver.latest_offer?.parser_version || "—"}</dd></div></dl>
      </button>)}
    </div>
  </>;
}

function Finance({ overview }: { overview: Overview | null }) {
  const c = overview?.counts || {};
  return <>
    <section className={styles.metrics}>
      <Metric label="Receita confirmada" value={money(overview?.finance?.confirmed_revenue_cents || 0)} />
      <Metric label="Assinaturas ativas" value={c.active_subscriptions ?? 0} note={`${c.subscriptions ?? 0} registros`} />
      <Metric label="Pagamentos" value={c.payments ?? 0} />
      <Metric label="Trials" value={c.trials ?? 0} />
      <Metric label="Entitlements" value={c.entitlements ?? 0} />
      <Metric label="Plano base" value="R$ 9,90" note="mensal" />
    </section>
    <article className={styles.card}>
      <SectionTitle eyebrow="MOVIMENTAÇÕES" title="Pagamentos recentes" />
      <div className={styles.tableWrap}><table><thead><tr><th>Data</th><th>Usuário</th><th>Tipo</th><th>Provedor</th><th>Valor</th><th>Status</th><th>Banco</th><th>Erro</th></tr></thead><tbody>
        {(overview?.finance?.payments || []).map((row: any) => <tr key={row.id}><td>{formatDate(row.created_at)}</td><td><code>{String(row.driver_id || "").slice(0, 8)}</code></td><td>{row.kind}</td><td>{row.provider}</td><td>{money(row.amount_cents)}</td><td><span className={`${styles.badge} ${statusClass(row.status)}`}>{row.status}</span></td><td>{row.bank_status || "—"}</td><td>{row.error_code || "—"}</td></tr>)}
      </tbody></table></div>
    </article>
  </>;
}

function Costs({ overview }: { overview: Overview | null }) {
  const ai = overview?.costs?.ai || {};
  return <>
    <section className={styles.metrics}>
      <Metric label="Chamadas IA · 30d" value={number(ai.calls)} />
      <Metric label="Tokens totais · 30d" value={number(ai.total_tokens)} />
      <Metric label="Entrada" value={number(ai.input_tokens)} />
      <Metric label="Saída" value={number(ai.output_tokens)} />
      <Metric label="Falhas IA" value={number(ai.failures)} />
      <Metric label="Custo monetário IA" value="Não calculado" note="preço por modelo não é persistido" />
    </section>
    <div className={styles.twoCols}>
      <article className={styles.card}><SectionTitle eyebrow="IA" title="Consumo por modelo" /><div className={styles.tableWrap}><table><thead><tr><th>Modelo</th><th>Chamadas</th><th>Entrada</th><th>Saída</th><th>Total</th><th>Falhas</th></tr></thead><tbody>{(ai.models || []).map((row: any) => <tr key={row.model}><td><strong>{row.model}</strong></td><td>{number(row.calls)}</td><td>{number(row.input_tokens)}</td><td>{number(row.output_tokens)}</td><td>{number(row.total_tokens)}</td><td>{number(row.failures)}</td></tr>)}</tbody></table></div><p className={styles.note}>{ai.note}</p></article>
      <article className={styles.card}><SectionTitle eyebrow="INFRAESTRUTURA" title="Vercel e Supabase" /><div className={styles.providerCards}><div><strong>Vercel</strong><span>Billing externo</span><p>{overview?.costs?.infrastructure?.vercel?.note}</p></div><div><strong>Supabase</strong><span>Billing externo</span><p>{overview?.costs?.infrastructure?.supabase?.note}</p></div></div><div className={styles.infoBox}>O Admin mostra apenas custos que o backend consegue provar. Para trazer valores em R$ da Vercel e do Supabase para dentro desta tela será necessário integrar os billing/usage APIs dos provedores com credenciais administrativas separadas.</div></article>
    </div>
  </>;
}

function DataV7({ overview }: { overview: Overview | null }) {
  const batch = overview?.v7?.canonical_batch;
  return <>
    <div className={styles.twoCols}>
      <article className={styles.card}><SectionTitle eyebrow="BASE CANÔNICA" title={batch ? (batch.extractor_version || batch.schema_version) : "Nenhum lote V7 canônico"} aside={batch ? <span className={`${styles.badge} ${statusClass(batch.status)}`}>{batch.status}</span> : null} />
        {batch ? <div className={styles.qualityGrid}><div><span>Recebidos</span><strong>{number(batch.received_count)}</strong></div><div><span>Temporal</span><strong>{number(batch.demand_temporal_ready_count)}</strong><small>{pct(batch.demand_temporal_ready_count, batch.received_count)}</small></div><div><span>Rota</span><strong>{number(batch.route_flow_ready_count)}</strong><small>{pct(batch.route_flow_ready_count, batch.received_count)}</small></div><div><span>Financeiro</span><strong>{number(batch.financial_ready_count)}</strong><small>{pct(batch.financial_ready_count, batch.received_count)}</small></div><div><span>Completo</span><strong>{number(batch.fully_ready_count)}</strong><small>{pct(batch.fully_ready_count, batch.received_count)}</small></div><div><span>Parciais</span><strong>{number(batch.partial_count)}</strong></div></div> : <Empty text="Sem lote canônico." />}
      </article>
      <article className={styles.card}><SectionTitle eyebrow="CONTRATO DE DADOS" title="O que cada base significa" /><div className={styles.rules}><div><strong>ride_offers</strong><span>ofertas operacionais reais</span></div><div><strong>V7</strong><span>ofertas históricas observadas</span></div><div><strong>outcomes</strong><span>decisão/corrida operacional</span></div><div><strong>Admin</strong><span>não expõe raw OCR ou coordenadas</span></div></div><a className={styles.primaryLink} href="/admin/importacoes">Abrir ferramenta de importação V7</a></article>
    </div>
    <article className={styles.card}><SectionTitle eyebrow="IMPORTAÇÕES" title="Lotes recentes" /><div className={styles.tableWrap}><table><thead><tr><th>Arquivo</th><th>Status</th><th>Extrator</th><th>Recebidos</th><th>Completos</th><th>Parciais</th><th>Finalizado</th></tr></thead><tbody>{(overview?.v7?.recent_batches || []).map((row: any) => <tr key={row.id}><td><strong>{row.original_filename}</strong><small>{row.source_name}</small></td><td><span className={`${styles.badge} ${statusClass(row.status)}`}>{row.status}</span></td><td>{row.extractor_version || row.schema_version}</td><td>{number(row.received_count)}</td><td>{number(row.fully_ready_count)}</td><td>{number(row.partial_count)}</td><td>{formatDate(row.finalized_at)}</td></tr>)}</tbody></table></div></article>
  </>;
}

function System({ overview }: { overview: Overview | null }) {
  const c = overview?.counts || {};
  return <>
    <section className={styles.metrics}>
      <Metric label="Banco" value={overview?.system?.database || "—"} />
      <Metric label="Sessões Web" value={c.active_web_sessions ?? 0} />
      <Metric label="Tokens MCP" value={c.active_mcp_tokens ?? 0} />
      <Metric label="Notificações · 24h" value={c.notifications_24h ?? 0} />
      <Metric label="Falhas push" value={c.notification_failures_24h ?? 0} />
      <Metric label="Métricas de veículo" value={c.vehicle_metrics ?? 0} note={`${c.energy_entries ?? 0} energia`} />
    </section>
    <div className={styles.twoCols}>
      <article className={styles.card}><SectionTitle eyebrow="PRIVACIDADE E SEGURANÇA" title="Contrato do Admin" /><div className={styles.rules}><div><strong>Raw OCR</strong><span>não exposto</span></div><div><strong>Coordenadas</strong><span>não expostas</span></div><div><strong>Excluir conta</strong><span>fora das ações administrativas rápidas</span></div><div><strong>Ações suporte</strong><span>confirmação + rate limit + log runtime</span></div></div></article>
      <article className={styles.card}><SectionTitle eyebrow="DEPLOY" title="Produção" /><div className={styles.rules}><div><strong>Ambiente</strong><span>{overview?.deployment?.environment || "—"}</span></div><div><strong>Commit</strong><span><code>{overview?.deployment?.commit_sha || "—"}</code></span></div><div><strong>Modo</strong><span>{overview?.system?.admin_mode || "—"}</span></div></div></article>
    </div>
    <article className={styles.card}><SectionTitle eyebrow="NOTIFICAÇÕES" title="Últimas entregas" /><div className={styles.tableWrap}><table><thead><tr><th>Data</th><th>Usuário</th><th>Categoria</th><th>Status</th><th>Erro</th></tr></thead><tbody>{(overview?.system?.notifications_24h || []).map((row: any) => <tr key={row.id}><td>{formatDate(row.created_at)}</td><td><code>{String(row.driver_id || "").slice(0, 8)}</code></td><td>{row.category}</td><td><span className={`${styles.badge} ${statusClass(row.status)}`}>{row.status}</span></td><td>{row.error_code || "—"}</td></tr>)}</tbody></table></div></article>
  </>;
}

function DriverPanel({ detail, busy, runAction }: { detail: DriverDetail; busy: boolean; runAction: (payload: Record<string, unknown>, question: string) => Promise<void> }) {
  const driverId = detail.driver.id;
  const latestSubscription = detail.subscriptions?.[0] || null;
  return <div className={styles.drawerBody}>
    <section className={styles.userStatusBar}>
      <span className={`${styles.badge} ${stateClass(detail.health?.operational_state)}`}>{stateLabel(detail.health?.operational_state)}</span>
      <div><small>Último aparelho</small><strong>{formatDate(detail.health?.latest_device_seen_at)}</strong></div>
      <div><small>Última oferta</small><strong>{formatDate(detail.health?.last_offer_at)}</strong></div>
      <div><small>Ofertas 24h</small><strong>{detail.counts.offers_24h ?? 0}</strong></div>
    </section>

    <div className={styles.drawerGrid}>
      <article className={styles.miniCard}><SectionTitle eyebrow="CONTA" title="Cadastro" /><KeyRows rows={[["E-mail", detail.driver.email || "—"],["Criado", formatDate(detail.driver.created_at)],["Último login", formatDate(detail.driver.last_login_at)],["Onboarding", detail.driver.onboarding_completed ? "Completo" : "Pendente"]]} /></article>
      <article className={styles.miniCard}><SectionTitle eyebrow="PLANO" title="Assinatura e créditos" /><KeyRows rows={[["Status", latestSubscription?.status || (detail.trial ? "trial" : "sem plano")],["Válido até", formatDate(latestSubscription?.current_period_end)],["Saldo IA", String(detail.wallet?.balance ?? 0)],["Recebidos / usados", `${detail.wallet?.lifetime_granted ?? 0} / ${detail.wallet?.lifetime_spent ?? 0}`]]} /></article>
    </div>

    <article className={styles.miniCard}><SectionTitle eyebrow="SUPORTE" title="Ações administrativas seguras" /><div className={styles.actionGrid}>
      <div><strong>Trial</strong><p>Estender um trial já iniciado sem alterar sua data de início.</p><div className={styles.actionButtons}>{[1,7,30].map((days) => <button key={days} disabled={busy || !detail.trial?.trial_started_at} onClick={() => void runAction({ action: "trial_extend", driver_id: driverId, days, confirmation: "ESTENDER_TRIAL" }, `Estender o trial de ${detail.driver.display_name} em ${days} dia(s)?`)}>+{days}d</button>)}</div></div>
      <div><strong>Sessões Web</strong><p>{detail.counts.active_web_sessions ?? 0} sessão(ões) ativa(s).</p><button className={styles.dangerButton} disabled={busy || !(detail.counts.active_web_sessions > 0)} onClick={() => void runAction({ action: "revoke_web_sessions", driver_id: driverId, confirmation: "ENCERRAR_SESSOES_WEB" }, `Encerrar todas as sessões Web de ${detail.driver.display_name}?`)}>Encerrar sessões</button></div>
      <div><strong>MCP</strong><p>{detail.counts.active_mcp_tokens ?? 0} token(s) ativo(s).</p><button className={styles.dangerButton} disabled={busy || !(detail.counts.active_mcp_tokens > 0)} onClick={() => void runAction({ action: "revoke_mcp_tokens", driver_id: driverId, confirmation: "REVOGAR_MCP" }, `Revogar todos os tokens MCP de ${detail.driver.display_name}?`)}>Revogar MCP</button></div>
      <div><strong>Créditos / plano</strong><p>Visíveis para suporte. Alteração direta permanece bloqueada até existir rotina financeira atômica com auditoria.</p><button disabled>Alteração protegida</button></div>
    </div></article>

    <article className={styles.miniCard}><SectionTitle eyebrow="APARELHOS" title="Dispositivos cadastrados" /><div className={styles.deviceList}>{detail.devices.map((device: any) => <div key={device.id}><div><span className={`${styles.badge} ${device.revoked ? styles.danger : styles.good}`}>{device.revoked ? "Revogado" : "Ativo"}</span><strong>{device.name || "Aparelho"}</strong><small>Último contato: {formatDate(device.last_seen_at)}</small></div><button className={device.revoked ? "" : styles.dangerButton} disabled={busy} onClick={() => void runAction({ action: "device_set_revoked", driver_id: driverId, device_id: device.id, revoked: !device.revoked, confirmation: device.revoked ? "REATIVAR_APARELHO" : "REVOGAR_APARELHO" }, `${device.revoked ? "Reativar" : "Revogar"} o aparelho ${device.name || device.id}?`)}>{device.revoked ? "Reativar" : "Revogar"}</button></div>)}</div></article>

    <div className={styles.drawerGrid}>
      <article className={styles.miniCard}><SectionTitle eyebrow="RADAR" title="Últimas ofertas" /><div className={styles.compactList}>{detail.offers.slice(0, 12).map((offer: any) => <div key={offer.id}><div><strong>{offer.platform || "oferta"} · {offer.service_type || "—"}</strong><small>{formatDate(offer.observed_at)} · {offer.parser_version || "parser —"}</small></div><div><b>{offer.per_km != null ? `R$ ${number(offer.per_km, 2)}/km` : "—"}</b><span className={`${styles.badge} ${statusClass(offer.verdict)}`}>{offer.verdict || "—"}</span></div></div>)}</div></article>
      <article className={styles.miniCard}><SectionTitle eyebrow="JORNADAS" title="Atividade recente" /><div className={styles.compactList}>{detail.journeys.slice(0, 12).map((journey: any) => <div key={journey.id}><div><strong>{journey.platform}</strong><small>{formatDate(journey.started_at)}</small></div><span className={`${styles.badge} ${journey.ended_at ? styles.neutral : styles.live}`}>{journey.ended_at ? "Encerrada" : "Aberta"}</span></div>)}</div></article>
    </div>

    <div className={styles.drawerGrid}>
      <article className={styles.miniCard}><SectionTitle eyebrow="IA" title="Uso em 30 dias" /><KeyRows rows={[["Chamadas", String(detail.counts.ai_calls_30d ?? 0)],["Tokens", number(detail.counts.ai_tokens_30d)],["Último modelo", detail.ai_usage?.[0]?.model || "—"],["Último status", detail.ai_usage?.[0]?.status || "—"]]} /></article>
      <article className={styles.miniCard}><SectionTitle eyebrow="SINCRONIZAÇÃO" title="Dados de jornada" /><KeyRows rows={[["Métricas veículo", String(detail.vehicle_metrics.length)],["Entradas energia", String(detail.energy_entries.length)],["Outcomes", String(detail.counts.outcomes ?? 0)],["Concluídas", String(detail.counts.completed ?? 0)]]} /></article>
    </div>

    <article className={styles.miniCard}><SectionTitle eyebrow="FINANCEIRO" title="Pagamentos do usuário" /><div className={styles.tableWrap}><table><thead><tr><th>Data</th><th>Tipo</th><th>Valor</th><th>Status</th><th>Banco</th><th>Erro</th></tr></thead><tbody>{detail.payments.map((row: any) => <tr key={row.id}><td>{formatDate(row.created_at)}</td><td>{row.kind}</td><td>{money(row.amount_cents)}</td><td><span className={`${styles.badge} ${statusClass(row.status)}`}>{row.status}</span></td><td>{row.bank_status || "—"}</td><td>{row.error_code || "—"}</td></tr>)}</tbody></table></div></article>
  </div>;
}

function KeyRows({ rows }: { rows: Array<[string, string]> }) {
  return <div className={styles.keyRows}>{rows.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>;
}

function Empty({ text }: { text: string }) {
  return <div className={styles.empty}>{text}</div>;
}
