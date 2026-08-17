# Sr. Rotas 2.0 — Alpha 0.5.2

Sr. Rotas é um copiloto Android para motoristas de aplicativo. O núcleo usa MediaProjection autorizado pelo usuário, OCR local com ML Kit, parser contextual de ofertas e HUD configurável de rentabilidade. O backend Next.js/Supabase mantém histórico estruturado, Pesquisa IA e MCP somente leitura.

**Desenvolvido pela BigCorps** — contato@bigcorps.com.br

## Identidade e arquitetura

- Marca: **Sr. Rotas**
- Domínio definitivo previsto: **srrotas.com**
- URL Alpha: `https://sr-rotas.vercel.app`
- Android principal: `com.srrotas.app`
- Play Store: **um único app Android**
- Kotlin: captura, OCR, parser, HUD e funções críticas/offline
- Next.js: dashboard, histórico, analytics, IA, MCP, conta e assinatura
- TWA `com.srrotas.web`: opcional, sem segundo app na Play Store

## Alpha 0.5.2

A 0.5.2 é o **Engine Freeze Candidate**.

Ela mantém a calibração da 0.5.1 e adiciona métricas locais de desempenho para a rodada final antes da fase visual.

Versão:
- `versionCode = 7`
- `versionName = 0.5.2-alpha`
- `parser_version = sr-rotas-v0.5.2`

## Próximos documentos

- `ROADMAP-PLAYSTORE-1.0.md`
- `MONETIZACAO-PIX-CREDITOS.md`
- `APLICAR-0.5.2-ENGINE-FREEZE.md`

## Regra de interpretação

O sistema registra **ofertas observadas**. Nada no Alpha infere automaticamente que uma oferta foi aceita, iniciada, concluída ou paga.
