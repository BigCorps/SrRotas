# Sr. Rotas 0.26.5-beta — identidade + rodada de campo 07/09/2026

Base obrigatória: commit `92a087424748665a865a529d5624a17295e104a0` (0.26.4-beta / Action #76 verde).

## Identidade visual

- Novo launcher produzido a partir do IMG-01, com enquadramento calibrado contra o IMG-02.
- Novo mascote de Configurações (IMG-03).
- Assinaturas próprias de tema claro e escuro (IMG-04/IMG-05).
- Novo ícone da janela flutuante (IMG-06).
- Novo mascote da IA com enquadramento acima da cintura/dorso (IMG-07).
- Títulos dos cabeçalhos reduzidos e condensados. A família **Russo One exata não é empacotada neste patch**; o APK usa `sans-serif-condensed` como fallback seguro, sem incluir arquivo de fonte externo.

## HUD e OCR

- Mantido o OCR principal por MediaProjection + ML Kit e o OCR periódico de confiabilidade já existente.
- Não reintroduz `AccessibilityService` removido na 0.12.
- Adicionado fallback textual local e conservador após falha do isolamento espacial, exigindo evidência de oferta e, nos fallbacks de tela inteira, uma única tarifa principal para evitar cruzamento de cards.
- O fallback Uber continua submetido às validações já existentes de duração, plausibilidade, R$/km e hora composta.

## Jornadas

- Nova faixa **Odômetros registrados** na aba Jornadas, lendo todas as jornadas retornadas no período, e não apenas as 12 primeiras exibidas pelo card legado.
- Mostra odômetro inicial isolado, final isolado ou par completo; mostra distância quando calculável e gastos informados.
- Botão de edição atualiza a própria tela após salvar.

## IA

- O bloco **Como posso ajudar?** passa a ser o próprio campo de pergunta.
- Removido visualmente o campo duplicado inferior; o compositor passa para o conteúdo principal.
- Créditos passam para legenda compacta abaixo do campo.
- Novo mascote da lâmpada.

## Agora

- Pull-to-refresh no topo com indicador de puxar/soltar/atualizando.
- Mini-cards de R$/km, R$/hora e Busca/min. centralizados e padronizados.
- Fundo neutro com borda viva de classificação, reduzindo grandes preenchimentos opacos.
- Base Pessoal com ícones de localização/apoio em azul mais vivo.

## Assistente Ativo

- Sugestão transformada em pequeno balão associado visualmente ao ícone flutuante.
- A ponta muda de lado conforme a posição do ícone e acompanha o ícone se ele for arrastado.
- Sem botão X.
- Toque fora ignora a sugestão.
- Ação **Verificar locais para novas corridas** abre Agora em `Momento`, limpa filtro de região antigo e atualiza os locais.
- Intervalo depois de ignorar configurável em 10, 12 ou 15 minutos.

## Janela flutuante

- Corrigida a causa da rolagem de Mensagens voltar ao topo: o watcher não reconstrói mais o trilho a cada ~350 ms usando o cache quando não houve sincronização real.
- Mantida proteção adicional de gesto no ScrollView.
- `UberX` passa a `X`; Comfort e Black permanecem compactos.
- Mais detalhes passa a exibir **Destino** quando há contexto válido, além de Busca/retirada.
- Informações financeiras do detalhe recebem tamanho mínimo maior.
- Sublinhado verde/amarelo/vermelho colocado junto ao bloco financeiro conforme avaliação.
- Configurações recebe escolha de fonte da janela flutuante: Compacta, Padrão ou Grande.

## Digitalizar jornada / histórico

- Feedback visual via Toast ao autorizar, iniciar, reconhecer e organizar a captura.
- Normalização conservadora de OCR sem apagar valores iguais pertencentes a cards diferentes.
- Segunda passagem **local** do ML Kit, somente quando a primeira leitura parece fraca, sobre imagem em tons de cinza + contraste.
- É escolhida a passagem com maior evidência OCR; textos das duas passagens não são concatenados, evitando bagunçar a estrutura do histórico.

## Backend / Supabase

- Nenhuma migration ou alteração de schema é necessária nesta rodada.
- A investigação confirmou que já existem registros em `journey_vehicle_metrics`; o ajuste desta versão é principalmente de recuperação/apresentação no app.
