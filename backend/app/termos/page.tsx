import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = { title: "Termos de Uso" };

export default function TermsPage() {
  return <LegalPage kicker="Termos" title="Termos de Uso do Sr. Rotas." intro="Última atualização: 18 de agosto de 2026. Ao utilizar o Sr. Rotas, o motorista reconhece que o serviço é uma ferramenta de apoio à decisão e não substitui sua avaliação, atenção ou responsabilidade ao dirigir.">
    <h2>1. Finalidade</h2><p>O Sr. Rotas organiza e calcula métricas de ofertas observadas na tela do motorista, histórico, analytics e recursos opcionais de IA/MCP.</p>
    <h2>2. Decisão sempre do motorista</h2><p>O aplicativo não aceita, recusa, toca ou executa ações em corridas de forma autônoma. O motorista permanece responsável por decidir se aceita uma oferta e por obedecer às regras da plataforma utilizada e às normas de trânsito.</p>
    <h2>3. Estimativas</h2><p>R$/km, R$/min, R$/hora, custos e lucro são estimativas baseadas nos dados reconhecidos e na estratégia configurada. OCR pode conter erros. Valores de ofertas observadas não representam faturamento, corrida concluída ou lucro realizado.</p>
    <h2>4. Segurança na direção</h2><p>Configurações, cadastro, leitura de relatórios e ajustes devem ser feitos com o veículo parado em local seguro. O HUD é auxiliar e não deve exigir interação durante a condução.</p>
    <h2>5. Serviços de terceiros</h2><p>Supabase, Vercel, OpenAI, Banco Inter, OneSignal e clientes MCP podem participar de funcionalidades específicas e possuem seus próprios termos. Sr. Rotas/BigCorps não é afiliado nem representa Uber ou outras plataformas de transporte mencionadas apenas para compatibilidade técnica.</p>
    <h2>6. Conta</h2><p>O usuário é responsável por manter senha e dispositivos sob seu controle. A conta pode ser excluída pelo app ou por srrotas.com/excluir-conta.</p>
    <h2>7. Plano e créditos</h2><p>Durante o Closed Beta, a exigência de assinatura pode permanecer desabilitada. Quando ativada, o plano e os créditos da IA serão apresentados antes da contratação. MCP e analytics determinísticos não consomem créditos da IA própria.</p>
    <h2>8. Contato</h2><p>Suporte e questões relacionadas ao serviço: contato@bigcorps.com.br.</p>
  </LegalPage>;
}
