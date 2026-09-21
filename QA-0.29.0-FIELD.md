# QA — Sr. Rotas 0.29.0 Field

Objetivo: validar a confiabilidade do **M1** antes de qualquer runtime Reader 2.0.

## Instalação

- Instalar por cima da 0.28.0 Field.
- Não desinstalar e não limpar dados.
- Confirmar versão `0.29.0-field` / `versionCode 70`.
- Reader deve permanecer em **M1**.

## Teste mínimo de campo

Rodar uma jornada real por **pelo menos 2 horas**, incluindo:
- ofertas Uber;
- se ocorrer naturalmente, ofertas 99;
- corrida aceita e retorno a novas ofertas;
- tela bloqueada/desbloqueada;
- alternância normal entre app de motorista, navegação e Sr. Rotas;
- janela flutuante compacta ativa em parte do teste.

## O que observar

1. O HUD deve continuar aparecendo para ofertas válidas.
2. Valores não devem saltar decimalmente entre frames (ex.: `1,0` → `11` km).
3. Pickup/tempo muito fora do padrão pode demorar uma segunda observação para aparecer — isso é esperado na 0.29.
4. Oferta genérica `other` sem origem/destino não deve virar registro oficial.
5. Se uma oferta real deixar de aparecer, usar **Registrar falha** imediatamente.
6. Se uma oferta aparecer com valor errado, usar **Registrar falha** imediatamente.
7. Não reiniciar a jornada apenas por erro pontual; deixar o diagnóstico registrar a sequência.

## Diagnóstico esperado

Ao final, exportar o diagnóstico combinado e conferir:

### `offer_integrity_028`
- `rejected_total`
- `pickup_speed_outlier`
- `trip_speed_outlier`
- demais motivos

### `offer_admission_029`
- `accepted_normal`
- `accepted_confirmed_tail`
- `accepted_replaces_conflict`
- `deferred_tail`
- `rejected_decimal_conflict`
- `rejected_generic_without_route`

### `shadow_recovery_027033`
Esperado:
- `disabled_in_029=true`
- `pass_attempts=0`
- `recovered_offers=0`
- `suppressed_submissions` pode crescer

### M1 / OCR
Comparar com a 0.28:
- 0.28: 9.396 OCRs concluídos
- 0.28: média ~564 ms
- 0.28: 94 resets de OCR
- 0.28: 1.699 passes extras do shadow recovery
- 0.28: 11 ofertas recuperadas pelo shadow recovery

Objetivo da 0.29:
- queda forte de resets de OCR;
- zero passes extras do shadow recovery;
- sem queda importante de ofertas válidas;
- menos sujeira `other-text-fallback`;
- nenhum P0/P1.

## Cinco campos patrimoniais

Para cada oferta problemática, conferir:
1. horário;
2. local de embarque;
3. tempo até embarque;
4. local de destino;
5. tempo total.

Cálculos financeiros ajudam, mas não substituem a qualidade desses cinco campos.

## Aprovação para 0.30

A fundação Reader 2.0 só começa depois de:
- diagnóstico 0.29 recebido;
- nenhum P0/P1;
- ausência de perda recorrente de ofertas por causa do gate de cauda;
- resets técnicos em nível aceitável;
- decisão explícita de avançar.
