# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-10-02.7`
Roadmap mestre: `ROADMAP-CANONICO.md`
Roadmap de publicação: `ROADMAP-PLAYSTORE-1.0.md`

Base Android homologada: `0.33.6-field / versionCode 83`
Field validada mais recente: `0.33.8-field / versionCode 85`
HEAD Radar Contextual: `0.33.11-field / versionCode 88`

## O que é o vc88
Integração real do handoff Radar Contextual no shell atual do Sr. Rotas.

O código de fundação já existia, mas estava solto. O vc88 conecta:
- navegação Radar;
- CTA durante a corrida;
- resolução do destino pela oferta da corrida ativa;
- runtime contextual;
- assistente proativo;
- rollback legado;
- controles de homologação Field;
- testes backend e Android que impedem o módulo de voltar a ficar desconectado.

## Defaults seguros
Em instalação/upgrade, todas as flags começam/desempenham false até o tester ativá-las:
- uiEnabled=false;
- runtimeEnabled=false;
- assistantEnabled=false.

O app abre inicialmente com o RadarPanel027035 antigo.

## Backend R1
Homologado antes do APK:
- 4 POIs;
- 7 eventos ativos ligados;
- 4 created;
- 3 linked;
- 0 active_unlinked;
- 0 review_with_poi.

Não reaplicar as migrations Radar. Elas já existem no Supabase.

## Teste Field
Na aba Radar existe "Homologação Radar Contextual" somente em versão `-field`.

Ordem:
1. validar Radar antigo em R0;
2. abrir Prévia DEMO;
3. ativar `1 · UI` e testar uma corrida real;
4. ativar `2 · Runtime` somente após UI aprovada;
5. ativar `3 · Assistente` somente após runtime aprovado;
6. validar `Assistente DEMO` para regressão visual;
7. usar `Rollback` a qualquer sinal de regressão.

## Preservado
- Reader M1;
- Reader2 shadow;
- MediaProjection/OCR;
- HUD;
- roteamento Uber/99;
- Money;
- histórico;
- V7;
- Access Resolver em observe;
- RadarPanel027035.
