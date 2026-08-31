"use client";
import Link from "next/link";
import {usePathname} from "next/navigation";
import type {ReactNode} from "react";
import NavIcon from "./NavIcon";
import ThemeController from "./ThemeController";

type Tone="user"|"plan"|"message"|"ai";
type NavLink={
  href:string;
  label:string;
  icon:"user"|"plan"|"mcp"|"message";
  tone:Tone;
};

const links:NavLink[]=[
  {href:"/app/perfil",label:"Usuário",icon:"user",tone:"user"},
  {href:"/app/plano",label:"Plano e créditos",icon:"plan",tone:"plan"},
  {href:"/app/perfil/mensagens",label:"Mensagens",icon:"message",tone:"message"},
  {href:"/app/mcp",label:"IA MCP",icon:"mcp",tone:"ai"},
];

function active(path:string,href:string){
  if(href==="/app/perfil") return path===href;
  return path===href||path.startsWith(`${href}/`);
}

export default function WebAppShell({
  children,
  playStoreUrl,
}:{
  children:ReactNode;
  playStoreUrl?:string;
}){
  const pathname=usePathname();

  if(pathname==="/app/entrar"){
    return <div className="srLoginFrame"><ThemeController/>{children}</div>;
  }

  return (
    <div className="srApp srAccountApp srAccountUnified024">
      <ThemeController/>

      <header className="srAccountTopBrand024">
        <div className="srAccountTopBrandInner024">
          <Link
            href="/app/perfil"
            className="srAccountTopLogo024"
            aria-label="Sr. Rotas — Central do usuário"
          >
            <img src="/logo-srrotas.png" alt="Sr. Rotas"/>
            <span className="srAccountTopName024">Sr.Rotas</span>
          </Link>
        </div>
      </header>

      <div className="srMain srAccountMain024">
        <main className="srContent sr023Content">{children}</main>

        {playStoreUrl?(
          <a
            className="srAccountAppLink"
            href={playStoreUrl}
            target="_blank"
            rel="noreferrer"
          >
            <span>As funções de motorista ficam no app Android</span>
            <b>Ver app →</b>
          </a>
        ):null}
      </div>

      <nav
        className="srBottomNav srAccountBottomNav srAccountFooter024"
        aria-label="Navegação da Central do usuário"
      >
        {links.map(link=>(
          <Link
            key={link.href}
            href={link.href}
            data-tone={link.tone}
            className={active(pathname,link.href)?"active":""}
          >
            <NavIcon name={link.icon}/>
            <span>{link.label}</span>
          </Link>
        ))}
      </nav>
    </div>
  );
}
