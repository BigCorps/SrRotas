# Aplicar — Sr. Rotas 0.8.0 Alpha

Patch incremental sobre a `main` 0.7.0, commit esperado:

`0d6eec11f1c9af728fb74e2d238d83fc06a988f0`

## Esta é a fase 0.8 inteira

- histórico analítico no Android;
- filtros de período: hoje / 7 / 30 / 90 dias;
- filtros por classificação, categoria e tipo de oferta;
- comparação automática com o período anterior de mesmo tamanho;
- gráficos nativos sem biblioteca externa;
- R$/km por dia;
- R$/hora por faixa horária;
- ranking de categorias;
- destaques de ofertas;
- lista/resumo de jornadas;
- fallback local quando offline;
- endpoint determinístico `/api/v1/analytics`;
- paginação do backend para analisar até 5.000 ofertas por período;
- nenhuma chamada de IA para analytics;
- nenhuma mudança no Offer Engine v1.

## Aplicação

Não há SQL nesta fase.

1. Extraia o ZIP na raiz do repositório.
2. Commit/push na `main`.
3. Aguarde:
   - GitHub Actions Android;
   - deploy Vercel do backend.
4. Confirme:
   - `Test parser`;
   - `Build debug APK`;
   - `Upload APK`;
   - Vercel sem erro de TypeScript.

## O que testar

### Online
1. Abra Histórico.
2. Teste 1 / 7 / 30 / 90 dias.
3. Filtre Boas / Atenção / Abaixo.
4. Filtre UberX / Comfort / Black / Electric.
5. Filtre Exclusivo / Radar.
6. Confira gráficos, categorias e jornadas.

### Offline
1. Abra o app com internet desligada.
2. Histórico deve continuar mostrando dados armazenados no aparelho.
3. O selo deve indicar `LOCAL`.

### Semântica
Os campos:
- “Valor observado”;
- “Lucro est. observado”;

são somatórios das **ofertas observadas**, não faturamento real.

O app continua sem afirmar que a corrida foi aceita, iniciada ou concluída.

## Motor

`parser_version` continua `sr-rotas-v0.5.4`.
