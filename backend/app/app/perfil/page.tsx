"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type Me = { display_name: string; email: string; onboarding_completed: boolean; legacy: boolean };
type Billing = { subscription: null | { active: boolean; status: string; current_period_end: string | null }; wallet: { balance: number } };
type Device = { id: string; name: string; revoked: boolean; last_seen_at: string | null; created_at: string };
type Devices = { devices: Device[]; active_count: number; max_active_devices: number; note?: string };

function when(value: string | null | undefined) {
  if (!value) return "Nunca";
  return new Date(value).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

export default function PerfilPage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [billing, setBilling] = useState<Billing | null>(null);
  const [devices, setDevices] = useState<Devices | null>(null);

  useEffect(() => {
    Promise.all([
      fetch("/api/v1/account/me", { cache: "no-store" }).then((response) => response.ok ? response.json() : null),
      fetch("/api/v1/billing/status", { cache: "no-store" }).then((response) => response.ok ? response.json() : null),
      fetch("/api/v1/account/devices", { cache: "no-store" }).then((response) => response.ok ? response.json() : null),
    ]).then(([account, plan, deviceList]) => {
      setMe(account);
      setBilling(plan);
      setDevices(deviceList);
    }).catch(() => undefined);
  }, []);

  async function logout() {
    await fetch("/api/v1/billing/web-logout", { method: "POST" }).catch(() => undefined);
    router.replace("/");
    router.refresh();
  }

  return <>
    <section className="srPageHead compact"><div><span className="srEyebrow">PERFIL</span><h1>Conta, dispositivos e segurança.</h1><p>A central Web reúne o que não precisa viver no motor Kotlin.</p></div></section>

    <section className="srGrid2">
      <article className="srPanel">
        <div className="srPanelHead"><div><span className="srEyebrow">CONTA</span><h2>Sua conta Sr. Rotas</h2></div><span className="srMutedPill">Conectada</span></div>
        <div className="srProfileRows">
          <div><span>Nome</span><strong>{me?.display_name ?? "—"}</strong></div>
          <div><span>E-mail</span><strong>{me?.email ?? "—"}</strong></div>
          <div><span>Onboarding</span><strong>{me ? (me.onboarding_completed ? "Concluído" : "Pendente") : "—"}</strong></div>
          <div><span>Plano</span><strong>{billing?.subscription?.active ? "Ativo" : "Sem assinatura ativa"}</strong></div>
          <div><span>Créditos IA</span><strong>{billing?.wallet?.balance ?? "—"}</strong></div>
        </div>
        <button className="srSecondaryButton" onClick={logout}>Sair do painel Web</button>
      </article>

      <article className="srPanel">
        <div className="srPanelHead"><div><span className="srEyebrow">DISPOSITIVOS</span><h2>{devices?.active_count ?? "—"} de {devices?.max_active_devices ?? 2} aparelhos ativos</h2></div><span className="srMutedPill">Somente leitura</span></div>
        <p className="srPanelText">Já exibimos os aparelhos reais cadastrados. Adicionar/remover pelo Web entra somente junto com device identity + Access Resolver para não criar regra paralela antes da 1.0-B/C.</p>

        {devices?.devices?.length ? <div className="srDeviceList">
          {devices.devices.map((device) => <div className={device.revoked ? "srDevice revoked" : "srDevice"} key={device.id}>
            <span className="srDeviceIcon">▣</span>
            <div><strong>{device.name}</strong><small>Último contato: {when(device.last_seen_at)} · cadastrado {when(device.created_at)}</small></div>
            <span className={device.revoked ? "srStateTag revoked" : "srStateTag active"}>{device.revoked ? "Revogado" : "Ativo"}</span>
          </div>)}
        </div> : <div className="srEmpty compact"><span>▣</span><strong>Nenhum aparelho encontrado</strong><p>O primeiro aparelho aparece depois que a conta é conectada pelo Android.</p></div>}

        <p className="srDataNote">{devices?.note || "Limite comercial definitivo entra na 1.0-B."}</p>
      </article>
    </section>

    <section className="srGrid3 srSectionGap">
      <Link className="srSetting" href="/app/plano"><strong>Plano e créditos</strong><span>Assinatura, trial e IA →</span></Link>
      <a className="srSetting" href="/privacidade"><strong>Privacidade</strong><span>Como tratamos seus dados →</span></a>
      <a className="srSetting" href="/suporte"><strong>Suporte</strong><span>Fale com a BigCorps →</span></a>
      <a className="srSetting" href="/termos"><strong>Termos</strong><span>Condições de uso →</span></a>
      <a className="srSetting" href="/excluir-conta"><strong>Excluir conta</strong><span>Solicitar remoção →</span></a>
      <Link className="srSetting" href="/app/mcp"><strong>Segurança MCP</strong><span>Chaves e revogação →</span></Link>
    </section>
  </>;
}
