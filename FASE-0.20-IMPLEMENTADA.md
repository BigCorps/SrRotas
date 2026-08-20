# Fase 0.20 — UX Android final + Sync auto-reparável

## Motivo

A 0.19 foi validada em campo, mas revelou uma fila de sincronização que não zerava:

- 31 ofertas;
- 31 contextos;
- 1 evento de jornada;
- 31 outcomes;
- 30 exposições.

Produção mostrou retries HTTP 400 em `/api/v1/offers` e 404 em `/api/v1/journeys`.

Também foi identificado que o botão Perfil → Sincronizar agora chamava apenas a fila antiga de ofertas/contextos.

## Solução

### SyncCoordinator

Novo coordenador único:

1. coleta IDs de jornadas referenciadas;
2. garante cada jornada idempotentemente no backend;
3. envia ofertas;
4. envia contexto pós-geocode;
5. envia eventos;
6. envia outcomes;
7. envia exposições;
8. envia encerramento;
9. marca sync somente em HTTP 2xx.

Erros `invalid_journey` e `journey_not_found` permitem um reparo e uma nova tentativa controlada.

Chamadas simultâneas são coalescidas por `AtomicBoolean`.

### Não há limpeza artificial

Nenhuma função da 0.20 apaga item pendente para fazer o contador chegar a zero.

### UX de sync

A UI passa a usar a fila total:

- Tudo sincronizado;
- Sincronizando N itens;
- N itens aguardando;
- offline com dados preservados.

### Menu flutuante

Refeito conforme referência funcional aprovada, usando a paleta oficial `UiKit`.

### Importação por screenshot

Ocultada da UI normal do motorista.
Infraestrutura preservada para admin/futuro.

### Roadmap

`ROADMAP-PLAYSTORE-1.0.md` foi consolidado e passa a registrar:

`0.19 → 0.20 → 1.0-A → B → C → D → E → F → RC → 1.0.0`

## Sem migration

0.20 não altera schema Supabase.

## Base técnica preservada

Offer Engine continua congelado.
