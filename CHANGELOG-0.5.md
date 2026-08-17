# Sr. Rotas 0.5 Alpha

Foco: robustez do Offer Engine após a primeira jornada real completa da 0.4.

## HUD

- fonte padrão migrada de 13 para 16;
- ajuste disponível de 14 a 24;
- removidas bordas internas de 3dp das métricas;
- indicador por símbolo + cor;
- R$/min passa à primeira posição padrão.

## Offer Engine

- `UberScreenGate` bloqueia a própria interface do Sr. Rotas antes do parser;
- `FrameChangeDetector` evita OCR em frames praticamente idênticos;
- `BRUberLineSanitizer` trata correções OCR seguras, incluindo `$ 17,99` e `Imin`;
- `BRUberRadarParser` separa cards do Radar por limites espaciais entre preços principais;
- `OfferValidator` descarta combinações implausíveis de tempo/distância;
- `OfferDeduplicator` mantém múltiplas fingerprints por 60 s e ignora mudança apenas de categoria/tipo;
- chave idempotente do backend passa a usar janela de ocorrência de 2 minutos, evitando bloquear para sempre uma oferta idêntica futura;
- Accessibility não compete com MediaProjection durante jornada ativa.

## CI

- testes unitários Android passam a ser executados antes do APK;
- novos casos cobrem auto-OCR, dedupe alternado, OCR com `$` sem `R`, e os padrões anômalos observados na jornada 0.4.
