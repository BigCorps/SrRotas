# Sr. Rotas — Checklist restante para 1.0.0

Atualizado em 28/09/2026 para 0.33.8-field / vc85.

## Concluído
- ✅ M1/captura/HUD/Radar/jornada/histórico baseline.
- ✅ Assistente Ativo exercitado em campo (overlay, VER e IGNORAR).
- ✅ recovery de captura exercitado com sucesso.
- ✅ odômetro/métricas 3/3 e energia 1/1 sincronizados no JSON de campo.
- ✅ Device Identity HMAC e limite server-side 2.
- ✅ antiabuso do trial por aparelho.
- ✅ hardening Supabase e FKs críticas.
- ✅ billing interno: trial 5, first paid 20, renovação, idempotência, divergências.
- ✅ OneSignal com entregas reais.
- ✅ Web handoff uso único.
- ✅ cascata de exclusão DB.
- ✅ Admin Diagnósticos + triagem/auditoria em produção.
- ✅ cron billing + purge HMAC.
- ✅ V7 canônica pronta; não reprocessar.
- ✅ causa da fila de 6 exposures identificada por repetição de HTTP 400 na Vercel.
- ✅ correção conservadora implementada na 0.33.8: quarentena local, sem delete.

## Gate 0.33.8
- ⛔ instalar vc85 por cima da vc84, sem limpar dados.
- ⛔ confirmar `exposure_queue_repair_0338.quarantined_total`.
- ⛔ confirmar que os 6 registros deixam `pending_exposure_count`.
- ⛔ confirmar novas exposures chegando normalmente.
- ⛔ confirmar `crash_observability_0332.pending=false`.
- ⛔ confirmar crash antigo visível em Admin → Diagnósticos ou aceito como duplicata.

## Ainda falta antes de 1.0
- ⛔ confirmar Leaked Password Protection do Supabase Auth ligada.
- ⛔ Pix real de R$ 9,90: Banco Inter → assinatura/entitlements/saldo.
- ⛔ MCP real: criar chave, listar/chamar ferramentas read-only e revogar.
- 🟡 offline → online com todas as filas recuperáveis zerando.
- 🟡 soak prolongado bateria/CPU/memória/temperatura.
- 🟡 estratégia segura para backlog legado de screenshots visíveis.
- ⛔ Play Integrity observe/soft ou adiamento formal.
- 🟡 revisão final de termos/privacidade/suporte.
- ⛔ Data Safety e declarações Play.
- ⛔ assetlinks.json com SHA-256 final.
- ⛔ AAB release assinado.
- ⛔ screenshots/listagem/classificação da Play.
- ⛔ RC instalada sobre build de campo sem perda local.
- ⛔ roteiro crítico final + zero P0/P1 + CI/Vercel verdes.

Reader2 Primary NÃO é gate de 1.0.
