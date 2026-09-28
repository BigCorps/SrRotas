# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-09-28.4
DATA_CANÔNICA: 28/09/2026
CURRENT_HEAD_STAGE: 0.33.8-field / versionCode 85 — Sync Recovery + Crash Flush
CURRENT_FIELD_TEST: 0.33.7-field / versionCode 84 — VALIDADA EM DOIS APARELHOS
BASE_HOMOLOGADA: 0.33.6-field / versionCode 83 — HOMOLOGADA
STATUS: RETA FINAL 1.0
README_CANONICO: README-CONTINUIDADE.md

## 0. Filosofia
ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR.
Integridade > estabilidade > contrato > compatibilidade > UX > novas funções.

## 1. Android
- 0.33.6/vc83 permanece a base formalmente homologada.
- 0.33.7/vc84 validou Device Identity/Access Resolver em Samsung SM-X626B/Android 16 e SM-N986B/Android 13.
- 0.33.8/vc85 corrige uma fila real de 6 exposures legadas inválidas e automatiza o envio do crash pendente antigo após Access Resolver bem-sucedido.
- M1 oficial.
- Reader2 shadow.
- Controlled Hybrid OFF.
- Histórico congelado.
- Money/fórmulas não alterados.

## 2. Evidência que justificou 0.33.8
No JSON de campo da 0.33.7:
- ofertas/contextos/eventos/outcomes estavam em zero;
- `pending_exposure_count=6`;
- backend continuava recebendo exposures novas válidas.

Na Vercel foram observados exatamente seis POST `/api/v1/journeys` com HTTP 400 repetidos em ciclos sucessivos, enquanto exposições novas eram salvas com 2xx.
Conclusão: seis registros locais legados com payload permanentemente incompatível eram reenviados indefinidamente.

## 3. Correção 0.33.8
`ExposureQueueRepair0338`:
- usa as mesmas condições determinísticas do backend para `invalid_exposure_fields` e `invalid_exposure_window`;
- move somente esses registros para `sync_state=2`;
- não apaga a linha local;
- não inventa dado para enviar ao backend;
- roda no startup e antes do flush canônico, com throttle;
- exporta apenas contadores sanitizados.

Gate de campo:
- os 6 itens antigos deixam a fila pendente;
- aparecem em `quarantined_exposures`;
- exposures novas continuam sincronizando normalmente.

## 4. Crash legado
O crash pendente é de 19/09/2026, versão 0.27.0-rc3.7/vc66.
A 0.33.8 chama `BetaTelemetry.flushPendingCrash` após o Access Resolver resolver a conta com sucesso.
Gate:
- `crash_observability_0332.pending=false`;
- Admin → Diagnósticos recebe o crash, ou o backend aceita a duplicata idempotentemente.

## 5. 1.0-B — Access Resolver
- HMAC Device Identity server-side;
- raw Android ID não é armazenado;
- limite=2;
- teste server-side 1→2→3 aprovado;
- trial antiabuso por aparelho;
- enforcement continua `observe`;
- `require_device_identity=false` até gate comercial/RC.

## 6. 1.0-C — Trial, Pix e créditos
QA interno aprovado:
- trial +5 temporários;
- primeira ativação paga fecha saldo trial e concede +20;
- confirmação duplicada idempotente;
- renovação +30 dias sem welcome novo;
- reserve/consume/refund idempotentes;
- valor/txid divergente → manual_review.

Falta pagamento Banco Inter real de R$ 9,90.

## 7. Admin / Web
P5-04 está em produção:
- Control Center;
- `/admin/diagnosticos`;
- triagem e auditoria;
- cron de billing;
- privacidade/exclusão atualizadas.

## 8. Integrações
- OneSignal com entregas reais observadas;
- Web handoff uso único observado;
- MCP estruturalmente read-only; teste externo real ainda pendente;
- exclusão DB validada e identidade OneSignal obrigatória;
- HMAC antiabuso anônimo é expurgado ao fim da retenção.

## 9. V7
Batch canônico `48323962-cabd-497b-890f-8315e0d0753a`.
Não reprocessar.

## 10. Próxima reta após 0.33.8
- validar fila/quarentena + crash/Admin;
- Pix real;
- MCP real;
- offline→online;
- soak;
- Play Integrity;
- Data Safety/declarações;
- AAB;
- RC sem apagar dados;
- zero P0/P1.

## 11. Regra
Módulos independentes podem avançar em paralelo desde que não quebrem contratos congelados, integridade ou gates de homologação.

IMPLEMENTADO ≠ HOMOLOGADO.
HEAD ≠ BUILD EM CAMPO.
CI VERDE ≠ TESTE DE CAMPO.
