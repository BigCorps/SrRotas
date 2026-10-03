# Sr. Rotas Reader 2.0 — Integration Contract v1 (0.29)

**Documento de contrato. Não é implementação.**

## Princípios

1. M1 continua fonte oficial durante foundation/shadow.
2. Nenhum segundo OCR M1 é permitido.
3. Reader 2.0 shadow nunca grava `local_offers` nem envia backend.
4. Geometry/spatial evidence é parte obrigatória da observação.
5. Accessibility Tree bypassa OCR.
6. Resultado final deve carregar evidência por campo.
7. Accumulator não pode misturar targets/ofertas.
8. Timeout precisa cancelar/invalidar a operação anterior; liberar lock não basta.
9. Preview é não-oficial; consumidores oficiais recebem evento somente após persistência.
10. Rollback não exige migration destrutiva.

## Modelo mínimo corrigido

```text
RawObservation
  observationId
  targetKey
  capturedAtEpochMs
  packageName
  source
  payload:
    - SpatialTextDocument
    - VisualFrameHandle

SpatialTextDocument
  fullText
  width
  height
  tokens[]:
    text
    bounds(IntRect)
    blockId?
    lineId?

OfferCandidate
  candidateId
  targetKey
  capturedAtEpochMs
  platform?
  fields...
  evidenceByField
  confidence

FieldEvidence
  field
  source
  observationIds[]
  confidence
  parserVersion

Reader2ShadowOffer
  candidate consolidado validado
  reader/profile/parser versions
  validation status
  NÃO é RideOffer oficial
```

## Estados de lifecycle

`isInteractive(): Boolean` deve evoluir para algo equivalente a:

```text
ReaderEligibility
  journeyActive
  screenInteractive
  ownUiVisible
  consentAccepted
  mediaProjectionAvailable
  accessibilityAvailable
  selectedMode
  recoveryInProgress
```

O coordinator só executa caminhos permitidos pelo estado.

## Plano de captura

O contrato não deve usar um único provider para `COMPARE`.

```text
ObservationPlan
  officialPath?
  shadowPaths[]
```

Primeira implementação:
- officialPath = M1 atual, fora do Reader 2.0;
- shadowPaths = shared M1 OCR observation;
- Accessibility entra em fase posterior.

## OCR compartilhado

Na primeira fase shadow:
- ML Kit continua dentro do M1 atual;
- Reader 2.0 recebe o documento já reconhecido;
- `SharedOcrEngine` existe como contrato futuro, mas não provoca nova chamada ML Kit.

Quando centralizado:
- uma instância/serviço lógico;
- generation token;
- latest-frame-wins;
- cancel/reset explícito;
- telemetria de tempo e timeout.

## Accumulator

Antes de existir implementação concreta:
- `targetKey` obrigatório;
- window curta configurável;
- reset em target change, journey end, mode change e timeout;
- nunca fundir candidatos apenas porque tarifa/horário parecem próximos;
- toda fusão registra quais observações contribuíram para cada campo;
- conflito não resolvido => rejeitar ou manter preview, nunca inventar valor.

## Validator composto

A implementação host deve preservar a ordem conceitual:

```text
completude estrutural
→ plausibilidade geral
→ integridade física/aritmética 0.28
→ política de promoção shadow
```

Mapeamento inicial:
- `OfferIntegrityGate027033`
- `OfferValidator`
- `OfferIntegrityGuard028`

## Persistência/eventos

Durante shadow:
`ShadowResultStore` separado.

No futuro oficial:
```text
commit idempotente
→ OfferPersisted
→ consumidores
→ enriquecimento assíncrono
→ OfferEnriched
```

`OfferPersisted` e `OfferEnriched` são eventos diferentes.

## Reader Lab ground truth

Os 5 campos oficiais são:

1. `capturedAt/offerTime`
2. `pickupAddress`
3. `pickupMinutes`
4. `destinationAddress`
5. `totalMinutes`

Extras:
- fare;
- pickupDistanceKm;
- tripDistanceKm;
- totalDistanceKm;
- service/platform.

Um sample deve incluir:
- sampleId;
- frame/observação privada de origem;
- ground truth;
- readerVersion;
- profileVersion;
- parserVersion;
- PASS/PARTIAL/FAIL;
- notas.

## Matching de readers

Nunca depender exclusivamente de distância/tempo lidos para decidir se duas leituras pertencem à mesma oferta.

Prioridade:
1. `sampleId` em corpus;
2. target/window + time window;
3. platform;
4. sinais estáveis;
5. campos lidos apenas como evidência auxiliar.

## Telemetria mínima

Por ciclo:
- sessionId;
- observationId;
- source;
- stage;
- stage timestamps/durations;
- outcome;
- reject reason;
- field presence;
- field confidence;
- match state;
- sem raw OCR por padrão.

## Single-flight corrigido

O token precisa conter generation. Ao timeout:
- invalidar generation;
- emitir TIMEOUT;
- solicitar cancel/reset do provider/engine quando suportado;
- impedir callback antigo de publicar resultado.

`recoverIfTimedOut()` sozinho não satisfaz esse requisito.

## Cancellation

`ReaderCoordinator` não deve engolir `CancellationException`.

## Profile

`ReaderProfile` deve crescer apenas conforme evidência, mas precisa poder representar:
- packages;
- triggers/view IDs;
- regiões/tipos de target;
- thresholds de frame-change;
- force refresh;
- campos obrigatórios;
- parser/interpreter version;
- regras semânticas versionadas.

## Hard gates

Até promoção explícita:
- `reader2_official_persistence=false`
- `reader2_hybrid_enabled=false`
- sem migration de `local_offers`
- sem alteração em `BackendClient.sendOffer`
- sem substituir `OfferParser`
- sem remover M1.
