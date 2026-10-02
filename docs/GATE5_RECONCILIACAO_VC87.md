# Gate 5 — reconciliação vc87

Base auditada: `7ec4001c612ba6b3fa68b97f20b820c7bb749a3f`.

## Diagnóstico
O APK vc86 não era antigo: o artefato assinado continha `0.33.9-field` e foi gerado do SHA correto.
A sensação de regressão vinha de correções anteriores que não haviam chegado a todas as superfícies ativas.

## Corrigido
- `OnboardingActivity`: remove contrato antigo de 5 créditos/Alpha.
- `AiPanel023`: remove carteira de créditos da superfície ativa.
- `SrUi023`: Base Coletiva diagonal quando ativa; Base Pessoal borda azul; seleção inequívoca.
- `SrAppHeader023`: logo ~30% menor; seção à direita preservada.
- guard de arquitetura dinâmico.

## Preservado
Não altera Reader, OCR, MediaProjection, HUD, OfferDispatcher, DriverPlatformOfferRouter, Radar, V7, Supabase ou enforcement.
