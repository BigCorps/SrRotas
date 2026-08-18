# Aplicar — Sr. Rotas 0.13.1 Beta — correção de UI

Base esperada: `5dc41403774929a39129561251bd724ffa85252d`.

## Correções
- respeita barra de status/notificações, recortes e barra de navegação no Android 15/16;
- corrige o conteúdo que estava aparecendo sob relógio/notificações;
- aplica a mesma safe area em MainActivity, Onboarding e Estratégia/HUD;
- troca o bloco "SR" do onboarding pelo logo oficial `logo_srrotas` já existente;
- corrige `\n` que estavam aparecendo literalmente nos textos;
- atualiza o beta para `0.13.1-beta` / versionCode 18.

## Aplicação
Extraia o ZIP na raiz do repositório, substitua os arquivos e faça commit/push.

Não há SQL, Edge Function ou alteração no Offer Engine.

Depois do GitHub Actions verde, gere/distribua o novo APK aos testadores.
