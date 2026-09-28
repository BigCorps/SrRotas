# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-09-28.4`
Roadmap mestre: `ROADMAP-CANONICO.md`
Roadmap de publicação: `ROADMAP-PLAYSTORE-1.0.md`

Base Android homologada: `0.33.6-field / versionCode 83`
Field validada mais recente: `0.33.7-field / versionCode 84`
HEAD 0.33.8: `0.33.8-field / versionCode 85` — Sync Recovery + Crash Flush.
Admin Web P5-04: produção READY.

## Por que existe a 0.33.8
O SM-X626B exportou `pending_exposure_count=6` enquanto as demais filas estavam zeradas.
A Vercel mostrou seis HTTP 400 reaparecendo em cada ciclo de `/api/v1/journeys`, ao mesmo tempo em que exposures novas eram persistidas normalmente.

Isso caracteriza 6 registros legados inválidos presos localmente, e não falha geral de sync.

## O que muda
### Exposure queue
Novo `ExposureQueueRepair0338`:
- só classifica como permanente o que viola o contrato atual:
  - id/journey/cell inválido;
  - cell fora de `g2:x:y`;
  - janela de tempo inválida;
- altera `sync_state` de 0 para 2;
- preserva a linha local;
- não apaga;
- não corrige/inventa payload;
- executa no startup e antes do flush;
- exporta contadores em `exposure_queue_repair_0338`.

### Crash legado
Após Access Resolver bem-sucedido, o app tenta enviar `pending_crash`.
Se o backend aceitar, o pending é removido.
Isso deve levar o crash antigo da 0.27.0 para Admin → Diagnósticos sem exigir que o usuário abra a tela de feedback.

## Não muda
- Reader M1;
- Reader2;
- Controlled Hybrid;
- parser;
- Money;
- HUD;
- histórico;
- regras de corrida;
- Supabase schema;
- Web/Admin;
- V7.

## Gate do tester
Instalar por cima da 0.33.7. Não desinstalar e não limpar dados.
O teste principal é preservar exatamente o estado local que contém os 6 registros antigos.

Esperado:
- `pending_exposure_count` deixa de carregar os 6 presos;
- `exposure_queue_repair_0338.quarantined_total >= 1` e, neste aparelho, expectativa principal = 6;
- `field_validation_019.facts.quarantined_exposures` aumenta;
- exposures novas continuam chegando ao backend;
- `crash_observability_0332.pending=false`;
- Admin `/admin/diagnosticos` passa a poder mostrar o crash legado.

## Access Resolver
Continua em OBSERVE:
- enforcement_mode=observe;
- max_active_devices=2;
- require_device_identity=false.

## Comercial
Lógica interna de trial/Pix/créditos validada.
Ainda falta um Pix real de R$ 9,90.

## MCP
Arquitetura read-only validada por código/resolver.
O QA temporário usado na preparação foi removido integralmente.
Ainda falta teste externo real do protocolo com uma chave criada por conta de teste/real.

## Próximos gates
- validar 0.33.8;
- Pix real;
- MCP real;
- offline→online;
- soak;
- Play Integrity;
- Data Safety/AAB/RC.
