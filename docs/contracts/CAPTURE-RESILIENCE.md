# Sr. Rotas — Capture Resilience 0.31.1

## Objetivo

Eliminar o workaround de encerrar a jornada quando a MediaProjection for interrompida.

## Contrato

Fluxo esperado:

`jornada ativa -> captura interrompida -> jornada preservada -> Retomar captura -> novo consentimento Android -> mesma jornada volta a receber OCR`

Regras:

- a interrupção da captura não encerra a jornada;
- `journey_id` não muda durante a retomada;
- o app não reutiliza token antigo de MediaProjection;
- uma nova sessão exige autorização explícita do usuário;
- M1 continua oficial;
- Reader 2.0 continua parallel shadow;
- recuperação técnica automática (`ACTION_RECOVER`) só ocorre enquanto a MediaProjection ainda existe;
- perda real da projeção gera estado de recuperação pendente, não loop de restart;
- nenhum dado sensível é gravado pela telemetria de resiliência.

### Fresh projection restart — Field vc95

1. Recovery técnico automático (`ACTION_RECOVER`) usa a projection existente, reseta pipeline/rearma surface/reconstrói worker conforme necessário; nunca solicita consentimento sozinho.
2. Fresh restart nasce de ação explícita do motorista (“Reiniciar captura”, HUD M1, ou retomada equivalente). CaptureRecoveryActivity/Activity principal abre o seletor oficial Android e envia autorização nova com `EXTRA_FORCE_FRESH_PROJECTION=true`. Sessão/VirtualDisplay antigos são liberados antes da criação nova; não reutilizar token silenciosamente. Mesma jornada, histórico e ofertas preservados; apenas resets normais da nova sessão.
3. Callback `onStop` só pode interromper a sessão cuja instância ainda é a projection oficial. Callback antigo após substituição é stale: não liberar sessão nova nem executar stopSelf. Resize/visibility antigos também são ignorados. Perda real da projection atual preserva journey e recovery pendente.
4. Consentimento cancelado não desmonta sessão existente. M2 não oferece restart MediaProjection. Semântica OCR/parser/gates/dedupe não muda.

Counters locais em memória exportados em capture_resilience_0311: technical_recovery_requested/technical_recovery_health_restored, fresh_projection_requested/authorized/replaced/started e stale_projection_callback_ignored; sem tokens/conteúdo sensível. Health restored indica heartbeat oficial saudável, não comprova leitura de oferta; homologação exige captura longa em aparelho.

## Diagnóstico

Seção: `capture_resilience_0311`.

Campos principais:

- `pending_recovery`;
- `current_interruption_ms`;
- `interruptions`;
- `projection_stopped_by_system`;
- `service_destroyed`;
- `resume_requested`;
- `resume_authorized`;
- `resume_success`;
- `resume_cancelled`;
- `resume_failed`;
- `total_interruption_ms`;
- `last_reason`.

## Não entra nesta versão

- Screenshot Storage Guard;
- Dark Mode / responsividade;
- Radar / rotas;
- promoção oficial do Reader 2;
- refatoração da janela flutuante.
