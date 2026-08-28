# Sr. Rotas 0.23.1-beta — UI Stabilization

Base: commit dcba01bdcff3bc2cdb6b7a37209bc2b5b0d945c3 (0.23.0-beta + BUILDFIX1 + BUILDFIX2).

## Objetivo
Transformar a UI Freeze em uma única identidade visual, preservando a arquitetura e o motor já validados.

## O que muda
- versionCode 37 / versionName 0.23.1-beta.
- UiKit volta a ser a fonte única dos fundos/surfaces/textos do Android.
- SrUi023 passa a ser somente uma camada de componentes sobre o UiKit.
- Claro: #F8F4DF / #FFFDF6 / #073746.
- Escuro: #05262F / #073746 / #F8F4DF.
- Cabeçalho nativo único com o logo existente do Sr. Rotas em Agora, Histórico, IA e Configurações.
- Agora/IA/Configurações responsivos e sem emojis usados como ícones de interface.
- HUD e janela flutuante passam a consumir UiKit, sem paletas paralelas.
- Web passa a ser Central do Usuário: Perfil, Plano/Créditos, Mensagens rápidas e Segurança MCP.
- /app, /app/agora, /app/historico, /app/ia e /app/configuracoes redirecionam para /app/perfil.
- CSS 0.23 usa os tokens oficiais --sr-* já existentes; não cria outra identidade de fundo.

## O que NÃO muda
- OCR, parser Uber/99/outros, MediaProjection, dedupe, cálculos, jornada e RegionalClient.
- Ícone launcher/mipmap/adaptive icon.
- SQL / schema / migration.
- API de mensagens rápidas.

## Aplicação
Suba todo o conteúdo deste ZIP por cima do repositório atual pelo GitHub. Não existe SQL novo.
Depois rode GitHub Action e Vercel. Em seguida gere o APK 0.23.1-beta e valide Claro/Escuro/Automático.
