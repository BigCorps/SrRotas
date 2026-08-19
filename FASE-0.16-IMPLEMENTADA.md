# Sr. Rotas 0.16 — Performance + Launcher Icon Fix

## Diagnóstico confirmado

A captura e o Offer Engine não foram alterados entre 0.14 e 0.15.
A regressão mais relevante entrou ao redor do dispatcher:

`JourneyCoordinator.canObserveOffers()` chamava `snapshot()`.

O snapshot lia SQLite para:
- estado atual;
- `DOING_RIDE`;
- última oferta.

Isso podia ocorrer mais de uma vez no processamento do mesmo card.

Além disso, após `saveOffer()` a 0.15 executava de forma síncrona:
- criação/consulta de outcome;
- consultas de exposição;
- corte/reabertura de exposição;
- atualização do mascote;
- acionamento de sync.

A 0.16 mantém o Offer Engine congelado e remove esse trabalho do caminho quente.

## Alterações

- runtime operacional em memória;
- hidratação única por processo;
- pós-processamento de oferta em executor dedicado de baixa prioridade;
- timestamp da oferta preservado ao fechar exposição;
- refresh de oferta do mascote só quando painel expandido;
- telemetria do dispatcher;
- localização regional aliviada (passivo preferencial + 45 s/180 m);
- launcher Android refeito com safe area;
- build `0.16.0-beta`, versionCode 23;
- Action passa a gerar também APK field release.

## Sem migration

Nenhuma alteração de schema é necessária.
