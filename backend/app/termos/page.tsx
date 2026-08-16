import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = { title: "Termos de uso — Alpha" };

export default function TermsPage() {
  return <LegalPage kicker="Termos do Alpha" title="O motorista continua no controle." intro="Estes termos resumem o comportamento técnico esperado da versão de testes do Sr. Rotas e não substituem a versão jurídica definitiva do lançamento público.">
    <h2>Finalidade</h2><p>O Sr. Rotas é uma ferramenta de apoio à interpretação de ofertas e à análise do histórico do próprio motorista. Indicadores e classificações são estimativas baseadas nos dados que o aplicativo conseguiu ler.</p>
    <h2>Decisão humana</h2><p>O aplicativo não deve ser tratado como mecanismo automático de aceite ou recusa. A decisão sobre qualquer oferta permanece exclusivamente com o motorista.</p>
    <h2>Precisão do Alpha</h2><p>OCR e parsing podem falhar conforme aparelho, versão do Android, layout da plataforma, qualidade de renderização e alterações feitas por terceiros. O usuário deve conferir os dados exibidos na plataforma antes de tomar decisões.</p>
    <h2>Dados observados</h2><p>Uma oferta registrada significa apenas que ela foi observada pelo Sr. Rotas. Isso não comprova que a corrida foi aceita, iniciada, concluída ou paga.</p>
    <h2>Plataformas de mobilidade</h2><p>O Sr. Rotas é um produto independente. O usuário é responsável por cumprir os termos, regras e exigências das plataformas de mobilidade que utiliza.</p>
    <h2>Testes fechados</h2><p>A versão Alpha pode mudar rapidamente, apresentar indisponibilidade e exigir reinstalação ou atualização. Não deve ser usada como única fonte para decisões financeiras ou operacionais.</p>
  </LegalPage>;
}
