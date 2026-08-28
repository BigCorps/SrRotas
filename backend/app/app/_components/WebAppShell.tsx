"use client";
import Link from "next/link";
import {usePathname} from "next/navigation";
import type {ReactNode} from "react";
import NavIcon from "./NavIcon";
import ThemeController from "./ThemeController";

type NavLink={href:string;label:string;icon:"user"|"plan"|"mcp"|"message"};
const links:NavLink[]=[
 {href:"/app/perfil",label:"Usuário",icon:"user"},
 {href:"/app/plano",label:"Plano e créditos",icon:"plan"},
 {href:"/app/perfil/mensagens",label:"Mensagens rápidas",icon:"message"},
 {href:"/app/mcp",label:"Segurança MCP",icon:"mcp"},
];

function active(path:string,href:string){
 if(href==="/app/perfil") return path===href;
 return path===href||path.startsWith(`${href}/`);
}

export default function WebAppShell({children,playStoreUrl}:{children:ReactNode;playStoreUrl?:string}){
 const pathname=usePathname();
 if(pathname==="/app/entrar")return <div className="srLoginFrame"><ThemeController/>{children}</div>;
 return <div className="srApp srAccountApp"><ThemeController/>
  <aside className="srSidebar srAccountSidebar">
   <Link href="/app/perfil" className="srBrand"><img src="/logo-srrotas.png" alt="Sr. Rotas"/><span><strong>Sr. Rotas</strong><small>Central do usuário</small></span></Link>
   <nav className="srNav">{links.map(l=><Link key={l.href} href={l.href} className={active(pathname,l.href)?"active":""}><NavIcon name={l.icon}/><span>{l.label}</span></Link>)}</nav>
   <div className="srSidebarBottom"><span className="srBuilt">Sr. Rotas · BigCorps</span></div>
  </aside>
  <div className="srMain">
   <main className="srContent sr023Content">{children}</main>
   {playStoreUrl?<a className="srAccountAppLink" href={playStoreUrl} target="_blank" rel="noreferrer"><span>As funções de motorista ficam no app Android</span><b>Ver app →</b></a>:null}
  </div>
  <nav className="srBottomNav srAccountBottomNav">{links.map(l=><Link key={l.href} href={l.href} className={active(pathname,l.href)?"active":""}><NavIcon name={l.icon}/><span>{l.label}</span></Link>)}</nav>
 </div>;
}
