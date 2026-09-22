# QA — Sr. Rotas 0.30.0 Field · Core Reader

## Objetivo

Validar **somente** o Core Reader 0.30:

- correção da admissão decimal;
- continuidade e estabilidade do M1 oficial;
- Reader 2.0 em shadow usando a mesma evidência espacial do M1;
- qualidade dos cinco campos patrimoniais;
- ausência de regressão em áreas congeladas.

Não é QA de responsividade, Radar, diálogos ou redesign da janela flutuante. Esses módulos serão tratados em etapas separadas.

## 1. Instalação

- Instalar por cima da 0.29.0 Field.
- **Não desinstalar.**
- **Não limpar dados.**
- Confirmar em diagnóstico/build:
  - `versionName = 0.30.0-field`;
  - `versionCode = 71`.
- Manter o leitor oficial em **M1**.
- Não selecionar M2 isolado para este teste.
- Reader 2 shadow é automático e não deve alterar a interface.

## 2. Preparação

Antes de iniciar:

- confirmar que o Histórico abre normalmente;
- confirmar que a jornada pode ser iniciada;
- manter as configurações usuais do aparelho;
- não mudar estratégia/HUD apenas para este teste;
- se screenshots privados já forem usados normalmente, podem permanecer ligados; não são requisito do Core Reader.

## 3. Duração mínima

Executar uma jornada real por **pelo menos 2 horas**.

Meta desejada: observar **40 ou mais ofertas**.  
O bug da 0.29 apareceu após aproximadamente 10–15 ofertas; portanto, encerrar com poucas ofertas não valida esta regressão.

Se a demanda real não permitir 40 ofertas em 2 h, continuar o uso normal até obter uma amostra razoável e registrar a quantidade final.

## 4. Cenários obrigatórios durante a mesma jornada

Sem reiniciar a jornada por um erro isolado:

- receber várias ofertas Uber consecutivas;
- aceitar ao menos uma corrida, se ocorrer naturalmente;
- após concluir/cancelar o fluxo externo, voltar a receber novas ofertas;
- alternar normalmente entre Uber, navegação e Sr. Rotas;
- bloquear e desbloquear a tela pelo menos uma vez;
- abrir/fechar a janela flutuante durante parte do teste;
- retornar ao app Sr. Rotas e abrir o Histórico;
- se Radar da Uber ou múltiplos cards aparecerem naturalmente, observar sem forçar cenário artificial;
- 99 só precisa ser observado se ocorrer naturalmente. O Reader 2 spatial shadow 0.30 está homologado inicialmente no caminho Uber.

## 5. Conferência de cada oferta problemática

O núcleo do teste são estes cinco campos:

1. horário da oferta;
2. local de embarque;
3. tempo até o embarque;
4. local de destino;
5. tempo total.

Também anotar quando houver problema em:

- tarifa;
- km até embarque;
- km da viagem/total;
- R$/km, R$/min ou R$/h.

Importante: se o cálculo financeiro estiver ruim, primeiro conferir se a tarifa/distância/tempo de entrada estão errados. Não concluir automaticamente que a fórmula financeira falhou.

## 6. Regressão decimal — atenção máxima

Observar especialmente casos como:

- `6,80` virar `68`;
- `1,0 km` virar `10/11 km`;
- `4 min` virar `40 min`;
- qualquer salto aproximado x10 entre frames da mesma oferta.

Se um valor errado aparecer no HUD ou for aceito como oferta oficial:

1. usar **Registrar falha** imediatamente;
2. anotar o horário aproximado;
3. continuar a jornada, salvo se o app ficar inutilizável;
4. ao final, exportar o diagnóstico combinado.

Esperado na 0.30:

- a primeira leitura decimal conflitante deve ser retida;
- uma mudança conflitante só pode ser aceita após observação compatível de confirmação;
- Reader 2 shadow deve registrar a divergência sem interferir na decisão M1.

## 7. Histórico — contrato congelado

Durante o teste:

- abrir Histórico antes/durante/depois da jornada;
- confirmar que ofertas oficiais continuam aparecendo;
- confirmar que a ação de corrida realizada/desfazer continua normal.

Qualquer regressão no Histórico é **P1**, porque este módulo não deveria ter sido alterado nesta versão.

## 8. O que NÃO avaliar como falha desta etapa

Registrar para etapas posteriores, mas não misturar com a aprovação do Core Reader:

- margens/responsividade de tablet/celular;
- diálogos com visual antigo;
- destino dos lugares marcados no Radar;
- botão de screenshot por oferta;
- redesign da janela flutuante;
- melhoria dos atalhos Buscar/Destino/Combinado.

Esses itens continuam no plano e não foram esquecidos.

## 9. Diagnóstico obrigatório ao final

Exportar **Diagnóstico de leitura** sem reiniciar/limpar o app antes.

Conferir a presença das seções:

### `offer_integrity_028`
- `rejected_total`;
- motivos de integridade física.

### `offer_admission_029`
Esperado:
- `runtime_delegated_to_030=true`.

Os contadores antigos da 0.29 são históricos/compatibilidade e deixam de ser o critério principal da build.

### `offer_admission_030`
Conferir:
- `accepted_normal`;
- `accepted_confirmed_tail`;
- `accepted_confirmed_decimal_change`;
- `deferred_tail`;
- `rejected_decimal_conflict`;
- `conflict_fare`;
- `conflict_pickup_km`;
- `conflict_trip_km`;
- `conflict_pickup_minutes`;
- `conflict_trip_minutes`;
- `last_conflict_fields`.

Se visualmente ocorreu um erro decimal e todos os contadores de conflito permaneceram zero, registrar como falha do gate.

### `reader2_shadow_030`
Obrigatório para ofertas Uber:
- `enabled=true`;
- `mode=shadow`;
- `source=shared_m1_spatial_ocr`;
- `second_ocr=false`;
- `official_persistence=false`;
- `backend_effect=false`;
- `hud_effect=false`;
- `admission_influence=false`;
- `observations > 0`;
- `spatial_handoff > 0` quando ofertas Uber espaciais foram reconhecidas.

Também observar:
- `m1_core_complete`;
- `shadow_core_complete`;
- `same_frame_disagreements`;
- `same_frame_decimal_disagreements`;
- `temporal_decimal_conflicts`;
- `last_divergent_fields`.

### OCR/M1
Continuar verificando:
- OCR não deve entrar em ciclo de resets;
- nenhuma segunda instância ML Kit deve aparecer por causa do Reader 2;
- jornada não deve parar de ler depois de várias ofertas.

## 10. Critérios de aprovação da 0.30.0

A build pode avançar quando:

- nenhuma regressão decimal recorrente aparece como oferta oficial;
- se houver conflito x10 real, `offer_admission_030` consegue registrá-lo/reter a primeira leitura conflitante;
- Reader 2 shadow produz observações sem segundo OCR;
- Reader 2 não altera HUD/banco/backend;
- M1 continua lendo após 10–15, 20, 30+ ofertas;
- os cinco campos patrimoniais não apresentam perda recorrente;
- Histórico permanece normal;
- nenhum P0/P1.

## 11. O que enviar após o teste

Enviar:

1. diagnóstico combinado exportado no fim da jornada;
2. quantidade aproximada de ofertas vistas;
3. duração da jornada;
4. descrição/print de qualquer oferta incorreta;
5. horário aproximado do erro;
6. dizer explicitamente se o erro apareceu no HUD, apenas no diagnóstico ou nos dois.

Não limpar dados antes de exportar o diagnóstico.
