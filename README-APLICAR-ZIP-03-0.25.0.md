# Sr.Rotas 0.25.0 — ZIP 03/04 — Inteligência

Base analisada: `0ce5f984e5bf8082f11fc36eb9982f811e286fa9`.
Este pacote é incremental e deve ser aplicado por cima dos ZIPs 01 e 02 da rodada 0.25.0.

## Objetivos desta rodada

1. Corrigir a Base Coletiva vazia sem reduzir o mínimo de privacidade.
2. Levar **Nova corrida no destino** diretamente ao HUD.
3. Permitir ligar/desligar esse sinal nas Configurações.
4. Preservar Busca, Destino, parser, OCR, dedupe, julgamento financeiro e fluxo de jornada já validados.

## Base Coletiva

A view coletiva anterior só publica uma linha quando existem pelo menos 3 motoristas distintos dentro da combinação exata de região + dia da semana + faixa de 3 horas + perfil. Com a base atual isso pode zerar a resposta mesmo quando a região já tem 3 participantes no histórico agregado.

O ZIP adiciona três views de fallback, todas com `HAVING count(distinct driver_id) >= 3`:

- mesma faixa de 3h em outros dias;
- região + perfil em todo o histórico;
- região agregada em todo o histórico.

O backend tenta, em ordem:

`exato -> mesma faixa de 3h em outros dias -> região+perfil -> região agregada`.

Se nenhuma camada tiver 3 participantes, a Base Coletiva continua vazia. Não existe fallback silencioso para dados pessoais nem redução para 1 ou 2 motoristas.

A consulta de validação em produção mostrou que já existem agregações seguras em regiões como Itaim Bibi, Perdizes e Vila Mariana quando a janela é ampliada. A combinação `Perdizes + 15h + Comfort`, por exemplo, já possui 3 participantes no recorte de mesma faixa em dias diferentes.

## Nova corrida no destino no HUD

O cálculo já existente continua assíncrono e fora do caminho quente do OCR. A 0.25.0 apenas passa a compor o resultado no HUD quando ele chega.

- padrão: **ligado**;
- Configurações ganha um card dedicado `Nova corrida no destino`;
- o HUD mostra, por exemplo, `72% · Alta`, `Média`, `Baixa` ou `Dados insuf.`;
- o fingerprint do Overlay agora inclui a continuidade, então o HUD se redesenha quando a resposta assíncrona chega;
- essa informação **não altera o veredito financeiro** da corrida. Ela é um sinal histórico complementar e não uma garantia de nova corrida.

## Ordem de aplicação

1. Extraia este ZIP sobre o repositório que já recebeu os ZIPs 01 e 02.
2. Suba os arquivos ao GitHub preservando os caminhos.
3. Execute no Supabase SQL Editor o arquivo `SQL-EXECUTAR-0.25.0-ZIP-03.sql`.
4. Não gere APK ainda. O APK único será gerado depois do ZIP 04.

O backend foi preparado para não dar erro caso a Vercel publique antes do SQL: enquanto as novas views ainda não existirem, ele mantém o comportamento anterior e marca internamente o fallback como indisponível.

## Arquivos de produção alterados/adicionados

- `backend/app/api/v1/intelligence/now/route.ts`
- `backend/src/collective-fallback-025.ts`
- `supabase/migrations/20260902_collective_fallback_025.sql`
- `android/app/src/main/java/com/bigcorps/driveraimvp/DestinationContinuityHud025.kt`
- `android/app/src/main/java/com/bigcorps/driveraimvp/DestinationContinuityHudRules025.kt`
- `android/app/src/main/java/com/bigcorps/driveraimvp/Hud025Renderer.kt`
- `android/app/src/main/java/com/bigcorps/driveraimvp/OverlayController.kt`
- `android/app/src/main/java/com/bigcorps/driveraimvp/SettingsHub023.kt`

## Validações feitas nesta preparação

- TypeScript do novo fallback coletivo: OK.
- TypeScript da rota + fallback com stubs equivalentes: OK.
- regras Kotlin puras de apresentação do sinal de destino: OK.
- consultas somente-leitura no Supabase confirmaram grupos com 3 participantes em agregações mais amplas.
- nenhum comando DDL/DML foi executado remotamente no Supabase.
- nenhum arquivo foi alterado remotamente no GitHub/Vercel.

## Não alterado nesta rodada

- `versionCode` / `versionName`;
- parser e Radar do ZIP 01;
- watchdog/Jornada/OCR do ZIP 02;
- botões Busca e Destino;
- fórmulas financeiras;
- classificação ponderada do HUD;
- identidade visual final da Base Coletiva;
- borda final do HUD.

Esses dois últimos itens ficam no ZIP 04/04, junto do fechamento visual e bump para `0.25.0-beta`.
