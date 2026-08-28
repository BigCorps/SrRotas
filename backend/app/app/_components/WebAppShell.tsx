"use client";
import Link from "next/link";
import {usePathname} from "next/navigation";
import type {ReactNode} from "react";
import NavIcon from "./NavIcon";
import ThemeController from "./ThemeController";
type NavLink={href:string;label:string;icon:"now"|"history"|"ai"|"settings"|"user";tone:string};
const links:NavLink[]=[
 {href:"/app/historico",label:"Histórico",icon:"history",tone:"history"},
 {href:"/app/ia",label:"IA",icon:"ai",tone:"ai"},
 {href:"/app/agora",label:"Agora",icon:"now",tone:"now"},
 {href:"/app/configuracoes",label:"Configurações",icon:"settings",tone:"settings"},
 {href:"/app/perfil",label:"Usuário",icon:"user",tone:"user"},
];
function active(path:string,href:string){return path===href||path.startsWith(`${href}/`);}
export default function WebAppShell({children,playStoreUrl}:{children:ReactNode;playStoreUrl?:string}){
 const pathname=usePathname();
 if(pathname==="/app/entrar")return <div className="srLoginFrame"><ThemeController/>{children}</div>;
 return <div className="srApp sr023App"><ThemeController/>
  <aside className="srSidebar sr023Sidebar"><Link href="/app/agora" className="srBrand"><img src="/logo-srrotas.png" alt=""/><span><strong>Sr. Rotas</strong><small>Seu copiloto</small></span></Link><nav className="srNav">{links.map(l=><Link key={l.href} href={l.href} className={`${active(pathname,l.href)?"active":""} sr023-${l.tone}`}><NavIcon name={l.icon}/><span>{l.label}</span></Link>)}</nav><div className="srSidebarBottom"><Link href="/app/plano" className="srPlanButton"><NavIcon name="plan"/><span>Plano e créditos</span></Link><span className="srBuilt">Sr. Rotas · BigCorps</span></div></aside>
  <div className="srMain"><header className="srTopbar"><Link href="/app/agora" className="srMobileBrand"><img src="/logo-srrotas.png" alt=""/><strong>Sr. Rotas</strong></Link><span className="srStage"><i/> Web conectado</span><Link href="/app/perfil" className="srAccountShortcut">Usuário <NavIcon name="external"/></Link></header>{playStoreUrl?<a className="srPlayBanner" href={playStoreUrl} target="_blank" rel="noreferrer"><img src="/logo-srrotas.png" alt=""/><span><strong>Baixe o app oficial Sr. Rotas</strong><small>Disponível no Google Play</small></span><b>Ver na Play Store →</b></a>:null}<main className="srContent sr023Content">{children}</main></div>
  <nav className="srBottomNav sr023BottomNav">{links.map(l=><Link key={l.href} href={l.href} className={`${active(pathname,l.href)?"active":""} sr023-${l.tone}`}><span className="sr023NavIcon"><NavIcon name={l.icon}/></span><span>{l.label}</span></Link>)}</nav>
 </div>;
}
