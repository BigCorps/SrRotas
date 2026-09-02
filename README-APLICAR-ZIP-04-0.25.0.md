# Sr. Rotas 0.25.0 — ZIP 04/04 — Acabamento + candidato de teste

Aplique este ZIP **depois** dos ZIPs 01, 02 e 03.

Este é o último patch individual da rodada 0.25.0. Depois dele já faz sentido gerar o único APK de teste desta versão.

## O que muda

### 1. Identidade da Base Coletiva
- mantém exatamente a família de cores/gradiente coletivo já aprovada no Histórico;
- a moldura coletiva passa a ter no máximo **2dp**, inclusive em callers antigos que ainda pediam 4dp;
- o mesmo espectro passa a identificar o botão **Base coletiva** no Agora;
- no tema claro, o texto desse botão usa tom escuro; no tema escuro, usa a cor de texto do tema para preservar contraste;
- os demais segmentos continuam com o visual anterior.

### 2. Borda do HUD
- a borda não usa mais o `offer.verdict` como atalho visual;
- ela calcula a **média ponderada das métricas ativas** pelo mesmo `HudMetricEvaluation0221` já validado;
- verde = média boa; amarelo = média regular; vermelho = média ruim;
- cores da borda ficaram mais saturadas para leitura rápida;
- espessura: **2dp no compacto** e **3dp no normal/grande**;
- o veredito textual continua pertencendo ao motor e pode continuar respeitando travas absolutas, como tarifa mínima;
- as cores internas de cada métrica não foram alteradas.

### 3. Continuidade no destino preservada
`Hud025Renderer.kt` parte da versão exata criada no ZIP 03. O card **Nova corrida no destino** continua presente e continua sem participar da média financeira.

### 4. Versão
- `versionCode = 46`
- `versionName = "0.25.0-beta"`

## O que NÃO muda

Este ZIP não altera:
- parser de duração;
- Radar;
- OCR/MediaProjection;
- dedupe/estabilização;
- cálculos financeiros;
- Base Coletiva do backend/migration do ZIP 03;
- Busca;
- destino/geocoding;
- Supabase;
- Vercel.

## Aplicação

1. Confirme que os ZIPs 01, 02 e 03 já foram aplicados na árvore do repositório.
2. Extraia este ZIP na raiz do repositório, preservando os caminhos.
3. Suba os arquivos substituídos/novos para o GitHub.
4. Gere o APK após o build ficar verde.
5. Instale esse APK no aparelho de teste e siga `TESTE-REAL-0.25.0.md`.

Não há SQL neste ZIP. O único SQL da rodada continua sendo o do ZIP 03.
