# QA de campo — Sr. Rotas 0.32.0 Field · Reader 2 Accumulator

## Regra principal do teste

O motorista **não precisa anotar horários, valores ou falhas enquanto dirige**.

A validação desta etapa será feita com:

1. uso normal do Sr. Rotas durante a jornada;
2. bug report simples depois da jornada;
3. JSON de diagnóstico exportado ao final;
4. screenshots apenas se já existirem naturalmente e puderem ser enviados sem distração.

Segurança e direção vêm antes do QA.

## Instalação

- instalar sobre a versão atual;
- não desinstalar;
- não limpar dados;
- confirmar `0.32.0-field`, versionCode `75`;
- usar normalmente o leitor padrão.

## O que observar apenas de forma geral

Depois da jornada, responder em texto livre:

- as ofertas pareceram ser computadas normalmente?
- houve muitos, poucos ou nenhum buraco perceptível?
- apareceu algum erro claramente grave/repetitivo?
- a jornada/captura permaneceu utilizável?

Não é necessário contar ocorrências.

## JSON obrigatório ao final

Exportar o diagnóstico combinado.

A instância desenvolvedora verificará principalmente:

### `reader2_parallel_031`

- cobertura do candidate builder independente;
- `reader2_only_candidates`;
- divergências M1 × Reader 2;
- cards longos/split geometry.

### `reader2_accumulator_032`

Esperado:

- `frames_seen > 0`;
- `windows_merged > 0` quando a mesma oferta permanecer em múltiplos frames;
- `fields_recovered` mede campos recuperados entre frames;
- `core_completed_by_accumulation` mede ofertas que ficaram completas graças à combinação temporal;
- `promotion_ready_transitions` mede candidatos estáveis em pelo menos duas observações;
- `promotion_effect = false`;
- `second_ocr = false`;
- `official_persistence = false`;
- `backend_effect = false`;
- `hud_effect = false`;
- `admission_influence = false`.

Os números `promotion_ready_*` **não significam que o Reader 2 já esteja oficial**.

### `capture_resilience_0311`

Continuar coletando esta seção. Se a captura tiver caído e sido retomada, o JSON deve registrar o evento; não é necessário o motorista anotar quando aconteceu.

## Regressões que não podem voltar

- Turbo Mais não pode virar tarifa principal;
- M1 não pode deixar de funcionar por causa do shadow;
- Histórico deve continuar normal;
- nenhuma oferta Reader 2 pode aparecer oficialmente no HUD/banco nesta build;
- não deve existir segundo OCR pesado.

## Critério para avançar ao Controlled Hybrid

A análise combinada dos JSONs deve mostrar:

- accumulator recuperando campos sem explosão de conflitos;
- candidatos core-completos estáveis;
- `promotion_ready_disagrees_with_m1` compreendido e baixo o suficiente para teste controlado;
- nenhum P0/P1 novo;
- comportamento satisfatório em mais de um aparelho antes de tornar Reader 2 o caminho principal.

O segundo testador pode chegar depois: seu JSON será incorporado à decisão, mas não é necessário atrasar a construção do accumulator.
