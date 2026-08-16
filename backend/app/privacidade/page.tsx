import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = { title: "Privacidade" };

export default function PrivacyPage() {
  return <LegalPage kicker="Privacidade" title="Privacidade por padrão." intro="O Alpha foi desenhado para manter a imagem usada pelo OCR no aparelho e sincronizar, por padrão, apenas dados estruturados necessários para histórico e análise.">
    <h2>O que o aplicativo processa</h2>
    <p>Durante uma jornada autorizada pelo motorista, o Android disponibiliza a tela ao Sr. Rotas por MediaProjection. O OCR é executado localmente no aparelho para identificar textos e números de ofertas visíveis.</p>
    <h2>Imagem e texto bruto</h2>
    <p>Os frames de tela são temporários e não são enviados ao backend pelo fluxo normal. O texto bruto do OCR fica localmente para diagnóstico. O compartilhamento de diagnóstico é uma ação explícita do usuário.</p>
    <h2>Dados estruturados</h2>
    <p>Quando uma oferta é reconhecida, podem ser sincronizados valor, distâncias, tempos, indicadores calculados, classificação, nível de confiança, plataforma, horário, método de captura e identificadores técnicos de deduplicação e jornada.</p>
    <h2>O que o Sr. Rotas não faz</h2>
    <ul><li>Não aceita nem rejeita ofertas automaticamente.</li><li>Não executa corridas em nome do motorista.</li><li>Não considera uma oferta observada como corrida aceita ou concluída.</li><li>Não precisa enviar screenshot para a nuvem para realizar o OCR principal.</li></ul>
    <h2>IA e MCP</h2>
    <p>A Pesquisa IA usa dados estruturados já sincronizados. As ferramentas MCP do Alpha são somente leitura. As respostas devem distinguir ofertas observadas de ganhos ou corridas efetivamente realizadas.</p>
    <h2>Segurança</h2>
    <p>Tokens de aparelho são armazenados no servidor somente como hash. Segredos de servidor, como a chave service role do Supabase, não devem ser incorporados ao APK nem publicados no GitHub.</p>
    <h2>Fase Alpha</h2>
    <p>Esta página acompanha a arquitetura atual do Alpha e deverá ser revisada antes do lançamento público, inclusive para refletir autenticação, retenção, exclusão e quaisquer novas integrações efetivamente habilitadas.</p>
  </LegalPage>;
}
