import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = { title: "Política de Privacidade" };

export default function PrivacyPage() {
  return <LegalPage kicker="Privacidade" title="Política de Privacidade do Sr. Rotas." intro="Última atualização: 18 de agosto de 2026. O Sr. Rotas é desenvolvido pela BigCorps e foi projetado para processar o mínimo necessário para analisar ofertas observadas pelo motorista.">
    <h2>1. Controlador e contato</h2><p>BigCorps. Contato de privacidade e suporte: <strong>contato@bigcorps.com.br</strong>.</p>
    <h2>2. Dados da conta</h2><p>Podemos tratar nome, e-mail, identificadores internos de conta e aparelhos, preferências, status de assinatura e registros necessários para autenticação e segurança. Senhas são administradas pela infraestrutura de autenticação do Supabase e não são armazenadas em texto puro pelo Sr. Rotas.</p>
    <h2>3. Captura da tela e OCR</h2><p>Durante uma jornada iniciada pelo próprio motorista, o Android solicita autorização de MediaProjection. As imagens da tela são processadas no aparelho por OCR para extrair campos estruturados de ofertas, como valor, distância, tempo, categoria e avaliação quando disponíveis. O Sr. Rotas não usa Serviço de Acessibilidade na versão de produção preparada a partir da 0.12.</p>
    <h2>4. Dados enviados ao backend</h2><p>Por padrão, o backend recebe os campos estruturados necessários ao histórico e analytics. O texto OCR bruto não é enviado na carga normal de ofertas. Diagnósticos brutos só são compartilhados quando o próprio usuário usa a ação explícita de compartilhar diagnóstico.</p>
    <h2>5. Capturas privadas</h2><p>A opção de salvar capturas privadas permanece desligada por padrão. Quando ativada, essas imagens ficam no armazenamento privado do aplicativo no aparelho e podem ser apagadas pelo usuário; não fazem parte do envio normal ao backend.</p>
    <h2>6. IA</h2><p>Quando o usuário pergunta à IA própria do Sr. Rotas, enviamos à OpenAI a pergunta e um contexto compacto de métricas/agregações e ofertas estruturadas necessárias para responder. O fluxo configurado usa a Responses API com armazenamento desativado na requisição do Sr. Rotas. OCR bruto não é incluído nesse contexto.</p>
    <h2>7. MCP</h2><p>Se o motorista gerar uma chave MCP, clientes externos compatíveis podem consultar ferramentas somente de leitura autorizadas para aquela conta. O cliente de IA escolhido pelo usuário possui suas próprias políticas e termos. Chaves MCP são armazenadas no servidor somente em forma de hash.</p>
    <h2>8. Pagamentos</h2><p>O pagamento do plano é iniciado no site e integrado à conta Banco Inter BigCorps por infraestrutura server-side. Tratamos dados de cobrança como txid, valor, status e datas necessários para conciliação e ativação. Credenciais bancárias não ficam no aplicativo Android.</p>
    <h2>9. Notificações</h2><p>Quando o OneSignal for ativado, poderemos tratar o identificador interno do motorista como External ID e tags operacionais não sensíveis para enviar notificações escolhidas pelo usuário. Não enviamos OCR bruto por push.</p>
    <h2>10. Finalidade e segurança</h2><p>Os dados são usados para autenticação, sincronização, cálculo e histórico de ofertas observadas, suporte, segurança, cobrança, IA quando solicitada e funcionalidades habilitadas pelo usuário. Usamos HTTPS, tokens por aparelho, RLS no banco e credenciais de serviço apenas no servidor.</p>
    <h2>11. Exclusão</h2><p>O usuário pode solicitar/executar a exclusão dentro do aplicativo em Perfil ou em <strong>srrotas.com/excluir-conta</strong>. Veja essa página para o escopo e efeitos da exclusão.</p>
    <h2>12. Observação importante</h2><p>O Sr. Rotas não controla o aplicativo da plataforma de transporte, não aceita nem recusa corridas e não comprova que uma oferta observada tenha sido aceita ou concluída.</p>
  </LegalPage>;
}
