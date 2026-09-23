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
