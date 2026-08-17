# Sr. Rotas 2.0 — Alpha 0.5.1

Sr. Rotas é um copiloto Android para motoristas de aplicativo. O núcleo usa MediaProjection autorizado pelo usuário, OCR local com ML Kit, parser contextual de ofertas e HUD configurável de rentabilidade. O backend Next.js/Supabase mantém histórico estruturado, Pesquisa IA e MCP somente leitura.

**Desenvolvido pela BigCorps** — contato@bigcorps.com.br

## Identidade e arquitetura

- Marca: **Sr. Rotas**
- Domínio definitivo previsto: **srrotas.com**
- URL Alpha: `https://sr-rotas.vercel.app`
- Android principal: `com.srrotas.app`
- Play Store: **um único app Android**. OCR, MediaProjection, HUD e serviços ficam no Kotlin; o dashboard Next.js será acessível dentro do mesmo app e também pelo navegador.
- A antiga estrutura TWA `com.srrotas.web` permanece apenas opcional e não é planejada como segundo app da Play Store.

## Alpha 0.5.1

- amostragem visual mais rápida, com um frame pendente para não perder mudanças enquanto o OCR está ocupado;
- detector de mudança mais sensível a alterações localizadas no card;
- rejeição de leitura parcial quando o R$/km calculado diverge do R$/km informado pelo Uber;
- dedupe sem depender de avaliação, categoria ou R$/km anunciado;
- filtro de overlays externos compactos com métricas `/km`, `/hr` e `/min`;
- HUD priorizado antes da persistência do lote Radar;
- menos gravações de diagnóstico e menos ruído de duplicatas no log;
- Acessibilidade não compete com MediaProjection durante a jornada;
- parser identificado como `sr-rotas-v0.5.1`.

## Regra de interpretação

O sistema registra **ofertas observadas**. Nada no Alpha infere automaticamente que uma oferta foi aceita, iniciada, concluída ou paga.

Veja `APLICAR-0.5.1-ALPHA.md` antes de aplicar.
