import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";
import DeleteAccountForm from "./DeleteAccountForm";

export const metadata: Metadata = { title: "Excluir conta e dados" };

export default function DeleteAccountPage() {
  return <LegalPage kicker="Privacidade" title="Excluir conta e dados." intro="O Sr. Rotas permite iniciar e concluir a exclusão tanto pelo aplicativo quanto por esta página pública.">
    <h2>Exclusão pela Web</h2>
    <p>Entre com a mesma conta Sr. Rotas e confirme a exclusão. A ação é permanente e encerra o acesso da conta.</p>
    <DeleteAccountForm />
    <h2>O que é removido</h2>
    <p>O perfil do motorista e os dados associados ao identificador da conta no backend são excluídos, incluindo dispositivos, preferências, jornadas, ofertas estruturadas, chaves MCP, carteira e registros vinculados pelo banco. Quando o OneSignal estiver ativo, a identidade de push associada ao mesmo identificador também é solicitada para exclusão.</p>
    <h2>No aparelho</h2>
    <p>Ao excluir pelo próprio aplicativo, o Sr. Rotas também limpa histórico local, diagnósticos e capturas privadas do aparelho. Se a exclusão for feita somente pela Web, desinstalar o app ou limpar os dados do Android remove os arquivos que permanecerem apenas naquele aparelho.</p>
    <h2>Retenção legal</h2>
    <p>Se futuramente houver dados que precisem ser mantidos por obrigação legal, fiscal, prevenção a fraude ou exercício regular de direitos, essa retenção será limitada ao necessário e descrita na Política de Privacidade. O fluxo atual de Closed Beta não depende de retenção de OCR bruto.</p>
  </LegalPage>;
}
