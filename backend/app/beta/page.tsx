import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = {
  title: "Closed Beta",
  robots: { index: false, follow: false },
};

export default function BetaPage() {
  return <LegalPage
    kicker="Closed Beta"
    title="Teste completo do Sr. Rotas 0.13."
    intro="Este teste deve ser feito por motoristas convidados. Configuração, leitura de relatórios e envio de feedback devem ser feitos com o veículo parado em local seguro."
  >
    <h2>Objetivo</h2>
    <p>Validar o produto completo antes da Release Candidate: onboarding, captura por MediaProjection, OCR, HUD, histórico, IA, MCP, plano/créditos e notificações.</p>

    <h2>Roteiro mínimo</h2>
    <ol>
      <li>Crie ou entre na conta e conclua o onboarding.</li>
      <li>Autorize HUD e notificações.</li>
      <li>Inicie uma jornada e autorize a captura do Android.</li>
      <li>Observe pelo menos 10 ofertas reais ao longo do turno, sem interagir com o app enquanto dirige.</li>
      <li>Com o veículo parado, teste fechar, mover e redimensionar o HUD.</li>
      <li>Encerre a jornada e confira Histórico, gráficos e resumo.</li>
      <li>Faça uma pergunta à IA do Sr. Rotas.</li>
      <li>Gere e revogue uma chave MCP.</li>
      <li>Use o botão de notificação de teste e confirme o resumo de jornada.</li>
      <li>Envie todo problema pela Central do testador no Perfil.</li>
    </ol>

    <h2>O que não precisa testar pagando</h2>
    <p>O Closed Beta permanece liberado enquanto BILLING_ENFORCEMENT=false. O testador não precisa realizar um Pix real, salvo se a BigCorps solicitar um teste específico de cobrança.</p>

    <h2>Feedback útil</h2>
    <p>Informe o que estava fazendo, o que apareceu, o que esperava e se conseguiu continuar usando o app. Não envie senhas, chaves MCP, placas, dados pessoais de passageiros ou capturas sensíveis.</p>

    <h2>Segurança</h2>
    <p>O Sr. Rotas é um assistente de cálculo. O motorista decide a corrida. Nunca configure ou leia relatórios enquanto conduz.</p>
  </LegalPage>;
}
