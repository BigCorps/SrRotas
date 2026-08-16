import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = { title: "Exclusão de dados" };

export default function DeleteAccountPage() {
  const email = process.env.NEXT_PUBLIC_SUPPORT_EMAIL?.trim() || "contato@srrotas.com";
  return <LegalPage kicker="Dados" title="Solicitar exclusão." intro="O Alpha atual usa pareamento por aparelho e ainda não oferece cadastro público de conta no site. Por isso, a exclusão ainda é tratada pela equipe de testes.">
    <h2>Como solicitar</h2><p>Envie uma solicitação para <strong>{email}</strong>, identificando o aparelho/participante do Alpha de forma suficiente para localizar os dados. Não envie tokens, senhas ou chaves secretas por e-mail.</p>
    <h2>Escopo</h2><p>A solicitação poderá abranger o cadastro de motorista de teste, dispositivos pareados, preferências, jornadas, ofertas estruturadas e registros de auditoria relacionados, observadas obrigações legais ou de segurança aplicáveis.</p>
    <h2>Dados locais</h2><p>O histórico diagnóstico armazenado apenas no aparelho pode ser removido desinstalando o aplicativo ou limpando os dados do Sr. Rotas nas configurações do Android.</p>
    <h2>Antes do lançamento</h2><p>Quando houver autenticação pública, esta página deverá ser conectada a um fluxo autenticado de exclusão e atualizada com prazos e política de retenção definitivos.</p>
  </LegalPage>;
}
