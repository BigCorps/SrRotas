# Referência Visual Oficial — Radar Contextual

## Arquivo obrigatório
`REFERENCIA_VISUAL/MOCKUP-RADAR-CONTEXTUAL-4-FACETAS.png`

Este screenshot é a **referência visual oficial do módulo** e deve ser usado pela instância desenvolvedora durante a integração.

Ele não é apenas ilustrativo. Define a direção visual, a hierarquia de informação e a relação entre as quatro facetas do Radar.

---

## Faceta 1 — Corrida em andamento / entrada discreta

Referência no mockup: painel mais à esquerda.

### Deve manter
- contexto claro de “Corrida em andamento”;
- busca / embarque / destino;
- CTA discreto: `Ver oportunidades no destino`;
- CTA inserido como parte natural da corrida, sem ocupar o foco principal;
- previsão de chegada e distância restante em cards compactos;
- Radar disponível na navegação inferior.

### Regra
A entrada para o Radar não pode parecer um novo aplicativo, popup invasivo ou tela desconectada da corrida.

---

## Faceta 2 — Mapa / Continuidade no Destino

Referência no mockup: segundo painel.

### Deve manter
- título `Continuidade no Destino`;
- destino e ETA no topo;
- mapa CENTRALIZADO NO DESTINO, não no GPS atual do veículo;
- destino destacado;
- poucos POIs relevantes;
- marcadores visualmente diferenciados por tipo;
- raio de análise visível;
- navegação inferior preservada;
- interface limpa, sem “árvore de Natal”.

### Sobre o código atual
`RadarMiniMapViewV1` é um fallback leve de homologação e uma representação espacial sem dependência de SDK de mapas.

**Ele não deve ser interpretado como obrigação de manter o mapa abstrato no produto final.**

Se a arquitetura vigente do Sr. Rotas permitir introduzir um mapa real sem criar dependência/regressão inadequada, o resultado visual deve se aproximar do mockup, com ruas e contexto espacial real.

Se isso não for desejável para a primeira integração:
1. homologar primeiro com `RadarMiniMapViewV1`;
2. preservar o contrato de markers/cards;
3. substituir apenas o renderer do mapa posteriormente.

O contrato de backend/POI não deve depender da tecnologia visual escolhida.

---

## Faceta 3 — POI selecionado / card detalhado

Referência no mockup: terceiro painel.

### Deve manter
- POI destacado no mapa;
- bottom card / card sobreposto visualmente associado ao marcador;
- nome + tipo + distância;
- janela temporal;
- potencial;
- confiança;
- seção `Por que está aqui?`;
- explicações curtas, escaneáveis;
- CTA `Ver no mapa`.

### Regra de dados
A UI não pode inventar justificativas.

A seção `Por que está aqui?` deve ser montada exclusivamente a partir de `evidence[]`.

`ranking_score` nunca deve aparecer como `%`.

Quando não houver estatística suficiente, mostrar “amostra insuficiente” / “contexto”, nunca fabricar probabilidade.

---

## Faceta 4 — Assistente Ativo / balão contextual

Referência no mockup: painel mais à direita.

### Deve manter
- balão compacto ancorado ao mascote;
- mensagem curta e objetiva;
- conteúdo semelhante a:
  `Boa chance de continuidade no destino!`
- explicação de no máximo 1–2 linhas;
- apenas duas ações:
  `Ignorar | Ver`;
- mapa permanece visível por trás;
- não bloquear grande parte da tela.

### Regra
Reutilizar o estilo/renderer visual do Assistente já existente quando possível, mas NÃO reutilizar a regra de disparo do `ActiveAssistant026`.

---

# Prioridade de fidelidade

A implementação deve buscar fidelidade nesta ordem:

1. hierarquia das informações;
2. fluxo entre as quatro facetas;
3. densidade/compactação;
4. posições relativas dos elementos;
5. comportamento de seleção;
6. cores/ícones compatíveis com a identidade canônica;
7. microdetalhes estéticos.

Não copiar cegamente pixels se isso conflitar com responsividade, tema escuro/claro ou componentes canônicos do Sr. Rotas.

---

# Pode adaptar

Pode adaptar:
- espaçamentos mínimos para diferentes densidades;
- tipografia para usar a fonte/estilo canônico;
- cores para obedecer `SrUi023`;
- tamanho de cards para celular/tablet/split-screen;
- ícones para equivalentes do pacote visual canônico;
- tecnologia do mapa.

---

# Não pode descaracterizar

Não deve:
- transformar Radar em lista textual sem mapa/visão espacial;
- colocar dezenas de POIs simultaneamente;
- centralizar mapa no motorista em vez do destino;
- esconder ETA/destino;
- remover o card explicativo;
- substituir `Por que está aqui?` por score opaco;
- mostrar ranking como probabilidade;
- tornar o Assistente um popup modal;
- remover `Ignorar | Ver`;
- misturar o fluxo do ActiveAssistant de ociosidade com a continuidade da corrida.

---

# Critério visual de aceitação

A integração só deve ser considerada visualmente aprovada quando, lado a lado com o mockup:

- a Faceta 1 é reconhecível como a mesma ideia de entrada discreta;
- a Faceta 2 mostra continuidade centrada no destino com poucos POIs;
- a Faceta 3 apresenta detalhe contextual equivalente;
- a Faceta 4 apresenta o mascote/balão compacto com `Ignorar | Ver`;
- a interface parece pertencer ao Sr. Rotas atual, não a um módulo externo colado ao app.

O mockup é parâmetro. O código é a fundação. Na dúvida de composição visual, **seguir o mockup preservando os contratos e componentes canônicos**.
