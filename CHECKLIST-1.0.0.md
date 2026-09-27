# Sr. Rotas — Checklist restante para 1.0.0

Atualizado em 26/09/2026 após a homologação da 0.33.6-field.

Legenda: ✅ concluído · 🟡 implementado, falta validação final · ⛔ falta fechar antes da publicação · ℹ️ informativo.

## A. Núcleo Android
- ✅ M1/Offer Engine baseline oficial, captura, HUD/Radar, jornada e histórico estabilizados.
- ✅ Assistente Ativo: overlay, VER e IGNORAR validados.
- ✅ odômetro/métricas e energia sincronizados.
- ✅ recovery/notificação exercitados.
- ✅ Reader2 pode permanecer shadow na 1.0; promoção não é gate.
- 🟡 soak final de RC e zero P0/P1.

## B. Web/Admin
- 🟡 aplicar este ZIP e validar desktop/mobile + claro/escuro.
- ✅ `/app` complemento do Android e `/admin` Control Center separado.
- 🟡 concluir área de diagnósticos/feedback/crashes prevista em 1.0-E.
- 🟡 integrar custos reais Vercel/Supabase ao Admin ou documentar gestão externa no lançamento.
- 🟡 decidir se override de crédito/plano entra na 1.0; se entrar, usar rotina atômica e auditável.

## C. Segurança / hardening
- ⛔ corrigir 5 helpers com `search_path` mutável: `sr_service_profile_v1`, `sr_text_key_v1`, `sr_region_canonical_label_v1`, `sr_region_key_v1`, `sr_region_label_v1`.
- ⛔ habilitar Leaked Password Protection.
- 🟡 revisar/documentar as 37 tabelas com RLS habilitada sem policy como desenho server-only; não criar policies cegamente.
- 🟡 revisar 10 FKs sem índice de cobertura e criar somente índices críticos conforme carga real.
- ℹ️ índices sem uso são INFO e não devem ser removidos cegamente.
- 🟡 revisão final de grants, SECURITY DEFINER, RPC Pix/créditos/admin, rate limits e logs sem secrets.

## D. Access Resolver / dispositivos / antiabuso
- ⛔ auditar/centralizar uma fonte comercial única equivalente a `resolve_driver_access(driver_id, device_id)`.
- ⛔ aplicar enforcement real do limite de 2 dispositivos ativos; hoje a Web exibe 2, mas a rota ainda documenta que a revogação/identity final depende do bloco 1.0-B/C.
- 🟡 validar reinstalação não reinicia trial e novo e-mail no mesmo aparelho não cria trial infinito.
- 🟡 validar estados TRIAL_PENDING, TRIAL_ACTIVE, PAID_ACTIVE, EXPIRED_READ_ONLY e BLOCKED.

## E. Comercial produção
- 🟡 trial começa na primeira oferta válida e dura 7 dias.
- 🟡 validar concessão única dos 5 créditos trial.
- 🟡 Pix real Banco Inter R$ 9,90: cobrar → pagar → confirmar → entitlement.
- 🟡 validar renovação/expiração, txid/idempotência/valor/divergência.
- 🟡 validar 20 créditos apenas na primeira ativação paga.
- 🟡 validar reserve → consume → refund de crédito de IA em falha.

## F. Integrações
- 🟡 Web handoff de uso único em produção.
- 🟡 MCP somente leitura respeitando acesso/trial/pago/expirado.
- 🟡 OneSignal push real + preferências.
- ⛔ Play Integrity em observe/soft ou decisão explícita de adiamento.
- 🟡 exclusão de conta fim a fim em produção.

## G. Operação/estabilidade
- 🟡 offline → online e fila voltar a zero; o JSON final ainda tinha exposições pendentes.
- 🟡 sessão prolongada de bateria/CPU/memória/temperatura.
- ⛔ triagem/limpeza do `pending_crash` legado da 0.27.0.
- 🟡 estratégia segura para backlog legado de screenshots; não apagar galeria em massa sem confirmação.
- ✅ V7 canônica pronta; não reprocessar.

## H. Privacidade e Play
- 🟡 revisão final de `/privacidade`, `/termos`, `/excluir-conta`, `/suporte` e retenção.
- ⛔ Data Safety da Play.
- ⛔ declaração/consentimento do AccessibilityService conforme função lançada.
- ⛔ revisão das declarações MediaProjection, foreground service e localização.
- ⛔ `assetlinks.json` com SHA-256 real da assinatura final.
- ⛔ AAB release assinado.
- ⛔ screenshots, descrição, classificação e dados finais da loja.

## I. Gate RC → 1.0.0
- ⛔ gerar RC com itens acima fechados.
- ⛔ instalar RC por cima da build de campo sem perda local.
- ⛔ repetir roteiro crítico: onboarding/login, captura, HUD/Radar, jornada, sync, Assistente, histórico, Web, Admin, Pix, trial, créditos, MCP, OneSignal e exclusão.
- ⛔ confirmar zero P0/P1, CI verde e Vercel READY.
- ⛔ AAB assinado com a chave definitiva.

Se a RC estiver limpa, o mesmo código recebe `1.0.0`.
