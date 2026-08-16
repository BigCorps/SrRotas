import "./landing.css";

const features = [
  {
    title: "Decisão em segundos",
    text: "O Sr. Rotas transforma os dados visíveis da oferta em indicadores simples de rentabilidade, sem tocar nos botões do app de mobilidade.",
    icon: "↗",
    tag: "HUD em tempo real",
  },
  {
    title: "Sua estratégia, suas regras",
    text: "Defina mínimo por km, mínimo por hora, custo estimado por km, valor mínimo e outros limites para avaliar cada oportunidade.",
    icon: "◎",
    tag: "Metas personalizadas",
  },
  {
    title: "OCR local no aparelho",
    text: "A leitura da tela é processada no próprio Android. A imagem usada no OCR não precisa ser enviada para a nuvem.",
    icon: "◫",
    tag: "Privacidade primeiro",
  },
  {
    title: "Histórico estruturado",
    text: "As ofertas identificadas podem virar dados úteis para comparar horários, regiões e padrões ao longo das suas jornadas.",
    icon: "⌁",
    tag: "Aprendizado contínuo",
  },
  {
    title: "Pesquisa com IA",
    text: "Pergunte sobre seu próprio histórico em linguagem natural e transforme registros de corrida em respostas mais fáceis de usar.",
    icon: "✦",
    tag: "Em desenvolvimento",
  },
  {
    title: "MCP preparado",
    text: "A arquitetura já nasce preparada para consultas externas seguras e somente leitura por assistentes compatíveis com MCP.",
    icon: "◇",
    tag: "Integração inteligente",
  },
];

const steps = [
  ["01", "Inicie sua jornada", "Abra o Sr. Rotas e autorize a análise da tela durante o período em que estiver trabalhando."],
  ["02", "Receba uma oferta", "Quando uma oferta aparecer no aplicativo de mobilidade, o OCR identifica os dados disponíveis."],
  ["03", "Veja o semáforo", "O HUD apresenta os principais indicadores e classifica a oportunidade conforme as metas definidas por você."],
  ["04", "Aprenda com o histórico", "Com o tempo, seus próprios dados ajudam a entender em quais contextos aparecem as melhores oportunidades."],
];

export default function Home() {
  return (
    <main className="landing">
      <div className="noise" aria-hidden="true" />
      <header className="nav shell">
        <a className="brand" href="#inicio" aria-label="Sr. Rotas — início">
          <img src="/logo-srrotas.png" alt="" />
          <span>Sr. Rotas</span>
        </a>
        <nav className="navLinks" aria-label="Navegação principal">
          <a href="#recursos">Recursos</a>
          <a href="#como-funciona">Como funciona</a>
          <a href="#privacidade">Privacidade</a>
        </nav>
        <span className="alphaPill"><i /> Alpha fechado</span>
      </header>

      <section id="inicio" className="hero shell">
        <div className="heroCopy">
          <div className="eyebrow"><span>●</span> Inteligência para motoristas de aplicativo</div>
          <h1>
            Menos achismo.
            <br />
            <em>Mais clareza</em> em cada oferta.
          </h1>
          <p className="heroText">
            O Sr. Rotas é um copiloto inteligente que ajuda você a interpretar ofertas,
            comparar rentabilidade e construir uma visão mais clara da sua própria jornada.
          </p>

          <div className="heroActions">
            <a className="primaryButton" href="#como-funciona">
              Ver como funciona
              <span>→</span>
            </a>
            <div className="testingNote">
              <strong>2.0 Alpha</strong>
              <small>Primeiros testes reais em Android</small>
            </div>
          </div>

          <div className="trustRow">
            <span><b>✓</b> Leitura local</span>
            <span><b>✓</b> Sem aceitar corridas</span>
            <span><b>✓</b> Estratégia configurável</span>
          </div>
        </div>

        <div className="heroVisual" aria-label="Exemplo visual da análise de uma oferta">
          <div className="orb orbOne" />
          <div className="orb orbTwo" />

          <div className="phone">
            <div className="phoneTop">
              <span>9:41</span>
              <div className="phoneSensors"><i /><i /><i /></div>
            </div>
            <div className="phoneHeader">
              <img src="/logo-srrotas.png" alt="Sr. Rotas" />
              <div>
                <strong>Sr. Rotas</strong>
                <small>Jornada ativa</small>
              </div>
              <span className="onlineDot" />
            </div>

            <div className="offerLabel">OFERTA IDENTIFICADA</div>
            <div className="offerCard">
              <div className="offerTop">
                <div>
                  <small>Valor da oferta</small>
                  <strong>R$ 32,50</strong>
                </div>
                <span className="goodBadge">BOA</span>
              </div>
              <div className="metricGrid">
                <div><small>Total</small><strong>12 km</strong></div>
                <div><small>Tempo</small><strong>23 min</strong></div>
                <div><small>R$/km</small><strong>2,71</strong></div>
                <div><small>R$/hora</small><strong>84,78</strong></div>
              </div>
              <div className="profit">
                <span>Lucro estimado</span>
                <strong>R$ 22,30</strong>
              </div>
            </div>

            <div className="goalCard">
              <div className="goalTitle"><span>Meta da jornada</span><strong>68%</strong></div>
              <div className="progress"><i /></div>
              <small>Exemplo visual • valores ilustrativos</small>
            </div>
          </div>

          <div className="floatingCard floatKm">
            <span>R$/km</span>
            <strong>2,71</strong>
            <small>acima da meta</small>
          </div>
          <div className="floatingCard floatAi">
            <span className="spark">✦</span>
            <div><strong>Pesquisa IA</strong><small>seu histórico, explicado</small></div>
          </div>
        </div>
      </section>

      <section className="statStrip">
        <div className="shell statGrid">
          <div><strong>Local-first</strong><span>OCR processado no Android</span></div>
          <div><strong>Read-only</strong><span>Não aceita nem rejeita ofertas</span></div>
          <div><strong>Personalizável</strong><span>Você define suas próprias metas</span></div>
          <div><strong>Em evolução</strong><span>Alpha em testes reais</span></div>
        </div>
      </section>

      <section id="recursos" className="section shell">
        <div className="sectionHead">
          <div>
            <span className="kicker">RECURSOS</span>
            <h2>Um painel de decisão que cabe <em>na sua rotina.</em></h2>
          </div>
          <p>
            O foco é transformar números espalhados na tela em uma leitura rápida,
            respeitando as regras que fazem sentido para cada motorista.
          </p>
        </div>

        <div className="featureGrid">
          {features.map((feature) => (
            <article className="featureCard" key={feature.title}>
              <div className="featureIcon">{feature.icon}</div>
              <span className="featureTag">{feature.tag}</span>
              <h3>{feature.title}</h3>
              <p>{feature.text}</p>
            </article>
          ))}
        </div>
      </section>

      <section id="como-funciona" className="howSection">
        <div className="shell howGrid">
          <div className="howIntro">
            <span className="kicker light">COMO FUNCIONA</span>
            <h2>Da oferta ao indicador, <em>sem complicação.</em></h2>
            <p>
              O Alpha usa captura autorizada pelo Android, OCR local e um parser especializado
              para interpretar as informações exibidas durante a jornada.
            </p>
            <div className="miniNotice">
              <span>i</span>
              <p>
                Compatibilidade inicial em testes com Android. O comportamento pode variar
                conforme aparelho, versão do sistema e layout do aplicativo de mobilidade.
              </p>
            </div>
          </div>
          <div className="steps">
            {steps.map(([n, title, text]) => (
              <article className="step" key={n}>
                <span className="stepNumber">{n}</span>
                <div>
                  <h3>{title}</h3>
                  <p>{text}</p>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="privacidade" className="section shell privacySection">
        <div className="privacyVisual">
          <div className="shield">✓</div>
          <div className="privacyRings ring1" />
          <div className="privacyRings ring2" />
          <div className="privacyRings ring3" />
          <span className="privacyChip chipOne">OCR local</span>
          <span className="privacyChip chipTwo">Sem screenshot na nuvem</span>
          <span className="privacyChip chipThree">Consulta somente leitura</span>
        </div>
        <div className="privacyCopy">
          <span className="kicker">PRIVACIDADE</span>
          <h2>Projetado para analisar dados, <em>não para dirigir por você.</em></h2>
          <p>
            O Sr. Rotas não foi criado para aceitar, rejeitar ou executar corridas automaticamente.
            O objetivo é oferecer contexto para que a decisão continue sendo do motorista.
          </p>
          <ul>
            <li><span>✓</span><div><strong>Imagem temporária</strong><small>O frame usado pelo OCR é processado localmente e descartado após a leitura.</small></div></li>
            <li><span>✓</span><div><strong>Dados estruturados</strong><small>O backend trabalha com indicadores necessários para histórico e análise.</small></div></li>
            <li><span>✓</span><div><strong>MCP somente leitura</strong><small>As integrações previstas consultam dados; não controlam o aplicativo de mobilidade.</small></div></li>
          </ul>
        </div>
      </section>

      <section className="cta shell">
        <div>
          <span className="kicker light">SR. ROTAS 2.0 ALPHA</span>
          <h2>Estamos colocando o copiloto na estrada.</h2>
          <p>
            A versão atual está em testes fechados para validar leitura, OCR, cálculo e HUD
            em situações reais antes de ampliar o acesso.
          </p>
        </div>
        <div className="ctaStatus">
          <span className="pulseDot" />
          <div>
            <strong>Alpha em testes</strong>
            <small>Novidades em breve</small>
          </div>
        </div>
      </section>

      <footer className="footer shell">
        <div className="brand footerBrand">
          <img src="/logo-srrotas.png" alt="" />
          <span>Sr. Rotas</span>
        </div>
        <div className="footerCenter">
          <p>Seu copiloto inteligente para jornadas mais claras.</p>
          <nav className="footerLinks" aria-label="Links institucionais">
            <a href="/privacidade">Privacidade</a>
            <a href="/termos">Termos</a>
            <a href="/suporte">Suporte</a>
            <a href="/excluir-conta">Excluir conta</a>
          </nav>
        </div>
        <small>© 2026 Sr. Rotas • Desenvolvido pela BigCorps • contato@bigcorps.com.br</small>
      </footer>
    </main>
  );
}
