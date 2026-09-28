import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";
import DeleteAccountForm from "./DeleteAccountForm";

export const metadata: Metadata = { title: "Excluir conta e dados" };

export default function DeleteAccountPage() {
  return (
    <LegalPage
      kicker="Privacidade"
      title="Excluir conta e dados."
      intro="O Sr. Rotas permite iniciar e concluir a exclusão tanto pelo aplicativo quanto por esta página pública."
    >
      <h2>Exclusão pela Web</h2>
      <p>Entre com a mesma conta Sr. Rotas e confirme a exclusão. A ação é permanente e encerra o acesso da conta.</p>
      <DeleteAccountForm />

      <h2>O que é removido</h2>
      <p>O perfil e os dados vinculados à conta no backend são excluídos, incluindo dispositivos, preferências, jornadas, ofertas estruturadas, chaves MCP, carteira, sessões e registros vinculados pelo banco. A identidade de push no OneSignal precisa ser removida ou confirmada como inexistente antes de o servidor concluir a exclusão.</p>

      <h2>Prevenção a abuso após a exclusão</h2>
      <p>O identificador bruto do Android não é armazenado. Para prevenir repetição indevida de trial, o servidor mantém apenas um HMAC pseudônimo do aparelho. Ao excluir a conta, qualquer vínculo desse HMAC com a pessoa é removido. Se houver trial utilizado, o registro pseudônimo pode permanecer somente até o fim da janela antiabuso, atualmente configurada em até 365 dias desde o início do trial, quando passa a ser elegível para expurgo automático. Não usamos IMEI, serial ou MAC para essa finalidade.</p>

      <h2>No aparelho</h2>
      <p>Ao excluir pelo aplicativo, o Sr. Rotas também limpa histórico local, diagnósticos e capturas privadas do aparelho. Se a exclusão for feita somente pela Web, desinstalar o app ou limpar seus dados remove os arquivos que permanecerem apenas naquele aparelho.</p>

      <h2>Retenção limitada</h2>
      <p>Informações que precisem ser mantidas por obrigação legal, segurança, prevenção a fraude/abuso ou exercício regular de direitos ficam limitadas ao necessário e, quando possível, sem vínculo direto com a conta. Os detalhes estão na Política de Privacidade.</p>
    </LegalPage>
  );
}
