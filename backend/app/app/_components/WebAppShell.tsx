"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import NavIcon from "./NavIcon";

const links = [
  { href: "/app", label: "Início", icon: "home" as const, exact: true },
  { href: "/app/historico", label: "Histórico", icon: "history" as const },
  { href: "/app/ia", label: "IA", icon: "ai" as const },
  { href: "/app/mcp", label: "MCP", icon: "mcp" as const },
  { href: "/app/perfil", label: "Perfil", icon: "profile" as const },
];

function isActive(pathname: string, href: string, exact?: boolean) {
  return exact ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);
}

export default function WebAppShell({ children, playStoreUrl }: { children: ReactNode; playStoreUrl?: string }) {
  const pathname = usePathname();
  const [canImport, setCanImport] = useState(false);

  useEffect(() => {
    if (pathname === "/app/entrar") {
      setCanImport(false);
      return;
    }

    let active = true;
    fetch("/api/v1/admin/imports/me", { cache: "no-store" })
      .then((response) => {
        if (active) setCanImport(response.ok);
      })
      .catch(() => {
        if (active) setCanImport(false);
      });

    return () => { active = false; };
  }, [pathname]);

  if (pathname === "/app/entrar") return <div className="srLoginFrame">{children}</div>;

  return (
    <div className="srApp">
      <aside className="srSidebar">
        <Link href="/app" className="srBrand" aria-label="Sr. Rotas — painel">
          <img src="/logo-srrotas.png" alt="" />
          <span><strong>Sr. Rotas</strong><small>Seu copiloto</small></span>
        </Link>
        <nav className="srNav" aria-label="Navegação do painel">
          {links.map((link) => <Link key={link.href} href={link.href} className={isActive(pathname, link.href, link.exact) ? "active" : ""}><NavIcon name={link.icon}/><span>{link.label}</span></Link>)}
        </nav>
        <div className="srSidebarBottom">
          {canImport ? <Link href="/admin/importacoes" className="srImportButton"><span>↥</span><span>Enviar históricos</span></Link> : null}
          <Link href="/app/plano" className={isActive(pathname,"/app/plano") ? "srPlanButton active" : "srPlanButton"}><NavIcon name="plan"/><span>Plano e créditos</span></Link>
          <span className="srBuilt">Sr. Rotas · BigCorps</span>
        </div>
      </aside>

      <div className="srMain">
        <header className="srTopbar">
          <Link href="/app" className="srMobileBrand"><img src="/logo-srrotas.png" alt=""/><strong>Sr. Rotas</strong></Link>
          <span className="srStage"><i/> Web conectado</span>
          {canImport ? <Link href="/admin/importacoes" className="srImportTop">Importar JSON</Link> : null}
          <a href="/app/perfil" className="srAccountShortcut">Perfil <NavIcon name="external"/></a>
        </header>

        {playStoreUrl ? <a className="srPlayBanner" href={playStoreUrl} target="_blank" rel="noreferrer"><img src="/logo-srrotas.png" alt=""/><span><strong>Baixe o app oficial Sr. Rotas</strong><small>Disponível no Google Play</small></span><b>Ver na Play Store →</b></a> : null}

        <main className="srContent">{children}</main>
      </div>

      <nav className="srBottomNav" aria-label="Navegação mobile">
        {links.map((link) => <Link key={link.href} href={link.href} className={isActive(pathname, link.href, link.exact) ? "active" : ""}><NavIcon name={link.icon}/><span>{link.label}</span></Link>)}
      </nav>
    </div>
  );
}
