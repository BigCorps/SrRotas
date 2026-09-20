# Sr. Rotas — especificação canônica do projeto

> **Este `README.md` é a fonte de verdade do estado atual do Sr. Rotas.**
> Documentos antigos `README-*`, `QA-*`, `TESTE-*`, `FASE-*`, manifests e changelogs de RC anteriores são apenas histórico. Eles não autorizam reintroduzir comportamento substituído. Quando uma regra funcional mudar, este arquivo deve ser atualizado no mesmo commit.

Última consolidação: **0.27.0 RC3.7.2 Consolidation Field · versionCode 68 · 20/09/2026**  
Base da consolidação: `fa7d02a47d4cca71e2d3b40149513adc8c482b29`.

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
                    ├─> interpretação/parser ─> integridade/dedupe ─> persistência
M2 Accessibility ───┘                                      │
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
- Agora possui **uma única** pesquisa de região.
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

É o modo seguro/padrão da RC3.7. Continua sendo a fonte oficial validada e persistida.

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
- uma pesquisa “Pesquisar região”;
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
- watchdog/recovery do M1.

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
- os stubs legados voltarem a crescer e acumular lógica.

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
8. não promover M2 à base oficial sem evidência de campo.

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
