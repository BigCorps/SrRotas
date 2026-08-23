"use client";
import Link from "next/link";
import {usePathname} from "next/navigation";
import type {ReactNode} from "react";
import NavIcon from "./NavIcon";
import ThemeController from "./ThemeController";
const links=[
 {href:"/app",label:"Início",icon:"home" as const,exact:true},
 {href:"/app/agora",label:"Agora",icon:"now" as const},
 {href:"/app/historico",label:"Histórico",icon:"history" as const},
 {href:"/app/ia",label:"IA",icon:"ai" as const},
 {href:"/app/mcp",label:"MCP",icon:"mcp" as const},
 {href:"/app/perfil",label:"Perfil",icon:"profile" as const},
];
function active(path:string,href:string,exact?:boolean){return exact?path===href:path===href||path.startsWith(`${href}/`);}
export default function WebAppShell({children,playStoreUrl}:{children:ReactNode;playStoreUrl?:string}){
 const pathname=usePathname();
 if(pathname==="/app/entrar")return <div className="srLoginFrame"><ThemeController/>{children}</div>;
 return <div className="srApp"><ThemeController/><aside className="srSidebar"><Link href="/app" className="srBrand" aria-label="Sr. Rotas — painel"><img src="/logo-srrotas.png" alt=""/><span><strong>Sr. Rotas</strong><small>Seu copiloto</small></span></Link><nav className="srNav" aria-label="Navegação do painel">{links.map(l=><Link key={l.href} href={l.href} className={active(pathname,l.href,l.exact)?"active":""}><NavIcon name={l.icon}/><span>{l.label}</span></Link>)}</nav><div className="srSidebarBottom"><Link href="/app/plano" className={active(pathname,"/app/plano")?"srPlanButton active":"srPlanButton"}><NavIcon name="plan"/><span>Plano e créditos</span></Link><span className="srBuilt">Sr. Rotas · BigCorps</span></div></aside><div className="srMain"><header className="srTopbar"><Link href="/app" className="srMobileBrand"><img src="/logo-srrotas.png" alt=""/><strong>Sr. Rotas</strong></Link><span className="srStage"><i/> Web conectado</span><Link href="/app/perfil" className="srAccountShortcut">Perfil <NavIcon name="external"/></Link></header>{playStoreUrl?<a className="srPlayBanner" href={playStoreUrl} target="_blank" rel="noreferrer"><img src="/logo-srrotas.png" alt=""/><span><strong>Baixe o app oficial Sr. Rotas</strong><small>Disponível no Google Play</small></span><b>Ver na Play Store →</b></a>:null}<main className="srContent">{children}</main></div><nav className="srBottomNav" aria-label="Navegação mobile">{links.map(l=><Link key={l.href} href={l.href} className={active(pathname,l.href,l.exact)?"active":""}><NavIcon name={l.icon}/><span>{l.label}</span></Link>)}</nav></div>;
}
