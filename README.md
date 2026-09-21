# Sr. Rotas — especificação canônica do projeto

> **Este `README.md` é a fonte de verdade do estado atual do Sr. Rotas.**
> Documentos antigos `README-*`, `QA-*`, `TESTE-*`, `FASE-*`, manifests e changelogs de RC anteriores são apenas histórico. Eles não autorizam reintroduzir comportamento substituído. Quando uma regra funcional mudar, este arquivo deve ser atualizado no mesmo commit.

Versão de campo atual: **0.29.0 Field · versionCode 70 · 21/09/2026**  
Base imediata: **0.28.0 Field** · commit `013b539a57b3d3ee4ca31d8dfde91bda64f221b2`.

A partir desta versão, o versionamento de campo deixa a sequência RC3.x. As próximas entregas funcionais seguem **0.28 → 0.29 → 0.30 → ...**, sempre com `versionCode` crescente.

## 1. Objetivo do produto

O Sr. Rotas é um assistente para motoristas cujo diferencial central é construir inteligência temporal e geográfica a partir de ofertas reais observadas em campo.

Uma oferta útil para o core procura preservar, com a maior confiabilidade possível:

1. horário da oferta;
2. local de embarque;
3. tempo até o embarque;
4. local de destino;
5. tempo total da corrida.

Cálculos como R$/km, R$/min, R$/h, custo e lucro são importantes para a decisão imediata, mas não substituem a coleta consistente dos dados acima.

## 2. Arquitetura oficial

Fluxo oficial:

```text
M1 MediaProjection ─┐
                    ├─> interpretação/parser ─> integridade/admissão ─> dedupe ─> persistência
M2 Accessibility ───┘                                               │
                                                           ├─> HUD
                                                           ├─> Histórico
                                                           ├─> Base Pessoal / Sync
                                                           ├─> Agora
                                                           └─> inteligência futura
```

### Donos de responsabilidade

- **Captura M1:** `MediaProjectionOcrService`.
- **Captura M2:** `DriverAccessibilityService`.
- **Laboratório M1/M2:** `ReaderLab027036` + `ReaderLabTelemetry0270361`.
- **Interpretação:** `OfferParser` / `DriverPlatformOfferRouter` / contexto existente.
- **Gate de integridade física pré-HUD/persistência:** `OfferIntegrityGuard028`.
- **Gate de admissão temporal/oficial:** `OfferAdmissionGate029`.
- **Persistência oficial:** `OfferDispatcher` + `LocalStore` + sync existente.
- **Shell principal:** `ConsolidatedMainActivity027037`.
- **Agora:** `NowPanel027037`.
- **Histórico de ofertas:** `RideHistoryPanel027035`.
- **Estatísticas:** `HistoryPanel`.
- **Radar:** `RadarPanel027035`.
- **Configurações:** `SettingsPanel027037`.
- **Cabeçalho:** `SrAppHeader023`.
- **Navegação inferior:** `SrBottomNav023`.
- **Janela flutuante/HUD:** `JourneyBubbleController` é o dono da geometria principal.

## 3. Regra de substituição — obrigatória

**Substituiu um componente = a implementação anterior deixa de executar.**

Não é permitido resolver uma alteração visual criando outro watcher que periodicamente esconda, redimensione ou substitua uma versão anterior do mesmo componente.

A partir da RC3.7:

- `SrRotasApplication` não instala `NowPanelPolish0262`, `FieldValidationPolish0263/0264/0265`, `BubbleRuntimePolish0265`, `ReleasePolish0270`, `Rc35UiPolish027035`, `Rc36ClosingPolish027036` ou `Rc361FieldFixes0270361`;
- os símbolos antigos permanecem somente como **stubs pequenos de compatibilidade**, para que referências históricas compilem sem reativar comportamento;
- novos ajustes devem ser realizados no componente definitivo responsável pela área;
- é proibido adicionar ticker visual (`Handler.postDelayed`) ao Agora para reparar layout;
- reflection não deve ser usada para modificar campos privados de outro componente de UI. Exceções de compatibilidade não podem controlar geometria/tela principal.

## 4. Navegação, UI e responsividade

Navegação inferior fixa:

```text
Estatísticas · IA · Agora · Histórico · Radar
```

Configurações e Usuário ficam no cabeçalho e **não** ocupam novas posições na barra inferior.

Regras atuais:

- Configurações é aberta dentro do shell; a barra inferior continua funcional.
- Telas secundárias fora do shell mantêm ação explícita de voltar.
- Agora possui **uma única** pesquisa de região e ela é **colapsável**; fechada mostra somente a entrada “Pesquisar região”, aberta mostra períodos/base/perfil/campo de busca.
- Agora não possui “Visualizar Radar”. Radar tem rota própria.
- Agora não exibe linha “N regiões em destaque”.
- O controle de jornada possui uma única árvore visual e não é reconstruído a cada oferta.
- O pré-jornada possui um único fluxo para odômetro e abastecimento/recarga.
- Histórico atualiza a partir de ofertas persistidas e permite marcar e **desmarcar** “Fiz essa corrida”.
- O ícone Agora usa rosa vivo/neon, distinto do roxo da IA.
- A IA preserva o Sr. Rotas grande no estado inicial com fade inferior, aplicado uma única vez e sem watcher.

### Contrato responsivo

- Headers e navegação fixa devem adaptar dimensões em telas estreitas e autoajustar textos que não podem quebrar horizontalmente.
- O botão flutuante de Voltar deve reservar espaço no header e nunca cobrir título/logo.
- Conteúdo principal não deve depender de altura fixa; telas com conteúdo variável devem permanecer roláveis.
- Fonte ampliada do Android deve aumentar a legibilidade do conteúdo sem provocar sobreposição em chrome fixo; header/nav podem usar auto-size para preservar os controles.
- `ResponsiveUi027038` concentra as regras compartilhadas de chrome responsivo; `ResponsiveLayoutMath027038` contém a matemática pura coberta por testes.
- Correções de layout devem ser feitas nesses componentes compartilhados ou no dono definitivo da tela, nunca por watcher visual.

### Onboarding de instalação limpa

- Para iniciar uma jornada, `onboardingCompleted` e `consentAccepted` precisam estar verdadeiros.
- A etapa final persiste explicitamente o aceite dos Termos/Política antes de marcar o onboarding como concluído.
- Reabrir a etapa final deve refletir um consentimento já persistido.
- Não há limite de quantidade de aparelhos como requisito desta versão.

## 5. Métodos de leitura de campo

### M1 — MediaProjection

É o modo seguro/padrão da 0.29. Continua sendo a fonte oficial validada e persistida.

A 0.28 adicionou um **gate externo de integridade** antes do HUD e da persistência. O gate não tenta “consertar” OCR por adivinhação: leituras com geometria fisicamente impossível ou cálculos internos incoerentes são rejeitadas e contabilizadas no diagnóstico. O caso real que motivou esta proteção foi uma mesma oferta lida como `1,0 km` e `11 km` de busca em poucos milissegundos; `11 km / 4 min` implica velocidade média incompatível com o trecho e não deve contaminar HUD/base.

A 0.29 acrescenta uma segunda barreira, `OfferAdmissionGate029`, antes de estabilização/HUD/persistência:

- conflito temporal compatível com deslocamento decimal x10 não é “corrigido”; a leitura mais extrema é descartada;
- pickup/tempo de cauda extrema aguarda uma segunda observação compatível dentro de uma janela curta de 7 s;
- `other-text-fallback` sem **pickup e destino** não entra na base oficial;
- o gate mantém somente correlação curta em memória e não persiste OCR/endereço/coordenadas.

O diagnóstico 0.28 mostrou que **gap semântico não pode ser tratado como falha do OCR**: houve 9.396 OCRs concluídos, zero falhas técnicas, mas 94 resets de pipeline. Na 0.29, `resetOcrPipeline()` fica reservado a stall/no-progress técnico ou recuperação manual; dificuldade de parser apenas fica registrada como gap semântico.

A releitura `ShadowOfferRecovery027033` também deixa de abrir um segundo ML Kit. Na 0.28 ela fez 1.699 passes extras para somente 11 ofertas recuperadas. Em 0.29 o símbolo é preservado por compatibilidade/telemetria, mas os pedidos de releitura pesada são **suprimidos**.

### Comparativo — M1 + M2 árvore

- M1 permanece oficial.
- M2 observa somente a árvore de Acessibilidade do Uber em shadow mode.
- M2 **não roda um segundo ML Kit OCR concorrente** neste modo.
- M2 não grava ofertas sozinho na Base Pessoal/Coletiva.
- O objetivo é medir se a árvore recupera ofertas/campos que M1 perde sem degradar o M1.

### M2 — Acessibilidade isolada

- MediaProjection não é iniciado pelo shell.
- M2 pode usar árvore + screenshot/OCR local de Accessibility.
- Resultados ficam em diagnóstico/laboratório e não entram na base oficial até homologação.
- A troca de método é bloqueada durante uma jornada; o método deve ser escolhido antes de iniciar.

### Reader 2.0 — módulo externo recebido, ainda não integrado

O pacote `SrRotas_Reader_2.0_Module.zip` e o `SrRotas_Reader_2.0_INTEGRATION_SPEC.md` são uma proposta arquitetural separada do M2 experimental já existente no Reader Lab. **Não ativar nem copiar cegamente para o runtime.** Antes de qualquer integração, deve existir um Integration Map mostrando providers reutilizados, adapters, persistência shadow, single-flight de OCR, feature flag/rollback e conflitos com a arquitetura atual. M1 permanece baseline durante essa fase.

O modo padrão após esta consolidação é **M1**. Instalações vindas de RC3.6/RC3.6.1 são migradas uma vez para M1 para evitar ativação simultânea involuntária.

## 6. Contrato do Histórico

Uma oferta exibida como registro consolidado pelo fluxo oficial deve chegar à persistência antes de ser tratada como histórico oficial.

`ACTION_CAPTURE_UPDATED` atualiza diretamente o painel de Histórico quando essa rota estiver visível. Histórico não depende de watcher visual ou de `SettingsPanel` para atualizar.

“Fiz essa corrida” é reversível:

```text
OFERECIDA → COMPLETED → NOT_COMPLETED/estado corrigido
```

A reversão serve para corrigir toque acidental; não deve duplicar a oferta.

## 7. Contrato do Agora

Agora é uma tela consumidora de inteligência. Ela não deve interferir em captura, parser, dedupe ou persistência.

Elementos oficiais:

- controle estável Iniciar/Encerrar jornada;
- “Antes de iniciar” com odômetro e abastecimento/recarga;
- uma pesquisa “Pesquisar região”, **colapsável** por padrão;
- Momento / Hoje / Semana / Pesquisa;
- Base Coletiva / Base Pessoal;
- perfil Todas / Popular / Conforto / Premium;
- cards regionais.

Atualizar estado da jornada deve alterar propriedades dos mesmos Views (`text`, `enabled`, `visibility`) e não trocar uma implementação por outra.

## 8. Diagnóstico único

O exportador oficial é `ReaderLabCombinedDiagnostic0270361`.

Ele deve incluir:

- diagnóstico base de captura/OCR/parser;
- modo Reader Lab;
- contadores M1/M2;
- estado da Acessibilidade;
- `m2_health`;
- watchdog/recovery do M1;
- `offer_integrity_028`, com contadores de rejeição por geometria/cálculo incoerente e último motivo técnico, sem OCR bruto/endereço/coordenadas;
- `offer_admission_029`, com caudas adiadas/confirmadas, conflito decimal e fallback genérico bloqueado;
- `shadow_recovery_027033`, agora declarando explicitamente `disabled_in_029=true` e quantos pedidos de OCR extra foram suprimidos.

`DiagnosticQuickActions0270`, a tela de Configurações e a Activity de exportação devem usar este mesmo exportador. Não criar um segundo caminho de diagnóstico com conteúdo diferente.

## 9. Regressão e CI

Toda atualização Android deve passar, nesta ordem:

```text
Architecture regression guard
→ unit tests
→ debug APK
→ field release APK
→ APK/asset size guard
→ verificação da assinatura
→ upload dos artifacts
```

O guard/testes devem falhar se:

- um polish visual legado voltar a ser instalado;
- `MainActivity` deixar o shell consolidado;
- Agora voltar a conter “Visualizar Radar”, contador de regiões ou ticker visual;
- M1 deixar de ser o padrão seguro;
- Histórico perder a reversão de corrida realizada;
- o diagnóstico deixar de usar o exportador combinado;
- a conclusão do onboarding deixar de persistir o aceite dos Termos/Política;
- o header deixar de reservar o botão Voltar;
- header/nav deixarem de aplicar as regras responsivas compartilhadas;
- telas canônicas deixarem de oferecer rolagem vertical quando o conteúdo puder exceder a altura;
- os stubs legados voltarem a crescer e acumular lógica;
- a opção **Usar painel compacto** desaparecer ou deixar de alimentar `JourneyUiPreferences.compactPanel()`;
- a pesquisa regional deixar de ser colapsável;
- o `OfferDispatcher` deixar de aplicar `OfferIntegrityGuard028` antes de HUD/persistência;
- o diagnóstico combinado deixar de incluir `offer_integrity_028`;
- gap semântico voltar a chamar `resetOcrPipeline("watchdog_semantic_gap")`;
- `ShadowOfferRecovery027033` voltar a instanciar/rodar `TextRecognition`;
- `OfferDispatcher` deixar de aplicar `OfferAdmissionGate029` antes do pipeline oficial;
- o diagnóstico deixar de incluir `offer_admission_029`.

## 10. Orçamento de tamanho

A baseline bruta confirmada do APK de campo anterior à consolidação é **62.821.516 bytes** (RC3.6.1). O CI aceita crescimento de até **2%** sobre essa baseline sem revisão explícita.

A soma dos seis assets gráficos `drawable-nodpi` monitorados permanece limitada a **12.000.000 bytes**.

Esses limites são tetos de segurança, não objetivos. Após a consolidação estar estável em campo, as imagens devem ser otimizadas e a baseline pode ser reduzida.

## 11. Regras para qualquer próxima alteração

Antes de editar:

1. ler este `README.md`;
2. identificar o componente dono da responsabilidade;
3. alterar o componente definitivo, não criar um patch paralelo;
4. adicionar/atualizar teste quando a mudança representar um comportamento que não pode regredir;
5. atualizar este README somente quando a especificação atual mudar;
6. registrar a mudança no `CHANGELOG.md`;
7. não alterar parser/OCR/dedupe/fórmulas em correções puramente visuais;
8. não promover M2 nem Reader 2.0 à base oficial sem evidência de campo;
9. uma correção de leitura deve preferir **rejeitar uma amostra ambígua** a inventar/corrigir decimal por heurística sem evidência;
10. a cada nova entrega funcional de campo, avançar a versão 0.28 → 0.29 → 0.30... e manter `versionCode` crescente.

## 12. Critério para candidato 1.0.0

O APK de campo só vira candidato 1.0.0 depois de validação real sem P0/P1, especialmente:

- sem travamento que exija reiniciar o aplicativo;
- sem perda silenciosa recorrente de leitura;
- Histórico acompanhando as ofertas oficiais persistidas;
- UI sem flick causado por implementações concorrentes;
- UI principal utilizável em telas estreitas e com fonte ampliada sem sobreposição de chrome;
- instalação limpa conclui onboarding e permite iniciar jornada;
- M1 estável ou decisão objetiva sobre M2;
- cinco campos core com qualidade suficiente para alimentar inteligência;
- CI completo verde e assinatura estável.

Itens exclusivamente de publicação (AAB final, Play Integrity, Data Safety/Accessibility, revisão final de políticas e Play Console) são fechados depois que a build de campo for aprovada.


## 13. Plano de ação e backlog vivo

Esta seção é o checklist oficial para não perder o contexto entre versões. Um item só sai daqui quando estiver implementado **e validado em campo** ou quando uma decisão explícita o cancelar.

### 13.1 — 0.28.0 Field — diagnóstico recebido

Objetivo original: recuperar regressões de UI e adicionar a primeira barreira de integridade.

Implementado:
- [x] restaurar a opção **Usar painel compacto**;
- [x] mover a compactação para o `JourneyBubbleController`, sem reativar watcher/polish antigo;
- [x] restaurar **Pesquisar região** colapsável;
- [x] adicionar `OfferIntegrityGuard028` antes de preview/HUD/persistência;
- [x] adicionar `offer_integrity_028` ao diagnóstico;
- [x] testes/CI/assinatura estável.

Evidência de campo recebida:
- [x] build 0.28.0-field / versionCode 69 identificada no diagnóstico;
- [x] `card_size=compact` persistido;
- [x] 9.396 OCRs concluídos e 0 falhas técnicas de OCR;
- [x] 94 resets de OCR — carga excessiva não explicada por falha técnica;
- [x] `ShadowOfferRecovery027033`: 1.699 passes extras / 11 ofertas recuperadas;
- [x] `OfferIntegrityGuard028`: bloqueou leituras fisicamente impossíveis;
- [x] ainda houve cauda atípica persistida (`11 km / 65 min`) porque velocidade sozinha não basta;
- [x] houve `other-text-fallback` sem rota entrando como oferta oficial;
- [x] decisão: **não ativar Reader 2.0 antes de fortalecer o baseline M1**.

Ainda não considerar a 0.28 como baseline final para benchmark.

### 13.2 — 0.29.0 Field — M1 Reliability (esta entrega)

Objetivo: transformar a evidência do diagnóstico 0.28 em correções pequenas, reversíveis e mensuráveis, sem alterar `OfferParser`, fórmulas ou schema oficial.

Implementado:
- [x] gap semântico **não reinicia mais o ML Kit**;
- [x] resets automáticos continuam existindo para stall/no-progress técnico;
- [x] recuperação manual continua disponível;
- [x] `ShadowOfferRecovery027033` deixa de executar segundo ML Kit e vira coletor de supressão compatível;
- [x] `OfferAdmissionGate029` roda antes do CardStabilizer/HUD/persistência;
- [x] observação normal recente protege contra salto decimal x10 da mesma oferta;
- [x] cauda extrema exige segunda observação compatível em até 7 s;
- [x] fallback genérico `platform=other` sem pickup+destino não contamina a base oficial;
- [x] novo diagnóstico `offer_admission_029`;
- [x] novos testes e Architecture Guard para essas regras;
- [x] versão 0.29.0-field / versionCode 70.

Validação de campo obrigatória:
- [ ] jornada de pelo menos 2 h com M1;
- [ ] confirmar queda forte de `watchdog_ocr_resets` em relação aos 94 da 0.28;
- [ ] confirmar `shadow_recovery_027033.pass_attempts=0`;
- [ ] observar `suppressed_submissions` sem piora perceptível de recall;
- [ ] conferir `offer_admission_029.deferred_tail` versus `accepted_confirmed_tail`;
- [ ] conferir `rejected_decimal_conflict`;
- [ ] confirmar que `rejected_generic_without_route` remove sujeira sem perder ofertas legítimas;
- [ ] quando houver leitura errada, usar **Registrar falha** imediatamente;
- [ ] conferir os cinco campos core: horário, pickup, tempo até pickup, destino e tempo total;
- [ ] nenhum P0/P1 antes de iniciar runtime Reader 2.0.

### 13.3 — Auditoria Reader 2.0 — concluída, sem runtime

O módulo externo recebido foi auditado contra o repositório real.

Concluído:
- [x] mapear providers/ports para componentes já existentes;
- [x] decidir reutilizar captura, OCR espacial, parser baseline, validators e telemetria;
- [x] identificar conflitos de `COMPARE/HYBRID`, geometria espacial, accumulator, lifecycle e cancellation;
- [x] definir shadow separado de `local_offers`, Base Pessoal/Coletiva e backend;
- [x] definir feature flags e rollback;
- [x] corrigir o ground truth conceitual para os cinco campos core;
- [x] definir que Accessibility Tree deve bypassar OCR;
- [x] definir que o primeiro shadow compartilhará **a mesma observação OCR espacial do M1**.

Documentos produzidos fora do runtime:
- `READER-2-INTEGRATION-MAP-0.29.md`;
- `READER-2-CONTRACT-V1-0.29.md`.

Esses documentos orientam as próximas versões, mas não autorizam ativação antes da validação da 0.29 Field.

### 13.4 — 0.30.0: fundação Reader 2.0 compilável, desligada

Só iniciar depois de diagnóstico 0.29 sem P0/P1.

- [ ] contratos Reader 2.0 corrigidos entram no build;
- [ ] preservar geometria espacial em `OcrDocument`;
- [ ] separar observação estruturada (Accessibility Tree) de frame que precisa OCR;
- [ ] corrigir single-flight com generation/cancellation;
- [ ] accumulator só aceita correlação segura por target/observation;
- [ ] adapters para componentes existentes — sem recriar parser/OCR/repository;
- [ ] feature flags entram **OFF**;
- [ ] nenhum Reader 2.0 executa durante jornada de campo nessa fase;
- [ ] rollback sem migration destrutiva.

### 13.5 — 0.31.0: Reader 2.0 shadow com OCR M1 compartilhado

Primeiro teste runtime Reader 2.0:

- [ ] M1 continua a única fonte oficial;
- [ ] uma captura MediaProjection;
- [ ] uma chamada ML Kit;
- [ ] a mesma saída espacial alimenta M1 e Reader 2.0 shadow;
- [ ] shadow grava somente store/telemetria própria;
- [ ] nenhum `BackendClient.sendOffer` pelo shadow;
- [ ] comparar **correção**, não apenas completude;
- [ ] por campo: presença, ground truth quando disponível, estabilidade e divergência;
- [ ] medir latência, CPU/memória e impacto no M1.

### 13.6 — 0.32.0: Accessibility como segunda observação

Somente se 0.31 não degradar o M1.

- [ ] árvore Accessibility entra como observação estruturada;
- [ ] árvore não passa por OCR;
- [ ] COMPARE não executa segundo ML Kit;
- [ ] manter Accessibility restrita ao Uber e com disclosure;
- [ ] medir ganho real de campos recuperados;
- [ ] sem persistência oficial Reader 2.0/híbrida.

### 13.7 — 0.33+ decisão de accumulator/hybrid/promoção

- [ ] accumulator campo-a-campo somente após corpus/fixtures e identidade segura;
- [ ] HYBRID somente com ganho mensurável;
- [ ] M1 pode continuar oficial indefinidamente se for melhor;
- [ ] promoção exige rollback, diagnóstico comparável e jornada longa;
- [ ] evento oficial somente após commit idempotente;
- [ ] enriquecimento posterior deve ser evento separado (`OfferEnriched`).

### 13.8 — Backlog de produto após estabilizar coleta

- [ ] **99:** melhorar leitura somente depois da baseline Uber/M1 ficar estável;
- [ ] **Radar:** mapa embutido continua planejado, mas não bloqueia leitor/coleta;
- [ ] **Histórico/V7:** preservar batches/arquivos/traceabilidade; staging versionado → validação → dedupe → qualidade → derivados → ativação;
- [ ] **qualidade da base:** medir consistência numérica e estabilidade entre frames além de completude;
- [ ] **diagnóstico:** relacionar falha manual à oferta/frame candidato sem conteúdo sensível desnecessário;
- [ ] **resolução/tela dividida:** continuar medindo geometria OCR por largura/altura/orientação e corrigir somente com evidência;
- [ ] **tamanho do APK:** depois da estabilidade, otimizar assets e reduzir baseline;
- [ ] **Play Store:** AAB, Accessibility, Data Safety, Play Integrity/políticas depois da decisão do leitor;
- [ ] **1.0.0:** somente após jornada longa sem P0/P1, core confiável, UI estável, CI verde e assinatura estável.

### 13.9 — Regra de prioridade

Quando houver conflito entre “mostrar mais cálculos” e “preservar dados confiáveis”, a prioridade é a coleta correta. O Sr. Rotas deve saber **quando, onde, quanto tempo até o embarque, para onde e quanto tempo total** antes de extrair inteligência mais sofisticada.

Também vale a regra operacional: **um campo preenchido não significa um campo correto**. Benchmark e diagnóstico devem medir consistência/correção, não apenas completude.

