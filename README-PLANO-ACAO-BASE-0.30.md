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
