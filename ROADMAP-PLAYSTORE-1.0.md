# Sr. Rotas — Roadmap oficial até a Play Store e divulgação

**Data-base:** 19/08/2026  
**Produto:** Sr. Rotas  
**Android principal:** `com.srrotas.app`  
**Stack:** Kotlin Android + Next.js + Supabase + Vercel  
**Empresa:** BigCorps  
**Suporte:** `contato@bigcorps.com.br`  
**Estado-base Android:** `0.13.3-beta`  
**Estado-base Web:** Web-P5.2  
**Base GitHub desta revisão:** `917ef4355e78ffb61628f9bdf1770be01c72a0e8`

---

# 1. Fonte oficial de produto e governança

Este arquivo é a **única fonte oficial de planejamento do Sr. Rotas até a publicação 1.0.0**.

## 1.1. Papéis de produto e validação

- O idealizador do Sr. Rotas é motorista de aplicativo e usuário real de ferramentas concorrentes.
- Requisitos de produto definidos pelo idealizador e aceitos pela BigCorps são tratados como **requisitos do produto**, não como sugestões opcionais.
- Os outros motoristas envolvidos na validação são usuários de campo. Eles não precisam atuar como testadores técnicos nem preencher obrigatoriamente a Central do testador.
- Relatos de bugs e opiniões desses motoristas podem ser recebidos por mensagem/conversa e devem ser cruzados com:
  - logs locais;
  - telemetria;
  - dados estruturados;
  - comportamento reproduzível;
  - código da versão usada.
- A Central do testador permanece como ferramenta opcional de telemetria estruturada, não como requisito para considerar um relato válido.
- Requisitos de Play Store, Android, LGPD, segurança ou impossibilidade técnica podem exigir adaptação de implementação, mas não devem ser ignorados silenciosamente: qualquer conflito deve ser documentado aqui.

## 1.2. Nova regra de governança pré-1.0

A regra anterior de “não adicionar funcionalidades novas após o Engine Freeze” fica **restrita ao Offer Engine numérico**.

Até a 1.0, é permitido adicionar funcionalidades necessárias ao produto desde que:
1. não alterem silenciosamente as fórmulas/thresholds aprovados do Offer Engine v1;
2. sejam implementadas em camadas paralelas quando possível;
3. tenham critério de teste de campo;
4. preservem performance do OCR;
5. respeitem segurança, privacidade e Play Store.

O backlog pós-1.0 continua sendo o destino de ideias não aprovadas como requisito do produto.

---

# 2. Definição oficial do produto

> **O Sr. Rotas analisa a rentabilidade imediata de uma oferta e utiliza um histórico espaço-temporal para estimar estatisticamente a qualidade da continuidade da jornada no destino.**

Nesta fase, a inteligência central é baseada em:

```text
OCR
→ dados estruturados
→ origem/destino
→ geolocalização
→ tempo
→ histórico
→ estatística
→ continuidade
→ recomendação
```

A inteligência estatística central **não depende de LLM/IA generativa**.

Regra:

```text
Banco de dados + geolocalização + estatística = Inteligência Sr. Rotas
```

A IA própria e o MCP continuam complementares e não são requisito para a avaliação estatística de uma oferta.

---

# 3. Decisões arquiteturais e comerciais já fechadas

1. **Um único aplicativo na Play Store:** `com.srrotas.app`.

2. O núcleo crítico permanece nativo/local:
   - MediaProjection;
   - ML Kit OCR;
   - Offer Engine;
   - Context Engine;
   - cálculos;
   - HUD;
   - jornada;
   - botão flutuante;
   - cache/banco local;
   - fila offline;
   - sincronização;
   - permissões;
   - serviços Android.

3. O dashboard Next.js continua como superfície dinâmica:
   - Histórico;
   - gráficos;
   - IA;
   - MCP;
   - Perfil;
   - dispositivos;
   - assinatura;
   - créditos;
   - cobrança;
   - suporte;
   - páginas jurídicas.

4. O dashboard Web pode ser aberto dentro do próprio `com.srrotas.app`, sem publicar um segundo aplicativo.

5. `com.srrotas.web` não será um segundo app da Play Store.

6. Domínio canônico:
   - `https://srrotas.com`;
   - `www` apenas redireciona.

7. Identidade oficial:
   - `docs/brand/sr-rotas-icon-source.png`;
   - Android/launcher: `docs/brand/sr-rotas-icon-app-black.png`;
   - Web/PWA: `docs/brand/sr-rotas-logo-web-transparent.png`;
   - não redesenhar o personagem.

8. A landing é a fonte visual oficial:
   - creme `#F8F4DF`;
   - azul-petróleo `#073746`;
   - teal `#0C8788`;
   - teal destaque `#0E9998`;
   - dourado `#F4CA50`;
   - dourado secundário `#E6B631`;
   - textos `#45656A` / `#60777A`;
   - verde/amarelo/vermelho somente para significado semântico/verdict.

9. Plano inicial:
   - R$ 9,90;
   - validade de 30 dias.

10. Pagamento:
    - Pix;
    - Banco Inter;
    - conta BigCorps.

11. Trial público:
    - 7 dias;
    - começa na primeira oferta válida persistida;
    - 5 créditos temporários de IA;
    - sem pagamento obrigatório para começar.

12. Primeiro pagamento:
    - ativa assinatura;
    - converte trial;
    - concede 20 créditos iniciais de IA uma única vez.

13. OCR, HUD, cálculos, histórico estruturado, estatística, SQL analytics e MCP não gastam créditos de IA.

14. MCP permanece somente leitura.

15. Máximo inicial:
    - 2 aparelhos ativos por conta.

16. A 1.0 exige e-mail confirmado antes do início do trial.

17. Antiabuso:
    - sem IMEI;
    - sem serial;
    - sem MAC;
    - device key apropriada + HMAC server-side;
    - Play Integrity como camada adicional.

18. OneSignal:
    - comunicação;
    - versão;
    - trial;
    - manutenção;
    - pagamento;
    - nunca avaliação de oferta em tempo real.

19. Raw OCR não é enviado ao servidor por padrão.

20. Screenshots não são enviados ao servidor por padrão.

21. Nenhum `service_role`, segredo bancário ou secret administrativo fica no Android/Web.

---

# 4. Offer Engine v1 — congelado

## 4.1. Baseline

Versão oficial:

```text
sr-rotas-v0.5.4
```

Componentes congelados:
- parser numérico;
- filtros de plausibilidade;
- deduplicação;
- Card Stabilizer;
- fórmulas aprovadas;
- frequência/performance validada do pipeline OCR, salvo bug reproduzível.

## 4.2. Regra para a nova inteligência

O novo núcleo espaço-temporal **não deve reabrir o Offer Engine numérico**.

Criar uma camada paralela:

```text
ML Kit OCR
    ├── Offer Engine v1
    │      → valor/km/minutos/métricas/verdict
    │
    └── Context Engine v1
           → origem/destino/contexto espacial
```

O `Context Engine` pode usar os mesmos blocos/linhas espaciais do ML Kit e os mesmos clusters de card do Radar, mas não deve alterar thresholds financeiros apenas para extrair localização.

Falha do Context Engine:
- não invalida uma oferta numericamente válida;
- não bloqueia HUD;
- não reduz frequência do OCR;
- deixa a oferta como `context_pending`/`context_unresolved`.

---

# 5. Descobertas técnicas atuais que orientam a próxima fase

## 5.1. Estrutura atual já favorece o Context Engine

O OCR atual já entrega linhas com `boundingBox` ao `SpatialOfferParser`.

Isso permite:
- associar textos de origem/destino ao mesmo card;
- respeitar clusters separados no Radar;
- evitar cruzar endereço de um card com valor de outro.

## 5.2. Banco atual ainda não possui contexto geográfico

`ride_offers` hoje possui:
- valor;
- km;
- minutos;
- métricas;
- serviço;
- tipo de oferta;
- verdict;
- confiança;
- jornada.

Ainda faltam:
- origem;
- destino;
- latitude/longitude;
- célula/região;
- ETA;
- status de geocoding;
- origem temporal de importação;
- estado “estou fazendo/realizada”.

## 5.3. Histórico local atual precisa evoluir

O banco local atual foi desenhado para histórico operacional e sincronização.

Para a Inteligência Sr. Rotas, criar armazenamento específico para:
- contexto geográfico;
- estados da jornada;
- presença/tempo ocioso por região;
- importações;
- agregados estatísticos;
- perfil de custos.

Não depender apenas da retenção atual de ofertas brutas.

---

# 6. Requisito estatístico essencial: registrar também quando NÃO aparece oferta

Para estimar uma probabilidade como:

```text
“70% de chance histórica de nova oferta”
```

não basta armazenar apenas ofertas recebidas.

Ofertas são somente os **eventos positivos**.

Também precisamos medir a **exposição**:

```text
motorista disponível
+ região
+ horário
+ tempo aguardando
+ recebeu ou não recebeu oferta
```

Sem denominador, o sistema consegue medir:
- volume histórico;
- atividade;
- frequência relativa;

mas não uma probabilidade calibrada.

## 6.1. Nova entidade: exposição regional

Durante jornada `ACTIVE`, quando o motorista não estiver marcado como `DOING_RIDE`, registrar de forma econômica:
- célula/região atual;
- início da permanência;
- fim da permanência;
- duração;
- dia da semana;
- faixa horária;
- próxima oferta recebida;
- tempo até próxima oferta.

Não registrar GPS a cada segundo.

Preferir:
- mudança de célula;
- mudança de estado;
- amostragem espaçada;
- agregação.

## 6.2. Definição de probabilidade v1

O Motor Estatístico deve conseguir responder:

```text
P(nova oferta válida em até H minutos |
  região,
  dia da semana,
  faixa horária)
```

H pode ser calculado para múltiplos horizontes, por exemplo:
- 5 min;
- 10 min;
- 15 min.

A UI pode exibir um único horizonte principal.

Regra:
- amostra insuficiente → `Dados insuficientes`;
- amostra intermediária → Baixa/Média/Alta;
- percentual somente quando houver base mínima confiável;
- o limite mínimo de amostra será calibrado nos testes, não inventado antes dos dados.

---

# 7. 0.13.3 — Estabilidade de campo antes da expansão

**Status de implementação: ✅ concluída em código · build/CI pendente após aplicação do ZIP.**

Esta fase estabiliza a superfície Android sem reabrir o Offer Engine v1. A validação de campo completa continua concentrada na 0.19, mas o build e os testes unitários precisam passar antes de iniciar a 0.14.

## Bugs/revisões confirmadas

### HUD piscando
Causa provável:
- `OverlayController.show()` remove e recria a View;
- o dispatcher pode exibir preview e depois resultado estabilizado.

Correção:
- OCR e renderização desacoplados;
- manter overlay existente;
- atualizar conteúdo somente quando estado semântico mudar;
- comparar fingerprint visual da oferta;
- não reduzir frequência do OCR.

### Arraste em tablet
Migrar bounds para métricas de janela/insets adequados.

Testar:
- celular;
- tablet;
- portrait;
- landscape.

### HUD Normal e Compacto
Reduzir:
- padding;
- gaps;
- margens;
- espaço de cabeçalho.

Priorizar compactação estrutural antes de diminuir fonte.

### Paleta Android
Atualizar `UiKit` e HUD para a paleta oficial da landing.

### Indicador de captura
Não tentar esconder/burlar indicadores obrigatórios do Android.

Adicionar explicação clara no onboarding/ajuda:
- o Android informa quando a tela está sendo compartilhada/capturada;
- a captura é iniciada pelo motorista;
- OCR ocorre localmente;
- screenshot bruto não é enviado por padrão.

### Voz
Manter a voz existente e preparar:
- seleção de métricas faladas;
- opção `Seguir ordem do HUD`;
- default recomendado: ativo.

### Implementado na 0.13.3

- `OverlayController` mantém uma única Window ativa e atualiza somente o conteúdo quando o fingerprint visual muda;
- releitura idêntica renova apenas o timeout, sem `removeView/addView`;
- atualização do HUD é suspensa durante o gesto de arraste para o OCR não interromper o motorista;
- bounds de arraste usam `WindowManager.maximumWindowMetrics` + system bars/display cutout no Android 11+ e fallback seguro nas versões anteriores;
- HUD Compacto reduz largura, padding, rail, cabeçalho e exibe somente as duas primeiras métricas;
- HUD Normal também reduz largura, padding e gaps sem reduzir agressivamente a fonte;
- paleta Kotlin/HUD passa a derivar da landing oficial;
- verde/amarelo/vermelho permanecem somente como semântica de verdict;
- `Lucro` visual passa a ser descrito como `Lucro est.*`/`Lucro est./h`;
- voz passa a permitir ativar/desativar Valor, R$/min, R$/km, R$/h, Distância e Duração;
- `Seguir ordem do HUD` é ativado por padrão e pode ser desligado;
- métricas exclusivas da voz mantêm a posição escolhida pelo motorista;
- destino falado fica explicitamente para a 0.14, quando o Context Engine fornecer esse dado;
- novo `HudPresentationTest` protege fingerprint idempotente e ordenação da voz;
- versão Android passa a `versionCode 20` / `0.13.3-beta`.

O Offer Engine, `OfferParser`, `SpatialOfferParser`, `CardStabilizer`, `OfferDeduplicator`, frequência do OCR e fórmulas financeiras não foram alterados.

### Critério de saída

```text
[ ] HUD sem piscar na mesma oferta
[ ] arraste percorre a área útil em tablet/celular
[ ] Normal menor
[ ] Compacto significativamente menor
[ ] paleta oficial aplicada
[ ] OCR sem regressão
[ ] cálculos sem regressão
```

---

# 8. 0.14 — Context Engine v1: origem, destino e geolocalização

Esta fase passa a ser **núcleo obrigatório pré-1.0**.

## 8.1. Extração local

Para cada oferta válida, tentar extrair:
- origem/retirada;
- destino;
- endereço/label mostrado;
- confiança do contexto.

Usar:
- blocos/linhas espaciais do ML Kit;
- cluster do card;
- proximidade visual;
- sem reabrir o parser financeiro.

## 8.2. Modelo de contexto

Criar conceito `OfferContext` com, no mínimo:

```text
offer_local_id
pickup_label
destination_label

pickup_lat
pickup_lng
destination_lat
destination_lng

pickup_cell
destination_cell

context_confidence
geocode_status
geocode_source

observed_at
estimated_arrival_at

source_type
time_source
```

## 8.3. ETA

Para uma oferta recebida em `observed_at`:

```text
ETA destino =
observed_at
+ pickup_minutes
+ trip_minutes
```

ou `total_minutes` quando consistente.

Não chamar API de rotas apenas para produzir a primeira ETA.

## 8.4. Geocoding

Arquitetura não bloqueante:

```text
texto extraído
↓
cache local
↓
geocoder assíncrono disponível
↓
se não resolver:
fila de enriquecimento
↓
fallback remoto/cacheado
```

Regras:
- geocoding nunca bloqueia HUD;
- falha não invalida oferta;
- resultados têm `source` + `confidence`;
- cachear local normalizado → coordenada para reduzir custo/latência;
- nunca tratar geocoder como fonte de segurança.

## 8.5. Google Maps

Para `Retirada` e `Destino`:
- usar Maps URLs/Intent;
- aceitar endereço textual quando lat/lng ainda não estiver disponível;
- usar coordenada quando houver;
- não depender de forçar modo satélite;
- não classificar risco.

## 8.6. Localização do aparelho para contexto e exposição

Se usada durante jornada:
- pedir permissão de localização com explicação explícita;
- usar somente durante jornada ativa;
- não coletar continuamente fora da jornada;
- declarar tipo de foreground service de localização conforme Android vigente;
- evitar `ACCESS_BACKGROUND_LOCATION` na 1.0 se o fluxo puder iniciar e permanecer em FGS a partir de ação visível do usuário;
- caso o sistema exija outro comportamento em um fabricante/versão, revisar antes do RC.

### Critério de saída

```text
[ ] Exclusive associa retirada/destino corretamente
[ ] Radar não mistura contexto entre cards
[ ] Maps abre retirada/destino
[ ] ETA calculada localmente
[ ] contexto não reduz performance do OCR
[ ] falha geográfica não quebra oferta
```

---

# 9. 0.15 — Jornada operacional, corrida realizada e exposição regional

Esta fase é necessária para produzir dados estatísticos confiáveis.

## 9.1. Estados da jornada

Estados oficiais:

```text
NOT_STARTED
ACTIVE
PAUSED
ENDED
```

Registrar evento/timestamp para:
- iniciar;
- pausar;
- retomar;
- encerrar.

## 9.2. Estado da oferta/corrida

Não usar “Aceitei” como estado principal.

Fluxo:

```text
OFFERED
↓
DOING_RIDE       (“Estou fazendo”)
↓
COMPLETED        (“Realizada”)

ou

NOT_COMPLETED / CANCELLED
```

Permitir correção posterior pelo Histórico:
- `Fiz esta corrida`;
- desmarcar;
- corrigir status.

## 9.3. Por que isso é obrigatório

Esses dados distinguem:
- oferta recebida;
- corrida que o motorista decidiu fazer;
- corrida realmente concluída;
- tempo livre/ocioso.

São necessários para:
- continuidade;
- custo de oportunidade;
- eficiência;
- lucro real aproximado;
- qualidade do destino.

## 9.4. Exposição regional

Quando:

```text
journey = ACTIVE
e
ride_state != DOING_RIDE
```

registrar presença agregada por região/célula.

Quando:
- oferta chega;
- motorista começa corrida;
- pausa;
- retoma;
- encerra;

fechar/reabrir intervalos adequadamente.

## 9.5. Botão flutuante Sr. Rotas

Mascote disponível durante a jornada.

Configurações:
- tamanho;
- opacidade;
- posição persistida.

### Jornada não iniciada
- `Iniciar jornada`.

### Jornada ativa
- última oferta;
- principais métricas;
- classificação;
- Retirada → Maps;
- Destino → Maps;
- `Estou fazendo`;
- Histórico;
- `Pausar jornada`.

### Corrida em andamento
- Destino → Maps;
- `Finalizar corrida`;
- `Cancelar / não realizada`;
- Histórico.

### Jornada pausada
- `Retomar jornada`;
- `Encerrar jornada`.

Manter poucas ações contextuais por estado.

## 9.6. Notificação da oferta

Exibir:
- valor;
- principais métricas;
- Boa/Atenção/Ruim;
- retirada;
- destino.

Ações:
- Retirada;
- Destino;
- Estou fazendo, se tecnicamente adequado à notificação.

### Critério de saída

```text
[ ] estados de jornada persistem corretamente
[ ] “Estou fazendo” separa oferta de corrida
[ ] corrida pode ser corrigida pelo Histórico
[ ] exposição regional é registrada
[ ] botão flutuante não atrapalha Uber
[ ] Maps funciona pela notificação e pelo mascote
```

---

# 10. 0.16 — Importação histórica de screenshots

**Prioridade alta para acelerar os testes do Motor Estatístico.**

## 10.1. Objetivo

Usar screenshots antigos para:
- testar Context Engine;
- alimentar eventos históricos positivos;
- validar geocoding;
- formar atividade regional;
- reduzir cold start de dados.

## 10.2. Fluxo

```text
Selecionar imagens/pasta
↓
fingerprint do arquivo
↓
metadados temporais
↓
OCR local em lote
↓
Offer Engine v1
+
Context Engine v1
↓
normalização
↓
geocoding
↓
deduplicação
↓
revisão de parciais
↓
banco local
↓
sync estruturado opcional
```

## 10.3. Tempo do screenshot

Tentar, em ordem de confiança:
- metadado confiável de captura;
- MediaStore;
- EXIF quando existir;
- padrão de nome do arquivo;
- data de modificação;
- confirmação manual;
- desconhecido.

Registrar:

```text
time_source
time_confidence
```

Nunca fingir que data incerta é precisa.

## 10.4. Deduplicação

Usar duas camadas:

1. `file_sha256`
2. fingerprint semântico da oferta

Fingerprint semântico pode considerar:
- timestamp/bucket;
- valor;
- km;
- minutos;
- origem;
- destino;
- tipo.

## 10.5. Privacidade

- imagem permanece local por padrão;
- upload bruto desligado;
- pode apagar imagem processada do cache interno;
- somente dados estruturados entram no banco remoto quando permitido;
- contribuição coletiva sempre opt-in.

## 10.6. Limitação estatística importante

Screenshots históricos registram principalmente **ofertas que existiram**.

Eles não dizem, sozinhos, quanto tempo o motorista ficou em uma região **sem receber oferta**.

Portanto:
- servem para atividade/volume/contexto;
- ajudam a bootstrapar regiões;
- **não são suficientes sozinhos para calibrar um percentual real de continuidade**.

A probabilidade calibrada depende também dos intervalos de exposição da fase 0.15.

### Critério de saída

```text
[ ] lote grande processado sem travar UI
[ ] duplicata de arquivo não duplica oferta
[ ] data incerta é marcada
[ ] imagem não sobe por padrão
[ ] parciais podem ser revisadas
```

---

# 11. 0.17 — Motor de Inteligência Estatística v1

## 11.1. Base pessoal

Funciona primeiro com dados do próprio motorista.

Entradas:
- ofertas;
- contextos;
- corridas realizadas;
- exposição regional;
- dia;
- horário;
- origem;
- destino;
- ETA;
- serviço;
- métricas;
- histórico importado.

## 11.2. Base coletiva

Arquitetura obrigatória, contribuição opcional.

Opt-in explícito.

Para coletivo, preferir enviar/agregar:
- célula geográfica;
- faixa horária;
- dia da semana;
- tipo de serviço;
- métricas necessárias;
- evento/exposição.

Não enviar para a base coletiva, por padrão:
- screenshot;
- OCR bruto;
- endereço textual exato;
- dados de passageiro;
- informação sem finalidade estatística.

## 11.3. Regiões

Usar uma representação espacial agregável:

```text
latitude/longitude pessoal
↓
célula geográfica
↓
agregado regional
```

A resolução será calibrada para equilibrar:
- utilidade;
- privacidade;
- densidade de amostra.

## 11.4. Métricas estatísticas mínimas

Por região/célula e janela temporal:
- ofertas observadas;
- exposições;
- tempo total disponível;
- tempo médio até oferta;
- mediana até oferta;
- distribuição por serviço;
- distribuição de R$/km;
- distribuição de R$/min;
- probabilidade por horizonte;
- tamanho da amostra.

## 11.5. Continuidade no destino

Para uma oferta:

```text
destination_cell
+
estimated_arrival_at
+
dia da semana
↓
consulta estatística
↓
continuidade
```

Saída v1:

```text
Dados insuficientes
ou
Baixa / Média / Alta
ou
percentual quando confiável
```

Mostrar:
- base pessoal;
- coletiva;
- ou combinada;
- tamanho de amostra de forma compreensível.

## 11.6. Não usar IA generativa

O cálculo principal deve ser SQL/Kotlin/TypeScript/estatística.

LLM pode explicar o resultado depois, mas não criar o número.

## 11.7. Custo de oportunidade

Nesta fase:
- coletar todos os campos necessários;
- calcular componentes;
- não inventar score definitivo sem base.

Conceito:

```text
retorno imediato
+
continuidade esperada
=
qualidade global da oportunidade
```

A 1.0 pode mostrar os componentes lado a lado antes de criar um score único.

### Critério de saída

```text
[ ] cálculo funciona sem LLM
[ ] amostra insuficiente não gera falso percentual
[ ] pessoal funciona localmente
[ ] coletivo é opt-in
[ ] agregado não depende de screenshot bruto
[ ] destino influencia a leitura estatística
```

---

# 12. 0.18 — Custos pessoais e Lucro est.*

## 12.1. Configuração rápida

Perguntar somente o necessário:

### Veículo
- combustão;
- elétrico;
- híbrido;
- híbrido plug-in.

### Situação
- quitado;
- financiado;
- alugado;
- assinatura.

### Energia/combustível
- gasolina;
- etanol;
- GNV;
- eletricidade;
- combinação.

### Preço
- R$/litro;
- R$/kWh.

### Consumo
- km/l;
- kWh/100 km;
- combinação aplicável.

### Custo fixo principal
- parcela;
- aluguel;
- assinatura;
- outro.

### Base de rateio
Adicionar um campo simples para permitir distribuir custo fixo:
- km de trabalho por mês;
- ou `Não sei`.

Quando `Não sei`:
- usar referência/estimativa configurável;
- marcar o valor como `estimated`, nunca `userProvided`.

## 12.2. Ajustar meus custos

Campos avançados opcionais:
- seguro;
- manutenção;
- pneus;
- financiamento;
- aluguel;
- outros;
- jornada média;
- km/mês;
- horas/mês.

## 12.3. Resultado

Usar sempre:

```text
Lucro est.*
```

Tela detalhada:
- `Estimativa de lucro`.

Disclaimer:
- é estimativa operacional;
- depende dos dados fornecidos;
- custos não incluídos alteram o resultado;
- não é lucro contábil;
- permitir ver memória do cálculo.

### Critério de saída

```text
[ ] configuração simples pode ser concluída rapidamente
[ ] “Não sei” não bloqueia
[ ] estimado e informado pelo usuário são distinguíveis
[ ] Lucro est.* tem memória de cálculo
```

---

# 13. 0.19 — Validação de campo do novo núcleo

Antes de iniciar a RC comercial, realizar nova rodada com o núcleo completo.

Validar:
- OCR;
- HUD;
- origem/destino;
- Radar;
- Maps;
- geocoding;
- ETA;
- botão flutuante;
- jornada;
- pausa/retomada;
- Estou fazendo;
- Realizada/Não realizada;
- exposição regional;
- importação;
- custos;
- estatística.

## Saída mínima

```text
[ ] zero P0
[ ] zero P1
[ ] Offer Engine numérico sem regressão
[ ] Context Engine não mistura cards
[ ] importação deduplica
[ ] exposição gera denominador estatístico
[ ] continuidade não inventa percentual
[ ] bateria/CPU aceitáveis
[ ] sync confiável
```

---

# 14. Base local e modelo de dados obrigatório

A implementação pode ajustar nomes finais, mas precisa representar estes conceitos.

## 14.1. Local

Prever:
- `local_offer_context`;
- `local_journey_state_events`;
- `local_ride_outcomes`;
- `local_zone_exposure`;
- `local_import_jobs`;
- `local_imported_media`;
- `local_region_stats`;
- `local_cost_profile`.

## 14.2. Supabase

Prever:
- contexto 1:1 da oferta;
- estados/eventos de jornada;
- status de corrida realizada;
- exposição regional;
- perfil de custos;
- consentimento coletivo;
- agregados estatísticos;
- versionamento do modelo estatístico.

## 14.3. Versionamento

Separar versões:

```text
parser_version       = Offer Engine
context_version      = Context Engine
stats_model_version  = Motor Estatístico
```

Isso permite evoluir inteligência geográfica sem alterar retrospectivamente o parser financeiro.

---

# 15. Privacidade e localização

Localização passa a ser um requisito arquitetural e precisa aparecer desde o desenho.

## 15.1. Princípios

- processamento local quando possível;
- minimização;
- finalidade;
- transparência;
- consentimento;
- retenção definida;
- segurança;
- sem coleta fora da finalidade;
- coletivo sempre opt-in.

## 15.2. Dados pessoais x coletivo

### Base pessoal
Pode manter mais detalhe quando necessário ao recurso do próprio motorista, com proteção e política clara.

### Base coletiva
Preferir:
- célula/região;
- horário agregado;
- métricas agregadas;
- nenhum endereço exato desnecessário.

## 15.3. Data Safety / Play

Antes do RC:
- atualizar declaração de dados para localização;
- revisar permissões;
- revisar foreground service;
- explicar uso durante jornada;
- revisar política de privacidade;
- revisar retenção;
- revisar exclusão.

---

# 16. 1.0-A — Hardening de segurança e banco

Depois da validação funcional 0.19:

## Supabase
- revisar `SECURITY DEFINER`;
- revogar RPC server-only de papéis públicos;
- proteger funções de:
  - Pix;
  - créditos;
  - pagamentos;
  - operações server-only;
- revisar RLS;
- rodar Security Advisor.

## Auth
- Leaked Password Protection;
- e-mail confirmado.

## Performance
Criar/revisar índices para:
- jornadas;
- ofertas;
- contexto;
- células;
- exposição;
- estatística;
- billing.

### Saída
- sem WARN crítico server-only;
- grants revisados;
- índices reproduzíveis.

---

# 17. 1.0-B — Trial, device identity e antiabuso

Trial:
- 7 dias;
- início = primeira oferta válida;
- 5 créditos temporários.

Estruturas:
- `trial_runs`;
- `trial_device_fingerprints`;
- `trial_abuse_markers`;
- evolução de `driver_devices`.

Regras:
- reinstalação não reinicia;
- novo e-mail no mesmo aparelho não ganha novo trial;
- segundo aparelho permitido não reinicia;
- máximo 2 ativos.

---

# 18. 1.0-C — Access Resolver

Criar fonte única:

```text
resolve_driver_access(driver_id, device_id)
```

Estados:
- `TRIAL_PENDING`;
- `TRIAL_ACTIVE`;
- `PAID_ACTIVE`;
- `EXPIRED_READ_ONLY`;
- `BLOCKED`.

Controlar:
- nova jornada;
- OCR;
- HUD;
- estatística;
- histórico;
- IA;
- MCP;
- billing;
- perfil.

Nenhuma superfície implementa regra comercial divergente.

---

# 19. 1.0-D/E/F — Web definitiva + handoff + Digital Asset Links

## Web já adiantada

### Web-P1 ✅
- fundação `/app`;
- navegação;
- PWA;
- identidade;
- rotas principais.

### Web-P2 ✅
- login;
- sessão Web temporária;
- dados reais;
- IA/MCP;
- Perfil;
- Plano.

### Web-P3 ✅
- dashboard;
- filtros;
- detalhes de jornada;
- dispositivos.

### Web-P4 ✅
- paleta oficial da landing.

### Web-P4.1 ✅
- hotfix TypeScript do endpoint de dispositivos.

## Ainda necessário

### 1.0-D
Integrar novas informações:
- origem/destino;
- Maps;
- corrida realizada;
- estado da jornada;
- continuidade;
- amostra estatística;
- Lucro est.*;
- importações.

### 1.0-E
Sessão Native → Web de uso único:

```text
Android
→ POST /api/v1/web/handoff
→ código temporário
→ /app/entrar?code=...
→ cookie HttpOnly + Secure
```

### 1.0-F
- `/.well-known/assetlinks.json`;
- Play App Signing SHA-256;
- PWA `start_url=/app`;
- abertura confiável dentro do app.

Não publicar segundo app.

---

# 20. 1.0-G — Billing real, Pix e créditos

Plano:
- R$ 9,90 / 30 dias.

Fluxo:
- Banco Inter;
- Pix;
- criação;
- consulta;
- processamento;
- entitlement;
- conversão do trial;
- 20 créditos uma única vez.

Regras:
- idempotência;
- txid único;
- valor confere;
- divergência → revisão;
- renovação não repete 20 créditos;
- falha de IA devolve crédito.

Manter `PLAY_PAYMENT_MODE` remoto conforme revisão de Play vigente antes da submissão.

---

# 21. 1.0-H/I — IA e MCP

## IA
- trial usa 5 créditos temporários;
- pago usa carteira;
- reserve → resposta → consume;
- erro → refund.

## MCP
- somente leitura;
- respeita entitlement;
- nunca controla Uber;
- pode expor:
  - histórico;
  - métricas;
  - contexto regional;
  - continuidade;
  - custos;
  - estatística;
  desde que respeite privacidade.

---

# 22. 1.0-J/K — OneSignal e Play Integrity

## OneSignal
- trial;
- versão;
- manutenção;
- pagamento;
- sync;
- recursos.

Nunca:
- oferta;
- verdict;
- HUD em tempo real.

## Integrity
Começar:
- `observe`;
- depois `soft`;
- `hard` só após telemetria.

`deviceRecall`, se disponível:
- reforço antiabuso;
- não rastreamento.

---

# 23. 1.0-L — Privacidade, exclusão e segurança final

Validar:
- `/app/perfil`;
- `/excluir-conta`;
- `/privacidade`;
- `/termos`;
- `/suporte`.

Exclusão:
- Auth;
- driver;
- devices;
- sessões;
- MCP;
- OneSignal;
- dados locais;
- dados pessoais geográficos conforme política.

Marcador antiabuso preservado, se houver:
- pseudônimo;
- finalidade;
- prazo;
- revisão jurídica.

---

# 24. 1.0-M — Release Candidate

## Produto

```text
[ ] zero P0
[ ] zero P1
[ ] Offer Engine v1 congelado
[ ] Context Engine validado
[ ] origem/destino
[ ] Maps
[ ] ETA
[ ] jornada ativa/pausada/encerrada
[ ] Estou fazendo / Realizada / Não realizada
[ ] exposição regional
[ ] importação de screenshots
[ ] deduplicação
[ ] Motor Estatístico v1
[ ] continuidade com controle de amostra
[ ] custos
[ ] Lucro est.*
[ ] HUD corrigido/compactado
[ ] botão flutuante
[ ] notificação operacional
[ ] voz configurável
[ ] onboarding
[ ] trial
[ ] antiabuso
[ ] Access Resolver
[ ] Web integrada
[ ] Pix
[ ] créditos
[ ] IA
[ ] MCP
[ ] OneSignal
[ ] Integrity observe/soft
[ ] exclusão
[ ] suporte
[ ] offline/sync
```

## Play

- target API 36;
- AAB release;
- assinatura definitiva;
- foreground service `mediaProjection`;
- foreground service `location` se usado na jornada;
- permissões de localização justificadas;
- vídeo da captura iniciada pelo usuário;
- Data Safety atualizado;
- política de privacidade;
- exclusão de conta;
- classificação;
- conteúdo da loja.

---

# 25. 1.0.0 — Publicação

Só publicar quando:

```text
[ ] Security Advisor revisado
[ ] Performance Advisor revisado
[ ] Context Engine validado
[ ] estatística não produz falsa precisão
[ ] localização/Data Safety revisadas
[ ] trial testado
[ ] antiabuso testado
[ ] Pix real testado
[ ] 20 créditos testados
[ ] 5 créditos trial testados
[ ] handoff Web testado
[ ] MCP testado
[ ] OneSignal testado
[ ] exclusão validada
[ ] MediaProjection validado
[ ] localização de jornada validada
[ ] AAB assinado
```

---

# 26. Divulgação

Landing:
- proposta;
- como funciona;
- screenshots;
- plano;
- trial;
- FAQ;
- IA;
- MCP;
- continuidade;
- histórico;
- suporte;
- login.

Mensagens atuais:

**“Você decide a corrida. O Sr. Rotas faz as contas.”**

**“Use a IA do Sr. Rotas ou conecte seus dados à IA que você já usa.”**

Nova mensagem de produto a validar:

**“Não olhe só quanto a corrida paga. Veja também onde ela vai te deixar.”**

Canais:
- Instagram;
- TikTok;
- YouTube/Shorts;
- comunidades de motoristas;
- creators;
- indicação.

---

# 27. Fora do core da 1.0

Não desenvolver antes da 1.0, salvo nova decisão explícita do idealizador/BigCorps:

- classificação de áreas perigosas;
- tráfico/milícia;
- score de segurança;
- alertas de risco regional;
- leitura de heatmap da Uber;
- interpretação das cores do mapa Uber;
- captura assistida por rolagem do histórico Uber;
- comunidade;
- anúncios;
- gamificação;
- moedas;
- programa de parceiros;
- cupom/indicação avançada;
- score único definitivo de oportunidade;
- modelo de ML complexo;
- previsão com percentual quando amostra for insuficiente.

---

# 28. Pós-1.0

- score Sr. Rotas consolidado;
- custo de oportunidade em score único;
- modelos estatísticos mais sofisticados;
- sazonalidade avançada;
- coletivo ampliado;
- benchmarking anonimizado;
- relatórios avançados;
- Pix Automático/recorrência;
- indicação/cupom;
- parceiros;
- widgets;
- personalização;
- novos clientes MCP;
- iOS;
- outras plataformas de mobilidade após revisão técnica/jurídica;
- expansão internacional.

---

# 29. Sequência oficial atualizada

```text
0.13.2
beta atual
        ↓
0.13.3
estabilidade HUD/tablet/paleta/voz
        ↓
0.14
Context Engine + origem/destino + geo + Maps + ETA
        ↓
0.15
jornada + corrida realizada + exposição + mascote
        ↓
0.16
importação histórica de screenshots
        ↓
0.17
Motor Estatístico v1 + pessoal/coletivo
        ↓
0.18
custos + Lucro est.*
        ↓
0.19
validação de campo do novo núcleo
        ↓
1.0-A/B/C
hardening + trial + Access Resolver
        ↓
1.0-D/E/F
Web + handoff + DAL
        ↓
1.0-G/H/I
Pix + créditos + IA + MCP
        ↓
1.0-J/K/L
OneSignal + Integrity + privacidade
        ↓
1.0-M
Release Candidate
        ↓
1.0.0
Play Store
        ↓
divulgação
```

---

# 30. Regra documental

A cada ZIP/fase:

1. consultar a `main` atual;
2. atualizar **este mesmo arquivo**;
3. não criar MDs paralelos de roadmap/checklist/changelog;
4. instruções de aplicação ficam no chat;
5. migrations entram somente quando a fase exigir;
6. cada ZIP deve ser incremental;
7. o Offer Engine v1 permanece separado do Context Engine e do Motor Estatístico;
8. decisão aceita do idealizador/BigCorps deve ser incorporada a este arquivo antes da implementação;
9. quando uma decisão antiga conflitar com uma nova decisão registrada aqui, vale a mais recente deste roadmap.

---

# 31. Web-P5 — Portal interno de importação histórica

**Status: ✅ implementação Web preparada; depende da migration de staging**

O portal de importação é uma ferramenta administrativa **exclusivamente Web**.

Rota oficial:

```text
https://srrotas.com/admin/importacoes
```

Não adicionar:
- tela Kotlin equivalente;
- item de navegação no APK;
- botão no HUD;
- recurso público para clientes na 1.0.

## 31.1. Controle de acesso

Administrador permanente:

```text
contato@bigcorps.com.br
```

Regras:
- autenticação usa **sessão Web administrativa própria**, baseada no Supabase Auth e independente de `drivers`;
- o administrador pode adicionar ou remover e-mails autorizados dentro do próprio portal;
- e-mail autorizado pode enviar JSON/JSONL e consultar seus próprios lotes;
- somente o administrador pode gerenciar a allowlist e visualizar lotes de todos os importadores;
- acesso por device token/Android não é aceito pelos endpoints administrativos;
- o e-mail precisa existir no Supabase Auth e estar autorizado; **não precisa existir em `drivers` nem ser conta de motorista**.
- o login normal de `/app` continua exclusivo para motoristas e não é reutilizado pelo portal administrativo.

Estrutura:
- `historical_import_access`;
- `historical_import_batches`;
- `historical_import_rows`.

As tabelas são server-only:
- RLS ligada;
- sem grants para `anon`/`authenticated`;
- operações somente pelo backend com `service_role`.

## 31.2. Staging obrigatório

Nenhum JSON enviado pelo portal grava diretamente em:
- `ride_offers`;
- agregados estatísticos;
- Context Engine final;
- base coletiva.

Fluxo:

```text
JSONL/JSON
→ lote
→ validação
→ deduplicação
→ original_payload + normalized_payload
→ staging
→ revisão/Context Engine
→ somente depois base histórica final
```

Estados de linha:
- `valid`;
- `partial`;
- `invalid`;
- `duplicate`.

Deduplicação inicial:
1. `source_file_sha256`, quando o GPT/processador preservar SHA-256 do screenshot;
2. `semantic_key` calculada a partir dos campos estruturados.

## 31.3. Formato preferido

Priorizar JSONL para lotes grandes.

O cliente Web processa o arquivo em chunks pequenos antes de enviar ao backend para evitar payload único gigante no Vercel.

O portal aceita também JSON para amostras menores.

## 31.4. Campos iniciais reconhecidos

- fonte;
- nome/hash do screenshot;
- data/hora;
- fonte/confiança temporal;
- valor;
- km/minutos;
- retirada;
- destino;
- tipo de serviço;
- tipo de oferta;
- avaliação;
- confiança OCR;
- confiança de contexto.

Campos desconhecidos permanecem preservados em `original_payload`; o staging não deve descartar dados fornecidos pelo processo de extração.

## 31.5. Relação com 0.16

Web-P5 antecipa **recebimento, auditoria e organização** dos dados.

A fase 0.16 continua responsável por:
- integração definitiva com Context Engine;
- enriquecimento/geocoding;
- revisão de parciais;
- promoção controlada para a base histórica;
- importação local Android, se mantida como recurso de teste/produto.

Portanto Web-P5 não substitui 0.16 e não cria estatística com dados não validados.

## 31.6. Web-P5.1 — Correção da identidade administrativa

**Status: ✅ corrigido após validação do primeiro login**

Problema observado:
- `contato@bigcorps.com.br` existe no Supabase Auth, mas não é motorista;
- a primeira Web-P5 reutilizava `billing_web_sessions`, que exige um registro em `drivers`;
- por isso o login retornava `driver_not_found`.

Decisão definitiva:
- administradores/importadores são **identidades Web administrativas**, não motoristas;
- `/app/entrar` e o dashboard normal permanecem ligados a `drivers`;
- `/admin/importacoes` usa autenticação própria;
- não criar `driver` artificial para BigCorps, Jadiel ou outros importadores apenas para satisfazer o portal.

Implementação:
- cookie separado `sr_import_admin`;
- nova tabela server-only `historical_import_web_sessions`;
- sessão criada diretamente a partir do Supabase Auth;
- allowlist continua em `historical_import_access`;
- `historical_import_batches.created_by_driver_id` passa a ser opcional;
- lotes Web usam `created_by_auth_user_id` + `created_by_email`;
- gerenciamento de allowlist registra `added_by_auth_user_id`.

Administrador permanente:
- `contato@bigcorps.com.br`.

Autorizado inicial:
- `jadielalmeida@gmail.com`.

O portal continua:
- exclusivamente Web;
- fora do APK Kotlin;
- fora do fluxo comercial do motorista;
- sem acesso por device token Android.

## 31.7. Web-P5.2 — Login Web unificado e acesso por papel

**Status: ✅ preparado para deploy**

Decisão de produto:
- existe **uma única tela de login Web pública**: `/app/entrar`;
- essa tela autentica qualquer identidade válida do Sr. Rotas e direciona conforme o papel;
- o portal `/admin/importacoes` não mantém mais formulário próprio de login.

Regras:

### `contato@bigcorps.com.br`
- identidade administrativa BigCorps;
- existe no Supabase Auth;
- **não existe e não deve existir em `drivers`**;
- login normal direciona para `/admin/importacoes`;
- recebe sessão administrativa `sr_import_admin`;
- vê allowlist e lotes de todos os importadores.

### Motorista autorizado para importação
Exemplo atual:
- `jadielalmeida@gmail.com`.

Regra:
- continua sendo motorista normal;
- mantém `drivers`, device token, Android e dashboard sem qualquer alteração;
- a própria sessão normal `sr_billing` pode ser usada para comprovar acesso ao importador;
- se estiver em `historical_import_access`, recebe no dashboard Web o botão `Enviar históricos / Importar JSON`;
- ao entrar em `/admin/importacoes`, aparece como `Importador`;
- o botão do portal retorna ao dashboard em vez de destruir sua sessão de motorista.

### Outros importadores futuros sem perfil de motorista
- podem existir apenas no Supabase Auth + allowlist;
- usam o mesmo `/app/entrar`;
- recebem sessão administrativa própria;
- não é criado `driver` artificial.

### Paleta do login
A tela `/app/entrar` deve usar exatamente a identidade da landing:
- fundo creme `#F8F4DF`;
- ink `#073746`;
- teal `#0C8788/#0E9998`;
- dourado `#F4CA50/#E6B631`;
- não reutilizar o fundo cyan/MonitorIA anterior.

### Segurança
- `next` do login aceita somente rotas internas `/app...` ou `/admin/importacoes...`;
- importador motorista continua sujeito à allowlist;
- administrador permanente continua sendo `contato@bigcorps.com.br`;
- nenhum e-mail administrativo é transformado em motorista só para autenticação.

