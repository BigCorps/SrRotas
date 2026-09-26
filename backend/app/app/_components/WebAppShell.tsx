"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import NavIcon from "./NavIcon";
import ThemeController from "./ThemeController";
import ThemeSelector024 from "./ThemeSelector024";

type Tone = "home" | "now" | "history" | "user" | "plan";
type NavLink = {
  href: string;
  label: string;
  icon: "home" | "now" | "history" | "user" | "plan";
  tone: Tone;
};

const links: NavLink[] = [
  { href: "/app", label: "Início", icon: "home", tone: "home" },
  { href: "/app/agora", label: "Agora", icon: "now", tone: "now" },
  { href: "/app/historico", label: "Histórico", icon: "history", tone: "history" },
  { href: "/app/perfil", label: "Usuário", icon: "user", tone: "user" },
  { href: "/app/plano", label: "Plano", icon: "plan", tone: "plan" },
];

function active(path: string, href: string) {
  if (href === "/app") return path === "/app" || path === "/app/inicio";
  if (href === "/app/historico") return path === href || path.startsWith(`${href}/`);
  return path === href || path.startsWith(`${href}/`);
}

export default function WebAppShell({
  children,
  playStoreUrl,
}: {
  children: ReactNode;
  playStoreUrl?: string;
}) {
  const pathname = usePathname();

  if (pathname === "/app/entrar") {
    return <div className="srLoginFrame"><ThemeController />{children}</div>;
  }

  return (
    <div className="srApp srAccountApp srAccountUnified024">
      <ThemeController />

      <header className="srAccountTopBrand024">
        <div className="srAccountTopBrandInner024">
          <Link
            href="/app"
            className="srAccountTopLogo024"
            aria-label="Sr. Rotas — Central do motorista"
          >
            <img src="/logo-srrotas.png" alt="Sr. Rotas" />
            <span className="srAccountTopName024">Sr.Rotas</span>
          </Link>
          <ThemeSelector024 />
        </div>
      </header>

      <div className="srMain srAccountMain024">
        <main className="srContent sr023Content">{children}</main>

        {playStoreUrl ? (
          <a
            className="srAccountAppLink"
            href={playStoreUrl}
            target="_blank"
            rel="noreferrer"
          >
            <span>As funções de captura e jornada ficam no app Android</span>
            <b>Ver app →</b>
          </a>
        ) : null}
      </div>

      <nav
        className="srBottomNav srAccountBottomNav srAccountFooter024"
        aria-label="Navegação principal do Sr. Rotas"
      >
        {links.map((link) => (
          <Link
            key={link.href}
            href={link.href}
            data-tone={link.tone}
            className={active(pathname, link.href) ? "active" : ""}
          >
            <NavIcon name={link.icon} />
            <span>{link.label}</span>
          </Link>
        ))}
      </nav>
    </div>
  );
}
