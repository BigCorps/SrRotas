import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = {
  title: "Política de Privacidade",
  description:
    "Política de Privacidade do Sr. Rotas, aplicativo desenvolvido pela BigCorps.",
};

export default function PrivacyPage() {
  return (
    <LegalPage
      kicker="Privacidade"
      title="Política de Privacidade do Sr. Rotas."
      intro="Última atualização: 8 de setembro de 2026. Esta Política explica como o Sr. Rotas, desenvolvido pela BigCorps, trata dados pessoais e informações de uso necessárias para oferecer seus recursos de análise de corridas, jornadas, regiões, estatísticas, inteligência artificial e serviços associados."
    >
      <h2>1. Controlador e contato</h2>
      <p>
        O Sr. Rotas é desenvolvido e operado pela <strong>BigCorps</strong>.
        Para questões de privacidade, proteção de dados ou suporte, entre em
        contato pelo e-mail <strong>contato@bigcorps.com.br</strong>.
      </p>

      <h2>2. Princípios de privacidade</h2>
      <p>
        O Sr. Rotas foi projetado para processar apenas os dados necessários
        para suas funcionalidades. O aplicativo não vende dados pessoais e não
        utiliza o conteúdo capturado da tela para publicidade.
      </p>
      <p>
        Sempre que possível, o processamento de imagem e OCR ocorre localmente
        no aparelho. O Sr. Rotas não utiliza Serviço de Acessibilidade
        (AccessibilityService) como mecanismo de leitura de ofertas na versão
        de produção.
      </p>

      <h2>3. Dados da conta e do dispositivo</h2>
      <p>Podemos tratar:</p>
      <ul>
        <li>nome e endereço de e-mail;</li>
        <li>identificadores internos da conta e do motorista;</li>
        <li>identificadores dos aparelhos vinculados;</li>
        <li>preferências e configurações do aplicativo;</li>
        <li>informações sobre plano, trial e créditos de IA;</li>
        <li>versão do aplicativo e informações técnicas necessárias à segurança, sincronização e suporte.</li>
      </ul>
      <p>
        As senhas são administradas pela infraestrutura de autenticação do
        Supabase e não são armazenadas em texto puro pelo Sr. Rotas.
      </p>

      <h2>4. Captura de tela, MediaProjection e OCR</h2>
      <p>
        Quando o motorista inicia uma jornada ou utiliza um recurso de
        digitalização, o Android pode solicitar autorização explícita para
        captura de tela por <strong>MediaProjection</strong>.
      </p>
      <p>
        Os frames capturados são processados por OCR para reconhecer informações
        exibidas por aplicativos compatíveis, como valor da oferta, distância,
        duração, categoria, busca, destino, avaliação e outros campos disponíveis
        na tela.
      </p>
      <p>
        O OCR principal e os mecanismos locais de contingência são executados
        sobre a captura autorizada pelo usuário. O texto OCR bruto e a imagem
        completa da tela não fazem parte do envio normal de ofertas ao backend.
      </p>

      <h2>5. Ofertas, jornadas e histórico</h2>
      <p>
        Para permitir histórico, estatísticas, sincronização e análises, o
        Sr. Rotas pode armazenar dados estruturados reconhecidos ou informados
        pelo usuário, incluindo:
      </p>
      <ul>
        <li>valor, distância, duração e categoria de ofertas observadas;</li>
        <li>indicadores calculados, como R$/km, R$/hora e R$/min;</li>
        <li>origem, destino, região e contexto associados à oferta, quando disponíveis;</li>
        <li>data e horário das observações;</li>
        <li>estado e duração das jornadas;</li>
        <li>odômetro inicial e final e distância percorrida;</li>
        <li>gastos informados com combustível, abastecimento ou recarga;</li>
        <li>correções realizadas pelo próprio usuário em registros digitalizados.</li>
      </ul>
      <p>
        Esses dados representam informações observadas ou registradas no
        aplicativo. Eles não comprovam, por si só, que uma corrida foi aceita,
        concluída, paga ou que determinado ganho foi efetivamente obtido.
      </p>

      <h2>6. Localização e inteligência regional</h2>
      <p>
        Com autorização do usuário, o Sr. Rotas utiliza
        <strong> localização aproximada</strong> durante a jornada para recursos
        como Agora, inteligência regional, exposição por região, sugestões de
        locais e contextualização de ofertas.
      </p>
      <p>
        A localização é utilizada para produzir contexto regional e estatístico.
        O aplicativo não solicita localização precisa como requisito normal da
        experiência atual.
      </p>

      <h2>7. Base Pessoal e Base Coletiva</h2>
      <p>
        A <strong>Base Pessoal</strong> utiliza o histórico do próprio motorista
        para gerar estatísticas e recomendações relacionadas às regiões, horários
        e condições observadas em sua conta.
      </p>
      <p>
        A participação na <strong>Base Coletiva</strong> é opcional. Quando o
        usuário opta por participar, informações estruturadas podem contribuir
        para estatísticas agregadas de regiões, horários e perfis de serviço.
      </p>
      <p>
        A Base Coletiva não foi projetada para mostrar a outros usuários o
        histórico individual de um motorista. As consultas coletivas utilizam
        agregação e guardrails de privacidade; as visualizações atuais exigem
        pelo menos <strong>3 motoristas distintos</strong> para publicar uma
        combinação coletiva.
      </p>
      <p>
        O usuário pode alterar sua participação na Base Coletiva pelas
        configurações disponíveis no aplicativo.
      </p>

      <h2>8. Capturas privadas</h2>
      <p>
        A opção de salvar screenshots de corridas permanece
        <strong> desligada por padrão</strong>. Quando ativada, as imagens ficam
        no armazenamento privado do aplicativo no aparelho e podem ser apagadas
        pelo usuário. Elas não fazem parte do envio normal ao backend.
      </p>

      <h2>9. Digitalização de jornada e histórico</h2>
      <p>
        Os recursos de digitalização utilizam a captura autorizada pelo Android
        e OCR local para tentar reconhecer informações apresentadas nas telas.
        O usuário pode revisar e corrigir os dados reconhecidos.
      </p>
      <p>
        Os dados estruturados resultantes podem ser armazenados e sincronizados
        com a conta para compor histórico e estatísticas. Imagens e OCR bruto não
        são enviados automaticamente como parte normal desse processo.
      </p>

      <h2>10. Inteligência artificial</h2>
      <p>
        Quando o usuário envia uma pergunta à IA do Sr. Rotas, enviamos à
        <strong> OpenAI</strong> a pergunta e um contexto compacto necessário à
        resposta, que pode incluir métricas, agregações e informações estruturadas
        da conta.
      </p>
      <p>
        O fluxo configurado pelo Sr. Rotas utiliza a Responses API com
        <strong> armazenamento desativado na requisição (store: false)</strong>.
        OCR bruto e screenshots não são incluídos no contexto normal enviado à IA.
      </p>
      <p>
        O uso da IA é iniciado pelo próprio usuário e pode consumir créditos
        conforme o plano aplicável.
      </p>

      <h2>11. Assistente Ativo</h2>
      <p>
        Quando habilitado, o Assistente Ativo utiliza informações disponíveis
        no Sr. Rotas, como contexto regional e estatísticas pessoais e, quando o
        usuário participa, informações coletivas agregadas, para apresentar
        sugestões no aplicativo.
      </p>
      <p>
        A ativação do Assistente e o intervalo de apresentação das sugestões
        podem ser ajustados pelo usuário.
      </p>

      <h2>12. MCP e integrações autorizadas</h2>
      <p>
        Se o motorista gerar uma chave MCP, clientes externos compatíveis podem
        consultar as ferramentas autorizadas para sua conta. A integração atual
        foi projetada para acesso somente de leitura.
      </p>
      <p>
        As chaves MCP são armazenadas no servidor somente em forma de hash.
        O cliente de IA ou serviço externo escolhido pelo usuário possui suas
        próprias políticas e termos.
      </p>

      <h2>13. Notificações</h2>
      <p>
        Quando as notificações estiverem habilitadas, o Sr. Rotas pode utilizar
        o <strong>OneSignal</strong> para enviar alertas e resumos escolhidos pelo
        usuário.
      </p>
      <p>
        Para isso, podem ser usados um identificador interno do motorista como
        External ID e tags operacionais, como plataforma, versão do app, estado
        de pareamento, estratégia, sincronização pendente e preferências de
        notificação. OCR bruto não é enviado por push.
      </p>

      <h2>14. Pagamentos e assinatura</h2>
      <p>
        Quando houver contratação de plano, o fluxo de pagamento pode ser
        iniciado pelo site e processado por infraestrutura server-side integrada
        à conta da BigCorps no Banco Inter ou por outro meio informado no momento
        da contratação.
      </p>
      <p>
        Podemos tratar identificadores da cobrança, valor, status e datas
        necessários para conciliação, ativação do plano e suporte. Credenciais
        bancárias não ficam armazenadas no aplicativo Android.
      </p>

      <h2>15. Diagnósticos, feedback e suporte</h2>
      <p>
        O Sr. Rotas pode tratar informações técnicas necessárias para
        diagnóstico, estabilidade e suporte, como versão do aplicativo, estado
        de sincronização e mensagens técnicas de erro.
      </p>
      <p>
        Screenshots, OCR bruto, tokens, senhas e chaves MCP não são incluídos
        automaticamente nos relatórios normais de crash ou feedback. Conteúdo
        adicional só deve ser compartilhado quando o próprio usuário optar por
        enviá-lo em uma ação explícita.
      </p>

      <h2>16. Com quem os dados podem ser compartilhados</h2>
      <p>
        Utilizamos prestadores de serviço apenas quando necessários às
        funcionalidades do Sr. Rotas. Dependendo do recurso utilizado, isso pode
        incluir:
      </p>
      <ul>
        <li><strong>Supabase</strong>, para autenticação, banco de dados e infraestrutura associada;</li>
        <li><strong>Vercel</strong>, para hospedagem e execução do backend e do site;</li>
        <li><strong>OpenAI</strong>, somente quando o usuário utiliza a IA do Sr. Rotas;</li>
        <li><strong>OneSignal</strong>, quando notificações estiverem habilitadas;</li>
        <li><strong>Banco Inter</strong> ou outro provedor informado, quando necessário ao fluxo de pagamento;</li>
        <li>clientes MCP ou integrações externas escolhidas e autorizadas pelo próprio usuário.</li>
      </ul>
      <p>
        Não vendemos dados pessoais a terceiros.
      </p>

      <h2>17. Finalidades e bases para o tratamento</h2>
      <p>
        Tratamos dados para fornecer e manter o serviço, autenticar contas,
        sincronizar informações, calcular métricas, gerar histórico e
        estatísticas, oferecer recursos regionais, responder solicitações de IA,
        enviar notificações escolhidas pelo usuário, processar pagamentos,
        prestar suporte, prevenir abuso e manter a segurança do serviço.
      </p>
      <p>
        Conforme a natureza da atividade e a legislação aplicável, o tratamento
        pode se apoiar na execução do serviço solicitado pelo usuário,
        cumprimento de obrigações legais ou regulatórias, exercício regular de
        direitos, legítimo interesse e, quando necessário, consentimento ou outra
        base legal prevista na legislação.
      </p>

      <h2>18. Armazenamento, retenção e exclusão</h2>
      <p>
        Os dados da conta, histórico, jornadas, ofertas estruturadas, métricas,
        preferências e demais registros associados podem ser mantidos enquanto
        a conta estiver ativa e pelo período necessário às finalidades descritas
        nesta Política.
      </p>
      <p>
        Após uma solicitação válida de exclusão, os dados associados à conta são
        removidos conforme o fluxo de exclusão, ressalvadas informações cuja
        conservação seja necessária para cumprimento de obrigação legal,
        prevenção a fraude, segurança, resolução de disputas ou exercício
        regular de direitos.
      </p>
      <p>
        Dados armazenados apenas localmente no aparelho podem permanecer no
        dispositivo quando a exclusão é feita exclusivamente pela Web. Nessa
        situação, desinstalar o aplicativo ou limpar seus dados remove os
        arquivos locais remanescentes.
      </p>

      <h2>19. Exclusão da conta</h2>
      <p>
        O usuário pode solicitar e concluir a exclusão da conta e dos dados pelo
        próprio aplicativo ou pela página pública:
        <strong> srrotas.com/excluir-conta</strong>.
      </p>
      <p>
        A exclusão abrange o perfil e os dados vinculados à conta no backend,
        incluindo, conforme aplicável, dispositivos, preferências, jornadas,
        ofertas estruturadas, métricas, chaves MCP e registros associados.
      </p>

      <h2>20. Segurança</h2>
      <p>
        Adotamos medidas técnicas e organizacionais compatíveis com o serviço,
        incluindo HTTPS, autenticação, tokens por aparelho, Row Level Security
        (RLS) no banco, credenciais de serviço restritas ao servidor e
        armazenamento de chaves MCP somente em forma de hash.
      </p>
      <p>
        Nenhum sistema é totalmente imune a incidentes. Em caso de evento de
        segurança relevante, adotaremos as providências cabíveis conforme a
        legislação aplicável.
      </p>

      <h2>21. Transferências internacionais</h2>
      <p>
        Alguns prestadores de infraestrutura e tecnologia utilizados pelo
        Sr. Rotas podem processar dados em outros países. Quando isso ocorrer,
        buscamos utilizar provedores reconhecidos e mecanismos compatíveis com
        as exigências aplicáveis de proteção de dados.
      </p>

      <h2>22. Direitos do titular</h2>
      <p>
        Nos termos da legislação aplicável, inclusive a Lei Geral de Proteção de
        Dados Pessoais (LGPD), o usuário pode solicitar informações e exercer
        direitos relacionados aos seus dados, como confirmação de tratamento,
        acesso, correção, exclusão quando cabível, informação sobre
        compartilhamentos e demais direitos previstos em lei.
      </p>
      <p>
        Solicitações podem ser enviadas para
        <strong> contato@bigcorps.com.br</strong>.
      </p>

      <h2>23. Serviços e plataformas de terceiros</h2>
      <p>
        O Sr. Rotas é uma ferramenta independente. Não é afiliado, patrocinado
        ou operado por Uber, 99 ou outras plataformas de transporte mencionadas
        apenas para fins de compatibilidade e identificação das informações
        exibidas pelo usuário.
      </p>
      <p>
        O Sr. Rotas não controla o aplicativo da plataforma de transporte, não
        aceita nem recusa corridas e não executa decisões autônomas em nome do
        motorista.
      </p>

      <h2>24. Alterações desta Política</h2>
      <p>
        Esta Política pode ser atualizada quando o Sr. Rotas ganhar novos
        recursos, quando houver mudanças nos fornecedores utilizados ou quando
        forem necessários ajustes legais ou operacionais. A data da versão mais
        recente será indicada no início desta página.
      </p>

      <h2>25. Contato</h2>
      <p>
        Dúvidas sobre esta Política ou sobre o tratamento de dados podem ser
        encaminhadas para <strong>contato@bigcorps.com.br</strong>.
      </p>
    </LegalPage>
  );
}
