# QA — Sr. Rotas 0.27.0-RC3.3 — Integridade de Coleta

## A. APK / captura

### A1. Oferta completa Uber
- iniciar jornada;
- confirmar HUD normal;
- conferir as duas pernas;
- nenhuma regressão de verdict/limiares.

### A2. Oferta 99 com pickup + viagem
- confirmar `pickup_minutes`, `pickup_km`, `trip_minutes`, `trip_km`;
- total deve representar soma das duas pernas;
- HUD só aparece quando a geometria está coerente.

### A3. 99 com somente perna de levar lida na primeira tentativa
**Esperado:**
- NÃO exibir classificação financeira baseada só nessa perna;
- iniciar shadow recovery;
- tentar completar a mesma captura em segundo plano;
- se recuperar, despachar a oferta completa;
- se esgotar, registrar falha automática sem contaminar financeiro.

### A4. Uber + 99 simultâneos/tela dividida
- provocar ofertas nas duas plataformas;
- verificar que releituras esquerda/direita conseguem isolar os painéis;
- observar se o leitor continua ativo após colisões sucessivas.

### A5. Oferta desaparece antes de completar OCR
- permitir que o card suma;
- confirmar que o frame privado já capturado continua sendo processado;
- não deve haver acesso à galeria nem screenshot permanente criado pelo recovery.

### A6. Leitor fica semanticamente preso
- após lacuna prolongada, watchdog deve resetar o pipeline OCR sem encerrar a jornada;
- diagnóstico deve conter falha automática `semantic_gap_watchdog` quando aplicável.

### A7. Fila OCR
- observar uso prolongado;
- fila deve trabalhar em `latest-frame-wins`, descartando pendente velho em favor do frame mais recente;
- jornada não deve precisar ser reiniciada por backlog.

## B. Diagnóstico

Exportar diagnóstico e conferir:
- schema `sr-rotas-diagnostic-v7`;
- `shadow_recovery_027033`;
- `failure_reports_0270.schema = sr-failure-reports-v2`;
- `total_report_count`;
- `exported_report_count` igual ao total da jornada retido;
- `manual_report_count` e `automatic_report_count`.

Teste pelo menos 21 toques manuais em uma jornada de QA. Não deve existir mais o corte silencioso em 20.

## C. UI Android

### Agora
- botão Iniciar/Encerrar e status `✓ OK` devem caber em tela estreita;
- erro de captura mostra texto curto `⚠ Captura`.

### Configurações
- permanecer 30–60 s na tela;
- rolar e alternar seções;
- cards não devem ficar mudando de tamanho por global-layout repetitivo.

### Jornadas
- abertura padrão em 90 dias;
- confirmar que jornadas antigas aparecem;
- até 50 itens podem aparecer no card antes do limite do backend.

### Regiões
- não exibir como destino regional: `Área`, `Região`, `Destino`, `Desloque-se até` ou equivalentes.

## D. Admin V7.1

### D1. Migration
Conferir colunas novas em `historical_import_batches` e `historical_import_rows`.

### D2. Uma imagem, duas ofertas
Criar duas linhas com mesmo `source_file_sha256` e `offer_index` 0/1.
**Esperado:** as duas podem ser aceitas; hash da imagem sozinho não é duplicidade.

### D3. Replay da mesma oferta
Reenviar mesmo `record_id`/hash+offer_index.
**Esperado:** duplicate.

### D4. Substituição controlada
Criar lote V7.1 com `supersedes_batch_id` apontando para lote antigo.
**Esperado:**
- novo lote pode coexistir para comparação;
- antigo permanece intacto;
- duplicidade do lote explicitamente substituído não bloqueia o novo lote.

### D5. Qualidade
Confirmar no painel:
- Temporal;
- Rota;
- Financeiro;
- Completo;
- Parciais;
- Duplicados;
- principais `quality_flags`.

### D6. Oferta trip-only
Entrada com tarifa + horário + origem/destino + `trip_*`, mas sem `pickup_*`.
**Esperado:**
- registro real pode continuar útil temporalmente;
- `financial_ready = false`;
- `fully_ready = false`;
- `validation_status = partial` no contrato V7.1.

### D7. Região candidata
`pickup_text = "Área"`, `pickup_region_candidate = "Barra Funda"`.
**Esperado:** temporal pode usar Barra Funda; texto genérico fica sinalizado.

### D8. Preservação
- `staged/ready` não devem oferecer exclusão destrutiva;
- ação disponível é Arquivar;
- Arquivado pode ser Restaurado;
- exclusão fica restrita a receiving/failed.

## E. Gate para reprocessar 40 mil
Só liberar o lote completo quando o piloto tiver:
- associação correta das duas pernas;
- nenhuma mistura entre cards;
- horário confiável;
- origem/destino aceitáveis;
- taxa de `fully_ready` melhor ou, no mínimo, explicável pelas flags;
- amostra manual aprovada de Uber, 99, Radar, Exclusive e telas divididas.
