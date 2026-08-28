"use client";
import Link from "next/link";
import {usePathname} from "next/navigation";
import type {ReactNode} from "react";
import NavIcon from "./NavIcon";
import ThemeController from "./ThemeController";

type NavLink={href:string;label:string;icon:"user"|"plan"|"mcp"|"settings"};
const links:NavLink[]=[
 {href:"/app/perfil",label:"Usuário",icon:"user"},
 {href:"/app/plano",label:"Plano e créditos",icon:"plan"},
 {href:"/app/perfil/mensagens",label:"Mensagens rápidas",icon:"settings"},
 {href:"/app/mcp",label:"Segurança MCP",icon:"mcp"},
];
function active(path:string,href:string){return path===href||path.startsWith(`${href}/`);}

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
   <header className="srTopbar"><Link href="/app/perfil" className="srMobileBrand"><img src="/logo-srrotas.png" alt="Sr. Rotas"/><strong>Sr. Rotas</strong></Link><span className="srStage"><i/> Conta conectada</span><Link href="/app/perfil" className="srAccountShortcut">Usuário <NavIcon name="user"/></Link></header>
   {playStoreUrl?<a className="srPlayBanner" href={playStoreUrl} target="_blank" rel="noreferrer"><img src="/logo-srrotas.png" alt="Sr. Rotas"/><span><strong>As funções de motorista ficam no app oficial</strong><small>Agora, Histórico, IA, jornada e HUD são recursos do Android.</small></span><b>Ver app →</b></a>:null}
   <main className="srContent sr023Content">{children}</main>
  </div>
  <nav className="srBottomNav srAccountBottomNav">{links.map(l=><Link key={l.href} href={l.href} className={active(pathname,l.href)?"active":""}><NavIcon name={l.icon}/><span>{l.label}</span></Link>)}</nav>
 </div>;
}
