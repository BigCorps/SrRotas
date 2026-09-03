# Teste real — Sr. Rotas 0.26.1-beta

## Pré-requisito

Só instalar o APK se o GitHub Actions concluir verde:
- unit tests;
- assembleDebug;
- assembleRelease;
- assinatura/apksigner;
- upload dos APKs.

## 1. HUD — teste principal

Fazer 20 ofertas reais consecutivas sem trocar configurações no meio do teste.

Registrar para cada oferta:
- serviço (UberX/Comfort/Black/Electric/Radar etc.);
- valor mostrado pela Uber;
- duração mostrada;
- distância mostrada;
- HUD apareceu?;
- números do HUD corretos?;
- houve duplicata ou HUD de oferta anterior?

Meta da rodada:
- pelo menos 18/20 ofertas com HUD correto;
- zero mistura óbvia entre cards;
- zero oferta Uber gravada/exibida como `other` quando houver identificação clara da Uber;
- zero duração absurda tipo 300 min originada de frame misturado.

Testar obrigatoriamente:
- uma oferta > 1 hora, se aparecer;
- Radar de Viagens;
- duas ou mais ofertas/cards visíveis na mesma tela, se a Uber apresentar;
- tela dividida com Maps/Waze, se esse for o uso normal.

## 2. Street View

- confirmar que `Street View — Destino` mantém seu espaço sem piscar;
- antes do destino resolver, deve ficar desabilitado/cinza;
- quando houver coordenada válida, deve ativar;
- ao tocar, abrir o destino correto no Street View/Google Maps.

## 3. Probabilidade

- HUD deve mostrar `Prob. novas corridas`;
- telas maiores devem usar `Probabilidade de novas corridas`;
- sem dados, o slot continua visível e não inventa percentual.

## 4. Base Coletiva

- moldura fina, multicolorida;
- conteúdo legível em claro e escuro;
- não deve voltar a parecer uma borda grossa.

## 5. Jornada

- abrir início de jornada;
- preencher km inicial;
- testar combustível e recarga;
- iniciar sem preencher;
- encerrar com km final;
- confirmar que a lógica continua igual à 0.26.0 e que o visual acompanha o restante do app.

## 6. Mensagens rápidas

Configurar pelo menos 10 mensagens.

Confirmar:
- barra não aumenta de altura;
- aproximadamente 6 posições visíveis;
- arrastar verticalmente rola;
- seta para cima e seta para baixo funcionam;
- selecionar uma mensagem continua copiando o texto correto.

## 7. Radar

- `Agora` deve abrir sem consulta repetitiva excessiva;
- abrir novamente em menos de 5 minutos deve aproveitar cache;
- `Atualizar Radar` deve forçar nova consulta;
- eventos Ticketmaster continuam aparecendo normalmente.

## 8. Scraper / painel

Depois de configurar `RADAR_INGEST_SECRET` e redeployar:
- abrir `/radar-admin`;
- informar o secret;
- cadastrar um evento manual de teste com coordenadas reais;
- confirmar que aparece na lista;
- editar;
- cancelar;
- conferir que o registro permanece com status `cancelled`.

Depois remover/cancelar qualquer evento criado somente para teste.
