# Sr. Rotas 0.29 — Reader 2.0 Integration Map

**Status:** análise concluída; nenhum código Reader 2.0 ativado no runtime.  
**Repositório auditado:** `BigCorps/SrRotas`  
**Baseline auditada:** `main` @ `013b539a57b3d3ee4ca31d8dfde91bda64f221b2`  
**Build de referência:** 0.28.0 Field · versionCode 69 · Action #109 verde  
**M1 continua oficial.**

## 1. Conclusão executiva

O pacote `SrRotas_Reader_2.0_Module.zip` é uma **boa base de contratos**, mas ainda não é um segundo leitor funcional pronto para ser conectado ao Sr. Rotas.

Ele contém:
- `ReaderCoordinator`;
- `SingleFlightGate`;
- modelos/ports;
- `UberBrProfile` mínimo;
- contratos de Reader Lab;
- documentação de decisões.

Ele **não contém implementações concretas** de captura, OCR, normalização, interpretação nova, accumulator, validator, repository ou telemetria. Portanto, não há hoje um algoritmo Reader 2.0 completo que possa ser comparado em campo com o M1.

A integração correta é progressiva: primeiro corrigir/fechar os contratos, depois adaptar componentes já existentes, depois criar um shadow reader sem segunda captura/OCR, e somente depois avaliar Accessibility/híbrido.

## 2. Pipeline real da 0.28 hoje

```text
ConsolidatedMainActivity027037
  └─ inicia jornada / pede MediaProjection
       └─ MediaProjectionOcrService
            ├─ ImageReader
            ├─ FrameChangeDetector
            ├─ single-flight atual: ocrBusy + latest-frame-wins
            ├─ ML Kit TextRecognizer
            ├─ OfferSpatialIsolation0221
            ├─ DriverPlatformOfferRouter / UberSpatialParser0221 / OfferParser
            ├─ OfferIntegrityGate027033
            ├─ ShadowOfferRecovery027033 (quando incompleto)
            └─ OfferDispatcher
                 ├─ OfferIntegrityGuard028
                 ├─ preview OverlayController
                 ├─ CardStabilizer
                 ├─ OfferDeduplicator
                 ├─ LocalStore.saveOffer (dedupe_key único)
                 ├─ JourneyCoordinator.onOfferObserved
                 ├─ BackendClient.sendOffer
                 └─ ACTION_CAPTURE_UPDATED
                      ├─ Histórico
                      ├─ Configurações
                      └─ JourneyBubbleController
```

M2 atual:

```text
DriverAccessibilityService
  ├─ árvore de Accessibility
  │    └─ OfferParser + OfferContextExtractor
  └─ M2 isolado: screenshot Accessibility + ML Kit + DriverPlatformOfferRouter

COMPARE atual:
  M1 oficial + M2 somente árvore
  (sem segundo ML Kit concorrente)
```

## 3. Integration Map — legado → Reader 2.0

| Sr. Rotas atual | Porta/conceito Reader 2.0 | Decisão 0.29 | Destino / plano |
|---|---|---|---|
| `ConsolidatedMainActivity027037.startJourney()` | `LifecycleController` / `CapturePolicy` | **Reusar host** | O host continua dono de permissão MediaProjection e início/fim de jornada nas primeiras fases. |
| `MediaProjectionOcrService` | `CaptureProvider(MEDIA_PROJECTION)` | **Não duplicar** | Primeiro expor observações ao shadow; só encapsular como provider após benchmark. |
| `DriverAccessibilityService` | `CaptureProvider(ACCESSIBILITY_*)` | **Reusar com adapter** | Árvore e screenshot continuam no serviço atual até existir coordinator único comprovado. |
| `FrameChangeDetector` | `FrameChangeDetector` port | **Reutilizar** | Adapter fino; não criar segundo detector M1. |
| ML Kit em `MediaProjectionOcrService` | `SharedOcrEngine` | **Reutilizar saída** | Shadow inicial recebe o OCR já produzido pelo M1. Nenhum segundo OCR. |
| ML Kit no M2 isolado | `SharedOcrEngine` | **Centralizar depois** | Só extrair para engine compartilhado quando o shadow M1 estiver estável. |
| `BRUberLineSanitizer` / `DriverOcrNormalizer` | `OcrNoiseNormalizer` | **Adaptar** | Sem duplicar regras. Uber-specific deve migrar para profile/interpreter progressivamente. |
| `DriverPlatformOfferRouter` | `PlatformInterpreter` | **Adapter baseline** | Primeira implementação 2.0 usa parser atual como referência. |
| `UberSpatialParser0221` / `OfferSpatialIsolation0221` | `PlatformInterpreter` / target evidence | **Preservar integralmente** | Reader 2.0 precisa transportar geometria espacial; `List<String>` não basta. |
| `OfferParser` / `UberOfferDetector` | `PlatformInterpreter` | **Reusar como adapter** | Não reescrever regex/regras na fundação. |
| `OfferContextExtractor0221` | evidência dos campos pickup/destino | **Reusar** | Contexto é essencial aos 5 campos core. |
| `CardStabilizer` | `OfferAccumulator` | **Baseline parcial** | Serve como estabilizador de melhor frame; não é accumulator campo-a-campo. |
| `OfferIntegrityGate027033` | `OfferValidator` | **Compor** | Mantém requisito de completude/legs. |
| `OfferValidator` | `OfferValidator` | **Compor** | Mantém plausibilidade geral. |
| `OfferIntegrityGuard028` | `OfferValidator` | **Compor** | Mantém integridade física/aritmética e proteção de decimal. |
| `OfferDeduplicator` | `OfferRepository.commit()` idempotente | **Preservar** | Não remover antes de repository oficial assumir a mesma garantia. |
| índice único `local_offers(dedupe_key)` | `OfferRepository.commit()` | **Preservar** | Segunda barreira de idempotência no armazenamento. |
| `LocalStore.saveOffer` | `OfferRepository` | **Repository oficial futuro** | Reader 2.0 shadow não pode chamar este caminho. |
| `OfferDispatcher` | repository + preview + event sink | **Não substituir em bloco** | É componente amplo; separar responsabilidades apenas em commits posteriores. |
| `OverlayController.show` | `OfferEventSink.onPreview` | **Preview explícito** | Preview nunca significa oferta oficial. |
| `ACTION_CAPTURE_UPDATED` | `OfferPersisted` interim adapter | **Reusar inicialmente** | Já ocorre após persistência no caminho oficial; depois pode virar evento tipado. |
| `RideHistoryPanel027035` | consumidor `OfferPersisted` | **Não tocar no início** | Continua lendo `LocalStore`. |
| `JourneyBubbleController` | consumidor oficial | **Não tocar no início** | Continua lendo ofertas persistidas. |
| `BackendClient.sendOffer` | side effect pós-commit | **Somente oficial** | Shadow Reader 2.0 nunca envia ao backend. |
| `ReaderLab027036` | Reader Lab / comparator | **Evoluir, não duplicar** | Adicionar comparação Reader 2.0 mantendo M1/M2 atuais compreensíveis. |
| `ReaderLabTelemetry0270361` | `ReaderTelemetry` | **Adapter/expansão** | Reusar filosofia local/privada; criar seção shadow própria, não outro sistema solto. |
| `OfferEngineReliability0270` | stage telemetry | **Reusar métricas** | É a baseline operacional do M1. |
| `RadarHudTrace024` / `FailureReportStore0270` | diagnóstico por estágio | **Reusar** | Reader 2.0 deve correlacionar com falhas sem guardar OCR bruto por padrão. |
| watchdog interno M1 + `ReaderRecoverySupervisor027036` | recovery/lifecycle | **Não competir** | Reader 2.0 não recupera M1 nas fases iniciais. Centralização só depois. |
| `PrivateScreenshotStore` | corpus/diagnóstico privado | **Opcional e explícito** | Nunca vira telemetria remota automática. |
| `ReaderLabRules027036` | comparação dos 5 campos | **Reusar como base, corrigir identidade** | Completude atual mede presença; falta correção/ground truth. |

## 4. Conflitos obrigatórios a resolver antes de integrar o módulo

### C1 — `COMPARE` não está implementado de verdade no coordinator
`ReaderCoordinator.runCycle()` escolhe **um** `CaptureProvider`. O enum possui `COMPARE` e `HYBRID`, mas o ciclo não executa/coordena duas fontes.

**Correção de contrato:** substituir `choose(): CaptureProvider?` por um plano de observação ou separar o shadow da fonte oficial.

### C2 — Accessibility Tree não deve passar por OCR
O pipeline do módulo sempre faz `capture -> SharedOcrEngine`. Uma árvore de Accessibility já é texto/estrutura e não deve ser transformada artificialmente em um frame OCR.

**Correção de contrato:** criar `ObservationProvider`/`RawObservation` com caminhos:
- texto/árvore já estruturado;
- frame visual que precisa de OCR.

### C3 — o módulo perde geometria espacial
`OcrObservation.lines` é apenas `List<String>`. O M1 atual depende de bounds/posição para separar cards, associar tarifa, tempo, distância, pickup e destino.

**Bloqueador:** Reader 2.0 não pode degradar a observação para texto plano.

**Correção:** `OcrDocument` com `fullText` + tokens/linhas com `IntRect` + dimensões do frame.

### C4 — `OfferAccumulator` não possui identidade segura
A interface recebe apenas um `OfferCandidate`, mas o candidate não possui `targetKey`, `observationId` nem evidência de correlação. Isso pode misturar duas ofertas diferentes.

**Correção:** toda observação/candidate precisa carregar chave de sessão/target e evidência. Reset obrigatório em mudança de target, timeout, fim de jornada e troca de plataforma.

### C5 — timeout do `SingleFlightGate` não cancela a operação real
`recoverIfTimedOut()` libera o token, mas o OCR/capture anterior pode continuar executando. Um novo ciclo pode começar enquanto o antigo ainda termina.

**Correção:** generation/cancellation token. A solução deve aproveitar o padrão já existente no M1 (`ocrGeneration` + reset do pipeline).

### C6 — ground truth do módulo não representa os 5 campos core
`ExpectedOffer` possui pickup, pickupMinutes, totalMinutes, destination e **fare**, mas não possui o **horário**, que é um dos cinco campos patrimoniais.

**Correção:** ground truth oficial:
1. horário;
2. pickup/origem;
3. tempo até pickup;
4. destino;
5. tempo total.

Fare/distâncias são extras importantes, mas não substituem horário.

### C7 — `NormalizedOffer` não substitui `RideOffer`
O modelo entregue não possui vários campos atualmente necessários: journeyId, serviceType, offerType, passengerRating, contexto geográfico, dedupeKey, custos e metadados da estratégia.

**Decisão:** durante shadow, usar um modelo Reader 2.0 próprio. Só criar conversão para `RideOffer` se/quanto houver promoção.

### C8 — lifecycle é insuficiente
`LifecycleController.isInteractive()` não representa:
- jornada ativa;
- app próprio visível;
- consentimento;
- permissão MediaProjection;
- Accessibility habilitada;
- screen gate;
- modo do Reader;
- recovery em andamento.

**Correção:** contrato de `ReaderEligibility/ReaderLifecycleState`.

### C9 — Profile ainda é placeholder
A proposta original fala em regiões OCR, thresholds, force refresh e regras semânticas. O `ReaderProfile` entregue só tem packageNames, viewIds, requiredFields e parserVersion.

**Correção:** expandir profile somente com parâmetros realmente medidos/necessários.

### C10 — telemetry/timing incompletos
`scanIntervalMs` e `resultCooldownMs` existem, mas não são usados no coordinator. O TIMEOUT não é emitido quando `recoverIfTimedOut()` libera uma operação.

### C11 — coroutine cancellation
O coordinator captura `Throwable`. Em implementação coroutine real isso pode engolir cancelamento.

**Correção:** relançar `CancellationException`.

### C12 — não existe `TargetDetector` concreto/port
O spec cita `WindowScanner / TargetDetector`, mas o código entregue tem apenas `WindowScanner`.

### C13 — contexto pode enriquecer depois do commit
Hoje endereço/texto pode ser persistido e depois geocodificado/enriquecido de forma assíncrona.

**Correção de eventos:** distinguir:
- `OfferPersisted`;
- `OfferEnriched`/`OfferContextUpdated`.

## 5. Contrato recomendado para o primeiro shadow

A primeira comparação Reader 2.0 **não deve capturar a tela novamente**.

```text
M1 continua:
MediaProjection -> FrameChange -> ML Kit -> OcrDocument espacial
                                  |
                                  +---- caminho oficial atual (inalterado)
                                  |
                                  +---- Reader 2.0 shadow
                                        -> normalizer/profile
                                        -> interpreter adapter
                                        -> accumulator 2.0
                                        -> validators
                                        -> ShadowResultStore
                                        -> Reader2 telemetry
```

Vantagens:
- zero segundo OCR;
- zero nova permissão;
- zero nova persistência oficial;
- mesma observação visual para comparação;
- se Reader 2.0 falhar, M1 continua intacto;
- rollback = desligar feature flag.

Depois, e somente depois, a árvore de Accessibility pode entrar como **outra observação** do mesmo engine/accumulator.

## 6. Feature flags e rollback

Flags propostas (todas OFF ao introduzir a fundação):

```text
reader2_contracts_enabled=false
reader2_shadow_enabled=false
reader2_shadow_shared_m1=false
reader2_accessibility_observation=false
reader2_hybrid_enabled=false
reader2_official_persistence=false
```

Regra dura:
`reader2_official_persistence` permanece `false` até decisão explícita posterior.

Rollback da fase shadow:
1. desabilitar `reader2_shadow_enabled`;
2. nenhuma migration reversa;
3. nenhuma alteração em `local_offers`;
4. M1 continua executando o caminho anterior;
5. apagar apenas dados shadow locais se necessário.

## 7. Dados shadow

Reader 2.0 não deve usar:
- `local_offers`;
- Base Pessoal;
- Base Coletiva;
- `BackendClient.sendOffer`.

Criar futuramente armazenamento local separado e limitado, por exemplo `Reader2ShadowStore`, com retenção curta e sem raw OCR por padrão.

Para diagnóstico:
- IDs/fingerprints;
- timestamps;
- source;
- presença e confiança por campo;
- resultado do validator;
- latências;
- match com M1;
- divergências.

Endereços/textos completos só em corpus privado explicitamente coletado para Reader Lab.

## 8. Evidência por campo

Cada campo core precisa declarar de onde veio:

```text
timestamp:
  source=system_capture_time

pickup:
  source=spatial_ocr | accessibility_tree | accumulated
  observation_ids=[...]

pickup_minutes:
  source=...
  confidence=...

destination:
  source=...

total_minutes:
  source=...
```

Um valor final sem evidência não deve ser elegível para promoção.

## 9. Métricas 0.30/0.31

Medir por oferta e por campo:

- recall de oferta visível;
- falso positivo;
- duplicatas;
- 5 campos presentes;
- 5 campos corretos;
- estabilidade do mesmo campo entre frames;
- divergência decimal/×10;
- tempo até primeiro candidate;
- tempo até resultado consolidado;
- OCRs por oferta;
- rejeições por estágio;
- recoveries/resets;
- CPU/memória quando shadow estiver ativo;
- impacto na latência/saúde do M1.

**Completude não é correção.** O diagnóstico anterior teve M1 quase completo, mas já demonstrou campo numérico incorreto em oferta real.

## 10. Identidade de oferta para benchmark

Não usar como chave principal os próprios campos que estão sendo avaliados.

O comparador atual usa tarifa/distâncias/tempos para parear M1/M2. Isso é útil operacionalmente, mas pode classificar uma leitura decimal errada como “outra oferta”.

Para o Reader 2.0:
- usar `sampleId` em corpus controlado;
- em campo, usar janela temporal + plataforma + target/window + sinais não disputados;
- registrar divergências como divergência da mesma oferta sempre que houver evidência suficiente;
- nunca “resolver” divergência alterando decimal automaticamente.

## 11. Sequência de versões revisada

### 0.28 — em campo agora
- baseline M1;
- integridade 0.28;
- regressões UI corrigidas;
- aguardar diagnóstico longo.

### 0.29 — Integration Map (esta fase)
- **concluída como análise/documentação**;
- nenhum runtime Reader 2.0;
- contratos corrigidos antes de código.

### 0.30 — fundação compilável, desligada
- incluir módulo/contracts corrigidos;
- testes unitários do coordinator/single-flight/models;
- adapters host;
- flags OFF;
- nenhum APK de campo com Reader 2.0 ativo.

### 0.31 — shadow usando OCR M1 compartilhado
- uma única captura;
- um único OCR M1;
- Reader 2.0 processa cópia da observação espacial;
- grava somente shadow telemetry/store;
- M1 continua oficial.

### 0.32 — Accessibility como segunda observação
- árvore Accessibility entra no mesmo modelo de observação;
- ainda sem segundo OCR concorrente em COMPARE;
- medir benefício real.

### 0.33+ — accumulator/hybrid/promoção
- somente com corpus, diagnóstico e rollback aprovados;
- nenhuma promessa de substituir M1.

## 12. Arquivos previstos por fase

### 0.29
Somente documentação:
- `READER-2-INTEGRATION-MAP-0.29.md`
- `READER-2-CONTRACT-V1-0.29.md`
- atualização futura do `README.md`

### 0.30
Prováveis:
- `settings.gradle.kts`
- `reader2/build.gradle.kts`
- `reader2/src/main/...` contratos corrigidos
- novos adapters em `android/app/.../reader2host/`
- testes puros Reader 2.0
- `README.md` / `CHANGELOG.md`

**Não tocar ainda:** `OfferParser`, `UberOfferDetector`, `LocalStore` schema, HUD, Histórico, Agora, Radar.

### 0.31
Provável ponto mínimo de integração:
- `MediaProjectionOcrService` apenas para publicar uma observação shadow depois do OCR já concluído;
- adapters/shadow store/telemetry;
- sem mudar caminho oficial.

## 13. Critérios para iniciar código da 0.30

- [x] Integration Map concluído.
- [x] conflitos de contrato identificados.
- [x] adapters/reuso definidos.
- [x] rollback definido.
- [x] sequência de commits definida.
- [ ] diagnóstico final da 0.28 recebido.
- [ ] 0.28 sem P0/P1 de campo.
- [ ] confirmar que o novo `OfferIntegrityGuard028` não está bloqueando ofertas legítimas de forma recorrente.

Até os três últimos itens, **não ativar Reader 2.0 em runtime**.
