import type { Metadata } from "next";
import LegalPage from "../_components/LegalPage";

export const metadata: Metadata = { title: "Suporte" };

export default function SupportPage() {
  const email = process.env.NEXT_PUBLIC_SUPPORT_EMAIL?.trim() || "contato@bigcorps.com.br";
  return <LegalPage kicker="Suporte" title="Ajuda para os testes do Sr. Rotas." intro="Durante o Alpha, o diagnóstico do próprio aplicativo é a principal fonte para investigar diferenças de leitura entre aparelhos e layouts.">
    <h2>Antes de reportar um problema</h2><ul><li>Informe modelo do aparelho e versão do Android.</li><li>Diga se a jornada estava ativa e se o HUD tinha permissão.</li><li>Compare os valores reais exibidos na oferta com os valores interpretados pelo Sr. Rotas.</li><li>Use “Compartilhar diagnóstico” no aplicativo quando solicitado.</li></ul>
    <h2>O pacote de diagnóstico</h2><p>O pacote textual pode incluir versão do app, modelo do aparelho, estratégia configurada, última leitura OCR, resumo das últimas ofertas locais e log técnico. Ele não inclui screenshots, token do aparelho ou código de pareamento.</p>
    <h2>Contato</h2><p>E-mail previsto para suporte: <strong>{email}</strong>. Enquanto o domínio ainda não estiver registrado/configurado, use o canal direto informado pela equipe do Alpha.</p>
  </LegalPage>;
}
