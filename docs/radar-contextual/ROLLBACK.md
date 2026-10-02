# Rollback

1. Desabilitar `assistantEnabled`.
2. Desabilitar `runtimeEnabled`.
3. Desabilitar `uiEnabled`.
4. Voltar navegação Radar para `RadarPanel027035`.

As tabelas novas podem permanecer sem uso; não afetam OCR/Reader.
As migrations são aditivas. Não apagar dados durante rollback de campo.

O endpoint `/radar/ingest` legado deve permanecer funcional durante todo o rollout.
