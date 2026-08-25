"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { applySrTheme, type SrTheme } from "../_components/ThemeController";

type Me = {
  display_name: string;
  email: string;
  onboarding_completed: boolean;
  legacy: boolean;
};
type Billing = {
  subscription: null | {
    active: boolean;
    status: string;
    current_period_end: string | null;
  };
  trial?: {
    trial_status: "pending" | "active" | "expired";
    days_remaining: number | null;
    trial_started_at: string | null;
    trial_ends_at: string | null;
  };
  wallet: { balance: number };
};
type Device = {
  id: string;
  name: string;
  revoked: boolean;
  last_seen_at: string | null;
  created_at: string;
};
type Devices = {
  devices: Device[];
  active_count: number;
  max_active_devices: number;
  note?: string;
};
type Prefs = {
  app_theme: SrTheme;
  collective_stats_opt_in: boolean;
  strategy_preset: "popular" | "comfort" | "premium" | "custom";
  max_pickup_km: number;
  max_pickup_minutes: number;
  min_per_km: number;
  min_per_minute: number;
  min_per_hour: number;
};

function when(value?: string | null) {
  return value
    ? new Date(value).toLocaleString("pt-BR", {
        dateStyle: "short",
        timeStyle: "short",
      })
    : "Nunca";
}

function presetName(value?: string) {
  if (value === "popular") return "Popular";
  if (value === "comfort") return "Conforto";
  if (value === "premium") return "Premium";
  return "Personalizado";
}

export default function PerfilPage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [billing, setBilling] = useState<Billing | null>(null);
  const [devices, setDevices] = useState<Devices | null>(null);
  const [prefs, setPrefs] = useState<Prefs | null>(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    Promise.all([
      fetch("/api/v1/account/me", { cache: "no-store" }).then((r) =>
        r.ok ? r.json() : null,
      ),
      fetch("/api/v1/billing/status", { cache: "no-store" }).then((r) =>
        r.ok ? r.json() : null,
      ),
      fetch("/api/v1/account/devices", { cache: "no-store" }).then((r) =>
        r.ok ? r.json() : null,
      ),
      fetch("/api/v1/preferences", { cache: "no-store" }).then((r) =>
        r.ok ? r.json() : null,
      ),
    ])
      .then(([account, plan, deviceList, preferenceResponse]) => {
        setMe(account);
        setBilling(plan);
        setDevices(deviceList);
        setPrefs(preferenceResponse?.preferences ?? null);
      })
      .catch(() => undefined);
  }, []);

  async function savePreference(patch: Record<string, unknown>) {
    setMessage("Salvando...");
    const response = await fetch("/api/v1/preferences", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    });
    const json = response.ok ? await response.json() : null;
    if (json?.preferences) {
      setPrefs(json.preferences);
      setMessage("Salvo.");
    } else {
      setMessage("Não foi possível salvar.");
    }
  }

  async function logout() {
    await fetch("/api/v1/billing/web-logout", { method: "POST" }).catch(
      () => undefined,
    );
    router.replace("/");
    router.refresh();
  }

  const trialLabel = billing?.subscription?.active
    ? "Plano ativo"
    : billing?.trial?.trial_status === "active"
      ? `${billing.trial.days_remaining ?? 0} dia(s) de trial restantes`
      : billing?.trial?.trial_status === "expired"
        ? "Trial encerrado"
        : "Trial inicia na primeira oferta válida";

  return (
    <>
      <section className="srPageHead compact">
        <div>
          <span className="srEyebrow">CONFIGURAÇÕES</span>
          <h1>Configurações, estratégia e aparência.</h1>
          <p>As escolhas do perfil ficam no backend para Android e Web usarem a mesma base.</p>
        </div>
      </section>

      <section className="srGrid2">
        <article className="srPanel">
          <div className="srPanelHead">
            <div>
              <span className="srEyebrow">CONTA</span>
              <h2>Sua conta Sr. Rotas</h2>
            </div>
          </div>
          <div className="srProfileRows">
            <div><span>Nome</span><strong>{me?.display_name ?? "—"}</strong></div>
            <div><span>E-mail</span><strong>{me?.email ?? "—"}</strong></div>
            <div><span>Acesso</span><strong>{trialLabel}</strong></div>
            <div><span>Créditos IA</span><strong>{billing?.wallet?.balance ?? "—"}</strong></div>
          </div>
          <button className="srSecondaryButton" onClick={logout}>Sair do painel Web</button>
        </article>

        <article className="srPanel">
          <div className="srPanelHead">
            <div>
              <span className="srEyebrow">ESTRATÉGIA</span>
              <h2>{presetName(prefs?.strategy_preset)}</h2>
            </div>
          </div>
          <p className="srPanelText">
            O perfil troca apenas as metas financeiras. Os limites de busca em km/min são independentes. O Offer Engine continua igual.
          </p>
          <div className="srThemeChoices">
            {(["popular", "comfort", "premium"] as const).map((preset) => (
              <button
                key={preset}
                className={prefs?.strategy_preset === preset ? "active" : ""}
                onClick={() => savePreference({ strategy_preset: preset }).catch(() => undefined)}
              >
                {presetName(preset)}
              </button>
            ))}
          </div>
          <div className="srProfileRows">
            <div><span>Meta R$/km</span><strong>{prefs?.min_per_km ?? "—"}</strong></div>
            <div><span>Meta R$/min</span><strong>{prefs?.min_per_minute ?? "—"}</strong></div>
            <div><span>Meta R$/h</span><strong>{prefs?.min_per_hour ?? "—"}</strong></div>
            <div><span>Busca máxima</span><strong>{prefs ? `${prefs.max_pickup_km} km / ${prefs.max_pickup_minutes} min` : "—"}</strong></div>
          </div>
          <p className="srDataNote">Personalizado continua disponível no Android para ajuste métrica por métrica.</p>
        </article>
      </section>

      <section className="srGrid2 srSectionGap">
        <article className="srPanel">
          <div className="srPanelHead">
            <div><span className="srEyebrow">APARÊNCIA</span><h2>Tema</h2></div>
          </div>
          <div className="srThemeChoices">
            {(["auto", "light", "dark"] as SrTheme[]).map((theme) => (
              <button
                key={theme}
                className={prefs?.app_theme === theme ? "active" : ""}
                onClick={() => {
                  applySrTheme(theme);
                  savePreference({ app_theme: theme }).catch(() => undefined);
                }}
              >
                {theme === "auto" ? "Automático" : theme === "light" ? "Claro" : "Escuro"}
              </button>
            ))}
          </div>
          <p className="srPanelText">Automático acompanha o tema do dispositivo/navegador.</p>
          <hr className="srRule" />
          <label className="srToggle">
            <input
              type="checkbox"
              checked={Boolean(prefs?.collective_stats_opt_in)}
              onChange={(event) =>
                savePreference({ collective_stats_opt_in: event.target.checked }).catch(
                  () => undefined,
                )
              }
            />
            <span>
              <strong>Participar da base coletiva</strong>
              <small>Contribui com dados anonimizados e libera os agregados da comunidade.</small>
            </span>
          </label>
          <p className="srDataNote">
            A Base Sr. Rotas histórica continua disponível como referência agregada; nenhuma oferta individual de outro motorista é exposta.
          </p>
          <small>{message}</small>
        </article>

        <article className="srPanel">
          <div className="srPanelHead">
            <div><span className="srEyebrow">DISPOSITIVOS</span><h2>{devices?.active_count ?? "—"} de {devices?.max_active_devices ?? 2} ativos</h2></div>
          </div>
          {devices?.devices?.length ? (
            <div className="srDeviceList">
              {devices.devices.map((device) => (
                <div className={device.revoked ? "srDevice revoked" : "srDevice"} key={device.id}>
                  <span className="srDeviceIcon">▣</span>
                  <div>
                    <strong>{device.name}</strong>
                    <small>Último contato: {when(device.last_seen_at)}</small>
                  </div>
                  <span className={device.revoked ? "srStateTag revoked" : "srStateTag active"}>
                    {device.revoked ? "Revogado" : "Ativo"}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <div className="srEmpty compact"><span>▣</span><strong>Nenhum aparelho encontrado</strong></div>
          )}
        </article>
      </section>

      <section className="srGrid3 srSectionGap">
        <Link className="srSetting" href="/app/agora"><strong>Agora</strong><span>Inteligência regional →</span></Link>
        <Link className="srSetting" href="/app/plano"><strong>Plano e créditos</strong><span>Assinatura e trial →</span></Link>
        <a className="srSetting" href="/privacidade"><strong>Privacidade</strong><span>Como tratamos seus dados →</span></a>
        <a className="srSetting" href="/suporte"><strong>Suporte</strong><span>Fale com a BigCorps →</span></a>
        <a className="srSetting" href="/excluir-conta"><strong>Excluir conta</strong><span>Solicitar remoção →</span></a>
        <Link className="srSetting" href="/app/mcp"><strong>Segurança MCP</strong><span>Chaves e revogação →</span></Link>
      </section>
    </>
  );
}
