# Sr. Rotas 0.33.1 Field — Stability Hotfix

Base: main 0.33.0-field / versionCode 77, Action #121 verde.

## Motivo
O diagnóstico de campo da 0.33 mostrou:
- crash pendente no aparelho;
- reinícios/reaberturas de captura;
- 15.327 screenshots visíveis legados;
- limite novo de 180.

Na 0.33.0, após salvar uma nova imagem, `PrivateScreenshotStore` chamava
`pruneVisibleMediaStore()` sincronamente no caminho da captura. Em aparelho com
milhares de arquivos antigos isso podia executar uma poda enorme enquanto OCR e
MediaProjection estavam ativos.

## Mudança P0
- remove a chamada de poda automática do caminho quente;
- mantém dedupe de 60s;
- mantém JPEG 72;
- mantém cache privado máximo 30;
- não apaga automaticamente screenshots visíveis antigos;
- diagnóstico passa a informar:
  - `automatic_visible_prune=false`
  - `legacy_backlog_detected`
  - `retention_mode=dedupe_active_gallery_cleanup_deferred`
  - `stability_hotfix=0.33.1`

A limpeza dos arquivos históricos será feita depois em manutenção explícita,
fora do OCR, e não deve bloquear captura.

## Escopo preservado
Reader 2, M1, admissão, Histórico, Capture Resilience, UI 0.33, Dark Mode,
responsividade e jornada compacta não foram alterados.
