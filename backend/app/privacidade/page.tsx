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
      intro="Última atualização: 28 de setembro de 2026. Esta Política explica como o Sr. Rotas, desenvolvido pela BigCorps, trata dados pessoais e informações de uso necessárias para oferecer seus recursos de análise de corridas, jornadas, regiões, estatísticas, inteligência artificial e serviços associados."
    >
      <h2>1. Controlador e contato</h2>
      <p>O Sr. Rotas é desenvolvido e operado pela <strong>BigCorps</strong>. Questões de privacidade, proteção de dados ou suporte podem ser enviadas para <strong>contato@bigcorps.com.br</strong>.</p>

      <h2>2. Princípios de privacidade</h2>
      <p>O Sr. Rotas foi projetado para processar apenas os dados necessários às suas funcionalidades. Não vendemos dados pessoais e não utilizamos o conteúdo capturado da tela para publicidade.</p>
      <p>Sempre que possível, imagem e OCR são processados localmente no aparelho. O Sr. Rotas não utiliza Serviço de Acessibilidade (AccessibilityService) como mecanismo de leitura de ofertas na versão de produção.</p>

      <h2>3. Dados da conta, aparelho e prevenção a abuso</h2>
      <p>Podemos tratar nome, e-mail, identificadores internos da conta, preferências, plano, trial, créditos, versão do aplicativo e informações técnicas necessárias à segurança, sincronização e suporte.</p>
      <p>Para reconhecer aparelhos vinculados, limitar dispositivos e evitar a repetição indevida do trial, o Android fornece ao aplicativo um identificador escopado ao app/dispositivo. O valor bruto não é armazenado no backend. O servidor transforma esse valor em um <strong>HMAC-SHA256 pseudônimo</strong> usando segredo restrito ao servidor.</p>
      <p>O Sr. Rotas não usa IMEI, número de série, endereço MAC nem lista de aplicativos instalados para essa finalidade.</p>
      <p>Quando uma conta é excluída, o vínculo entre esse identificador pseudônimo e a conta é removido. Se o aparelho tiver utilizado trial, o registro pseudônimo pode permanecer apenas até o fim da janela antiabuso aplicável, atualmente configurada em até 365 dias desde o início do trial. Após o prazo, um processo de retenção remove o registro sem vínculo. Identidades sem trial e sem conta vinculada são elegíveis para expurgo após 30 dias.</p>
      <p>As senhas são administradas pela infraestrutura de autenticação do Supabase e não são armazenadas em texto puro pelo Sr. Rotas.</p>

      <h2>4. Captura de tela, MediaProjection e OCR</h2>
      <p>Ao iniciar uma jornada ou recurso de digitalização, o Android pode solicitar autorização explícita para captura de tela por <strong>MediaProjection</strong>. Os frames são processados por OCR para reconhecer campos visíveis como valor, distância, duração, categoria, busca, destino e avaliação.</p>
      <p>O texto OCR bruto e a imagem completa da tela não fazem parte do envio normal de ofertas ao backend.</p>

      <h2>5. Ofertas, jornadas e histórico</h2>
      <p>Para histórico, sincronização e análises, podemos armazenar dados estruturados reconhecidos ou informados pelo usuário, incluindo valor, distância, duração, categoria, indicadores calculados, origem/destino/região quando disponíveis, data/hora, estado da jornada, odômetro e gastos de combustível/recarga.</p>
      <p>Esses dados representam informações observadas ou registradas. Não comprovam, por si só, corrida aceita, concluída, paga ou lucro efetivo.</p>

      <h2>6. Localização e inteligência regional</h2>
      <p>Com autorização, o Sr. Rotas utiliza <strong>localização aproximada</strong> durante a jornada para Agora, inteligência regional, exposição por região e contextualização de ofertas. Localização precisa não é requisito normal da experiência atual.</p>

      <h2>7. Base Pessoal e Base Coletiva</h2>
      <p>A Base Pessoal usa o histórico do próprio motorista. A participação na Base Coletiva é opcional e utiliza informações estruturadas em estatísticas agregadas. As visualizações coletivas atuais exigem pelo menos <strong>3 motoristas distintos</strong> para publicar uma combinação.</p>

      <h2>8. Capturas privadas</h2>
      <p>Salvar screenshots permanece <strong>desligado por padrão</strong>. Quando habilitado, as imagens ficam no armazenamento privado/local conforme a configuração do app e não integram o envio normal ao backend.</p>

      <h2>9. Digitalização de jornada e histórico</h2>
      <p>A digitalização utiliza captura autorizada pelo Android e OCR local. O usuário pode revisar e corrigir dados reconhecidos. Imagens e OCR bruto não são enviados automaticamente como parte normal desse processo.</p>

      <h2>10. Inteligência artificial</h2>
      <p>Quando o usuário pergunta à IA do Sr. Rotas, podemos enviar à <strong>OpenAI</strong> a pergunta e um contexto compacto com métricas e informações estruturadas necessárias à resposta. O fluxo usa a Responses API com armazenamento desativado na requisição (<strong>store: false</strong>). OCR bruto e screenshots não fazem parte do contexto normal.</p>
      <p>O uso da IA é iniciado pelo usuário e pode consumir créditos conforme o plano.</p>

      <h2>11. Assistente Ativo</h2>
      <p>Quando habilitado, o Assistente Ativo usa contexto regional, estatísticas pessoais e, quando autorizado, informações coletivas agregadas para apresentar sugestões. O usuário controla a ativação e o intervalo.</p>

      <h2>12. MCP e integrações autorizadas</h2>
      <p>Se o motorista gerar uma chave MCP, clientes externos compatíveis podem consultar ferramentas autorizadas. A integração atual é somente de leitura. As chaves MCP são armazenadas no servidor somente em forma de hash.</p>

      <h2>13. Notificações</h2>
      <p>Quando habilitadas, notificações podem usar <strong>OneSignal</strong>. O identificador interno do motorista pode ser usado como External ID, acompanhado de tags operacionais como versão, pareamento, estratégia, sincronização e preferências. OCR bruto não é enviado por push.</p>

      <h2>14. Pagamentos e assinatura</h2>
      <p>A contratação pode ser processada por infraestrutura server-side integrada ao Banco Inter. Tratamos identificadores da cobrança, valor, status e datas necessários à conciliação e ativação do plano. Credenciais bancárias não ficam armazenadas no Android.</p>

      <h2>15. Diagnósticos, feedback e suporte</h2>
      <p>Podemos tratar versão do aplicativo, modelo do aparelho, estado de sincronização, feedback e informações técnicas de crash quando o aplicativo as envia. O console administrativo não expõe OCR bruto, screenshots, coordenadas, tokens ou o HMAC do aparelho nesses registros.</p>

      <h2>16. Com quem os dados podem ser compartilhados</h2>
      <ul>
        <li><strong>Supabase</strong>, para autenticação, banco e infraestrutura associada;</li>
        <li><strong>Vercel</strong>, para hospedagem e backend;</li>
        <li><strong>OpenAI</strong>, somente quando o usuário utiliza a IA;</li>
        <li><strong>OneSignal</strong>, quando notificações estão habilitadas;</li>
        <li><strong>Banco Inter</strong> ou outro provedor informado, para pagamentos;</li>
        <li>clientes MCP ou integrações escolhidas pelo próprio usuário.</li>
      </ul>
      <p>Não vendemos dados pessoais a terceiros.</p>

      <h2>17. Finalidades e bases</h2>
      <p>Tratamos dados para prestar o serviço, autenticar, sincronizar, calcular métricas, manter histórico, oferecer recursos regionais, responder IA, enviar notificações, processar pagamentos, prestar suporte, prevenir abuso e manter segurança. A base legal aplicável depende da atividade e pode incluir execução do serviço, obrigação legal, exercício regular de direitos, legítimo interesse ou consentimento quando necessário.</p>

      <h2>18. Armazenamento, retenção e exclusão</h2>
      <p>Dados associados à conta podem ser mantidos enquanto ela estiver ativa e pelo período necessário às finalidades descritas. Após exclusão válida, o perfil e dados vinculados são removidos, ressalvadas informações que precisem ser conservadas de forma limitada por obrigação legal, prevenção a fraude/abuso, segurança, resolução de disputas ou exercício regular de direitos.</p>
      <p>Logs técnicos que permanecem após exclusão devem ficar sem vínculo direto com a conta. O HMAC antiabuso segue a retenção específica descrita na seção 3 e é expurgado automaticamente após a janela aplicável.</p>
      <p>Dados apenas locais podem permanecer no aparelho quando a exclusão é feita exclusivamente pela Web; desinstalar ou limpar os dados do Android remove esses arquivos locais.</p>

      <h2>19. Exclusão da conta</h2>
      <p>O usuário pode excluir conta e dados pelo aplicativo ou em <strong>srrotas.com/excluir-conta</strong>. O fluxo remove perfil e dados vinculados no backend, dispositivos, preferências, jornadas, ofertas, métricas, chaves MCP e sessões, e também exige a remoção/ausência da identidade OneSignal antes de concluir a exclusão server-side.</p>

      <h2>20. Segurança</h2>
      <p>Adotamos HTTPS, autenticação, tokens por aparelho, RLS, acesso server-only por credenciais de serviço, HMAC de dispositivo e armazenamento de chaves MCP somente em hash. Nenhum sistema é totalmente imune a incidentes.</p>

      <h2>21. Transferências internacionais</h2>
      <p>Alguns prestadores podem processar dados em outros países. Buscamos utilizar provedores reconhecidos e mecanismos compatíveis com as exigências aplicáveis.</p>

      <h2>22. Direitos do titular</h2>
      <p>Nos termos da LGPD e demais normas aplicáveis, o usuário pode solicitar confirmação, acesso, correção, exclusão quando cabível, informações sobre compartilhamentos e outros direitos previstos em lei. Solicitações: <strong>contato@bigcorps.com.br</strong>.</p>

      <h2>23. Plataformas de terceiros</h2>
      <p>O Sr. Rotas é independente e não é afiliado, patrocinado ou operado por Uber, 99 ou outras plataformas citadas por compatibilidade. Não aceita nem recusa corridas e não executa decisões autônomas em nome do motorista.</p>

      <h2>24. Alterações desta Política</h2>
      <p>Esta Política pode ser atualizada quando houver novos recursos, fornecedores ou ajustes legais/operacionais. A data mais recente aparece no início da página.</p>

      <h2>25. Contato</h2>
      <p>Dúvidas sobre privacidade e tratamento de dados: <strong>contato@bigcorps.com.br</strong>.</p>
    </LegalPage>
  );
}
