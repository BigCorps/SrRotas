# Sr. Rotas Radar — relatório técnico do estado atual

**Auditoria:** 04/09/2026  
**Escopo:** somente leitura de código, Vercel/Supabase e comportamento já implementado. Este relatório não altera o aplicativo nem o banco.

## 1. Existem dois recursos diferentes chamados Radar

### 1.1 Radar de ofertas / OCR
É parte do motor que lê a tela do aplicativo de transporte no próprio aparelho. Ele reconhece ofertas convencionais e cards do tipo Radar, separa tarifa, distância, duração, categoria, busca e contexto, calcula indicadores e encaminha uma oferta válida ao HUD.

Esse Radar trabalha sobre **uma oferta que já foi mostrada ao motorista**. Ele não procura shows, estádios ou eventos na internet e não utiliza a base `sr_event_opportunities`.

### 1.2 Sr. Rotas Radar de eventos e concentração
É uma camada regional independente. Ela procura acontecimentos e pontos com potencial de concentração/saída de passageiros e os apresenta como **oportunidades contextuais**, nunca como corrida garantida.

Esse segundo Radar não participa do parser financeiro da oferta, não inventa R$/km ou R$/h e não transforma um evento em `ride_offer`.

## 2. Como o Radar de eventos funciona hoje

Fluxo atual:

1. O backend obtém centros geográficos derivados de coordenadas recentes de embarque/destino já observadas pelo Sr. Rotas.
2. O cron chama `/api/v1/radar/refresh` a cada duas horas, no minuto 17 (`17 */2 * * *`).
3. A fonte automática ativa consulta a Ticketmaster Discovery API.
4. Cada evento é normalizado para o padrão interno do Sr. Rotas.
5. Os registros são gravados/atualizados em `sr_event_opportunities` por `source + external_id`.
6. Cada execução da fonte gera um registro operacional em `sr_radar_source_runs`.
7. O Android consulta a API própria do Sr. Rotas, não a Ticketmaster diretamente.
8. O Agora apresenta as oportunidades próximas com distância, tipo, horário/janela de saída e confiança.

Não existe um agente LLM rodando continuamente nesse ciclo e, portanto, a coleta normal de eventos não precisa consumir tokens de IA.

## 3. Base de dados ativa

### `sr_event_opportunities`
É a base normalizada das oportunidades. O padrão atual armazena, entre outros:

- `source`
- `external_id`
- `event_type`
- `name`
- `venue_name`
- `address`
- `city`
- `state`
- `country_code`
- `lat` / `lng`
- `starts_at`
- `expected_end_at`
- `egress_start_at`
- `egress_end_at`
- `source_url`
- `confidence`
- `end_time_source`
- `status`
- `last_verified_at`
- `updated_at`
- `metadata`

O identificador lógico para deduplicação é **`source + external_id`**.

### `sr_radar_source_runs`
Registra a saúde das coletas:

- fonte;
- status;
- quantidade de centros consultados;
- quantidade encontrada;
- quantidade salva;
- mensagem de erro, quando houver;
- horário da execução.

## 4. Estado real observado na auditoria

Na consulta de leitura feita em 04/09/2026 havia:

- **16 oportunidades** armazenadas;
- **15 oportunidades ativas**;
- **14 execuções de fonte** registradas;
- **11 execuções `ok`**;
- **3 execuções não `ok`**;
- última verificação da base em torno de **16:17 UTC** em 04/09/2026.

A fonte automática efetivamente populando a base neste momento é **Ticketmaster**. Há eventos classificados, por exemplo, como música, esportes e evento genérico.

Esses números são um retrato do momento da auditoria e mudam conforme o cron executa.

## 5. Atualização e expiração

Quando a fonte encontra novamente o mesmo `source + external_id`, o registro é atualizado em vez de duplicado.

Quando uma fonte externa usa `snapshot_complete`, registros ativos daquela fonte que deixaram de aparecer no snapshot podem ser marcados como expirados.

Independentemente disso, eventos cuja janela de saída terminou há mais de duas horas são marcados como `expired`. O registro é preservado para auditoria; não precisa ser fisicamente apagado.

Status aceitos no padrão atual:

- `active`
- `expired`
- `cancelled`

## 6. Fontes externas / scrapers

A 0.26.1 já preparou o endpoint:

`POST /api/v1/radar/ingest`

A autenticação é feita por:

`Authorization: Bearer <RADAR_INGEST_SECRET>`

O formato aceita até 500 eventos por envio e normaliza a fonte antes de gravar. Se a origem não fornecer `external_id`, o backend consegue gerar uma identificação determinística usando fonte, nome, local e início.

Isso permite criar scrapers independentes para sites de eventos sem criar um agente de IA e sem mudar o Android para cada nova fonte.

Tipos já aceitos pela arquitetura incluem:

- `event`
- `music`
- `sports`
- `theatre`
- `fair_convention`
- `family`
- `airport`
- `bus_terminal`
- `mall`
- `cultural`
- `mobility_hub`

**Importante:** aceitar um tipo no schema não significa que já exista uma fonte automática ativa para ele. Na auditoria, Ticketmaster é a fonte automática confirmada. Aeroportos, rodoviárias, shoppings e hubs já têm contrato compatível, mas ainda dependem de fontes/scrapers específicos para serem alimentados automaticamente.

## 7. Painel administrativo

A infraestrutura da 0.26.1 também prevê a tela `/radar-admin` e rotas administrativas para consultar, editar e alterar status de oportunidades.

O objetivo do painel é cobrir situações como:

- evento local ausente da Ticketmaster;
- correção de horário/local;
- cancelamento;
- reativação;
- revisão de uma oportunidade trazida por scraper.

O painel e o scraper usam a **mesma tabela normalizada**, evitando uma base manual paralela.

## 8. Como o Android recebe e mostra os dados

O aplicativo consulta o backend do Sr. Rotas e recebe eventos normalizados. O motorista não chama Ticketmaster nem um scraper diretamente.

A camada Android aplica localização/distância e regras temporais para selecionar as oportunidades relevantes. A informação aparece no **Agora** como Sr. Rotas Radar, separada das métricas financeiras de uma oferta.

A apresentação deve ser entendida como:

> existe um acontecimento próximo cuja janela de início/saída pode ser útil para decidir onde aguardar.

Não deve ser entendida como:

> haverá certamente uma nova corrida ou determinada tarifa.

## 9. O que já está implementado

- tabela normalizada de oportunidades;
- tabela de saúde das fontes;
- cron automático;
- integração Ticketmaster;
- normalização e deduplicação;
- estimativa de término quando a fonte não fornece horário final;
- janela estimada de saída do público;
- expiração temporal;
- endpoint Android de consulta;
- cliente Android e apresentação no Agora;
- cache curto no cliente;
- endpoint autenticado para scrapers externos;
- contrato para novas categorias/fontes;
- operações administrativas para editar/status;
- estrutura do painel Radar Admin.

## 10. O que ainda é expansão/proposta

- adicionar fontes brasileiras adicionais de forma automática;
- scraper específico para Sympla e/ou agendas públicas, respeitando regras de acesso de cada site;
- fonte dedicada de aeroportos baseada em movimento/voos, e não apenas localização fixa;
- fonte dedicada de rodoviárias, shoppings e hubs;
- ampliar cobertura fora das regiões onde já há atividade do Sr. Rotas;
- critérios de impacto/capacidade de público mais refinados quando a fonte disponibilizar esses dados;
- rotina operacional de revisão humana para fontes de baixa confiança.

## 11. Recomendação para os próximos scrapers

Manter cada coletor fora do aplicativo e fazer com que todos enviem o mesmo payload para `/api/v1/radar/ingest`.

Cadência inicial recomendada:

- agendas de eventos: a cada 1–2 horas;
- fontes que só mudam diariamente: frequência menor;
- evitar scraping contínuo por motorista.

O scraper deve preferir dados estruturados/APIs oficiais quando disponíveis, manter `external_id` estável, reenviar eventos atualizados e usar `snapshot_complete` apenas quando a execução realmente representa a visão completa daquela fonte.

---

### Resumo

O **Radar OCR** interpreta ofertas que já chegaram ao motorista. O **Radar de eventos** antecipa contexto regional usando uma base própria. Eles compartilham a marca Radar, mas têm origem, propósito e dados diferentes. O Radar de eventos já possui backend, banco, cron, Ticketmaster, ingestão externa e cliente Android ativos; a principal evolução pendente é aumentar a variedade e a qualidade das fontes automáticas.
