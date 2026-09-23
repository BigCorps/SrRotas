# Changelog — Sr. Rotas

Este é o changelog contínuo. Arquivos `CHANGELOG-*` antigos permanecem apenas como histórico das RCs anteriores.

## 0.32.0 Field — 23/09/2026

### Reader 2.0 Accumulator
- Adiciona `Reader2Accumulator032` sobre os candidatos independentes da 0.31.
- Combina somente campos ausentes de observações compatíveis da mesma oferta em janela curta de 4,5 s.
- Valores/labels conflitantes não sobrescrevem estado anterior.
- Estado do accumulator é somente em memória e limitado a poucas janelas.
- Reader 2 continua sem segundo OCR e sem efeito oficial.

### Promotion Readiness — somente shadow
- Candidato precisa estar core-completo, ter pelo menos 2 observações, estar sem conflito e confiança >= 0,70.
- `promotion_effect=false`: readiness não publica oferta, não altera HUD, banco, backend ou admissão.
- Diagnóstico adiciona `reader2_accumulator_032` com campos recuperados, core completado por acumulação e concordância futura com M1.

### Lifecycle / telemetria
- Runtime do Reader 2 paralelo e accumulator passa a ser resetado junto com o início da sessão Reader.
- Corrige risco de telemetria Reader 2 misturar jornadas no mesmo processo.

### Roadmap canônico
- Adiciona `ROADMAP-CANONICO.md` como fonte persistente e versionada de objetivo → módulo → estado → contrato → dependências → próximo passo.
- Architecture Guard passa a exigir roadmap e etapa canônica coerentes.

### Preservado
- M1 continua oficial.
- Reader 2 0.32 não persiste oferta oficial.
- Capture Resilience 0.31.1 permanece intacto.
- Histórico, Radar, UI, screenshots e janela flutuante não são alterados nesta build.

### Versionamento
- `versionCode 75`
- `versionName 0.32.0-field`

## 0.31.1 Field — 23/09/2026

### Capture Resilience
- Adiciona `CaptureResilience0311` para registrar interrupções e retomadas da MediaProjection sem encerrar a jornada.
- Quando a captura cai, a jornada permanece aberta e o estado passa a indicar recuperação pendente.
- `Agora` passa a exibir `Retomar captura` somente quando existe jornada M1/Comparativa aberta sem MediaProjection ativa.
- A retomada solicita novo consentimento Android e reinicia a captura dentro da mesma jornada lógica.
- Nenhum token de MediaProjection é reutilizado silenciosamente.

### Telemetria
- Diagnóstico adiciona `capture_resilience_0311`.
- Contadores: interrupções, `projection_stopped_by_system`, `service_destroyed`, pedidos/autorização/sucesso/cancelamento/falha de retomada e tempo acumulado sem captura.
- Nenhum OCR, screenshot, endereço, coordenada ou conteúdo de tela é persistido pelo módulo.

### Preservado
- Reader 2.0 paralelo 0.31 permanece shadow/parallel e não é promovido nesta build.
- M1 continua oficial.
- Histórico, Radar, fórmulas, admissão, Money Roles e persistência oficial não são alterados.
- Screenshot Storage Guard continua como próxima etapa separada.

### Versionamento
- `versionCode 74`
- `versionName 0.31.1-field`

## 0.31.0 Field — 23/09/2026

### Reader 2.0 paralelo pré-M1
- Adiciona `Reader2Parallel031` como candidate builder independente.
- O Reader 2 recebe as linhas espaciais do mesmo OCR antes de `MoneyRoleResolver030`/OfferParser decidirem se existe oferta M1.
- Pode observar e reconstruir frames que o M1 rejeita, inclusive cards longos e geometrias quebradas em linhas separadas.
- M1 continua sendo o único leitor oficial nesta build.

### Interpretação independente
- Reader 2.0 não chama `OfferParser.parse`, `UberOfferDetector.detect`, `MoneyRoleResolver030` nem o parser shadow 0.30 para formar seus candidatos.
- Possui seleção monetária própria, pareamento próprio de tempo/km e associação própria de origem/destino.
- Mantém a regressão Turbo Mais protegida: `R$ 34,15` principal + `R$ 6,57` promocional continua produzindo tarifa `34,15`.
- Dois cards legítimos no mesmo painel continuam podendo gerar dois candidatos.

### Cards longos
- O candidate builder aceita tempo e km em linhas diferentes e os associa pela proximidade espacial/ordem do card.
- O diagnóstico mede `long_card_frames`, `split_geometry_candidates`, `long_card_candidates` e `long_card_reader2_only`.
- Não há relaxamento do gate oficial M1: a nova leitura permanece shadow até evidência de campo suficiente.

### Comparação M1 × Reader 2
- Diagnóstico adiciona `reader2_parallel_031`.
- Mede `reader2_only_candidates`, `reader2_only_core_complete`, `m1_only_offers`, candidatos pareados e divergências por campo.
- Mede em quais frames o Reader 2 entrega mais campos core completos do que o M1 e vice-versa.

### Hard gates
- Mesmo OCR M1; nenhum segundo `TextRecognizer`.
- Reader 2 não grava `LocalStore`, não envia backend, não controla HUD e não influencia admissão.
- Histórico, Radar, UI, screenshots e janela flutuante permanecem congelados nesta build.

### Evidência que motivou a etapa
- Field2 confirmou Turbo Mais resolvido no shadow monetário.
- Reader 2 shadow apresentou cobertura core superior ao M1 em parte relevante da amostra, mas ainda com divergências que exigem comparação real.
- Foram observadas rejeições repetidas de cards com muito texto por ausência de pickup km/min no M1.

### Próxima etapa registrada
- `0.31.1 Field — Capture Resilience`: preservar jornada quando MediaProjection/service cair e permitir retomar captura com novo consentimento Android.

### Versionamento
- `versionCode 73`
- `versionName 0.31.0-field`

## 0.30.0 Field2 — 22/09/2026

### Core Reader / Money Roles
- Adiciona `MoneyRoleResolver030`.
- M1 deixa de tratar todo `R$` plausível como possível tarifa principal.
- Papéis: `PRIMARY_FARE`, `ADVERTISED_PER_KM`, `PROMOTION_BONUS`, `SECONDARY_MONEY`, `UNKNOWN_MONEY`.
- Turbo Mais/bônus deixam de criar fare line/card artificial.
- Proteção estrutural bloqueia segundo valor monetário na mesma coluna antes de boundary real de rota/card, mesmo se o rótulo promocional falhar no OCR.
- `UberSpatialParser0221` e `OfferSpatialIsolation0221` usam somente fare lines semanticamente válidas em contexto Uber.
- fallback Uber também parte das fare lines seguras.

### Reader 2.0 shadow
- Adiciona `Reader2MoneyShadow030`.
- Segunda interpretação de tarifa usando o mesmo OCR já existente, sem captura/OCR extra e sem persistência/HUD/backend/admissão.
- Diagnóstico adiciona `reader2_money_shadow_030_field2`.

### Regressão coberta
- `Electric / R$ 34,15 / R$/km / nota / Verificado / Turbo Mais / R$ 6,57 / rota` deve produzir tarifa oficial `34,15`.
- `6,57` permanece valor promocional e nunca origina métricas financeiras da oferta.

### Preservado
- `OfferParser` e fórmulas financeiras.
- `OfferDispatcher`, Histórico e contratos oficiais de persistência/backend.
- UI, Radar, janela flutuante e screenshots não são alterados nesta build.

### Próxima etapa registrada
- `0.30.1 Field — Screenshot Storage Guard`.

### Versionamento
- `versionCode 72`
- `versionName 0.30.0-field2`

## 0.30.0 Field — 22/09/2026

### Core Reader modular
- `OfferDispatcher` permanece no contrato existente e continua chamando `OfferAdmissionGate029`.
- `OfferAdmissionGate029` vira fachada de compatibilidade runtime para `OfferAdmissionGate030`, evitando reabrir Dispatcher/HUD/persistência.
- A 0.30 concentra a intervenção na leitura/admissão e congela Histórico, Radar, UI geral e fórmulas financeiras.

### Admissão decimal 0.30
- A identidade temporal deixa de depender de tarifa, km ou minutos.
- Conflito aproximado x10 passa a cobrir tarifa, pickup/trip/total em km e pickup/trip/total em minutos.
- Primeira leitura conflitante é retida; nenhuma correção numérica é inventada.
- Mudança decimal só pode substituir a referência depois de nova observação compatível.
- Rota/contexto estável é priorizado para evitar correlacionar duas ofertas diferentes.
- Fallback genérico sem pickup+destino continua fora da persistência oficial.

### Reader 2.0 shadow
- Ativa `Reader2Shadow030` apenas como observador.
- `UberSpatialParser0221` entrega ao shadow a mesma observação espacial já produzida pelo OCR M1.
- Não cria segundo `TextRecognizer` e não executa nova captura/OCR.
- Reader 2 não grava `LocalStore`, não chama backend, não controla HUD e não influencia admissão.
- Compara evidência por campo no mesmo frame e estabilidade temporal entre observações.
- Telemetria prioriza os cinco campos patrimoniais: horário, embarque, tempo até embarque, destino e tempo total.

### Diagnóstico
- Adiciona `offer_admission_030`.
- Adiciona `reader2_shadow_030`.
- `offer_admission_029` permanece para compatibilidade e declara `runtime_delegated_to_030=true`.

### Contrato de desenvolvimento
- Adiciona `README-PLANO-ACAO-BASE-0.30.md` com a sequência canônica até o plano mestre completo.
- Adiciona `QA-0.30.0-FIELD.md` com teste de campo obrigatório antes de avançar de etapa.
- Architecture Guard bloqueia segundo OCR/persistência no shadow e acoplamento do Core Reader ao Histórico.

### Versionamento
- `versionCode 71`
- `versionName 0.30.0-field`

## 0.29.0 Field — 21/09/2026

### M1 Reliability
- Gap semântico deixa de reiniciar o ML Kit. `resetOcrPipeline()` permanece reservado a stall/no-progress técnico e recuperação manual.
- `ShadowOfferRecovery027033` deixa de executar segundo `TextRecognizer`; pedidos antigos são suprimidos e contabilizados.
- Mantém um único OCR pesado no M1 durante a jornada.

### Admissão oficial
- Adiciona `OfferAdmissionGate029` antes de estabilização/HUD/persistência.
- Leitura normal recente pode bloquear salto decimal x10 da mesma oferta sem "corrigir" o valor por adivinhação.
- Caudas extremas aguardam segunda observação compatível em janela de 7 s.
- `platform=other` por `other-text-fallback` sem pickup+destino não entra na base oficial.
- Estado de correlação é curto, em memória e não persiste OCR/endereço/coordenadas.

### Diagnóstico
- Adiciona `offer_admission_029`.
- `shadow_recovery_027033` passa a declarar `disabled_in_029=true`, `pass_attempts=0` e `suppressed_submissions`.
- README registra a evidência de campo da 0.28 e desloca o runtime Reader 2.0 para depois da validação da 0.29.

### Reader 2.0
- Integration Map e Contract v1 são preservados como documentação.
- Nenhum Reader 2.0 é ativado nesta versão.
- Próxima etapa planejada: 0.30 fundação compilável com flags OFF; 0.31 shadow compartilhando o mesmo OCR espacial do M1.

### Versionamento
- `versionCode 70`
- `versionName 0.29.0-field`

## 0.28.0 Field — 21/09/2026

### Regressões corrigidas
- Restaura **Usar painel compacto** em Configurações → Janela flutuante.
- A compactação volta a ser aplicada pelo `JourneyBubbleController`, sem reativar polishes/watchers visuais legados.
- `Pesquisar região` volta a iniciar recolhida e abrir/recolher por toque.

### Integridade de leitura
- Adiciona `OfferIntegrityGuard028` entre interpretação e HUD/persistência.
- Rejeita geometrias com velocidade média impossível por trecho e inconsistências de soma/cálculo interno.
- O caso de campo `1,0 km` → `11 km` em 4 min fica protegido sem inventar correção de decimal.
- `OfferDispatcher` aplica o gate antes de preview/HUD, estabilização e persistência.

### Diagnóstico e testes
- `ReaderLabCombinedDiagnostic0270361` passa a exportar `offer_integrity_028`.
- Novos testes cobrem a regressão 11 km/4 min, a leitura correta 1 km/4 min, viagem longa legítima, painel compacto, pesquisa colapsável e presença do gate.

### Reader 2.0
- O módulo externo recebido **não é ativado** nesta build.
- README registra plano 0.29 para Integration Map e 0.30 para shadow/compare somente após a baseline M1 da 0.28 ser validada.

### Versionamento
- Sai a nomenclatura RC3.x para builds funcionais de campo.
- Próximas entregas seguem 0.28 → 0.29 → 0.30 → ... com `versionCode` crescente.

## 0.27.0 RC3.7 Consolidation Field — 19/09/2026

### Arquitetura
- `MainActivity` passa a usar `ConsolidatedMainActivity027037` como shell único.
- Remove do runtime a cadeia de polishes visuais acumulados entre 0.26.2 e RC3.6.1.
- Implementações visuais antigas são reduzidas a stubs de compatibilidade sem watchers/tickers.
- `README.md` passa a ser a especificação canônica do estado atual.
- CI ganha Architecture Regression Guard e orçamento de tamanho.

### Agora
- Uma única pesquisa de região.
- Remove “Visualizar Radar”.
- Remove contador “N regiões em destaque”.
- Controle de jornada tem uma única árvore visual estável.
- Pré-jornada unificado para odômetro e abastecimento/recarga.
- Agora usa rosa neon distinto de IA.

### Navegação
- Configurações e Usuário integrados diretamente ao cabeçalho.
- Barra inferior permanece funcional ao sair de Configurações.
- Rotas fixas: Estatísticas · IA · Agora · Histórico · Radar.

### Histórico
- Atualização direta quando oferta oficial é persistida e a rota está visível.
- “Fiz essa corrida” pode ser desfeito.

### M1 / M2
- M1 passa a ser o modo padrão seguro.
- Modo Comparativo: M1 oficial + M2 apenas pela árvore de Accessibility, sem segundo ML Kit concorrente.
- M2 isolado: MediaProjection não inicia; M2 usa Accessibility e pode usar OCR local sem persistir na base oficial.
- Troca de leitor é bloqueada durante jornada.
- Supervisor do M1 não executa em M2 isolado.

### Diagnóstico
- Configurações, quick action e Activity de exportação usam `ReaderLabCombinedDiagnostic0270361`.
- Evita novo JSON sem `reader_lab_0270361` / `m2_health`.

### Preservado
- Sem alteração deliberada em `OfferParser`, `OfferDeduplicator`, fórmulas financeiras ou contrato oficial da base do M1.
