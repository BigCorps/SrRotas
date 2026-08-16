# Sr. Rotas 2.0 — Alpha 0.3

Sr. Rotas é um copiloto Android para motoristas de aplicativo. O núcleo atual usa MediaProjection autorizado pelo usuário, OCR local com ML Kit, parsing de ofertas e HUD de rentabilidade. O backend Next.js/Supabase mantém histórico estruturado, Pesquisa IA e MCP somente leitura.

## Identidade

- Marca: **Sr. Rotas**
- Domínio definitivo previsto: **srrotas.com**
- URL Alpha: `https://sr-rotas.vercel.app`
- Android: `com.srrotas.app`
- TWA complementar: `com.srrotas.web`

## O que entra no 0.3

- persistência local SQLite antes da sincronização;
- jornadas locais e no Supabase;
- fila de ofertas pendentes quando não houver internet;
- compartilhamento explícito de diagnóstico textual;
- sincronização de estratégia com o backend;
- IA com contexto de estratégia/jornada;
- MCP ampliado para estratégia e jornadas;
- páginas de privacidade, termos, suporte e exclusão;
- headers de segurança no padrão usado no MonitorIA;
- rota `.well-known/assetlinks.json` segura para TWA futura;
- CI separado para backend e Dependabot;
- hardening e índices no Supabase.

## Regra de interpretação

O sistema registra **ofertas observadas**. Nada no Alpha deve inferir automaticamente que uma oferta foi aceita, iniciada, concluída ou paga.

## Parser

O `SpatialOfferParser` não é alterado neste patch. A calibração fina ficará para depois dos primeiros testes reais do Uber, usando os diagnósticos coletados.

Veja `APLICAR-0.3-ALPHA.md` antes de aplicar.
