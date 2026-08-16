# Teste Alpha — Uber Driver

## Objetivo

Descobrir qual formato a versão real do Uber Driver no aparelho entrega ao OCR do Sr. Rotas e calibrar o parser sem automatizar nenhuma ação no Uber.

## Antes de sair

- Backend e Supabase podem ficar desligados no primeiro teste; o semáforo é local.
- Defina R$/km, R$/hora e custo/km.
- Permita `Exibir sobre outros apps`.
- Inicie a jornada e autorize a captura do Android.
- A Acessibilidade é opcional neste teste.

## Quando uma oferta tocar

Observe se o HUD mostra:

- valor da oferta;
- R$/km;
- R$/hora;
- lucro estimado;
- semáforo coerente.

## Se estiver errado

Volte ao Sr. Rotas > Diagnóstico da leitura e copie:

- Método
- Texto reconhecido
- Log local das últimas linhas

O screenshot não é salvo pelo Alpha. O texto bruto fica local e não é sincronizado por padrão.

## Radar

Se o Uber mostrar vários cards, anote quantas ofertas aparecem. O parser v0.2 usa as coordenadas do ML Kit para separar os cards pelo preço `R$` mais próximo.

## Critério de sucesso do Alpha

Antes de avançar para histórico avançado/mapa, queremos:

- valor correto em pelo menos 95% dos cards testados;
- distância/tempo coerentes;
- sem duplicatas em atualizações repetidas da mesma tela;
- HUD sem bloquear toques do Uber;
- jornada estável por pelo menos 60 minutos.
