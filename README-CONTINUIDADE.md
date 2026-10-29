# Sr. Rotas — Continuidade Canônica

SINGLE_SOURCE_OF_TRUTH: true
CANONICAL_CONTINUITY_VERSION: 2026-10-15.1
CURRENT_HEAD_STAGE: 0.33.30-field / versionCode 107 — Estatísticas 2 colunas por conteúdo + cabeçalho canônico restaurado + Radar sem mapa interno (Google Maps externo); Reader congelado; homologação física pendente
GREEN_BASELINE_BEFORE_DOC_CLEANUP: 35885221f36b7f048af229745d75f1e483bc6524
Base Android homologada: `0.33.6-field / versionCode 83`
Field validada mais recente: `0.33.8-field / versionCode 85`
HEAD anterior vc98: `0.33.21-field / versionCode 98` (CI verde, teste de campo parcial)
HEAD anterior vc104: `0.33.27-field / versionCode 104` (Etapa 1; campo reprovou a fidelidade visual)
HEAD anterior vc105: `0.33.28-field / versionCode 105` (quatro telas de Estatísticas aprovadas visualmente)
HEAD anterior vc106: `0.33.29-field / versionCode 106` (Reader P1 + P2 grade responsiva)
HEAD atual: `0.33.30-field / versionCode 107` (Estatísticas 2 colunas + cabeçalho canônico + Radar sem cartografia interna; homologação física pendente)
HEAD candidato vc99: `0.33.22-field / versionCode 99` (aguardando homologação física)

> Este arquivo é a única fonte operacional de verdade para agentes e continuidade do desenvolvimento.
> Histórico de fases, QA, handoffs, manifests de ZIP e roadmaps antigos foram removidos do branch principal e permanecem recuperáveis pelo Git.
> Antes de qualquer alteração, valide também o código e os serviços atuais. Se houver conflito entre este arquivo e documentação histórica de commits antigos, este arquivo + estado atual do código/serviços prevalecem.

## 29/10/2026 — vc107: Estatísticas 2 colunas, cabeçalho canônico, Radar sem mapa interno
- **A — Estatísticas (causa):** a regra vc106 `65 + 92×fontScale` dp por card era um mínimo fixo: com fonte 1,3× pedia 185dp por coluna e derrubava para 1 coluna contêineres ≤ 377dp (tela dividida/janela do SM-X626B), mesmo com o conteúdo cabendo. **Regra vc107:** `AdaptiveGrid105` usa 2 colunas sempre que `(largura medida − 8dp)/2 ≥ mínimo exigido pelo conteúdo`; o mínimo = parte fixa compacta (48dp: padding 6 + ícone 30 + margem 6 + folga) + maior valor dos 8 KPIs (e "R$ 0.000,00") medido em negrito a 14sp × fonte, ou a maior palavra do rótulo + selo "!". `65 + 92×fontScale` permanece apenas como largura confortável: abaixo dela o `KpiCard105` compacta (padding 9→6, ícone 38→30, rótulo até 2 linhas) e `FitText105` reduz o valor em passos de 0,5sp até no mínimo 14sp × fonte. Centavos e "!" nunca são ocultados; 1 coluna só quando o conteúdo de fato não cabe. Gráficos, cálculos, filtros, navegação e fontes inalterados.
- **Medição no aparelho:** o diálogo "Fonte dos dados" mostra "Layout dos indicadores: largura útil X dp · mínimo por card Y dp · N coluna(s) · fonte F× · densidade D" — usar na homologação do SM-X626B para registrar largura real/breakpoint/colunas. Robolectric (gráficos nativos, SM-X626B 680dp de conteúdo — limite do `HistoryPanel`): tela cheia retrato/paisagem e dividida 50% (608/680dp), 1/3 (392dp) e popup (332dp) com fonte 1,0/1,15/1,3 → 2 colunas, zero valores cortados, menor valor 14,5sp (popup 1,3×). Matriz CI 280–700dp × 1,0/1,15/1,3/1,5 sem cortes. **Breakpoint medido (contêiner mínimo para 2 colunas):** fonte 1,0× 276dp (vc106: 322), 1,15× 297 (350), 1,3× 327 (378), 1,5× 359 (414); mínimo por card exigido 134/145/160/176dp.
- **B — Cabeçalho:** `SrAppHeader023` restaurado à composição canônica pré-Estatísticas (logo original, título, meia-pílula superior direita com Configurações e Usuário, tamanhos 28/30/34dp); única adição: ícone de tema (Claro/Escuro/Automático → `showAppearanceOnly`) antes de Configurações. Removidos `appearanceAction`, o layout empilhado e `StatisticsHeaderLayout`; Estatísticas usa o mesmo componente sem regra especial. Teste de consistência: mesma altura/pílula/ordem de ícones em 7 telas, 360/411/800dp, fonte 1,0/1,3.
- **C — Radar:** removida a cartografia embutida (MapLibre `MapView`, snapshot/bitmap, gates de render, aba "Mapa" de 490dp) e a dependência `org.maplibre.gl:android-sdk-opengl`. Abas: Resumo / Oportunidades / Áreas. Resumo mostra destino, ETA/chegada, probabilidade somente com amostra de baseline (`RadarCompactOverlayV1.probability`), contagem de oportunidades, aviso de eventos elegíveis, top 3 oportunidades e detalhe da selecionada; sem corrida, card "Sua região" com "Atualizar localização". Botão "Ver no Google Maps" (`RadarExternalMapsV1` → `OfferMaps.searchIntent`, link universal `https://www.google.com/maps/search/?api=1&query=…`) só aparece com coordenada válida (fallback endereço ≥ 3 caracteres), nunca em DEMO, nunca abre sozinho; sem app de mapas nem navegador → aviso, sem crash; voltar retorna ao Sr. Rotas. Waze, oportunidades, eventos, histórico, telemetria (`trackNavigation`) e contratos preservados; contadores de mapa do diagnóstico permanecem e devem ficar em zero. Sem SDK Google Maps nem API paga.
- **APK release (local, mesma configuração):** vc106 104.769.588 bytes → vc107 63.097.356 bytes (−41.672.232 bytes, −39,8%; sem bibliotecas nativas MapLibre).
- **D — Reader congelado:** OCR/parser/Reader2/integridade/HUD/fórmulas fora do diff; guards de hash (Field6/Field98) verdes; regressões Reader vc106 verdes. Testes de ciclo de vida do mapa (Field95/96, 38 casos) removidos por exigência de produto; guards de imutabilidade atualizados só para os 4 arquivos de mapa excluídos.
- Pendente: homologação física SM-X626B (2 colunas em retrato/paisagem/dividida, fonte normal e grande), cabeçalho em todas as telas e "Ver no Google Maps" com e sem app instalado. Backend/Supabase/Vercel não alterados.

## 28/10/2026 — vc106: Reader P1 (total × trechos) + Estatísticas P2 (grade responsiva)
- **Evidência:** 4 capturas reais de ofertas Uber (Black R$ 17,99; Comfort R$ 11,99; Electric R$ 36,94 com +R$ 2,25 incluído; Comfort R$ 37,27 com +R$ 3,50 incluído; botões Aceitar e Tenho interesse) passaram por OCR real (Tesseract, original e pré-processado) e as linhas com caixas foram reproduzidas pela rota M1 oficial (`UberSpatialParser0221.parseLines` → cluster → `OfferParser` → `FlexibleDriverOfferParser` fallback → `OfferIntegrityGate027033`). ML Kit não roda neste ambiente (sem aparelho/emulador): o OCR do APK pode diferir. Capturas, OCR bruto e endereços ficam fora do Git; testes usam fixtures anonimizadas e geometria sintética rotulada.
- **Causa confirmada (vc105):** `UberOfferDetector.pairRegex` só reconhecia `min (km)`. Nos cards `min • km`, o fallback por ordem tomava o TOTAL do cabeçalho (`45 min • 14.2 km • Est. de R$2,60/km`) como busca e a busca como viagem: Electric saiu 49 min/15,0 km (R$ 45,23/h) e, noutra leitura, 86 min (R$ 25,77/h), ambos com integridade `ready` (pickup+trip = total interno). O `FlexibleDriverOfferParser` (Uber) repetia o erro com três pares. Cards só com "Tenho interesse" e categoria perdida pelo OCR não eram reconhecidos; etiqueta de mapa "+R$ 2,25" lida sem "+" virava tarifa principal; ETA do mapa ("12 min") contaminava o fallback. `Reader2Parallel031` (shadow) já ignorava o cabeçalho por ser linha monetária e permanece inalterado/shadow.
- **Correção:** `UberLegResolver106` distingue total/busca/viagem por evidência estrutural — marcador `Est. de R$x/km` na linha do total e, sem marcador, a soma exata (±1 min; ±max(0,2 km; 4%)); busca/viagem pela ordem do card somente após excluir o total; separadores `•·*-–—:«º`, `min`/`minutos`, vírgula/ponto. Ambíguo (trecho perdido, trechos que contradizem o total, três pares sem relação, total sem trechos) → nenhuma oferta/derivado publicado. Fallback por ordem recusa mais de dois candidatos. Layout legado `(km)` inalterado. "Tenho interesse" vira âncora de card Uber (não muda offerType). `MoneyRoleResolver030`: tarifa que tem o cabeçalho logo abaixo substitui um R$ acima na mesma coluna sem cabeçalho (pílula do mapa). Bônus "incluído" nunca é somado. HUD só exibe campos da oferta (sem recálculo).
- **Resultado com OCR real:** todos os cards publicados saíram exatos (Black 2/18/20 min, R$ 0,90/min, 53,97/h, 5,14/km; Comfort 2/13/15, 0,80, 47,96, 3,43; Electric 4/41/45, 0,82, 49,25, 2,60; Comfort 8/39/47, 0,79, 47,58, 2,89). Leituras em que o OCR perdeu trecho/decimal/tarifa (`Amin`, `2minºe`, `8min:24km`, `R$ 1799`) ficam indisponíveis — antes publicavam valores errados ou nada.
- Guards de congelamento do Reader (`RadarContextualField6/7`, `Field98`) atualizados apenas para os 5 arquivos alterados, com comentário "reviewed vc106 Reader P1 exception". Reader2 shadow, Controlled Hybrid OFF e single-heavy-OCR mantidos.
- **P2:** `AdaptiveGrid105` decide 1/2 colunas pela largura efetivamente medida do contêiner (padding, limite 680dp, tela dividida/freeform) com largura mínima de card proporcional à fonte (`65 + 92×fontScale` dp); antes usava `screenWidthDp` e limiar fixo 1,3×. Mantém 2×4 nos layouts aprovados; corrige 345dp/1,2× e painéis estreitos em janela larga (cortava "R$ 12.345,6"). Redimensionar/girar recria a Activity (sem `configChanges`): Estatísticas reaproveita por até 2 min as leituras concluídas do mesmo período/filtros/sessão, sem novas consultas.
- Testes: suíte JVM Android, 15 testes Reader novos no CI (texto + rota espacial Robolectric), replay privado opt-in e matriz responsiva opt-in. Backend/Supabase/Vercel/fórmulas e registros históricos não alterados. Homologação física no SM-X626B pendente.

## 28/10/2026 — Estatísticas vc105: quatro mocks + séries diárias (homologação física pendente)
- Motivo: vc104 reprovada em campo (Samsung SM-X626B, Android 16): cards inflados, `R$ 1.236,79` quebrado, avisos como botões, gráficos ocultos (`detailsExpanded=false` + `collapsibleAnalyticsCard` em `GONE`) e nenhuma série financeira diária.
- Android `0.33.28-field / versionCode 105`. Telas nativas em `StatisticsScreens105` (estado puro `StatsState105`), orquestradas por `HistoryPanel`: Visão rápida (faixa rolável de chips + `•••›`, 7 KPIs + card de completude 2×4, faixa única de pendências sem dupla contagem, Ofertas e atividade 2×2, Fonte dos dados, Explorar = Cruzar métricas / Comparar períodos / Todos os relatórios), Relatório de tarifas de corridas concluídas (aberto pelo KPI), Biblioteca (Produtividade + categorias; destinos sem relatório real ficam desabilitados com motivo) e Cruzar métricas (dois eixos, legenda, resumo factual). Horários e Análises de ofertas continuam acessíveis, rotulados como ofertas observadas. Voltar do sistema retorna à tela anterior (`OnBackInvokedCallback` API 33+ / `onBackPressed` legado).
- Valor monetário indivisível (`StatsFormat105.money`, NBSP) e `FitText105` encolhe a fonte em vez de quebrar/cortar centavos. Cabeçalho `SrAppHeader023` e `SrBottomNav023` reutilizados; Estatísticas só empilha o cabeçalho abaixo de 340dp ou com fonte grande efetiva. Conteúdo limitado a 680dp e centralizado em tablet. Metadados técnicos (versão do APK, origem das ofertas, relatório web) movidos para Fonte dos dados e limitações.
- Backend (somente leitura): `GET /api/v1/statistics/series?from&to` → `srrotas-statistics-series-v1`, mesma autenticação/entitlement `can_analytics`, isolado por `driver_id`, sem escrita nem agregado persistido. Receita diária = soma por dia local (fuso `DEFAULT_TIMEZONE`, padrão America/Sao_Paulo) de início da jornada dos totais canônicos por jornada de `listJourneyRealized0262` (mesma fonte do KPI). `null` é lacuna; `0` só em dia sem jornada com fonte completa. Também: corridas concluídas, horas de jornadas encerradas, distância do odômetro, ofertas observadas (semântica `OBSERVED_OFFER`, nunca receita). Tarifa média/maior/menor e detalhamento por modalidade/faixa de distância só quando as linhas por corrida reproduzem os totais canônicos. Comparação % e insight apenas com evidência completa nos dois períodos. Região de origem/destino, tempo ocioso e R$/km × região permanecem indisponíveis (sem fonte conciliada).
- Android recusa série com schema, período ou semântica divergentes; o cliente usa exatamente `from`/`to` da consulta de ofertas (igual ao snapshot).
- Validação local: 630 testes JVM Android (623 anteriores + 7 novos), zero falhas; 72 testes de Estatísticas backend (11 novos) e 11 do Radar; `tsc --noEmit`. QA visual opt-in `gradle :app:testDebugUnitTest -PqaScreenshots` (Robolectric, gráficos nativos) gera PNGs das quatro telas em claro/escuro, estados disponível/parcial/sem dados/carregando/sem sessão, 411dp, 360dp, 1,3× e formato SM-X626B retrato/paisagem; inclui teste de valores R$ 9,90…123.456,78 numa linha. Capturas Robolectric não substituem aparelho: sombras de elevação não são desenhadas e não há emulador (sem KVM) nesta execução.
- Pendente: deploy do backend (Vercel via main) para o gráfico aparecer no APK; homologação física no SM-X626B e em telefone; leitura autenticada real das séries. Reader/OCR/HUD/Radar/Agora/IA/V7, fórmulas e Supabase não alterados.

## 24/10/2026 — Release de homologação da Etapa 1 (vc104)
- Parecer final do coordenador aprovou estaticamente A–F em `bf34131`; proprietário autorizou push fast-forward consolidado e APK com identidade de assinatura existente. Sequência preservada: `d968e18 → e88806e → bf34131 → commit final desta preparação`.
- Dois ajustes finais de apresentação: indicador indisponível não é contado novamente como parcial; disponibilidade e contagens consideram somente as sete chaves da Visão rápida. Sem alteração de valores, elegibilidade, fórmulas ou contratos financeiros. Testes de regressão incluem indisponível+parcial, sete indicadores e métricas auxiliares.
- Versão Android: `0.33.27-field / versionCode 104`. Usar o workflow oficial do push único para produzir o APK com os Secrets de assinatura já existentes; conferir artifact, SHA da execução, versão/applicationId e certificado antes de disponibilizar. Não entregar fallback debug/UNSTABLE-SIGNING como atualização.
- Fonte financeira canônica e ofertas observadas separadas; correções A–F, autenticação, temas, shell, cabeçalho, rodapé e navegação preservados. Backend, Supabase, V7, Reader/OCR/HUD/Radar/Agora/IA e Etapas 2–4 fora do diff.
- Validação final local: `compileDebugKotlin` e suíte JVM completa aprovados (623 testes, zero falhas/erros); guard de arquitetura e diff check aprovados. Quatro expectativas antigas de versão e dois contratos de apresentação foram atualizados para vc104/snapshot já aprovado, sem mudar HUD/Radar.
- Publicação ainda depende de credencial Git do executor: preflight de push falhou por ausência de Username/credencial; conector não contém os commits locais para mover a referência preservando SHAs. Não entregar APK anterior ou com assinatura alternativa.
- Homologação visual no aparelho e leitura autenticada real continuam pendentes. Aprovação automatizada não equivale a aprovação do motorista; não declarar pronta a versão 1.0.0.

## 24/10/2026 — Correções da auditoria de Estatísticas (incremento local sobre e88806e)
- Correções restritas aos achados A–F do documento `SrRotas_Etapa1_Auditoria_GPT.md`; preservada a implementação anterior. HTTP 402 agora é acesso restrito, com orientação para a conta; 401, 403, rede e servidor continuam distintos. Nenhuma alteração de autorização no backend ou nas regras comerciais.
- Avisos oferecem ação contextual para a conta/configuração quando o problema é conhecido. “Ver jornadas” aparece somente com evidência explícita de duração encerrada inválida ou cobertura incompleta de odômetro, lida dos campos já existentes de `answers.PERIOD_SUMMARY.details`, com versão/origem/período conferidos. Essa orientação opcional não muda o schema HTTP, valores ou elegibilidade do snapshot. Ausência histórica/causa desconhecida mantém explicação e Fechar, sem prometer recuperação nem inventar falta de configuração de custo.
- Aparência removida da faixa rolável e integrada ao `SrAppHeader023` por callback opcional habilitado apenas no cabeçalho de Estatísticas. Reutiliza `showAppearanceOnly()` e `Strategy021Store`; telas estreitas ou fonte ampliada separam título/logo das ações. Cabeçalhos dos demais módulos conservam o comportamento anterior.
- Cards disponíveis levam a Corridas/Jornadas existentes quando relacionados; Resultado estimado e indicadores sem destino direto abrem fonte e limitações. Avisos continuam independentes do clique do card. Carregamento tem estado próprio; banner conta somente estados explícitos dos indicadores, sem transformar HTTP em pendência histórica. Fontes usam rótulos para motoristas, cobertura percentual e amostra, sem chaves/origens brutas.
- Validação incremental: `compileDebugKotlin`, 25 testes JVM relacionados (21 de Estatísticas e 4 de responsividade/paleta), guard de arquitetura e `git diff --check` aprovados. `PerformanceReading.display()` foi conferido idêntico ao commit-base. Backend, Supabase, fórmulas e módulos protegidos sem alteração.
- Sem push, merge, deploy, Actions ou APK; versão permanece `0.33.26-field / versionCode 103`. Sem aparelho/emulador disponível para capturas reais em claro/escuro; QA visual e leitura autenticada real continuam pendentes da rodada de homologação autorizada. Diff incremental entregue para revisão final.

## 24/10/2026 — Estatísticas Android, Etapa 1 (implementação local para revisão)
- Base conferida: `main` em `d968e18c7b6fdc29340d0a81bf27408c7e04a4ee`. Mudanças locais ainda não publicadas; versão permanece `0.33.26-field / versionCode 103`. Não há novo APK ou homologação de campo.
- `HistoryPanel.performanceOverview()` usa exclusivamente sete leituras elegíveis do snapshot `srrotas-performance-snapshot-v1`. Cliente Android GET isolado reutiliza URL e Bearer do dispositivo, respeita 401/403 e não altera `BackendClient.request()`, APIs ou motores. Schema, semântica, tipos, disponibilidade, fonte, amostra, completude e cobertura são validados. Ausência/nulo/contrato inválido mantêm `—`; zero explícito elegível é preservado.
- `/api/v1/analytics` permanece fonte de ofertas observadas. A consulta ao snapshot envia exatamente `from`/`to` retornados nessa leitura; resposta com período divergente é recusada. “Hoje” foi renomeado “Últimas 24h”, sem inventar alinhamento de calendário. Filtros de classificação/categoria/tipo são identificados como exclusivos das ofertas; KPIs canônicos cobrem todas as corridas no intervalo.
- Faixa horizontal de período e destinos existentes, Resumo selecionado e 7 dias por padrão; aparência reutiliza `showAppearanceOnly()`. Shell/cabeçalho/rodapé oficiais preservados. Grade responsiva, avisos com painel próprio, estado contextual, aviso de evidência parcial somente quando declarado pelo contrato, Ofertas e atividade, Fonte dos dados e limitações e Explorar com destinos reais. Análises antigas continuam acessíveis sob expansão explícita. Sem drill-down de ganhos, biblioteca nova, Produtividade/Horários fictícios ou cruzamento de métricas.
- Respostas antigas são ignoradas ao trocar período; falhas financeiras nunca são substituídas por agregação local de ofertas. Sem alterações em Reader/OCR/HUD/Radar/V7, cálculos, banco, Supabase, rotas ou funcionalidades web.
- Validação local: `compileDebugKotlin` e `testDebugUnitTest --tests com.srrotas.app.Performance*Test` concluídos com sucesso (11 testes, zero falhas/erros); guard de arquitetura e `git diff --check` aprovados. Nenhum Actions/build Vercel executado. Teste físico em Claro/Escuro/Automático, fonte ampliada, largura reduzida, rolagem, painel e rodapé permanece obrigatório; não há sessão autenticada de motorista ou emulador disponível nesta execução. Revisão do cliente financeiro/autorização deve ocorrer antes de publicação, conforme matriz da skill.

## 23/10/2026 — DIRETRIZ: SR. ROTAS DIRETAMENTE NA MAIN ATÉ 1.0.0
- Por decisão expressa do proprietário, o Sr. Rotas não precisa de preview/branch de integração para ajustes até a versão 1.0.0. Trabalhar diretamente na main, preservar outras mudanças e build Android versionado para o homologador. Não confundir deploy Web com APK instalado. Nada disso autoriza mutações financeiras, quebra de invariantes ou migrations sem revisão.
- Identificada causa da ausência de diferença nas Estatísticas dentro do APK: `ConsolidatedMainActivity027037.statisticsContainer()` usava `HistoryPanel` nativo, enquanto o mockup "Seu desempenho" havia sido aplicado apenas em `backend/app/app/estatisticas/page.tsx`. A main vc103 adiciona primeira dobra nativa com KPIs, filtros de período, indicadores observados e botão de handoff autenticado para relatório canônico completo. KPIs financeiros que não sejam comprovados no contexto nativo continuam indisponíveis (—); preservar integridade das evidências.
- Outro achado: o halo verde do HUD era condicionado a `rideActive && !colorBlind`; fora da corrida aparecia outra cor. Correção vc103: verde sempre que modo daltônico estiver desligado.
- Para campo exigir confirmação de `0.33.26-field / versionCode 103` nas informações do aplicativo; APK main build separado, nunca reaproveitar o vc102. QA físico ainda obrigatório.
 
## 23/10/2026 — INTEGRAÇÃO DA MAIN: HOTFIX FINANCEIRO MANTIDO
- Preservado integralmente o contrato da main: `POST /api/v1/reconciliation` devolve HTTP 423 (`reconciliation_writes_locked`) após autenticação e entitlement; GET continua permitindo análise sem qualquer escrita de conciliação. O teste de segurança `backend/tests/security/reconciliation-post-lock.test.mjs` da main foi mantido. Reativação somente com transação atômica e revisão independente.
- Web Estatísticas e Android vc102 são integrados à main apenas com os commits já revisados; sem migração no Supabase nem reprocessamento de dados. A versão Android assinado continua vc102, exigindo instalação manual por cima da vc101; merge sozinho não atualiza o celular.
- Testes integrados e build da main são gates de release, não confundir CI com homologação física.

## 23/10/2026 — ANDROID vc102: RELATÓRIO FIELD (A VALIDAR)
- Diagnóstico vc101: tarifa R$11,77 com 13+2=15 min e, um segundo depois, 2+11=13 min. Primeiro frame com R$3,92/km calculado versus R$4,36/km anunciado; segundo com R$4,36/km consistente. Parser rejeita mais de 2 pares e rejeita divergência >8% no R$/km independente quando dois pares completos, aguardando novo frame. Não inventa tempos; Reader2 continua shadow, M1 oficial.
- Revisão pela galeria: atividade em processo separado `:rescan`, não inicia os serviços de captura/sync/push; bloqueia OCR com jornada/captura ativa. Calcula prévia de tempo, tarifa, R$/km, R$/h, R$/min, custo e resultado sem sobrescrever oferta oficial. Disponível além do Field, ícone de fotos sobrepostas. Menus simplificados para Jornada/Histórico.
- Halo colorido no ícone original do HUD, sem novo WindowManager; Radar intacto. Requer CI Android, revisão cruzada de M1/overlay, e QA do Samsung Android 16 antes de RC 1.0.0. Sem deploy, Supabase ou merge.

## 23/10/2026 — ESTATÍSTICAS: MOCKUP "SEU DESEMPENHO" (BRANCH ISOLADA; QA PENDENTE)
- Referência visual obrigatória recebida em 23/10: título Seu desempenho, filtros Ontem/7 dias/30 dias/Personalizado, abas Resumo/Produtividade/Horários/Regiões/Corridas/Custos (preservando Oportunidades existente), sete cards com ícones/tipos de evidência, alerta de indisponibilidade, bloco Ofertas e atividade com quatro métricas e comparativos de períodos; números do mockup SÃO ilustrativos e não devem entrar no código.
- API de Estatísticas mantém autenticação e entitlement e adiciona resposta compatível `snapshot` tipada, versão `srrotas-performance-snapshot-v1`, construída exclusivamente dos `StructuredAnswer` do mesmo NaturalQuestionEngine usado por Pergunte/IA. Inclui chave semântica, período, fonte, amostra, completude e cobertura para reuso futuro por IA/Agora. Não recalcula fórmulas nem escreve banco; não redefine V7, corridas ou tarifas.
- Resumo consulta também OFFER_QUALITY para percentual real de ofertas boas somente quando a evidência alcançar a amostra mínima. Interface troca duplicação do resumo genérico por bloco compacto, integra leitura do snapshot e mantém fallback para API antiga em Preview; compara somente ofertas observadas sem inventar receita.
- CI de Estatísticas, Preview visual autenticado e revisão de semântica financeira ainda necessários antes da `main`. Nenhuma migração ou deploy manual autorizado. Reader/correção de tempo e galeria do relato de 23/10 seguem trilha Android separada.

## 20/10/2026 — CANDIDATA Android vc101 (CI APROVADO; AGUARDA CAMPO)
- Diagnóstico sobre a vc100 instalada: refresh manual do mesmo destino descartava e recriava o MapLibre desnecessariamente (pressão de memória, mapa piscando e demora possíveis). vc101 preserva a instância do mapa quando a identidade exata da corrida/destino/ETA não mudou; troca de destino e DEMO continuam liberando o mapa antigo.
- ETA inválida/vencida recebe explicação visível, inclusive quando vence com Radar aberto. Assistente contextual continua bloqueado até existir uma nova ETA REAL; sem estimar tempo ou auto-finalizar corrida. Reader/M1/OCR, captura, fórmulas financeiras, HUD e Android permissions intactos.
- Novo workflow de APK assinado tem gatilho de push somente em android/** nesta branch de integração, sem duplicar o build da Web. Confere arquitetura, JVM, debug/release, assinatura estável e publica artefatos. CI executado com sucesso: GitHub Actions run `39508735790`, commit binário `e24f71ee907f7d664d995cbad85cae556b36e668`, guard de arquitetura, JVM, debug/release, check tamanho e certificado estável aprovado (SHA-256 do certificado `cc4476c4eb3ac725b692914488282f0ac70a83b3682ca6b7ac0f841f9de2117c`). Artefato `12304882670` (`sr-rotas-field-release-apk`), expira 19/11/2026. Validação física no Samsung Android 16 ainda obrigatória.
- Base de POIs e oportunidades não é fabricada: aprimorar catálogo depende de ingestão revisada e homologação backend; não atribuir ausência de oportunidades ao APK exclusivamente. Travamentos de causa ainda desconhecida exigem diagnóstico físico. Sem merge, banco ou deploy produtivo.

## 20/10/2026 — INTEGRAÇÃO ISOLADA: Estatísticas + FIELD vc100
- Branch de integração `feature/integration-vc100-statistics` baseada exclusivamente no commit vc100 `5545454aa31ba0d1742ab66216b40e2cdf262dd3`; os arquivos Android/Reader/Radar, APIs POI e políticas de segurança desse baseline são preservados integralmente.
- Foram trazidos **somente** UI/API/testes de Estatísticas da branch `feature/statistics-smart-comparisons` (HEAD `7effaf31744578910c72ffd7cc5408157214b03b`), além de script de teste e workflow focado; nenhuma migração, mudança de identidade, formulário de pagamentos, firmware ou alteração no histórico V7.
- Resumo centrado no motorista: faturamento confirmado, resultado estimado, corridas confirmadas, km registrados, horas de jornadas encerradas e taxas de faturamento por hora/km quando cobertura integral. Ofertas observadas não são receita. Comparativos sob demanda somente para ofertas operacionais observadas, provisórios sem watermark de sincronização.
- Duração é a soma **integral** das jornadas iniciadas no recorte; pode cruzar o fim do período; se houver jornada aberta, a duração divulgada é parcial e taxas por hora/km são omitidas até cobertura completa. Testes unitários incluem parciais e divisão por zero.
- Feedback vc100 de 20/10 registra avanço cartográfico, mas problemas visuais e travamentos intermitentes, contexto ETA expirado e POI insuficiente. Radar continua pendente, não desbloqueado por esta integração. A versão Android continua `0.33.23-field/vc100`, sem APK novo.
- Preview e integração não representam homologação de motorista, nem autorização de merge/main ou publicação. CI da estatística isolada: 17/17 testes. CI e Preview desta combinação devem ser verificados **depois** da criação da branch. Priorizar builds estritamente necessários (auto-preview do Vercel gera build a cada push); evitar commits fragmentados no branch integrado.

- **Revisão de integridade financeira (20/10, posterior ao primeiro Preview):** leitores existentes `listJourneyRealized0262` e `listJourneyMetrics` recuperam até 150 jornadas recentes e olham no máximo 90 dias. A primeira integração não propagava este limite às taxas R$/h/R$/km. Passou a exigir que a janela não exceda 89 dias, que o recorte tenha menos de 150 jornadas e que o leitor devolva todas as jornadas; caso contrário, taxas indisponíveis e resultado estimado não recebe selo de cobertura completa. Números parciais restantes são explicitamente avisados, sem mudar `ride_outcomes`, `ride_offers`, V7 ou fórmulas. Testes adicionais para 150 jornadas, lookback vencido e subconjunto incompleto.
- **CI econômico:** workflow Radar continua ativo nas demais branches e em PRs, mas ignora apenas o push de `feature/integration-vc100-statistics`; o CI da integração cobre ambos os testes com TypeScript e o Preview Vercel verifica build Next, evitando duplo build em push. Esta regra não promove nem mescla código.

- **Acabamento e revisão de estado (20/10):** Produtividade agora mostra no topo horas de jornada encerrada, faturamento por hora/km com cobertura, corridas e km; não depende mais apenas dos cards do Resumo. O resumo considera jornadas encerradas mesmo quando não houve oferta observada e sinaliza `partial` enquanto houver jornada aberta ou odômetro incompleto, sem confundir horas parciais com período consolidado. Erros de API exibem mensagem amigável e ação explícita de tentar novamente, sem expor erros internos. Contratos de teste adicionados; Preview autenticado/QA visual e revisão financeira independente ainda não homologados.

- **Revisão financeira de tarifas nulas (20/10):** detectado que a leitura de `ride_offers.fare` e `uber_session_imports.earnings` convertia `null` em 0 via `Number(null)`, elevando falsamente `fare_matched_trips` e a cobertura. Nesta branch de integração, `parseKnownFare` exige valor numérico explícito e finito (0 real segue válido); nulos, string vazia, inválidos e negativos não são tarifa conhecida. Sem alterar registros existentes, histórico V7, lógica de aceitação de corrida ou fórmulas de custo. Testes de regressão cobrem nulo/zero e garantem uso do parser no leitor. Alteração financeira depende de revisão independente antes de merge/publicação.

- **Pré-homologação financeira — amostra real agregada (20/10):** consulta `SELECT` no projeto Supabase SrRotas indicou 181 jornadas nos últimos 30 dias, 2 abertas, 111 no máximo para um motorista no recorte; 97 outcomes COMPLETED de fontes não legadas no histórico total e apenas 6 registros globais de distância `distance_km >= 0`. Não houve consulta a nome, telefone ou coordenadas. Com essa cobertura, resultados reais por km/hora devem frequentemente apresentar `—`, nunca aproximações inventadas.
- **Proteções adicionais:** `estimated_profit_per_hour` só é divulgado quando há cobertura integral de tarifas, jornadas encerradas e distância real; `bestJourney` não promove uma jornada à "melhor do período" caso a amostra esteja truncada ou existam corridas sem tarifa conciliada. Os cálculos de custo/receita, histórico V7 e Reader não mudaram. Pendente revisão financeira independente, inspeção autenticada e homologação; sem merge na main.

- **Bloqueio de linguagem financeira (20/10):** auditoria de esquema confirmou que `ride_outcomes.status=COMPLETED` certifica estado da corrida, porém valores `realized_revenue` atualmente derivam de `ride_offers.fare` (tarifa exibida na oferta), e não de comprovante de pagamento final. `uber_completed_ride_imports.fare` tem fonte de histórico separada, mas `sr_ride_reconciliations` não apresentava conciliações. Alterado apenas texto de indicadores/respostas/documentação para `Tarifas de corridas concluídas`, evitando afirmar faturamento final confirmado. Chaves de API, cálculo e schema preservados. Uma conciliação real do valor recebido continua como dependência de futura evolução financeira.

- **UX de evidência (20/10):** a tela de Estatísticas distingue explicitamente o percentual de cobertura de tarifas/odômetro do escopo completo/parcial do resultado. Um bloco textual explica por que taxas por hora/km ficam indisponíveis, usando somente detalhes já retornados pela API (jornada aberta/inválida, tarifa faltante, odômetro ausente ou limite dos leitores). Sem alteração de cálculo, fonte financeira, schema ou Android. Testes unitários e de apresentação cobrem as mensagens. Ainda exige sessão autenticada para QA visual antes de homologar.

- **Conciliação Uber — diagnóstico operacional (20/10):** leitura agregada Supabase encontrou 283 importações concluídas, zero decisões humanas, 20 pistas legadas (não são decisões), 234 importações sem `occurred_at` e um registro com horário futuro (além de algumas canceladas, fora do total de concluídas). A API original permite candidatos e confirmações humanas, porém não se deve apresentar valores do histórico como pagamentos definitivos nem promover candidatos automaticamente. A branch de integração agora inclui, apenas na aba Corridas e por clique, painel agregado somente-leitura `GET /api/v1/reconciliation?summary_only=1` (contagem de total, revisadas, pendentes, confirmadas, não-correspondidas, sem horário e futuro); sem nomes, trajetos, tarifas ou writes nesse endpoint e sem consultas extras no carregamento inicial. Consulta por conta em todo histórico, não limitada ao filtro de datas. Revisão cruzada financeira e QA autenticado continuam pendentes.

- **Prévia de revisão manual (20/10):** a aba Corridas incorpora, por clique, lista limitada das importações `completed` sem decisão e visualização de sugestões com similaridade, motivos, tarifa capturada, horários/origem/destino e alertas para `occurred_at` nulo/futuro. A escolha de candidato e a opção "sem correspondência" são simulações locais; nenhum POST nem decisão persistida. Detalhes ficam no estado da página, sem logs/cookies extras. Auditoria identificou risco de vincular importações diferentes à mesma oferta no endpoint POST atual: **não habilitar gravação sem hardening, revisão cruzada financeira e QA autenticado**. A UI é Preview de preparação, não homologação. Sem mudança no backend de decisão, schema, Reader ou Android.

- **Trava de gravação da Conciliação (20/10):** auditoria SQL SELECT confirmou que há unicidade por (driver_id,import_id) mas não por (driver_id,selected_ride_offer_id); 7 ofertas são referenciadas por múltiplas pistas legadas, até 4 importações por oferta, e tais pistas não são confirmação. O POST do backend fazia mutações em várias requisições sem transação e podia sobrepor outcomes. Nesta branch isolada, `POST /api/v1/reconciliation` agora responde `423 reconciliation_writes_locked` após auth/entitlement; a cadeia legada `decideReconciliation` foi removida do backend (nenhuma escrita acessível por esta API). A leitura de resumo, pendências e candidatas continua operante. Esboço de migration/RPC atômica com índice único parcial preparado apenas em `docs/contracts/srrotas-reconciliation-atomic-v2.review.sql`, não em `supabase/migrations`, SEM execução. Revisão financeira/de segurança independente, testes concorrentes e aprovação de migration/POST continuarão obrigatórios.

- **Revisão SQL ledger-only (20/10):** esquema e privilégios service_role auditados com consultas SELECT. Contrato de revisão de Conciliação em docs/contracts foi reduzido a um INSERT de decisão humana, com indice único parcial por oferta, travas para concorrência por importação e oferta, validação de propriedade da jornada e proteção para datas ausentes/futuras. Retiradas mutações em ride_outcomes e dicas originais, pontuação fornecida pelo solicitante e SECURITY DEFINER (usa SECURITY INVOKER com EXECUTE somente para service_role). Especificação continua não executada, testes novos são apenas estáticos e não comprovam transações reais; requer revisão financeira independente, QA concorrente em banco descartável, auditoria do ator e CSRF antes de qualquer ativação. A main/POST 423 e o Android permanecem intocados.

- **Ajustes após revisão independente (20/10):** contrato ledger-only recebeu as duas ressalvas de validação: bloqueio de `observed_at` nulo/futuro e validação de janela inclusiva ±75 minutos em relação a `occurred_at`; plataforma do candidato agora exige `ride_offers.platform='uber'`. Em SELECT real: `ride_offers.platform` inclui `uber`, `99` e `other`; 20/20 pistas legadas associadas permanecem no recorte e são Uber (pistas não são decisões). A criação do índice deixa de usar `IF NOT EXISTS`, com preflight que aborta em nome reutilizado, confirmação duplicada ou sobrecarga antiga de 5 argumentos. Pontos pendentes: tradução de SQLSTATE 23505 no backend futuro; decisão imutável e correção auditável (service_role mantém UPDATE/DELETE); QA concorrente, controles de identidade/CSRF e alinhamento do preview com a janela; teste PostgreSQL ainda não executado. `POST` permanece 423, sem migration, sem mudanças em banco/prod/Android.

- **Candidatas somente-leitura e erros futuros (20/10):** GET da comparação filtra pistas legadas inválidas e ofertas fora do contrato Uber/tempo/motorista/jornada antes de calcular/mostrar sugestões; exclui ofertas já confirmadas por outra importação. UI do Preview explicita janela ±75 minutos, condições de validade e número de candidatas descartadas; rascunho de escolha segue local. Adicionado classificador puro (sem conexão com rota POST) para futuro SQLSTATE 23505 -> HTTP 409 seguro, ocultando mensagens SQL. A decisão auditável, correção com histórico, prevenção CSRF, validação PostgreSQL de duas sessões e revisão independente permanecem pendentes. Nunca equiparar score, vínculo ou tarifa com pagamento. Sem migration, sem SQL escrito, sem Android, sem main; POST permanece 423.\n\n## FIELD 15/10/2026 — IMPLEMENTAÇÃO vc100 (ainda NÃO homologada)

- Radar: mapa de 490dp permanece no estado inicial, durante consulta, erro ou zero oportunidades quando há coordenadas verificáveis. Usa último fix com permissão e até 20min de idade; quando não existe, usa apenas o último destino verdadeiro ainda em memória ou apresenta placeholder honesto. Nunca cria coordenadas nem rastreia localização em segundo plano. Marcador aproximado do usuário mostrado quando dentro do recorte; motorista distante não força mudança do zoom. Componentes MapLibre, Bitmap e Reader congelados permanecem sem alteração.
- HUD: borda fina verde para corrida ativa e âmbar quando sem corrida; atualização detecta mudança de `JourneyCoordinator.snapshot` e oferece descrição de acessibilidade.
- Radar da corrida: buscar a oferta persistida por `currentRide.localOfferId` no índice exato, exigindo mesma jornada. O contexto persistido completo tem prioridade sobre snapshot em memória potencialmente desatualizado; a janela de 100 ofertas recentes deixava a oferta original inacessível durante turnos longos e podia produzir `source_offer_missing`. Trace `persisted_offer_by_id` foi adicionado à allowlist sem exportar dados pessoais; esta é uma correção de origem de dados, não um afrouxamento de ETA.
- Assistente tradicional: recuperação idempotente após processo/Activity retomada; lembrete de corrida possivelmente esquecida, **somente informativo**, após 40min de corrida + 20min de ETA vencida ou 2h sem ETA, a cada 90min no máximo por corrida. Ações Sim e Ver jornada; nenhuma ação de autoencerramento. O bug de sugestão contextual permanece em investigação, especialmente a origem da ETA expirada e contexto ausente.
- Oportunidades: importação administrativa autenticada de POIs permanentes via dry-run, dados inativos por padrão, aprovação humana e preservação de locais ativos. Complementa a API prévia de eventos temporários; nenhuma gravação de produção nesta rodada.
- CI vc100 Android/Web aprovado no Actions run `38776029410` (HEAD Android do APK `1a7e2f4`). Pendente: revisão independente, teste Samsung Android 16, mapa/localização/lembrete e suíte `npm run test:radar` do backend. O importador de POIs recebeu hardening contra corrida entre importação e aprovação e conflito de identidade; isso ainda exige testes do CI próprio e revisão antes de qualquer deploy. Sem deploy, merge ou migration.
- Versão candidata: `0.33.23-field / versionCode 100`. Base vc99 homologada em CI intacta.

## FIELD 15/10/2026 — triagem pós-vc99 (nova branch isolada, NÃO homologada)

- Relato real Samsung SM-X626B Android 16, APK `0.33.22-field/vc99`; diagnóstico exportado em 2026-10-15T15:44:07Z. Base vc99 `c1e319f` permanece estável e intocada; branch de estudo `feature/field-vc100-radar-opportunities-assistant`. Nenhum merge, deploy, migration, nova inscrição, build ou alteração ao Supabase de produção.
- Reader: 41.735 OCRs concluídos na sessão e zero falhas, 243 ofertas parseadas; preservar M1, MediaProjection, Parser, deduplicação, Integridade e fórmulas sem alterações.
- Radar: mapa 24 criações/24 ready, zero erros de carregamento, diagnóstico `last_map_release_reason=idle`. É desmontado deliberadamente no estado sem corrida; corrigir continuidade cartográfica sem inventar destino/localização nem alterar altura aprovada de 490dp. O MapLibre Native protegido na freeze requer gate próprio para qualquer alteração.
- Abas: alteração de baixo risco do texto visível de `Áreas de interesse` para `Áreas`; manter três abas por enquanto. Conteúdo de área/contexto reaproveita oportunidades existentes, não duplicar registros.
- Implementação inicial na branch (NÃO em produção): API `/api/v1/radar/poi/admin` para GET/POST/PATCH autenticados com o segredo server-side já usado em eventos. Novo normalizador aceita lote de até 50 POIs, valida identidade fonte+ID externo, geocélula, coordenadas, nome, tipo e URL, `dry_run`, idempotência sem depender do índice UNIQUE parcial do banco, submissão inicialmente `inactive`, aprovação manual explícita via PATCH, e bloqueio de sobrescrita automática de registros já ativos. Sem novos schemas/migrations e sem gravar dados na produção; ainda precisa de UI admin, CI/review/QA, política de fontes e concorrência controlada.
- Oportunidades: Supabase (leitura 15/10) tem `sr_radar_pois` com quatro registros ativos, todos do tipo music, e `sr_event_opportunities` com 68 registros, oito ativos (6 music, 2 theatre). Base dupla complementar já existe: POIs permanentes + eventos temporais; vinculação via `poi_id`. Há ingest autenticada de eventos em `/api/v1/radar/ingest` (max 500 por envio e idempotência por source/external_id) e `/api/v1/radar/admin` para CRUD de eventos. NÃO existe painel de gestão equivalente identificado para POIs; estruturar backend/admin dedicado com edição/revisão, proveniência, dedupe, expiração e dry-run de importação, reusando estas tabelas. Futuro coletor semanal externo (notebook) deve publicar só via endpoint autenticado e nunca embutir segredos no Android.
- Assistente contextual: um episódio `RADAR_SPEC_READY` com ETA_delta_seconds=-548 e `ASSISTANT_BLOCKED=eta_expired`; outras corridas têm `RADAR_SPEC_BLOCKED=context_missing`. Runtime temporal preserva proteção contra sugestão depois da ETA, mas fluxo de atualização de destino/ETA ainda precisa investigação por corrida.
- Assistente tradicional: `enabled=true`, overlay_permission=true, mas `engine_running=false` apesar de jornada aberta; há sete episódios históricos de overlay, porém não comprovam disparo no relato atual. Investigar reidratação e ancoragem de ociosidade, falta de candidato, race/cooldown antes de mudar thresholds; nunca criar indicação fictícia de demanda.
- Proposta de lembrete `Ainda está em corrida?`: baseado em corrida ativa e tempo/observações concretas, ação exclusivamente MANUAL `Ainda estou` ou `Encerrar` abrindo fluxo de confirmação; nunca autoencerrar, não inferir término por ETA isolada, rate limit e sem sobreposição durante direção.
- Estado discreto do ícone HUD: projetar sinal usando `JourneyCoordinator.snapshot` (corrida ativa vs sem corrida vs atenção), sem reinterpretar o sinal de seleção para relatório ou adicionar overlays concorrentes; testes de contraste/acessibilidade em campo.
- Estatísticas: em `feature/statistics-smart-comparisons`, página `backend/app/app/estatisticas/page.tsx` organiza Resumo, Produtividade, Horários, Regiões, Corridas, Oportunidades, Custos; respostas contêm exposição extensa de metadados de confiança/cobertura/semântica técnica. Recomendada revisão driver-first: recebido vs realizado vs estimado; resultado/hora, resultado/km, custo, tempo parado, comparativos de períodos equivalentes; evidência e limitações recolhidas, nunca promover oferta a receita realizada.
- Próximo gate: mudanças do Radar e Assistente exigem implementação + testes específicos + CI + revisão independente + campo; estatísticas continuam na branch paralela. Não considerar a mera criação desta branch como correção comprovada do Assistente ou do mapa.

## FIELD 14/10/2026 — feedback de campo vc98 / próxima UX isolada

- Fonte: diagnóstico SM-X626B Android 16 vc98 e report de 14/10. Reader/OCR 27.032 conclusões, zero falhas; mapa 18 instâncias criadas/prontas. Registrar como observação de campo, não homologa todo o fluxo.
- Branch derivada `feature/field-vc99-feedback-ux` do vc98 SHA `533c695`; não altera a branch vc98 nem `main`.
- Modal de encerramento de jornada padronizado nos dois fluxos atuais: ação Salvar e encerrar em pílula verde de altura moderada; Cancelar vermelho; Ignorar cinza. Ignorar mantém exatamente a semântica existente de encerrar sem odômetro, sem alteração de storage/Reader.
- Incremento vc99: diagnóstico do gate de ETA registra `ASSISTANT_BLOCKED`/`eta_expired` uma vez por revisão do destino quando a sugestão automática não pode consultar o backend. Não modifica a elegibilidade nem força avisos; a causa raiz do balão ausente continua aberta.
- Digitalização: substituído tema translúcido por `AppTheme` e adicionada superfície visual de autorização; MediaProjection, serviço de captura, parser e persistência intactos. Histórico ainda não validado em campo.
- Foto / Rescan: escolha de galeria como ação principal, prévia e confirmação manual preservadas, opções técnicas recolhidas; exclusão de OCR e rollback preservados.
- Microtextos: removidos avisos Alpha obsoletos do SettingsHub e AiMcpPanel; consentimentos de segurança do leitor M2 continuam.
- Primeira implementação: três abas sempre disponíveis no Radar, inclusive sem corrida, carregamento e zero oportunidades. Áreas de interesse mostra apenas eventos com contexto verificado e janela temporal real; sem inventar feeds, notícias, regiões ou POIs. Layout de fichário por três seletores compactos.
- Controles R2/R3/R4/rollback e diagnóstico continuam presentes mas recolhidos sob pequeno controle ⚙ Diagnóstico, somente na Field. Preserva mecanismo de rollback.
- Assistente Ativo ainda é bug aberto: diagnóstico registra `assistant_shown` às 15:02Z, mas também `RADAR_SPEC_BLOCKED=context_missing` às 21:59Z para outra oferta. A evidência não justifica forçar STRONG/REGION ou desabilitar o gate de ETA; requer reconstrução temporal por corrida/estado antes de alterar o gatilho.
- Pendências: testar digitalização da jornada/histórico e Foto/Rescan em aparelho; causa real da ausência do Assistente; sanitização restante de microtextos. Estatísticas seguem na branch `feature/statistics-smart-comparisons` em paralelo e screenshot↔corrida permanece etapa posterior. O código vc99 exige CI Android, revisão e validação física; não alterar a vc98 instalada sem gate.

## FIELD 13/10/2026 — vc98 EM DESENVOLVIMENTO (não homologado)

- Report de campo positivo: Radar e mapa estão operacionais no Samsung Android 16; preservar MapLibre, OpenFreeMap, bitmap de compatibilidade e Reader vc92/MediaProjection vc95/Rescan vc97 sem mudanças.
- Problema de temporalidade confirmado no export vc97: ETA da corrida 14:33:32Z, sugestão contextual exibida 14:36:04Z, enquanto o app ainda considerava currentRide ativa. A tolerância antiga da sugestão REGION após ETA não deve produzir nova convocação automática vencida.
- Branch isolada `feature/field-vc98-radar-ux` a partir de `91322b81`. WIP: gate puro de frescor de ETA, validação antes do sinal/overlay/clique, ocultação do banner em transições operacionais; acesso manual ao Radar permanece, não há conclusão automática de corrida.
- Corrigido após CI verde: registro `assistant_shown` e cooldown só ocorrem quando o card passa pelo primeiro `OnPreDrawListener` com host/card medidos e visíveis; anexação sozinha não conta como exibição. Exige novo CI por alterar renderer nativo e revisão cruzada ainda pendente.
- Auditoria do trace após M1/B1: os motivos sanitarizados `stale_destination` (clique) e `stale_destination_recovery` (painel) passaram a constar na allowlist local, evitando que sejam gravados como `unknown`. O guard e teste validam o contrato; CI do novo HEAD pendente.
- Pós-review vc98 (M1/B1): painel aberto reconcilia revisões de destino/ETA da mesma corrida durante callback, render pendente, retomada e verificação a cada 20 s somente quando visível. Loading oferece botão Atualizar; mudança para outra corrida retorna a tela neutra sem autoabertura. Banner não visível no primeiro pre-draw ou timeout é ocultado sem consumir cooldown; callback de shown revalida flag e resultado. Revisão e CI desse HEAD ainda pendentes.
- WIP visual: check operacional verde preenchido sem alterar o toggle de relatório, remoção de mensagens Radar e CORRIDA ATIVA do card, link curto RADAR, pílula de continuidade abreviada, limpeza de Agora/atalhos/Rescan. Abas Mapa/Oportunidades; Eventos somente com contexto temporal de evento retornado pelo backend. Mapa e bitmap MapLibre existentes são preservados; responsividade e dimensionamento ainda exigem homologação física.
- Hardening adicional 13/10: mesmo o resultado STRONG do backend é bloqueado na decisão quando o destino/corrida já não coincide com o snapshot do runtime; não registrar falsa elegibilidade por resposta obsoleta.
- Hardening 13/10: geração de requests no runtime invalida HTTP anterior somente após mudança da assinatura de jornada/corrida, não por cada broadcast de captura; destino/ETA revisados na mesma corrida também invalidam HTTP e aviso antigo no loop, com checagem de igualdade do destino antes de aceitar callbacks e cliques; geração de apresentação cancela anexação atrasada de sugestão ao HUD e remove banners obsoletos. Sugestão real tem duração limitada pelo ETA restante (inclusive se menor que os 5–30 s configurados), com teste JVM determinístico; DEMO conserva duração configurada. ETA expirada suspende polling automático, mas nunca encerra corrida ou bloqueia Radar manual. Check de relatório independente de indicador de corrida; acessibilidade de Agora conserva DevelopStatus033 sem mostrar M1.
- Radar: aba Eventos exige parse e sobreposição temporal com confiança positiva real; mapa indica oportunidade selecionada e oferece detalhes sem reconstruir MapLibre, ETA textual atualizada a cada 30 s na aba Mapa. Respostas assíncronas da tela aberta também exigem o destino/ETA completo atual, não apenas o ID da oferta.
- Compatibilidade vc98: guard cumulativo corrigido para comparar os 17 arquivos Reader vc92 com vc92, mas o Service MediaProjection (lifecycle vc95) com vc97. Teste JVM adicional `Field98ImmutableSourceContractTest` compara esses 17+18 arquivos por Git blob SHA durante `testDebugUnitTest`, inclusive em checkout shallow no Actions, sem modificar workflow. Verificação manual do guard completo ainda requer histórico Git; compilação Android, revisão Claude e homologação permanecem pendentes.
- CI manual executado em 13/10/2026 no SHA `9801709` (run 38447877616): Web/Next.js OK, arquitetura Android OK e compileDebugKotlin alcançou os testes; `testDebugUnitTest` terminou com 567 testes, 6 falhas em contratos antigos vc96/Radar v4/v6/v7 que exigiam strings/IDs substituídos na vc98. Corrigidas apenas as expectativas desses testes para os novos invariantes (ícone nativo, HUD compacto, validação do destino completo), com testes negativos contra comportamento antigo. Ainda falta repetir CI para confirmar os 567 testes, gerar APK e realizar revisão independente; NÃO declarar build homologado antes disso.
- Rollback: reverter commits exclusivos desta branch ao SHA base `91322b81` sem tocar em main/branch de campo. Homologação física e revisão Claude ainda pendentes; não publicar nem mesclar.

## 1. Objetivo atual

Levar o Sr. Rotas 1.0 à Play Store preservando primeiro:
1. integridade de dados;
2. estabilidade de captura;
3. contratos funcionais;
4. compatibilidade;
5. UX;
6. novas funcionalidades.

Próximo objetivo imediato:
- consolidar o vc98 na branch isolada e executar compilação/testes Android, guards e revisão independente antes do APK;
- homologar no Samsung: mapa com abas, overlay compacto, sugestão temporal do Assistente e checkmark de 3 estados;
- Estatísticas inteligentes evoluem em branch paralela sem se misturar ao APK vc98;
- após homologação, realizar teste de NOVO USUÁRIO ponta a ponta e fechar os gates de lançamento restantes.

## 2. Arquitetura congelada

Android:
- `ConsolidatedMainActivity027037` é o shell principal.
- Reader M1/MediaProjection é o leitor oficial.
- Reader 2 permanece shadow/observacional.
- Controlled Hybrid permanece OFF.
- não criar segundo OCR pesado concorrente;
- não alterar Reader/HUD/parser/dedupe/fórmulas em correções puramente visuais/comerciais.
- `JourneyBubbleController` é o host principal da janela flutuante/HUD.

Dados:
- `ride_offers` = observações operacionais; não representam automaticamente corridas concluídas ou receita.
- `ride_outcomes` confiável exclui legado `uber_history_ocr` das métricas realizadas fortes.
- V7 é histórico canônico observado; NÃO reprocessar.
- nunca inventar valores ausentes.
- pseudonimizado não significa anonimizado.
- não persistir OCR bruto, screenshots, trilha GPS arbitrária, tokens ou Android ID bruto em telemetria analítica.

Web/backend:
- Next.js + Supabase + Vercel.
- AnalyticsDomain/Evidence Policy é a base compartilhada por Estatísticas/Pergunte/Agora/MCP.
- Pergunte é determinístico; não depende de API externa de IA e não consome créditos por consulta.

## 3. Estado dos Gates 1.0

### Gate 1 — MCP OAuth
Estado: funcional/homologado tecnicamente.
- OAuth 2.1 + DCR + PKCE S256.
- refresh rotation, revogação e reautorização validadas.
- sem chave manual.
- 12 ferramentas read-only:
  - `get_srrotas_capabilities`
  - `get_history_dashboard`
  - `get_driver_summary`
  - `get_driver_strategy`
  - `get_strategy_progress`
  - `search_offers`
  - `compare_periods`
  - `get_best_hours`
  - `get_cost_breakdown`
  - `get_current_journey`
  - `list_journeys`
  - `get_journey_summary`
- identidade duplicada de tester foi consolidada: Android operacional + Web/OAuth + V7 agora convergem para um único driver.
- smoke com dados recentes deve ser repetido sempre que houver alteração de identidade/auth, mas não reconstruir OAuth sem evidência.

### Gate 2 — Fundação Premium
Estado: ✅ fechado.
- AnalyticsDomain/Evidence/NQE compartilhados.
- 18 intents determinísticos.
- EvidenceEnvelope e políticas de suficiência/confiança.
- sem OpenAI/API externa.

### Gate 3 — Conciliação + Estatísticas + Pergunte + MCP
Estado: ✅ fechado.
- `/app/estatisticas`
- `/app/ia`
- `/app/conciliacao`
- reconciliação é decisão humana; nunca auto-confirma.
- métricas realizadas fortes ignoram outcomes `source=uber_history_ocr`.
- `microphone=(self)` homologado para SpeechRecognition.
- MCP continua read-only.

### Gate 4 — Comercial
Estado: ✅ fechado.
Modelo:
- **Sr. Rotas Copiloto** = gratuito/permanente para contas não bloqueadas.
- **Sr. Rotas Inteligência** = R$ 9,90 / 30 dias.
- trial = 7 dias de Inteligência a partir da primeira oferta operacional válida.
- sem cobrança automática.
- Pergunte determinístico não usa créditos.
- tabelas/carteiras antigas de créditos ficam apenas como legado/auditoria.

Pix Banco Inter:
- Pix real de R$ 9,90 homologado.
- retorno bancário `CONCLUIDA`.
- assinatura ativada por 30 dias.
- checkout deve mostrar:
  - `Intermediações de Pagamentos BigCorps`
  - `Sr.Rotas | Desenvolvido por BigCorps`

### Gate 5 — Android / Radar Contextual
Estado: vc92 homologou satisfatoriamente o Reader em campo. Radar chegou a R3 com corrida/destino/ETA válidos e HTTP 200, mas zero POIs/oportunidades; cobertura pequena do catálogo será tratada separadamente. vc93 provou R4, currentRide, ETA/geocode, runtime e backend HTTP 200 com 1 POI/oportunidade; PendingIntent DEMO chegou à Activity, mas mapa ficou liberado/inativo. Navegação/mapa falhou na homologação; Reader não é a causa. vc94 homologou abertura da superfície/DEMO, mas falhou na cartografia e turnover; vc95 preservou abertura/cards, mas cartografia branca persistiu e controles aumentaram o HUD; vc96 implementou compatibilidade bitmap, mas o report de 09/10/2026 ainda relata mapa parcial e Assistente ausente. vc97 adiciona observabilidade, rescan diagnóstico isolado e submenu compacto; não declara corrigida a cartografia.


**FIELD 09/10/2026 — report operacional vc96 / objetivo vc97**

- Assistente não aparece nas condições esperadas; Radar abre parcialmente, às vezes com apenas um ponto; origem/destino nem sempre completos. Necessários rescan de uma screenshot e reorganização dos controles do HUD.
- Evidência fornecida do Samsung SM-X626B / Android 16: `mapview_created=true`, `style_loaded=true`, `map_fully_callback=true`, `live_snapshot_success=true`, `visible_bitmap_shown=true`. Usuário ainda vê mapa incompleto/pouco funcional. Esses callbacks e bitmap não branco NÃO comprovam tiles/cartografia útil.
- Radar: HTTP 200 em consultas sucessivas, POIs=0/ops=0, backend `no_strong_opportunity`, entrega `outside_region_eta_window`. Assistant tradicional `no_journey` no instante do export, com episódios históricos de overlay: NÃO concluir que nunca disparou. Labels presentes com `geocode_status=partial` demonstram distinção entre interpretação e resolução.
- Objetivo: reconstruir Oferta → OCR → interpretação → contexto/geocode → currentRide/ETA → baseline/oportunidades → decisão → HUD attached/visível → toque → superfície → bitmap. Sem thresholds novos, sem forçar STRONG, sem mudança cartográfica sem causa demonstrada.

**Matriz de fontes auditadas antes da implementação**

| Camada | Fonte real | Evidência / limite do vc97 |
|---|---|---|
| Reader / texto | Oferta admitida em LocalStore; OfferContextEngine / parser espacial congelados | Presença de labels e confiança armazenada. OCR_CAPTURED é explicitamente inferido da oferta capturada persistida, não instrumenta frames rejeitados nem reprocessa M1. Normalização continua no Context Engine. |
| Geocode | local_offer_context atualizado pelo fluxo existente | Resolved/partial/pending/failed/unresolved separados da presença de texto; célula e validade das coordenadas em booleanos, sem exportar conteúdo. |
| Corrida / ETA | JourneyCoordinator.currentRide → RadarDestinationContextV1 | ID opaco para correlação, seleção explícita, spec pronta/bloqueada e delta ETA assinado. Resolver atual procura latest/recentes(100); fonte fora desse alcance aparece como source_offer_missing, sem correção especulativa. RADAR_SPEC_SOURCE distingue latest_offer_snapshot de persisted_recent_offer; contexto em memória pode estar defasado em relação à atualização SQLite e exige evidência por corrida antes de correção. |
| Radar / potencial | Resultado capturado por localOfferId no client/runtime/panel | HTTP, cache, baseline samples/probabilidade, oportunidades e POIs distintos das oportunidades. O payload NÃO fornece contagem bruta do catálogo nem causa causal de zero: `zero_reason_not_exposed_by_backend`. Baseline alto não inventa STRONG. |
| Assistente contextual | DestinationRadarAssistantBridgeV1 + RuntimeBridge + Renderer | Decisão, flags bloqueadas, tentativa, host/card anexados e post-layout realmente shown/medidos; toque aceito mantém identidade da corrida. STRONG/DISCOVERY/REGION e cooldown intactos. |
| Assistente tradicional | ActiveAssistant026 e diagnóstico/histórico existentes | Separado do contextual. `no_journey` atual não apaga evidência histórica nem prova ausência anterior. |
| Superfície / mapa | Activity coordinator, Panel, MapLibre bitmap | Pedido distinto de abertura renderizada; stale/flags/readiness diagnosticados. MAP_BITMAP_VISIBLE não prova cartografia: marcadores podem produzir variação; câmera/tiles/gestos exigem campo. |

**vc97 — Field vc97 — End-to-end trace + rescan + compact HUD**

- `0.33.20-field / versionCode 97`, base `877bb13406ecbe002439facb0980ebfe3272e2cd`. Trace local persistido em preferences: até 12 episódios, 64 transições por episódio, últimos estados por camada e retenção de 7 dias, fila de escrita limitada a 128, LRU e debounce de disco 500 ms; export em `radar_contextual_v1.field_pipeline_trace_v1`. Não é telemetria remota. Identidade opaca derivada do ID local; sem OCR bruto, endereço, coordenadas, imagem, tokens ou texto de erro livre. Escrita fora do worker do Reader; sob sobrecarga pode perder eventos (contador dropped_queue_events), mantendo limites explícitos. O export pode preceder uma escrita ainda na fila; encerramento abrupto antes do debounce pode perder a cauda.
- Oferta/contexto observados depois da persistência oficial, sem efeito na admissão. Respostas conservam o ID capturado na requisição; callback antigo não é atribuído à corrida nova. Estados por camada sobrevivem à rotação do ring e saída/recreate/processo.
- Foto / Rescan: ícone de câmera ao lado de Jornada. Nova captura privada ganha índice exato full localOfferId → arquivo privado (limite 30 / mesma retenção); legado não é inferido por prefixo e Galeria não é varrida. Sem vínculo, seletor oficial Android de uma imagem e URI temporária somente leitura. Preview e confirmação obrigatórios; limites 16 MB / 40 MP na entrada e lado decodificado até 1800 px, orientação EXIF respeitada. Nenhuma imagem é salva pelo rescan ou enviada ao backend.
- Executor diagnóstico próprio, separado do HistoricalScreenshotImporter (este salva e sincroniza ofertas). Reutiliza SpatialOfferParser sem alterações: leitura **Uber pelo caminho espacial histórico**, não reprodução exata do M1 nem suporte novo a 99. Múltiplos cards não são escolhidos arbitrariamente. Compara tarifa, busca/origem, km/min de busca, destino, km/min de viagem, confiança e geocode; campos igual/recuperado/diferente/ausente. Sem geocode remoto; texto novo sozinho geralmente não basta para RadarDestinationSpec. Nenhuma oferta, corrente de admissão, currentRide, sincronização ou Radar alterada.
- Exclusão conservadora: rescan somente sem jornada/projection/service de captura, em M1 e **antes de qualquer OCR operacional/importação no processo**. Após uso de captura/importação, encerrar jornada e reiniciar app manualmente. Motivo: workers congelados não expõem drenagem de tarefas ML Kit; não inferir o fim do OCR pelo fim do Service. Gate em callsites impede start de leitura durante rescan; não muda Reader/MediaProjection lifecycle. Timeout visual informa espera, mas não libera lease até a tarefa realmente completar; fechar Activity não libera lease prematuramente. Não interrompe MediaProjection nem solicita consentimento sozinho.
- HUD: Play principal apenas expande/recolhe Jornada, inicialmente recolhida. Submenu Play/Pause/Stop/Reiniciar preserva ações; Reiniciar usa fresh MediaProjection via DiagnosticQuickActions0270, nunca ACTION_RECOVER. M2/sem jornada desabilita restart. Fecha no clique ou recolhimento. Histórico/Mensagens/Digitalização/Bug continuam acessíveis. Ações de oferta só no card expandido; ReportSelection permanece relatório.
- MapLibre 13.6.1/OpenGL/OpenFreeMap Liberty, snapshotter/câmera/gestos intactos. Trace declara cartografia não comprovada. Hipóteses ainda abertas: bitmap com marcadores e base ausente, região sem oportunidades, contexto parcial, ETA fora de janela; não escolher causa sem evidência de campo. Baseline/regra STRONG/política de oportunidades intocados.
- Reader: 17 arquivos do baseline vc92 `1421f512d966101cc6bbd0dfda52cf0626a9c4dd` idênticos; MediaProjectionOcrService e lifecycle aprovado vc95 idênticos à base vc96. SpatialOfferParser, M2 e digitization worker congelados; Reader2 shadow, Controlled Hybrid OFF, single-heavy-OCR. Backend/Supabase/workflows intactos. Sem Actions/deploy/merge.

**Complemento pós-review vc97 — visualização e exclusão OCR**

- Precheck no onCreate antes de qualquer seletor automático. Jornada ativa, captura em andamento, outro OCR, necessidade de reiniciar processo, modo incompatível ou verificação indisponível têm motivos próprios. Imagem privada vinculada e dados oficiais permanecem disponíveis para comparação manual durante a jornada; sem vínculo, o tester pode escolher uma imagem manualmente somente para visualização. Não interrompe captura/jornada e não executa OCR ao abrir a imagem.
- Confirmação OCR habilitada somente em condição segura, com revalidação sob a mesma exclusão usada pelos inícios operacionais. Falhas de SettingsRepository ou agendamento liberam a lease antes de iniciar trabalho; bitmap só passa ao worker depois de carregar configurações. Finalização real do worker descarta seu bitmap e libera a lease em finally, inclusive em erro. Destruição da Activity, aviso visual de 20 s ou interrupção de espera não liberam OCR nativo ainda ativo.
- Risco residual: ML Kit nativamente travado pode reter a exclusão indefinidamente. Procedimento: aguardar conclusão; para travamento confirmado, forçar a parada do app nas configurações Android e reabrir (recolher a Activity não encerra necessariamente o processo). Não há timeout que permita Reader concorrente nem reinício automático da MediaProjection. Para uma nova releitura após OCR operacional anterior, encerrar a jornada e reiniciar manualmente o processo; visualizar não exige essas ações.
- Validação do micro-patch: 550 testes JVM sem falhas/erros, compileDebugKotlin com Java 17, diff check e guards Radar v1–v6/Field vc95–vc97/architecture. Guard de tamanho do APK executado, mas indisponível por ausência de APK release; nenhum APK foi gerado.
- Aceite específico: visualizar imagem vinculada/galeria durante jornada sem picker automático/OCR; conferir motivo e botão bloqueado; simular falha de configuração/agendamento e comprovar captura normal novamente permitida; destruir Activity/aguardar aviso sem liberar worker ativo. Validação em aparelho permanece pendente. Rollback “Desativar rescan” bloqueia novas releituras, conserva visualização manual e nunca libera lease de worker ativo.

**Aceite, rollback e handoff incremental vc97**

- Validação local 09/10/2026: 538 testes JVM, zero falhas/erros; compileDebugKotlin Java 17, diff check, guards Radar v1–v6/Field vc95–vc97/architecture. Aceite em aparelho pendente: recuperar trace após sair/matar/reabrir app, correlacionar corrida/ETA/bloqueio, comprovar attach/visibilidade/toque; comparar imagem sem alterar oferta; tentar iniciar Reader durante rescan; verificar seis ícones compactos e submenu; testar URI revogada, foto rotacionada, imagem grande e Activity destruída durante OCR.
- Rollback: botão “Desativar rescan (rollback)” na própria Foto / Rescan impede novas execuções sem afetar jornada ou dados. Gate de exclusão permanece durante operação já iniciada até terminar. Rollback Radar R0 continua disponível; sem reativação de flags. Reverter este commit restaura o HUD/trace anteriores; preferences/índice inertes não alteram dados oficiais.
- Revisão Claude somente diff da base acima até o novo HEAD: URIs/memória/lease OCR, ausência de efeitos oficiais, trace sanitizado, submenu e preservação Reader. Testes locais não homologam GPU, permissões, BAL ou concorrência no Samsung. Após commit/push PARAR antes de Actions; nenhuma revisão Claude foi executada automaticamente.
- Próximo passo: Claude read-only; após aprovação explícita, Actions/APK e campo vc97. Cartografia continua **ABERTA**, catálogo POI separado. Atualização confirmada de correções é fase futura: exigir oferta/versão de origem ainda idênticas, diff explícito aprovado pelo usuário, revalidar contexto/geocode e contrato de sincronização/transação; nunca promover resultado deste rescan automaticamente.

**FIELD VC94 — 07/10/2026 — report operacional real**

Status encontrado: **ABERTO antes do vc95**.
1. Radar/Continuidade: superfície e Prévia DEMO abrem; header/cards/POIs/botões e espaço físico do mapa aparecem. Navegação/surface lifecycle melhoraram, mas MapLibre/cartografia permanece totalmente branco; renderização cartográfica segue aberta.
2. Corrida ativa: após currentRide/DOING_RIDE, ofertas seguintes perdem a seleção operacional normal e podem ficar em “OUTRA CORRIDA ATIVA”; a nova corrida não assume currentRide e prejudica o destino seguinte.
3. Reader: qualidade/leitura satisfatórias; perda após aproximadamente 1h30–2h. Reinício técnico existente não recuperou naquela ocorrência. Preservar interpretação/parser; atuar somente na resiliência MediaProjection/capture lifecycle.
4. Requisito operacional: botão direto no HUD solicita NOVA MediaProjection, reabre o seletor Android e preserva a mesma jornada, sem reutilizar silenciosamente token antigo.

Evidência JSON vc94: `surface_open_state=rendered`, panel attached/shown=true, stage=666×780, surface_render_completed=true, map_created_count=4/map_ready_count=4, mas cartografia branca. `map_ready` não prova frame cartográfico. Captura: pending_recovery=true, interruptions=21, projection_stopped_by_system=21, resume_requested=7/resume_success=7 e manual_recovery_unavailable. Sessão atual: 5043 OCRs concluídos, 0 failures; Reader não é causa de parser/gates.

**FIELD VC95 — NOVO REPORT DE CAMPO — 07/10/2026**

Status: **ABERTO antes do vc96**. Prioridades: mapa → compactação HUD → reiniciar captura → screenshot review.
1. Radar: tela/cards/área física abrem, mas cartografia continua ausente/branca após vc95.
2. HUD: ESTOU NESSA CORRIDA e controles fora da expansão aumentaram a altura; regressão visual. Todos os controles específicos pertencem ao card expandido correspondente.
3. Reiniciar captura: botão permanente sob o rodapé ocupa espaço; a ação deve existir somente no menu Bug/Diagnóstico.
4. Screenshot review é viável, mas OCR replay NÃO faz parte deste commit; próximo bloco isolado.

Evidência JSON vc95 (`0.33.18-field / versionCode 95`, Samsung SM-X626B, Android 16): usuário viu branco; export posterior mostrou R3, surface_open_state=idle, map_created_count=0/map_ready_count=0, style/first/fully=false e map_state=não criado. Episódio desapareceu do diagnóstico em memória; callbacks não provam imagem visível.

vc96 — Field vc96 — Radar visible-map compatibility + HUD compact restore:
- `0.33.19-field / versionCode 96`. MapView real permanece atrás de ImageView passivo; snapshot do próprio MapLibre após state/render/camera idle é amostrado em grade pequena. Bitmap não uniforme vira cartografia visível; blank/erro/timeout aciona um único MapSnapshotter com mesmo estilo/câmera em resolução limitada, destino/POIs desenhados via pixelForLatLng. Debounce/generation/timeouts limitados, cancelamento em nova geração/detach/release. Sem persistir/enviar bitmap, sem screenshot de tela, sem OCR.
- Falha das duas vias mantém explicação “Mapa indisponível neste aparelho. As oportunidades e Maps/Waze continuam funcionando.” Cards/Atualizar/navegação externa preservados. MapLibre Native 13.6.1/OpenGL/OpenFreeMap Liberty inalterados.
- `last_map_episode` local em SharedPreferences preserva fonte demo/real, versão, início, etapas snapshot/fallback e erro por código sanitizado após sair/recreate/release. Não guarda endereço, coordenadas, nomes de POI, URL dinâmica, imagem ou OCR.
- Todos os controles operacionais ficam exclusivamente no card expandido. CurrentRide source é consultada pontualmente no LocalStore e ocupa um dos N cards normais, sem duplicação/expansão automática; source ausente mostra somente pill compacto/diagnóstico. Troca continua exigindo dois toques confirmados, ReportSelection permanece relatório.
- Rodapé volta aos ícones compactos; Reiniciar captura existe somente no menu Bug/Diagnóstico e usa DiagnosticQuickActions0270.restartReading. Lifecycle fresh projection aprovado vc95 permanece byte a byte intacto.
- Reader semântica vc92 congelada: 17 arquivos idênticos ao baseline `1421f512d966101cc6bbd0dfda52cf0626a9c4dd`; Service/capture lifecycle idênticos ao vc95 `5040760430d302479f4a389347ca493e49934581`. Reader2 shadow, Controlled Hybrid OFF, single-heavy-OCR; nenhum backend/Supabase/workflow/provider alterado. Nenhum auto-launch, deploy/Actions/merge.
- Heurística pode rejeitar região cartográfica muito uniforme; snapshots não substituem homologação física de GPU/gestos/memória. Revisão Claude antes de APK/Actions; IMPLEMENTADO ≠ HOMOLOGADO.

**Plano histórico vc96: Offer Screenshot Review V1 — execução diagnóstica entregue no vc97 acima**
- Não escanear Galeria/backlog legado de 12 mil+ imagens. Para novas capturas, PrivateScreenshotStore.save já recebe RideOffer: índice local futuro full localOfferId → private screenshot file, com retention do cache privado.
- No card EXPANDIDO, VER CAPTURA/REVISAR LEITURA somente com vínculo existente. Revisão manual de uma imagem: OCR isolado compara original × nova leitura de origem/busca, distância/minutos de busca, destino, distância/minutos de viagem e tarifa.
- V1 diagnóstica: não substitui oferta oficial, currentRide ou Radar; não envia screenshot ao backend. Não executar replay em paralelo à captura live sem contrato específico. NÃO implementado em vc96.

vc95 — Field vc95 — Map render + Ride turnover + Fresh Projection Recovery:
- MapLibre observa style/loading/first frame/fully rendered/error. Fallback só desaparece após fully-rendered em superfície ativa com destino submetido; load tem timeout de 15 s a partir do attach; render tem 8 s somente após style+destino submetido. Timeouts mantêm explicação e são recuperáveis por fully-rendered tardio; erros reais continuam fatais. Atualizar recria o mapa pelo fluxo existente. MapLibre 13.6.1/OpenGL/OpenFreeMap/STYLE_URI preservados.
- Primeira seleção permanece em um toque. Troca no HUD exige TROCAR PARA ESTA CORRIDA → CONFIRMAR TROCA em até 7 s, somente em memória e vinculada à jornada/corrida atual/oferta. Coordinator nega replacement por padrão: notificação antiga não substitui currentRide. Seleção confirmada permite substituir corrida na mesma jornada ACTIVE, com anterior NOT_COMPLETED/replaced_by_new_ride e nova DOING_RIDE em transação; toque na própria corrida é idempotente. CORRIDA ATIVA, REALIZADA/NÃO REALIZADA e Radar ficam no card normal. Checkmark continua somente ReportSelection/relatório.
- Reiniciar captura no HUD M1 pede consentimento Android novo; force fresh projection desmonta a sessão antiga antes de usar resultData recém-autorizado. Jornada/histórico preservados; callbacks antigos não liberam/encerram a nova sessão. M2 não oferece o botão.
- Pós-review: fresh rejeitado preserva projection existente. Com consentimento válido e sem projection, o serviço reconhece FGS antes de validar a jornada; se ela encerrou entre Activity e serviço, registra fresh_projection_rejected_no_session e executa stopForeground/remove → stopSelf, sem criar MediaProjection ou reabrir jornada. Consentimento inválido retorna nos dois callsites antes de iniciar FGS. Diagnóstico mapa distingue load_timeout/render_timeout e map_late_render_recovered sem dados sensíveis.
- Recovery técnico ACTION_RECOVER continua usando a projection existente, sem seletor automático. Perda real mantém recovery pendente/journey aberta. Novos counters locais distinguem recovery técnico/fresh/stale callback.
- Semântica Reader congelada no baseline vc92 `1421f512d966101cc6bbd0dfda52cf0626a9c4dd`: 17 arquivos byte a byte; MediaProjectionOcrService é exceção somente lifecycle/consentimento/callback/diagnóstico. OCR/parser/gates/sampling/dedupe/M1 temporal preservados pelo guard estrito. Reader 2 shadow, Controlled Hybrid OFF, single-heavy-OCR.
- Nenhum auto-launch de mapa. Backend/Supabase/workflows/provider permanecem inalterados. Não houve deploy/Actions/merge. Implementação local não homologa o render GPU nem captura longa: revisão Claude, APK e campo continuam necessários.

vc94 — Radar Contextual Field 7:
- `0.33.17-field / versionCode 94`; Reader permanece byte a byte no baseline vc92 `1421f512d966101cc6bbd0dfda52cf0626a9c4dd` (guard v6 e manifesto JVM). Reader 2 shadow, Controlled Hybrid OFF, single-heavy-OCR.
- Um coordenador local da Activity recebe REAL/DEMO de Agora, HUD, Assistente e Field; consumo único após onPostResume, seleção da aba Radar, auxiliares ocultos e superfície anexada/visível/medida. No máximo 6 tentativas por abertura; nenhuma nova Activity ou persistência do pending.
- CTA Agora resolve current spec no clique. REAL revalida currentRide antes de abrir e renderizar. DEMO sobrevive a resume/refresh automático; saída explícita, estágio/Rollback, refresh operacional ou REAL abandonam DEMO.
- Resultado válido oculto fica armazenado para render pendente; geração/corrida continuam protegidas. Nenhum MapLibre em superfície hidden/zero-size; provider, URI OpenFreeMap e BAL preservados. Field diagnóstico recolhível com scroll limitado para reservar área ao mapa.
- DISCOVERY/REGION: ETA de -10 a +18 minutos (tolerância de chegada); STRONG permanece backend. REGION = uma vez por corrida/localOfferId, sem expiração após shown; duas preferences fixas e migração lazy da chave antiga da corrida atual. DISCOVERY conserva cooldown de 20 min por oportunidade; STRONG inalterado. REGION mantém ID nulo e metadata.variant=region.
- Diagnóstico apenas memória: origem/kind/estado/medidas/tentativas/defer/render, DEMO e contadores/erros/release do mapa fora do ring; eta_delta_seconds assinado, sem endereço/coordenadas/OCR/screenshot.
- PendingIntent chega à Activity; vc94 corrige lifecycle da superfície e ETA boundary. Nenhum mapa autoaberto pelo runtime: exige toque. Backend/Supabase/workflows/ranking/radius não alterados.
- Revisão Claude somente leitura antes de Actions; nenhum merge. Testes JVM/guards não substituem validação física.

vc93 — Radar Contextual Field 6:
- `0.33.16-field / versionCode 93`.
- **READER VC92 CONGELADO** no commit funcional `1421f512d966101cc6bbd0dfda52cf0626a9c4dd`; guard v5 compara conteúdo/inventário e teste JVM compara SHA-256. Nenhuma mudança em M1/OCR/parser/dedupe/admission/fórmulas, Reader 2 shadow, Controlled Hybrid OFF e single-heavy-OCR.
- Migração local única, somente Field vc93+: R3 com UI/runtime ativos promove assistente para R4; flags gerais continuam false. Mesmo sem promoção, migração é consumida; escolha manual e Rollback também a consomem e nunca são revertidos pelo próximo sync.
- STRONG/DISCOVERY conservam comportamento vc92. REGION, somente após resposta bem-sucedida da corrida atual, zero oportunidades e ETA 0–18 min: “Radar analisou sua região de chegada. Quer ver?” / “Ver região”. Sem oportunidade/POI sintético, ID de oportunidade nulo, cooldown de 20 min por corrida separado das oportunidades reais.
- HUD informa região analisada sem oportunidade e oferece VER REGIÃO DO DESTINO. Mapa abre somente após toque pelo launcher BAL existente, centralizado no destino mesmo sem marcadores, com empty state explícito e atualização manual.
- Diagnóstico inclui field_r4_migration_applied persistido, assistant_kind e region_signal_generated/rendered/view_clicked; não exporta destino textual, coordenadas, OCR ou screenshots.
- Backend/Supabase/ranking/radius/MapLibre provider/workflows/permissões permanecem iguais.
- Hardening pós-review: falha de WindowManager limpa host/estado visual; renderer exige host e card anexados antes de shown/cooldown. REGION envia metadata.variant=region mantendo ID nulo; diagnóstico separa decisão atual e último tipo renderizado e preserva seleção válida em decisões nulas. Versão vc93 mantida.
- Após commit/push: nova revisão Claude somente do patch, somente leitura; nenhum Actions nesta tarefa e nenhum merge. Homologação física de overlay, lifecycle, BAL e migração ainda necessária.

vc92 (Radar Contextual Field 5 sobre vc91):
- `0.33.15-field / versionCode 92`.
- STRONG preserva elegibilidade/headline/action do backend; DISCOVERY usa oportunidades existentes, texto neutro, ETA 0–18 min e cooldown de 20 min da mesma oportunidade.
- Nenhum mapa aberto automaticamente; callback atualiza somente HUD existente e motorista toca Ver para abrir Radar.
- Feedback Radar no HUD é vinculado à identidade currentRide; latest é limpo na troca de corrida.
- Prévia DEMO local perceptível: visibilidade, scroll ao topo, feedback Field e eventos demo_preview_opened/rendered, sem backend ou telemetria operacional.
- M1 temporal single-OCR recovery usa dois OCRs recebidos em memória por até 2 s: tarifa única igual, dimensões e posição de card estáveis, âncora explícita Uber, sem navegação/99 e dois pares complementares. Frames isolados ou conflitantes não formam candidato.
- Candidato passa por OfferParser/contexto/OfferIntegrityGate027033, dispatcher/admission/dedupe existentes; sem relaxar gates, fórmulas ou 99.
- Diagnóstico exporta contadores m1_temporal_* e uber_anchor_geometry_0/1/2plus, sem OCR bruto/endereço/coordenada/screenshot. recovered_offers conta recuperados prontos no Integrity Gate enviados ao dispatcher; admissão/dedupe ainda podem suprimi-los.
- Reader 2 continua shadow; Controlled Hybrid OFF; único OCR pesado; ShadowOfferRecovery027033 permanece desativado.
- Backend/migrations/workflows/provider MapLibre/permissões não alterados.

vc91 (Radar Contextual Field 4 sobre vc90):
- `0.33.14-field / versionCode 91`.
- crash de reentrada corrigido: View de loading nova a cada abertura.
- fetch UI protegido contra callback obsoleto por geração, currentRide.localOfferId e superfície visível; DEMO, idle, ocultação e detach invalidam consultas.
- MapLibre permanece lazy e releaseMap preservado.
- diagnóstico M1 separa `candidate_no_offer` (`blocked_offers=0`) de `integrity_reject` (`blocked_offers>0`), com contadores explícitos e reason original preservado.
- eventos legados sem contagem permanecem não classificados; métricas espaciais preservadas, sem OCR bruto/endereço/coordenada/screenshot.
- sem alteração funcional do Reader M1; Reader 2 continua shadow; Controlled Hybrid OFF.
- nenhum backend/migration alterado.

vc90 (Field 3 sobre vc89):
- `0.33.13-field / versionCode 90`.
- o motorista confirma explicitamente **ESTOU NESSA CORRIDA** no card da oferta; somente essa ação cria `DOING_RIDE`/`currentRide`.
- corrida ativa mostra **VER OPORTUNIDADES NO DESTINO** quando destino + ETA estão disponíveis.
- Runtime é acordado imediatamente após `markDoingRide` e o diagnóstico prova `ride_mark_requested → ride_mark_success → spec_resolved → backend_query`.
- Assistente real e DEMO abrem a Activity por `PendingIntent` com opt-in BAL para Android 14+/15+; diagnóstico prova `open_attempt → open_sent → intent_received`.
- MapLibre é lazy: nenhum `MapView` é criado no bootstrap do shell; ao sair da superfície Radar o mapa é destruído e `map_active=false`.
- Reader M1, router, Uber gate/spatial parser, OfferParser, OfferDeduplicator e backend permanecem idênticos ao vc89.
- nenhuma migration.

vc89 (Field 2 sobre vc88):
- `0.33.12-field / versionCode 89`.
- feature flags do Radar Contextual começam `false`.
- `RadarPanel027035` permanece como rollback.
- módulo contextual está ligado ao fluxo real, não apenas presente no repositório.
- destino da corrida é resolvido por:
  `currentRide.localOfferId → LocalStore → RideOffer.context`
- não voltar a usar `latestOffer` como pressuposto da corrida ativa.

Radar R1 backend homologado:
- 4 POIs.
- 4 aliases.
- 7 eventos ativos ligados.
- 4 `created`.
- 3 `linked` por identidade exata.
- 0 eventos ativos sem POI.
- `review` nunca grava `poi_id`.
- migrations Radar já estão aplicadas; NÃO reaplicar.

Teste Field do vc89:
1. instalar por cima da build atual; nunca limpar dados;
2. R0: validar Radar legado;
3. `Prévia DEMO`;
4. ativar `1 · UI`;
5. testar corrida real e CTA `Ver oportunidades no destino`;
6. ativar `2 · Runtime` somente após UI aprovada;
7. ativar `3 · Assistente` somente após Runtime aprovado;
8. validar `Assistente DEMO`;
9. usar `Rollback` a qualquer regressão;
10. conferir telemetria/diagnóstico antes de promover flags.

Correções P1 do primeiro Field:
- DEMO é estritamente uma prévia explícita e não sobrevive a refresh operacional, CTA real, troca de corrida ou mudança R2/R3/R4;
- mapa interno passa a usar MapLibre Native + OpenFreeMap, sem API key paga;
- POIs do backend Sr.Rotas são os marcadores do mapa; Google Maps/Waze ficam como navegação externa;
- painel Field mostra fonte `currentRide.localOfferId → LocalStore → RideOffer.context`, presença de lat/lng/ETA, última consulta, HTTP/backend, POIs, oportunidades, decisão do assistente e cooldown;
- JSON combinado exporta `radar_contextual_v1` sem endereço textual ou coordenadas exatas;
- gate R4: não avançar até R2/R3 provarem visualmente a identidade da corrida real.

Runtime:
- somente em corrida válida com destino resolvido;
- runtime não captura/OCR; vc92 somente atualiza o HUD existente;
- cadência contextual nunca mais frequente que 1/min;
- contexto externo pode alterar relevância/ranking, nunca fabricar `continuity_probability_pct`.

## 4. Access Resolver

Estados:
- `TRIAL_PENDING`
- `TRIAL_ACTIVE`
- `PAID_ACTIVE`
- `EXPIRED_READ_ONLY`
- `BLOCKED`

Configuração de homologação:
- `enforcement_mode=observe`
- `max_active_devices=2`
- `require_device_identity=false`

Contrato:
- Copiloto: `can_operate=true` e `can_history=true` para contas não bloqueadas.
- Inteligência: `can_analytics`, `can_ai`, `can_mcp` dependem de trial/assinatura quando enforcement for promovido.
- TRIAL_PENDING deve permitir a primeira oferta.
- fim do trial/assinatura nunca deve bloquear o Copiloto.
- não promover `observe → enforce` antes do teste de novo usuário ponta a ponta.

Device identity:
- `Settings.Secure.ANDROID_ID`.
- raw ID não é armazenado no servidor.
- HMAC SHA-256 server-side.
- fallback por instalação quando necessário.

## 5. V7 — congelado

Não reprocessar.

Referência normativa:
`docs/contracts/V7-DATA-CONTRACT-v1.0.md`

Métricas do lote consolidado:
- recebidas: 36.089
- fully-ready: 33.532
- partial: 2.557
- invalid: 0
- duplicate: 0
- temporal-ready: 35.996
- route-flow-ready: 35.452
- financial-ready: 34.129

O ownership foi consolidado para o driver canônico do tester sem reprocessar nenhuma oferta.

## 6. Pergunte / Estatísticas / Conciliação

Filtros Estatísticas:
- Ontem
- 7 dias
- 30 dias
- Personalizado

Seções:
- Resumo
- Produtividade
- Horários
- Regiões
- Corridas
- Oportunidades
- Custos

Conciliação:
- compara histórico Uber x ofertas Sr. Rotas;
- candidato pode usar tempo, valor, origem, destino, duração, sequência e delta;
- usuário confirma/nega;
- nunca auto-confirmar;
- downstream usa cobertura de conciliação e qualidade da evidência.

Semântica de oportunidades:
- oferta não concluída ≠ receita perdida garantida;
- previsões exibem amostra/confiança;
- nunca prometer corrida futura.

## 7. Contratos normativos preservados

Os únicos documentos técnicos permanentes devem estar em `docs/contracts/`:
- Capture Resilience
- MCP OAuth
- Radar ingest
- Reader 2 base/parallel/accumulator/consensus
- V7 Data Contract
- Radar Contextual POI / Contextual / Runtime Learning

Documentos de publicação/referência permitidos:
- `docs/DATA-SAFETY.md`
- `docs/PLAY-STORE.md`
- `docs/FIELD-SIGNING.md`
- `docs/ENVIRONMENT.md`
- `docs/DIAGNOSTICS.md`
- `docs/PARSER-REAL-WORLD-FIXTURES.md`

Todo o restante de handoffs, QA antigo, fases, manifests e instruções de ZIP é histórico do Git.

## 8. Supabase / segurança

Princípios:
- tabelas server-only podem ter RLS habilitado sem policy de anon/auth; não criar policy falsa apenas para silenciar advisor.
- funções `SECURITY DEFINER` expostas em `public` precisam ter `EXECUTE` revogado de `public/anon/authenticated` quando forem server-only.
- não executar migrations antigas apenas porque o nome de arquivo difere do versionamento gerado pelo MCP.
- não mass-delete backlog de screenshots.
- não reprocessar V7.

Pendência manual conhecida para Gate 6:
- confirmar `Leaked Password Protection` no Auth/Supabase.

## 9. Play Store — antes da publicação

Antes da RC:
- validar vc88 no tester;
- executar novo usuário ponta a ponta;
- validar offline → online;
- soak/bateria/sync/crash;
- zero P0/P1;
- promover Access Resolver de `observe` para `enforce` somente depois dos testes;
- Play Integrity soft/observe;
- Data Safety;
- declaração MediaProjection;
- foreground service;
- localização;
- Accessibility: produção NÃO usa Accessibility como leitor oficial de ofertas;
- assetlinks com SHA-256 da assinatura final;
- AAB final;
- política de privacidade, termos, suporte e exclusão de conta;
- instalação RC sobre Field sem desinstalar/limpar dados.

## 10. Teste obrigatório de NOVO USUÁRIO antes da Play

Executar instalação limpa com uma pessoa/conta nova e acompanhar banco:
1. instalar;
2. onboarding;
3. cadastro;
4. vincular primeiro aparelho;
5. verificar exatamente 1 `driver`;
6. primeira oferta;
7. trial de Inteligência inicia;
8. Reader/HUD/Copiloto;
9. Web;
10. Pix R$ 9,90;
11. confirmação Banco Inter;
12. PAID_ACTIVE;
13. Estatísticas/Pergunte;
14. MCP OAuth;
15. logout/login;
16. reinício;
17. segundo aparelho;
18. confirmar que ainda existe um único driver canônico.

Falha que crie segundo `driver` é P0 para publicação.

## 11. Regras que NÃO podem regredir

- M1 continua oficial.
- Reader2 continua shadow.
- Controlled Hybrid OFF.
- V7 não reprocessar.
- Money/fórmulas não reabrir sem regressão comprovada.
- `RadarPanel027035` fica como rollback enquanto Radar Contextual não for homologado.
- Radar sem cartografia interna (vc107): nenhum MapView/MapLibre/SDK Google Maps; locais só via Google Maps externo por ação explícita do usuário.
- Radar flags default false.
- destino contextual = `currentRide.localOfferId → LocalStore → RideOffer.context`.
- `review` do POI Resolver nunca escreve `poi_id`.
- MCP nunca executa ações em corridas/mobilidade.
- reconciliação nunca auto-confirma.
- não expor OCR bruto/screenshots/coordenadas/tokens/device HMAC.
- não criar segundo sistema concorrente de HUD/WindowManager.
- não usar documentação histórica como requisito atual.

## 12. Regra documental para agentes

Toda mudança que altere versão, arquitetura, gate, feature flag, contrato comercial, schema/semântica ou estado de homologação deve atualizar **este arquivo no mesmo PR**.

Não criar novos arquivos na raiz com prefixos:
- `APLICAR-`
- `FASE-`
- `PHASE-`
- `QA-`
- `TESTE-`
- `VALIDACAO-`
- `VALIDATION_REPORT`
- `LEIA-PRIMEIRO`
- `LEIA_PRIMEIRO`
- `FIX_`
- `HANDOFF-`
- `ROADMAP-`
- `MANIFEST-`
- `PATCH-MANIFEST`
- `PATCH_MANIFEST`
- `SRROTAS-*-MANIFEST`

Não criar manifests de ZIP como documentação operacional.

Use:
- `README-CONTINUIDADE.md` para estado atual;
- `CHANGELOG.md` para histórico resumido;
- `docs/contracts/` somente para contratos normativos permanentes.

## 13. Próxima ação canônica

1. revisar somente vc97 com Claude antes de Actions; após aprovação, homologar trace, Foto / Rescan offline e submenu Jornada no Samsung/Android 16. Cartografia continua aberta; aplicação de correções e catálogo POI são blocos futuros separados;
2. conferir telemetria Radar após uso real;
3. corrigir somente regressões demonstradas;
4. executar teste completo de novo usuário;
5. fechar Gate 6;
6. gerar RC/AAB e fechar Gate 7.

IMPLEMENTADO ≠ HOMOLOGADO.
CI VERDE ≠ TESTE DE CAMPO.
HEAD ≠ BUILD EM CAMPO.
