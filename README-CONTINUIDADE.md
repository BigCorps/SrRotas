# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-09-28.3`
Roadmap mestre: `ROADMAP-CANONICO.md`
Roadmap de publicação: `ROADMAP-PLAYSTORE-1.0.md`

Base Android homologada: `0.33.6-field / versionCode 83`
HEAD 0.33.7: `0.33.7-field / versionCode 84` — Device Identity/Access Resolver validado em dois aparelhos, ainda com enforcement OBSERVE.
Frente atual: `Pré-1.0 — P5-04 Diagnósticos + Comercial + Privacidade`

## Android
0.33.6 permanece a base formalmente homologada.
0.33.7/vc84 validou Device Identity em Samsung SM-X626B/Android 16 e SM-N986B/Android 13.
Cada conta ficou com um único dispositivo identity-bound.
Limite server-side foi testado: 1º aceito, 2º aceito, 3º rejeitado.
Nenhum Android é alterado neste pacote.

## Access Resolver
Supabase permanece:
- enforcement_mode=observe;
- max_active_devices=2;
- require_device_identity=false.

Este pacote leva ao Git os guards backend preparados anteriormente. Em OBSERVE eles não bloqueiam o fluxo atual.

## Limpeza de legado
Foram revogadas as sessões antigas sem Device Identity nas duas contas já migradas. Registros históricos não foram apagados.

## Comercial 1.0-C
Corrigido em produção:
- trial usa `trial_grant` (+5 temporários);
- primeira ativação paga encerra saldo trial temporário e concede `welcome` +20;
- confirmação duplicada não concede novamente;
- renovação adiciona 30 dias e não repete +20;
- reserve/consume/refund de IA idempotentes;
- amount_mismatch e txid_mismatch → manual_review;
- cobrança antiga expirada foi normalizada.

QA interno descartável foi removido após os testes.

Falta evidência externa final:
- gerar e pagar um Pix real de R$ 9,90 pelo Banco Inter.

## Integrações
- Web handoff: 40/40 tokens consumidos, uso único confirmado.
- OneSignal: 218 entregas `sent` observadas.
- MCP: arquitetura read-only confirmada; ainda falta criar uma chave e executar teste real.
- exclusão: cascata DB validada; OneSignal passa a ser obrigatório antes de concluir.
- HMAC antiabuso fica desvinculado após exclusão e tem expurgo automático ao fim da retenção.

## Admin P5-04
Nova área `/admin/diagnosticos`:
- feedback/crash;
- filtros;
- versão/aparelho/checklist;
- triagem novo/analisando/resolvido/ignorado;
- nota administrativa;
- auditoria de triagem;
- sem OCR bruto, coordenadas, tokens ou Device Identity.

## Próximos gates
- Leaked Password Protection do Supabase Auth, se ainda não habilitado manualmente;
- teste Pix real;
- teste MCP real;
- offline→online + fila zerando;
- soak bateria/CPU/memória/temperatura;
- pending crash legado;
- Play Integrity;
- Data Safety/declarações/AAB/RC.
