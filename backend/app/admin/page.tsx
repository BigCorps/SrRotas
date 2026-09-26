"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import styles from "./admin.module.css";

type Tab = "overview" | "drivers" | "data" | "finance" | "system";

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
  recent_journeys_loaded: number;
  current_journey: null | { id: string; state: string; platform: string; started_at: string };
  trial: null | { trial_started_at: string; trial_ends_at: string; ai_credits_granted: number };
  subscription: null | { id: string; plan_id: string; status: string; current_period_end: string | null };
  wallet: null | { balance: number; lifetime_granted: number; lifetime_spent: number };
  active_entitlements: string[];
};

type Batch = {
  id: string;
  status: string;
  original_filename: string;
  source_name: string;
  schema_version: string;
  extractor_version: string | null;
  received_count: number;
  valid_count: number;
  partial_count: number;
  invalid_count: number;
  duplicate_count: number;
  demand_temporal_ready_count: number;
  route_flow_ready_count: number;
  financial_ready_count: number;
  fully_ready_count: number;
  created_at: string;
  finalized_at: string | null;
  created_by_driver_id: string | null;
  canonical_for_intelligence: boolean;
  canonical_owner_resolved: boolean;
  canonicalized_at: string | null;
};

type Overview = {
  generated_at: string;
  deployment: { environment: string | null; commit_sha: string | null };
  counts: Record<string, number>;
  outcomes: Record<string, number>;
  drivers: DriverRow[];
  recent_journeys: Array<{
    id: string;
    driver_id: string;
    platform: string;
    state: string;
    started_at: string;
    ended_at: string | null;
    end_reason: string | null;
  }>;
  finance: {
    subscriptions: Array<Record<string, any>>;
    payments: Array<Record<string, any>>;
    entitlements: Array<Record<string, any>>;
    confirmed_revenue_cents: number;
  };
  v7: { canonical_batch: Batch | null; recent_batches: Batch[] };
  system: {
    database: string;
    admin_mode: string;
    sensitive_offer_text_exposed: boolean;
    coordinates_exposed: boolean;
    self_service_account_deletion: boolean;
  };
};

type DriverDetail = {
  generated_at: string;
  driver: {
    id: string;
    display_name: string;
    email: string | null;
    onboarding_completed: boolean;
    created_at: string;
    last_login_at: string | null;
  };
  counts: Record<string, number>;
  devices: Array<Record<string, any>>;
  journeys: Array<Record<string, any>>;
  trial: Record<string, any> | null;
  subscriptions: Array<Record<string, any>>;
  payments: Array<Record<string, any>>;
  entitlements: Array<Record<string, any>>;
  wallet: Record<string, any> | null;
  vehicle_metrics: Array<Record<string, any>>;
  energy_entries: Array<Record<string, any>>;
  privacy: Record<string, any>;
};

type Viewer = {
  email: string;
  is_owner: boolean;
  allowed: boolean;
  is_driver: boolean;
  can_admin_ops?: boolean;
};

function formatDate(value?: string | null, withTime = true) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("pt-BR", withTime
    ? { dateStyle: "short", timeStyle: "short" }
    : { dateStyle: "short" });
}

function money(cents: number) {
  return (Number(cents || 0) / 100).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
}

function pct(value: number, total: number) {
  if (!total) return "0%";
  return `${Math.round((value / total) * 1000) / 10}%`;
}

function statusTone(status?: string | null) {
  const value = String(status || "").toLowerCase();
  if (["active", "ready", "confirmed", "completed", "ok"].includes(value)) return styles.good;
  if (["pending", "paused", "receiving"].includes(value)) return styles.warn;
  if (["failed", "cancelled", "expired", "invalid"].includes(value)) return styles.bad;
  return styles.neutral;
}

async function api(path: string) {
  const response = await fetch(path, { cache: "no-store" });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data?.error || `HTTP ${response.status}`) as Error & { status?: number };
    error.status = response.status;
    throw error;
  }
  return data;
}

export default function AdminOpsPage() {
  const [viewer, setViewer] = useState<Viewer | null>(null);
  const [auth, setAuth] = useState<"loading" | "ready" | "denied" | "error">("loading");
  const [tab, setTab] = useState<Tab>("overview");
  const [overview, setOverview] = useState<Overview | null>(null);
  const [selectedDriver, setSelectedDriver] = useState<DriverDetail | null>(null);
  const [selectedDriverId, setSelectedDriverId] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const loadOverview = useCallback(async () => {
    setBusy(true);
    setMessage("");
    try {
      const data = await api("/api/v1/admin/ops");
      setOverview(data as Overview);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao carregar Admin.");
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
      .catch(() => {
        if (active) setAuth("error");
      });
    return () => { active = false; };
  }, [loadOverview]);

  async function openDriver(driverId: string) {
    setSelectedDriverId(driverId);
    setSelectedDriver(null);
    setBusy(true);
    try {
      const data = await api(`/api/v1/admin/ops?view=driver&driver_id=${encodeURIComponent(driverId)}`);
      setSelectedDriver(data as DriverDetail);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao abrir motorista.");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    await fetch("/api/v1/admin/imports/logout", { method: "POST" }).catch(() => undefined);
    window.location.replace("/app/entrar");
  }

  const driverName = useMemo(() => {
    if (!selectedDriverId) return "";
    return overview?.drivers.find((row) => row.id === selectedDriverId)?.display_name || "";
  }, [overview, selectedDriverId]);

  if (auth === "loading") {
    return <main className={styles.page}><div className={styles.center}><img src="/logo-srrotas.png" alt="" /><strong>Validando acesso administrativo…</strong></div></main>;
  }

  if (auth === "denied") {
    return <main className={styles.page}><div className={styles.center}><img src="/logo-srrotas.png" alt="" /><span>ACESSO RESTRITO</span><h1>Admin exclusivo BigCorps</h1><p>A conta {viewer?.email || "atual"} não está na lista de administradores completos.</p><button onClick={logout}>Sair</button></div></main>;
  }

  if (auth === "error") {
    return <main className={styles.page}><div className={styles.center}><img src="/logo-srrotas.png" alt="" /><h1>Não foi possível validar a sessão.</h1><button onClick={() => window.location.replace("/app/entrar?next=%2Fadmin")}>Entrar novamente</button></div></main>;
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <a href="/admin" className={styles.brand}>
          <img src="/logo-srrotas.png" alt="" />
          <div><span>BIGCORPS · SR. ROTAS</span><strong>Admin Operacional</strong></div>
        </a>
        <div className={styles.headerActions}>
          <small>{viewer?.email}</small>
          <a href="/admin/importacoes">Importações V7</a>
          <button disabled={busy} onClick={() => void loadOverview()}>{busy ? "Atualizando…" : "Atualizar"}</button>
          <button onClick={logout}>Sair</button>
        </div>
      </header>

      <div className={styles.shell}>
        <section className={styles.hero}>
          <div>
            <span>ADMIN · SOMENTE LEITURA</span>
            <h1>Operação do Sr. Rotas em um só lugar.</h1>
            <p>Motoristas, aparelhos, jornadas, ofertas, outcomes, V7, trial, assinatura, pagamento e saúde. Nenhuma ação destrutiva está habilitada nesta primeira homologação.</p>
          </div>
          <aside>
            <small>Ambiente</small>
            <strong>{overview?.deployment?.environment || "produção"}</strong>
            <span>{overview?.deployment?.commit_sha ? overview.deployment.commit_sha.slice(0, 10) : "commit indisponível"}</span>
          </aside>
        </section>

        {message ? <div className={styles.error}>{message}</div> : null}

        <nav className={styles.tabs} aria-label="Módulos do Admin">
          <button className={tab === "overview" ? styles.active : ""} onClick={() => setTab("overview")}>Visão geral</button>
          <button className={tab === "drivers" ? styles.active : ""} onClick={() => setTab("drivers")}>Motoristas</button>
          <button className={tab === "data" ? styles.active : ""} onClick={() => setTab("data")}>Dados & V7</button>
          <button className={tab === "finance" ? styles.active : ""} onClick={() => setTab("finance")}>Financeiro</button>
          <button className={tab === "system" ? styles.active : ""} onClick={() => setTab("system")}>Sistema</button>
        </nav>

        {tab === "overview" ? <OverviewTab overview={overview} /> : null}
        {tab === "drivers" ? <DriversTab overview={overview} onOpen={openDriver} busy={busy} /> : null}
        {tab === "data" ? <DataTab overview={overview} /> : null}
        {tab === "finance" ? <FinanceTab overview={overview} /> : null}
        {tab === "system" ? <SystemTab overview={overview} /> : null}

        {selectedDriverId ? (
          <section className={styles.detail}>
            <div className={styles.detailHead}>
              <div><span>MOTORISTA</span><h2>{selectedDriver?.driver?.display_name || driverName || "Carregando…"}</h2></div>
              <button onClick={() => { setSelectedDriverId(""); setSelectedDriver(null); }}>Fechar</button>
            </div>
            {selectedDriver ? <DriverDetailPanel detail={selectedDriver} /> : <div className={styles.loading}>Carregando detalhes…</div>}
          </section>
        ) : null}

        <footer className={styles.footer}>
          <span>Atualizado {formatDate(overview?.generated_at)}</span>
          <b>Admin V1 · leitura segura · sem raw OCR · sem coordenadas</b>
        </footer>
      </div>
    </main>
  );
}

function Metric({ label, value, note }: { label: string; value: string | number; note?: string }) {
  return <article className={styles.metric}><span>{label}</span><strong>{value}</strong>{note ? <small>{note}</small> : null}</article>;
}

function OverviewTab({ overview }: { overview: Overview | null }) {
  const c = overview?.counts || {};
  const outcomes = overview?.outcomes || {};
  return <>
    <section className={styles.metrics}>
      <Metric label="Motoristas" value={c.drivers ?? "—"} note={`${c.active_devices ?? 0} aparelhos ativos`} />
      <Metric label="Jornadas abertas" value={c.open_journeys ?? "—"} note="em andamento no backend" />
      <Metric label="Ofertas" value={(c.offers ?? 0).toLocaleString("pt-BR")} note={`${c.offers_24h ?? 0} nas últimas 24h`} />
      <Metric label="Corridas concluídas" value={outcomes.COMPLETED ?? 0} note={`${outcomes.NOT_COMPLETED ?? 0} desmarcadas / não concluídas`} />
      <Metric label="Assinaturas ativas" value={c.active_subscriptions ?? 0} note={`${c.trials ?? 0} trials registrados`} />
      <Metric label="Métricas veículo" value={c.vehicle_metrics ?? 0} note={`${c.energy_entries ?? 0} lançamentos de energia`} />
    </section>

    <section className={styles.grid2}>
      <article className={styles.card}>
        <div className={styles.cardHead}><div><span>JORNADAS</span><h2>Atividade recente</h2></div><b>{c.open_journeys ?? 0} abertas</b></div>
        <div className={styles.list}>
          {(overview?.recent_journeys || []).slice(0, 10).map((journey) => (
            <div className={styles.listRow} key={journey.id}>
              <div><strong>{journey.platform}</strong><small>{formatDate(journey.started_at)}</small></div>
              <span className={`${styles.tag} ${statusTone(journey.ended_at ? "completed" : journey.state)}`}>{journey.ended_at ? "encerrada" : journey.state}</span>
            </div>
          ))}
          {!overview?.recent_journeys?.length ? <p className={styles.empty}>Nenhuma jornada carregada.</p> : null}
        </div>
      </article>

      <article className={styles.card}>
        <div className={styles.cardHead}><div><span>OUTCOMES</span><h2>Estado operacional</h2></div></div>
        <div className={styles.outcomes}>
          {["OFFERED","DOING_RIDE","COMPLETED","NOT_COMPLETED","CANCELLED"].map((status) => (
            <div key={status}><span>{status}</span><strong>{overview?.outcomes?.[status] ?? 0}</strong></div>
          ))}
        </div>
        <p className={styles.note}>Oferta observada continua separada de corrida concluída. O Admin não transforma V7 em outcome.</p>
      </article>
    </section>
  </>;
}

function DriversTab({ overview, onOpen, busy }: { overview: Overview | null; onOpen: (id: string) => void; busy: boolean }) {
  return <section className={styles.card}>
    <div className={styles.cardHead}><div><span>USUÁRIOS / MOTORISTAS</span><h2>Contas e presença operacional</h2></div><b>{overview?.drivers?.length ?? 0} contas</b></div>
    <div className={styles.tableWrap}><table><thead><tr><th>Motorista</th><th>Cadastro</th><th>Aparelhos</th><th>Último contato</th><th>Jornada</th><th>Plano</th><th>Créditos</th><th></th></tr></thead><tbody>
      {(overview?.drivers || []).map((driver) => (
        <tr key={driver.id}>
          <td><strong>{driver.display_name}</strong><small>{driver.email || "sem e-mail legado"}</small></td>
          <td><span className={`${styles.tag} ${driver.onboarding_completed ? styles.good : styles.warn}`}>{driver.onboarding_completed ? "completo" : "pendente"}</span><small>{formatDate(driver.created_at, false)}</small></td>
          <td>{driver.devices_active}/{driver.devices_total}<small>ativos / total</small></td>
          <td>{formatDate(driver.latest_device_seen_at)}</td>
          <td>{driver.current_journey ? <span className={`${styles.tag} ${styles.good}`}>ativa</span> : <span className={`${styles.tag} ${styles.neutral}`}>sem jornada</span>}</td>
          <td>{driver.subscription?.status || (driver.trial ? "trial" : "—")}<small>{driver.subscription?.plan_id || ""}</small></td>
          <td>{driver.wallet?.balance ?? "—"}</td>
          <td><button className={styles.linkButton} disabled={busy} onClick={() => void onOpen(driver.id)}>Abrir</button></td>
        </tr>
      ))}
    </tbody></table></div>
  </section>;
}

function DataTab({ overview }: { overview: Overview | null }) {
  const batch = overview?.v7?.canonical_batch;
  return <>
    <section className={styles.grid2}>
      <article className={styles.card}>
        <div className={styles.cardHead}><div><span>V7 CANÔNICO</span><h2>{batch ? `${batch.extractor_version || batch.schema_version}` : "Nenhum lote canônico"}</h2></div>{batch ? <span className={`${styles.tag} ${statusTone(batch.status)}`}>{batch.status}</span> : null}</div>
        {batch ? <>
          <div className={styles.quality}>
            <div><span>Recebidos</span><strong>{batch.received_count.toLocaleString("pt-BR")}</strong></div>
            <div><span>Temporal</span><strong>{batch.demand_temporal_ready_count.toLocaleString("pt-BR")}</strong><small>{pct(batch.demand_temporal_ready_count,batch.received_count)}</small></div>
            <div><span>Rota</span><strong>{batch.route_flow_ready_count.toLocaleString("pt-BR")}</strong><small>{pct(batch.route_flow_ready_count,batch.received_count)}</small></div>
            <div><span>Financeiro</span><strong>{batch.financial_ready_count.toLocaleString("pt-BR")}</strong><small>{pct(batch.financial_ready_count,batch.received_count)}</small></div>
            <div><span>Completo</span><strong>{batch.fully_ready_count.toLocaleString("pt-BR")}</strong><small>{pct(batch.fully_ready_count,batch.received_count)}</small></div>
            <div><span>Parciais</span><strong>{batch.partial_count.toLocaleString("pt-BR")}</strong></div>
          </div>
          <p className={styles.note}>canonical_for_intelligence: {String(batch.canonical_for_intelligence)} · owner_resolved: {String(batch.canonical_owner_resolved)}</p>
        </> : <p className={styles.empty}>Sem lote canônico.</p>}
      </article>

      <article className={styles.card}>
        <div className={styles.cardHead}><div><span>CONTRATO</span><h2>Separação patrimonial</h2></div></div>
        <ul className={styles.rules}>
          <li><b>ride_offers</b><span>somente operação real sincronizada</span></li>
          <li><b>V7</b><span>ofertas históricas observadas</span></li>
          <li><b>outcomes</b><span>somente decisão/corrida operacional</span></li>
          <li><b>Admin</b><span>não retorna raw OCR nem coordenadas</span></li>
        </ul>
        <a className={styles.primaryLink} href="/admin/importacoes">Abrir importação histórica →</a>
      </article>
    </section>

    <section className={styles.card}>
      <div className={styles.cardHead}><div><span>LOTES</span><h2>Histórico recente de importação</h2></div></div>
      <div className={styles.tableWrap}><table><thead><tr><th>Arquivo</th><th>Status</th><th>Contrato</th><th>Ofertas</th><th>Completo</th><th>Parciais</th><th>Finalizado</th></tr></thead><tbody>
        {(overview?.v7?.recent_batches || []).map((row) => <tr key={row.id}>
          <td><strong>{row.original_filename}</strong><small>{row.source_name}</small></td>
          <td><span className={`${styles.tag} ${statusTone(row.status)}`}>{row.status}</span></td>
          <td>{row.extractor_version || row.schema_version}</td>
          <td>{row.received_count.toLocaleString("pt-BR")}</td>
          <td>{row.fully_ready_count.toLocaleString("pt-BR")}</td>
          <td>{row.partial_count.toLocaleString("pt-BR")}</td>
          <td>{formatDate(row.finalized_at)}</td>
        </tr>)}
      </tbody></table></div>
    </section>
  </>;
}

function FinanceTab({ overview }: { overview: Overview | null }) {
  return <>
    <section className={styles.metrics}>
      <Metric label="Assinaturas" value={overview?.counts?.subscriptions ?? "—"} note={`${overview?.counts?.active_subscriptions ?? 0} ativas`} />
      <Metric label="Pagamentos" value={overview?.counts?.payments ?? "—"} />
      <Metric label="Receita confirmada" value={money(overview?.finance?.confirmed_revenue_cents || 0)} />
      <Metric label="Entitlements" value={overview?.counts?.entitlements ?? "—"} />
    </section>
    <section className={styles.card}>
      <div className={styles.cardHead}><div><span>PAGAMENTOS</span><h2>Movimentações recentes</h2></div></div>
      <div className={styles.tableWrap}><table><thead><tr><th>Data</th><th>Motorista</th><th>Tipo</th><th>Provedor</th><th>Valor</th><th>Status</th><th>Banco</th></tr></thead><tbody>
        {(overview?.finance?.payments || []).map((row: any) => <tr key={row.id}>
          <td>{formatDate(row.created_at)}</td><td><code>{String(row.driver_id).slice(0,8)}</code></td><td>{row.kind}</td><td>{row.provider}</td><td>{money(row.amount_cents)}</td>
          <td><span className={`${styles.tag} ${statusTone(row.status)}`}>{row.status}</span></td><td>{row.bank_status || "—"}</td>
        </tr>)}
      </tbody></table></div>
    </section>
  </>;
}

function SystemTab({ overview }: { overview: Overview | null }) {
  const c = overview?.counts || {};
  return <section className={styles.grid2}>
    <article className={styles.card}>
      <div className={styles.cardHead}><div><span>SAÚDE</span><h2>Serviços e sessões</h2></div><span className={`${styles.tag} ${styles.good}`}>operacional</span></div>
      <div className={styles.systemRows}>
        <div><span>Banco</span><strong>{overview?.system?.database || "—"}</strong></div>
        <div><span>Sessões Web ativas</span><strong>{c.active_web_sessions ?? 0}</strong></div>
        <div><span>Tokens MCP ativos</span><strong>{c.active_mcp_tokens ?? 0}</strong></div>
        <div><span>Notificações 24h</span><strong>{c.notifications_24h ?? 0}</strong></div>
        <div><span>Chamadas IA · 30d</span><strong>{c.ai_calls_30d ?? 0}</strong></div>
      </div>
    </article>
    <article className={styles.card}>
      <div className={styles.cardHead}><div><span>PRIVACIDADE</span><h2>Contrato do Admin</h2></div></div>
      <ul className={styles.rules}>
        <li><b>Raw OCR</b><span>{overview?.system?.sensitive_offer_text_exposed ? "exposto" : "não retornado"}</span></li>
        <li><b>Coordenadas</b><span>{overview?.system?.coordinates_exposed ? "expostas" : "não retornadas"}</span></li>
        <li><b>Exclusão</b><span>{overview?.system?.self_service_account_deletion ? "self-service disponível" : "indisponível"}</span></li>
        <li><b>Ações destrutivas</b><span>desligadas nesta homologação</span></li>
      </ul>
    </article>
  </section>;
}

function DriverDetailPanel({ detail }: { detail: DriverDetail }) {
  return <div className={styles.detailGrid}>
    <article className={styles.card}>
      <div className={styles.cardHead}><div><span>CONTA</span><h2>{detail.driver.display_name}</h2></div><span className={`${styles.tag} ${detail.driver.onboarding_completed ? styles.good : styles.warn}`}>{detail.driver.onboarding_completed ? "onboarding ok" : "pendente"}</span></div>
      <div className={styles.systemRows}>
        <div><span>E-mail</span><strong>{detail.driver.email || "legado sem e-mail"}</strong></div>
        <div><span>Último login</span><strong>{formatDate(detail.driver.last_login_at)}</strong></div>
        <div><span>Ofertas</span><strong>{detail.counts.offers ?? 0}</strong></div>
        <div><span>Outcomes</span><strong>{detail.counts.outcomes ?? 0}</strong></div>
        <div><span>Concluídas</span><strong>{detail.counts.completed ?? 0}</strong></div>
        <div><span>Jornadas</span><strong>{detail.counts.journeys ?? 0}</strong></div>
      </div>
    </article>

    <article className={styles.card}>
      <div className={styles.cardHead}><div><span>ODÔMETRO / ENERGIA</span><h2>Sincronização de jornada</h2></div></div>
      <div className={styles.systemRows}>
        <div><span>Métricas gravadas</span><strong>{detail.vehicle_metrics.length}</strong></div>
        <div><span>Energia / abastecimento</span><strong>{detail.energy_entries.length}</strong></div>
        <div><span>Créditos IA</span><strong>{detail.wallet?.balance ?? "—"}</strong></div>
      </div>
      <p className={styles.note}>Esta tela é útil para validar a próxima build Android sem precisar abrir o banco manualmente.</p>
    </article>

    <article className={`${styles.card} ${styles.wide}`}>
      <div className={styles.cardHead}><div><span>APARELHOS</span><h2>Dispositivos cadastrados</h2></div><b>{detail.counts.active_devices ?? 0} ativos</b></div>
      <div className={styles.tableWrap}><table><thead><tr><th>Nome</th><th>Estado</th><th>Último contato</th><th>Criado</th></tr></thead><tbody>
        {detail.devices.map((row: any) => <tr key={row.id}><td>{row.name}</td><td><span className={`${styles.tag} ${row.revoked ? styles.bad : styles.good}`}>{row.revoked ? "revogado" : "ativo"}</span></td><td>{formatDate(row.last_seen_at)}</td><td>{formatDate(row.created_at)}</td></tr>)}
      </tbody></table></div>
    </article>

    <article className={`${styles.card} ${styles.wide}`}>
      <div className={styles.cardHead}><div><span>JORNADAS</span><h2>Últimas jornadas</h2></div></div>
      <div className={styles.tableWrap}><table><thead><tr><th>Início</th><th>Estado</th><th>Plataforma</th><th>Fim</th><th>Motivo</th></tr></thead><tbody>
        {detail.journeys.map((row: any) => <tr key={row.id}><td>{formatDate(row.started_at)}</td><td><span className={`${styles.tag} ${statusTone(row.ended_at ? "completed" : row.state)}`}>{row.ended_at ? "encerrada" : row.state}</span></td><td>{row.platform}</td><td>{formatDate(row.ended_at)}</td><td>{row.end_reason || "—"}</td></tr>)}
      </tbody></table></div>
    </article>
  </div>;
}
