# Sr. Rotas — Checklist restante para 1.0.0

Atualizado em 28/09/2026 após 0.33.7 + hardening comercial + P5-04.

## Concluído
- ✅ M1/captura/HUD/Radar/jornada/histórico baseline.
- ✅ Assistente Ativo, VER/IGNORAR, odômetro, energia e recovery.
- ✅ Device Identity HMAC e limite server-side 2.
- ✅ antiabuso do trial por aparelho.
- ✅ hardening Supabase e FKs críticas.
- ✅ trial_grant 5 temporários separado do welcome pago 20.
- ✅ confirmação/renovação/idempotência/divergência do billing testadas internamente.
- ✅ OneSignal com entregas reais.
- ✅ Web handoff uso único.
- ✅ cascata de exclusão DB.
- ✅ Admin Diagnósticos + triagem/auditoria.
- ✅ cron de fallback para pagamentos + purge de HMAC expirado.
- ✅ V7 canônica pronta; não reprocessar.

## Ainda falta antes de 1.0
- ⛔ confirmar Leaked Password Protection do Supabase Auth ligada.
- ⛔ executar um Pix real de R$ 9,90 e confirmar Banco Inter → assinatura/entitlements/saldo.
- ⛔ criar chave MCP real, consultar ferramentas e revogar.
- 🟡 offline → online e filas voltarem a zero, incluindo exposures.
- 🟡 soak prolongado de bateria/CPU/memória/temperatura.
- ⛔ triagem/limpeza do pending crash legado da 0.27.0.
- 🟡 estratégia segura para backlog legado de screenshots.
- ⛔ Play Integrity observe/soft ou adiamento formal.
- 🟡 revisão final de termos/privacidade/suporte após este deploy.
- ⛔ Data Safety e declarações Play.
- ⛔ assetlinks.json com SHA-256 da assinatura final.
- ⛔ AAB release assinado.
- ⛔ screenshots/listagem/classificação da Play.
- ⛔ RC instalada por cima da build de campo sem perda local.
- ⛔ roteiro crítico final + zero P0/P1 + CI/Vercel verdes.

Reader2 Primary NÃO é gate de 1.0.
