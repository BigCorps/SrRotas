# Sr. Rotas 2.0 — Alpha 0.5.3

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

## Alpha 0.5.3

Última candidata de motor antes da fase 0.6.

Principais mudanças:
- dois frames pendentes protegidos contra perda;
- OCR moderadamente redimensionado em telas muito grandes;
- correção `1l/ll -> 11` em tempo/distância;
- categoria Electric;
- bloqueio de screenshot de oferta em outra interface;
- dedupe tolerante a geometria parcial por 2,5 s;
- ícone e logo interno com a arte oficial.

Versão:
- `versionCode = 8`
- `versionName = 0.5.3-alpha`
- `parser_version = sr-rotas-v0.5.3`

## Próximo marco

Se o teste de campo vier limpo, declarar **Offer Engine v1 congelado** e iniciar a **0.6 — Design System, personalização, cards e identidade visual**.

## Regra de interpretação

O sistema registra **ofertas observadas**. Nada no Alpha infere automaticamente que uma oferta foi aceita, iniciada, concluída ou paga.
