import Link from "next/link";
import NavIcon from "./_components/NavIcon";

const cards = [
  { title: "Histórico", text: "Jornadas, ofertas, filtros e comparações estruturadas.", href: "/app/historico", icon: "history" as const },
  { title: "Pesquisa IA", text: "Pergunte sobre seus próprios dados quando a sessão 1.0 estiver conectada.", href: "/app/ia", icon: "ai" as const },
  { title: "MCP", text: "Use seus dados em assistentes compatíveis, sempre em modo leitura.", href: "/app/mcp", icon: "mcp" as const },
  { title: "Plano", text: "Trial, assinatura, créditos e status comercial em uma só tela.", href: "/app/plano", icon: "plan" as const },
];

export default function AppHome() {
  return <>
    <section className="srPageHead"><div><span className="srEyebrow">PAINEL WEB</span><h1>Seu Sr. Rotas, também fora da rua.</h1><p>O motor de oferta continua no Android. Histórico, IA, MCP e conta passam a evoluir aqui, sem depender de atualização do APK.</p></div><div className="srStatusCard"><span className="srStatusDot"/><div><strong>Fundação Web ativa</strong><small>A conexão automática com a sessão do app entra após o fechamento do beta.</small></div></div></section>

    <section className="srGrid4">{cards.map(card => <Link className="srFeature" href={card.href} key={card.href}><span className="srFeatureIcon"><NavIcon name={card.icon}/></span><h2>{card.title}</h2><p>{card.text}</p><b>Abrir →</b></Link>)}</section>

    <section className="srGrid2 srSectionGap"><article className="srPanel"><div className="srPanelHead"><div><span className="srEyebrow">JORNADAS</span><h2>Resumo do período</h2></div><span className="srMutedPill">Aguardando sessão</span></div><div className="srMetrics"><div><span>Ofertas</span><strong>—</strong></div><div><span>Boas</span><strong>—</strong></div><div><span>R$/km médio</span><strong>—</strong></div><div><span>R$/h médio</span><strong>—</strong></div></div><div className="srEmpty"><span>⌁</span><strong>Seus dados aparecerão aqui</strong><p>Esta etapa prepara a interface. Nenhum dado privado é exposto até ativarmos o handoff seguro Native → Web na 1.0.</p></div></article>
    <article className="srPanel srDarkPanel"><span className="srEyebrow light">ARQUITETURA 1.0</span><h2>O app faz o trabalho pesado. O Web mostra o valor.</h2><p>MediaProjection, OCR, parser e HUD continuam locais. Este painel recebe somente os dados estruturados já autorizados e sincronizados.</p><ul><li><b>Native</b><span>OCR + HUD + jornada</span></li><li><b>Web</b><span>Histórico + IA + MCP + conta</span></li><li><b>Backend</b><span>Trial + acesso + Pix + créditos</span></li></ul></article></section>
  </>;
}
