# Sr. Rotas 2.0 — Alpha 0.4

Sr. Rotas é um copiloto Android para motoristas de aplicativo. O núcleo usa MediaProjection autorizado pelo usuário, OCR local com ML Kit, parser contextual de ofertas e HUD configurável de rentabilidade. O backend Next.js/Supabase mantém histórico estruturado, Pesquisa IA e MCP somente leitura.

**Desenvolvido pela BigCorps** — contato@bigcorps.com.br

## Identidade

- Marca: **Sr. Rotas**
- Domínio definitivo previsto: **srrotas.com**
- URL Alpha: `https://sr-rotas.vercel.app`
- Android: `com.srrotas.app`
- TWA complementar: `com.srrotas.web`

## Destaques do 0.4

- parser recalibrado com amostras reais do Uber Brasil;
- filtro de contexto para rejeitar `Registro de viagens`, ganhos, mapa e outros falsos positivos;
- preço principal separado de `R$/km aprox.` e de adicionais `+R$ ... incluído` do Priority;
- detecção de `Exclusivo` x `Radar de Viagens` por âncoras reais da interface;
- leitura de avaliação do passageiro e tipo de serviço;
- métricas R$/min, lucro/hora e margem de lucro;
- nova tela **Estratégia e HUD**, inspirada no conceito Cherry Picker;
- faixas vermelho/amarelo/verde configuráveis;
- presets Equilibrado, Conservador e Volume, sempre editáveis;
- escolha e ordenação das métricas do HUD;
- posição esquerda/centro/direita, tema, opacidade, fonte e modo para daltonismo;
- preview do HUD;
- notificações de texto e voz opcionais;
- captura automática privada opcional, desligada por padrão, sem galeria/nuvem;
- mensagem padrão para passageiro apenas para copiar ao clipboard, sem envio automático;
- testes unitários com fixtures derivadas dos testes reais do Alpha;
- rodapés institucionais BigCorps.

## Regra de interpretação

O sistema registra **ofertas observadas**. Nada no Alpha infere automaticamente que uma oferta foi aceita, iniciada, concluída ou paga.

Veja `APLICAR-0.4-ALPHA.md` antes de aplicar.
