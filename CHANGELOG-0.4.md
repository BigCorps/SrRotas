# Changelog — Sr. Rotas 0.4 Alpha

## Parser calibrado com testes reais

- exige contexto de card antes de aceitar qualquer valor `R$`;
- rejeita tela inicial, registro de viagens, tendências e faixas de tempo do mapa;
- separa preço principal de R$/km aproximado;
- ignora adicionais `+R$ ... incluído` como preço principal;
- diferencia Exclusivo e Radar por âncoras da própria interface;
- reconhece UberX, Priority, Comfort, Black e Moto quando disponíveis;
- captura avaliação do passageiro;
- valida o R$/km calculado contra o valor aproximado mostrado pelo Uber;
- adiciona fixtures automatizadas dos falsos positivos observados no Alpha.

## Estratégia / Cherry Picker

- faixas vermelho/amarelo/verde para R$/km, R$/hora, avaliação e R$/min;
- opções para lucro/hora e margem de lucro;
- custo por km, valor mínimo, distância máxima de embarque e lucro mínimo;
- presets Equilibrado, Conservador e Volume;
- métricas do HUD selecionáveis e reordenáveis;
- posição, tema, opacidade, fonte e modo para daltonismo;
- preview do HUD.

## Avançado

- notificação textual opcional;
- leitura por voz opcional;
- captura privada opcional, desligada por padrão;
- mensagem padrão somente para copiar, sem automação de envio.

## Backend / Supabase

- novas métricas persistidas: R$/min, lucro/hora, margem, avaliação, R$/km anunciado e tipo de serviço;
- novas faixas Cherry Picker sincronizadas;
- IA e MCP passam a receber as novas métricas disponíveis.

## Institucional

- suporte: contato@bigcorps.com.br;
- rodapés: “Sr. Rotas é desenvolvido pela BigCorps”.
