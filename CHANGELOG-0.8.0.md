# Changelog — Sr. Rotas 0.8.0 Alpha

## Histórico
- painel completo de analytics determinísticos;
- filtros por período/classificação/categoria/tipo;
- comparação com janela anterior;
- resumo por jornada;
- destaques do período.

## Gráficos
- gráfico nativo de R$/km por dia;
- gráfico nativo de R$/hora por horário;
- sem dependência externa de chart.

## Offline
- analytics locais usam SQLite do aparelho;
- filtros e comparação continuam funcionando sem conexão;
- sincronização volta a usar a nuvem quando disponível.

## Backend
- novo `GET /api/v1/analytics`;
- `historyDashboard()` em TypeScript;
- paginação de ofertas para analytics;
- até 5.000 linhas por período antes de marcar `truncated`.

## Custos
Nenhuma chamada de OpenAI é feita para histórico ou gráficos.

## Motor
Offer Engine v1 permanece congelado em `sr-rotas-v0.5.4`.
