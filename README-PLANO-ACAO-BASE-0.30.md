# Sr. Rotas — README base do plano de ação

**Estado:** contrato canônico de continuidade a partir da 0.30.0 Field.  
**Baseline técnica:** `main` @ `c95f4df874843581972d996b10adfcb6367ce858` (0.29.0 Field).  
**Escopo deste documento:** fixar as decisões já aprovadas antes do plano de ação mestre completo.

Este README não substitui o futuro plano mestre detalhado. Ele existe para impedir que, durante as próximas correções, decisões já tomadas sejam esquecidas ou reabertas sem necessidade.

## 1. Objetivo central do Sr. Rotas

O Sr. Rotas deve coletar dados confiáveis sobre cada oferta. Os cinco campos patrimoniais são:

1. horário da oferta;
2. local de embarque;
3. tempo até o embarque;
4. local de destino;
5. tempo total.

Tarifa, distância, R$/km, R$/min, R$/hora, custo e lucro são importantes, mas não substituem a confiabilidade desses cinco campos.

Sem os cinco campos, o Sr. Rotas é apenas um calculador. Com eles, passa a construir base histórica capaz de gerar inteligência real de horário, origem, destino, fluxo e continuidade para o motorista.

## 2. Regra arquitetural canônica

A partir da 0.30:

- o aplicativo deve ser modular;
- cada módulo possui contrato explícito;
- uma correção não pode alterar silenciosamente outro módulo;
- função estabilizada fica congelada até decisão explícita de mudança de contrato;
- qualquer mudança em área congelada deve aparecer como decisão consciente no changelog/QA;
- novas versões devem alterar o menor conjunto possível de arquivos;
- o CI deve bloquear acoplamentos proibidos quando for tecnicamente possível.

## 3. Áreas congeladas nesta etapa

Na 0.30.0 Core Reader, não devem receber intervenção funcional:

- Histórico;
- Radar;
- tela Agora;
- responsividade geral;
- diálogos/identidade visual;
- janela flutuante, exceto se necessário para manter compilação sem mudança de contrato;
- fórmulas financeiras;
- `OfferParser`;
- `OfferDeduplicator`;
- schema oficial de `local_offers`/`ride_offers`;
- envio oficial ao backend.

O **Histórico** é particularmente canônico: está funcionando e deve permanecer preservado.

## 4. Core Reader 0.30

A prioridade imediata é corrigir leitura/admissão antes de qualquer trabalho visual não relacionado.

### 4.1 M1 continua oficial

O M1 permanece responsável pelo fluxo oficial durante a fase de campo:

`captura -> OCR M1 -> interpretação -> integridade -> admissão -> estabilização -> HUD/persistência`

A 0.30 não substitui M1.

### 4.2 Correção de admissão decimal

A regressão da 0.29 mostrou que a identidade temporal da oferta não pode depender de campos que podem estar errados.

Contrato 0.30:

- tarifa, distância e tempo não participam da chave primária de correlação;
- conflitos aproximados de fator x10 são observados em tarifa, km e minutos;
- nenhum valor é "corrigido" por adivinhação;
- a primeira observação conflitante é retida;
- uma mudança conflitante só pode ser promovida após nova observação compatível;
- mudança de rota impede que duas ofertas diferentes sejam tratadas como a mesma;
- fallback genérico sem rota continua fora da base oficial.

`OfferAdmissionGate029` permanece como ponto compatível chamado pelo `OfferDispatcher`, mas delega a decisão runtime para `OfferAdmissionGate030`. Assim o Dispatcher não precisa ser reaberto nesta etapa.

## 5. Reader 2.0 — modo shadow

A 0.30.0 ativa o primeiro shadow funcional do Reader 2.0.

Regras duras:

- M1 continua oficial;
- Reader 2 não grava oferta oficial;
- Reader 2 não chama backend;
- Reader 2 não controla HUD;
- Reader 2 não interfere na admissão;
- Reader 2 não cria segundo `TextRecognizer`;
- Reader 2 recebe a mesma observação espacial já produzida pelo OCR M1 no caminho Uber;
- nenhum OCR bruto, endereço, coordenada ou screenshot é persistido pela telemetria shadow.

O shadow mede duas coisas:

1. comparação da interpretação M1 com uma derivação shadow sobre a mesma evidência espacial;
2. estabilidade temporal de cada campo, incluindo conflitos decimais x10 entre frames.

O diagnóstico oficial deve exportar `reader2_shadow_030` e `offer_admission_030`.

## 6. Base histórica — contrato V7

A base histórica anterior tem qualidade inferior à nova base canônica processada pelo V7 e não precisa ser reconciliada registro por registro.

Contrato aprovado:

- preservar dados coletados diretamente pelos aparelhos reais de teste;
- preservar a identificação dos aparelhos/jornadas reais;
- descartar da base oficial os registros antigos promovidos a partir dos JSONs históricos inferiores;
- substituir essa camada pelo lote canônico V7 atual;
- manter os lotes e payloads originais em `historical_import_batches` / `historical_import_rows` para auditoria e reprocessamento;
- preservar proveniência clara da nova promoção;
- executar a substituição como operação de banco independente do APK Core Reader.

Estado verificado antes desta entrega:

- promoção histórica antiga: 38.756 `ride_offers` com `historical-import/jsonl` / `historical-import-promotion-v1`, vinculados ao dispositivo sintético `Histórico JSONL — Jadiel`;
- lote canônico: `srrotas_database_canonical_v1.jsonl`;
- extractor: `V7.5`;
- recebidos: 36.089;
- fully ready: 33.532;
- parciais: 2.557;
- inválidos: 0;
- duplicados no lote: 0.

A substituição da base não faz parte do ZIP Android 0.30.0.

## 7. Sequência obrigatória de trabalho

### Etapa A — Core Reader

Versões `0.30.x` ficam reservadas ao Core Reader até aprovação de campo.

Objetivos:
- regressão decimal corrigida;
- Reader 2 shadow medido;
- cinco campos patrimoniais monitorados;
- nenhum segundo OCR;
- nenhuma regressão em áreas congeladas.

Se a 0.30.0 falhar, a correção permanece na família 0.30.x. Não avançar para módulos seguintes.

### Etapa B — Base histórica V7

Operação independente no Supabase:
- preservar aparelhos reais;
- retirar promoção histórica antiga;
- promover V7 canônico com proveniência/auditoria;
- validar contagens antes e depois.

### Etapa C — Responsividade e diálogos

Somente após Core Reader aprovado:
- celular com melhor aproveitamento de largura;
- tablet sem margens laterais excessivas;
- política responsiva única;
- diálogos antigos Android Beta substituídos pelo padrão visual canônico.

### Etapa D — Radar, rotas e screenshots

Depois:
- destino claro para lugares marcados no Radar;
- visualização dos lugares salvos;
- revisar Buscar / Destino / Combinado;
- índice oferta -> screenshot;
- ícone visual de fotos empilhadas na janela da oferta;
- preview rápido da imagem correspondente.

### Etapa E — Janela flutuante

Intervenção própria e separada.

A janela flutuante deverá ser tratada como módulo independente para que mudanças nela não atinjam Reader, Histórico, Radar ou persistência.

### Etapa F — plano mestre completo

Ainda falta consolidar o plano de ação mestre com:
- promoção futura do Reader 2;
- critérios de desligamento/substituição do M1;
- uso dos cinco campos na inteligência regional;
- estratégia de qualidade/ground truth;
- módulos posteriores até Play Store/produção;
- testes de regressão permanentes por contrato.

Este item **não está esquecido**. O presente README é a base canônica que deverá alimentar esse plano completo.

## 8. Critério para avançar de etapa

Uma etapa só é considerada fechada quando:

- build/CI verde;
- QA específico executado;
- diagnóstico de campo analisado;
- ausência de P0/P1 relacionados ao módulo;
- funções congeladas continuam funcionando;
- não existe regressão recorrente que precise ser explicada pela próxima etapa.

Nunca empilhar uma nova intervenção sobre um Core ainda não aprovado.

---

## Adendo canônico — 0.30.0 Field2 / Money Roles

O teste real da 0.30.0 revelou uma classe diferente de erro: não apenas deslocamento decimal, mas **seleção do campo monetário errado dentro do mesmo card**.

Caso de regressão usado nesta etapa:

- categoria Electric;
- tarifa principal: `R$ 34,15`;
- valor aproximado por km;
- nota;
- `Verificado`;
- promoção `Turbo Mais`;
- valor promocional: `R$ 6,57`;
- rota abaixo.

A partir do Field2, nem todo `R$` pode iniciar card. O Core Reader passa a classificar papéis monetários antes do isolamento espacial.

### Contrato Field2

- M1 continua oficial;
- `OfferParser` e fórmulas financeiras continuam congelados;
- `MoneyRoleResolver030` classifica tarifa principal, R$/km anunciado, promoção/bônus, valor secundário e desconhecido;
- um segundo valor monetário dentro do mesmo bloco não cria novo card sem boundary real de rota/ação;
- a proteção estrutural existe mesmo se o nome `Turbo Mais` falhar no OCR;
- `Reader2MoneyShadow030` faz uma segunda interpretação monetária sem segundo OCR e sem side effects;
- diagnóstico exporta `reader2_money_shadow_030_field2`.

### Sequência atualizada e obrigatória

1. **0.30.0 Field2 — Core Reader / Money Roles** — etapa atual.
2. **0.30.1 Field — Screenshot Storage Guard** — uma captura útil por oferta, compressão suficiente para auditoria/reprocessamento e retenção controlada. O recorte atual deve ser preservado.
3. **0.30.2 Field — UI / responsividade / jornada** — reduzir margens e usar indicação compacta `OK ✓ — M1/M2/2.0` em Develop Mode.
4. **0.30.3 Field — Radar / rotas / screenshot UX** — locais salvos, Buscar Destino, índice oferta→screenshot e preview exato.
5. **Etapa separada — janela flutuante modular**.
6. **Base histórica V7** — operação independente no Supabase, preservando dados físicos e staging para auditoria.
7. **Plano mestre completo** — continua pendente e obrigatório.

Nenhuma dessas etapas pode ser esquecida ou absorvida silenciosamente por outra versão.

---

## Adendo canônico — 0.31.0 Field / Reader 2 Parallel

Os testes de campo do Field2 alteraram a ordem das próximas etapas sem mudar os contratos centrais.

### Evidência de campo que motivou a 0.31

- Turbo Mais deixou de provocar seleção da tarifa promocional como tarifa principal.
- Reader 2 shadow apresentou mais leituras core completas que o M1 em parte relevante da amostra, mas com divergências que precisam ser comparadas por campo antes de qualquer promoção oficial.
- Cards longos continuam podendo ser rejeitados pelo M1 quando tempo/km aparecem quebrados em linhas separadas ou quando o card cresce com endereço/observações adicionais.
- O Reader 2 anterior dependia de uma `RideOffer` M1 existir para receber o handoff; portanto não podia estudar ofertas totalmente rejeitadas pelo M1.

### Contrato 0.31

- M1 continua oficial.
- Reader 2 recebe a mesma observação espacial OCR **antes** da formação/rejeição M1.
- Reader 2 possui candidate builder próprio para tarifa, busca, corrida e contexto.
- Reader 2 não chama `OfferParser.parse`, `UberOfferDetector.detect` ou `MoneyRoleResolver030` para montar seus candidatos.
- Reader 2 pode contabilizar `reader2_only_candidates` quando encontra estrutura suficiente em um frame no qual o M1 não produz oferta.
- Nenhum candidato Reader 2 vira HUD, Histórico, banco, backend ou admissão nesta versão.
- Nenhum segundo ML Kit OCR é criado.
- OCR bruto, endereço, coordenada e screenshot não são persistidos pela telemetria Reader 2.

### Core permanente

A comparação continua centrada nos dados patrimoniais:

1. horário da oferta;
2. local de embarque;
3. tempo/km até embarque;
4. tempo/km da corrida;
5. local de destino;
6. tempo total derivado.

Tarifa e métricas financeiras continuam importantes, mas o objetivo de longo prazo é inteligência temporal/geográfica confiável.

### Sequência atualizada e obrigatória

A ordem abaixo **substitui a sequência registrada no adendo Field2**, porque os dois novos bug reports trouxeram evidência suficiente para antecipar Reader 2 e resiliência de captura:

1. **0.31.0 Field — Reader 2 Parallel** — etapa atual; candidate builder independente antes do M1, comparação por campo e foco em cards longos.
2. **0.31.1 Field — Capture Resilience** — queda de MediaProjection/service não encerra jornada; usuário retoma captura com novo consentimento Android dentro da mesma jornada lógica.
3. **Screenshot Storage Guard** — uma captura útil por oferta, compressão suficiente para auditoria/reprocessamento e retenção controlada.
4. **UI / Dark Mode / responsividade / jornada** — validar dark mode na build atual, reduzir margens e aplicar indicação compacta `OK ✓ — M1/M2/2.0` em Develop Mode.
5. **Radar / rotas / screenshot UX** — locais salvos, Buscar Destino, índice oferta→screenshot e preview exato.
6. **Janela flutuante modular** — intervenção separada.
7. **Base histórica V7** — operação independente no Supabase, preservando dados físicos e staging para auditoria.
8. **Plano mestre completo / lançamento** — promoção futura do Reader 2, ground truth, critérios de substituição do M1, testes permanentes e preparação Play Store/produção.

### Critério para promover Reader 2

A 0.31.0 ainda não promove Reader 2. Para sair de shadow/parallel será necessário, no mínimo:

- amostra de campo suficiente em mais de um aparelho;
- comparação dos casos `reader2_only` com prints/horários quando possível;
- divergências por campo compreendidas;
- ausência de regressão Turbo Mais;
- estabilidade em cards longos e layouts variados;
- nenhum efeito colateral sobre Histórico, HUD, backend ou admissão;
- decisão explícita de promoção em versão própria.


---

## Adendo canônico — 0.31.1 Field / Capture Resilience

A 0.31.0 passou no CI e o diagnóstico de campo confirmou o Reader 2 paralelo operando sem segundo OCR e sem side effects oficiais. Ele permanece em paralelo: houve candidatos exclusivos do Reader 2, mas ainda sem evidência suficiente para promoção oficial.

### Contrato 0.31.1

- perda de MediaProjection ou destruição do serviço **não encerra a jornada**;
- a mesma `journey_id` permanece válida enquanto o motorista reautoriza a captura;
- não existe tentativa de reutilizar silenciosamente autorização/token antigo;
- nova sessão de MediaProjection exige ação/consentimento explícito do usuário;
- `Agora` e a notificação de captura interrompida oferecem `Retomar captura`;
- o supervisor continua autorizado a recuperar worker/surface/OCR quando a projeção ainda está viva;
- quando a projeção já acabou, o supervisor não dispara recuperação técnica impossível;
- diagnóstico exporta `capture_resilience_0311`;
- Reader 2 paralelo, Histórico, Radar, fórmulas, admissão e backend permanecem congelados.

### Sequência atualizada

1. **0.31.1 Field — Capture Resilience** — etapa atual.
2. **Screenshot Storage Guard** — uma captura útil por oferta, compressão e retenção controlada.
3. **UI / Dark Mode / responsividade / jornada compacta** — incluindo `OK ✓ — M1/M2/2.0`.
4. **Radar / rotas / screenshot UX**.
5. **Janela flutuante modular**.
6. **Base histórica V7**.
7. **Plano mestre completo / lançamento**.

O Reader 2 continua acumulando evidência em paralelo durante estas etapas; sua promoção será uma decisão explícita e separada.


---

## Adendo canônico — 0.32.0 Field / Reader 2 Accumulator

O bug report posterior ao 0.31 confirma evolução positiva do Reader 2, com menos falhas perceptíveis, mas ainda com buracos ocasionais. O último JSON disponível da 0.31 mostrou candidatos `reader2_only` sem fechamento core completo.

A 0.32 prioriza a etapa F6 já prevista na especificação original do Reader 2: um accumulator próprio para observações parciais.

### Contrato 0.32

- Reader 2 continua shadow/parallel;
- M1 continua oficial;
- observações compatíveis podem preencher apenas campos ausentes;
- conflitos não sobrescrevem valores existentes;
- janela curta de 4,5 s e estado apenas em memória;
- candidato `promotion_ready` exige core completo + repetição + ausência de conflito + confiança mínima;
- `promotion_ready` não tem qualquer efeito oficial nesta build;
- diagnóstico exporta `reader2_accumulator_032`;
- motorista não precisa anotar eventos durante a condução: bug report + JSON passam a ser o QA padrão de campo.

### Roadmap persistente

`ROADMAP-CANONICO.md` passa a ser a fonte primária do estado do desenvolvimento no repositório. Ele deve ser atualizado no mesmo patch de qualquer mudança deliberada de contrato, arquitetura ou sequência.

### Próxima decisão Reader 2

Se o accumulator produzir candidatos core-completos estáveis e `promotion_ready` com baixo conflito, a próxima etapa será **Controlled Hybrid**: Reader 2 poderá resgatar ofertas que o M1 não fechou, atrás de feature flag/rollback e passando pelos gates oficiais.

As demais frentes continuam registradas e não esquecidas: Capture Resilience em validação, Screenshot Storage Guard, UI/Dark Mode/responsividade, Radar/rotas/screenshot UX, janela flutuante, V7.5, hardening e lançamento.
