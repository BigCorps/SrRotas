# Aplicar — Sr. Rotas 0.5.2 Alpha (Engine Freeze Candidate)

Patch incremental sobre a 0.5.1 já presente na `main`.

## O que esta versão muda

- `versionCode = 7`
- `versionName = 0.5.2-alpha`
- `parser_version = sr-rotas-v0.5.2`
- adiciona telemetria local e anônima de desempenho do pipeline OCR;
- não altera fórmula, estratégia, threshold, dedupe ou lógica de card da 0.5.1;
- inclui o roadmap oficial até a Play Store;
- documenta a decisão Pix + créditos de IA;
- preserva a arte original escolhida como fonte oficial para a fase 0.6.

## Aplicação

1. Extrair este ZIP na raiz do repositório.
2. Substituir os arquivos existentes.
3. Manter os novos arquivos.
4. Commit/push na `main`.
5. Aguardar `Android Debug APK`.
6. Confirmar:
   - `Test parser = success`
   - `Build debug APK = success`
   - `Upload APK = success`

## No teste do irmão

Fazer uma jornada real semelhante às anteriores.

Ao encerrar, no fim do LOG LOCAL deve existir uma linha parecida com:

`DESEMPENHO OCR · amostras=... · inalterados=... · ocr=... · fila=... · substituídos=... · frames_com_oferta=... · ofertas_detectadas=... · ocr_médio=...ms · ocr_máx=...ms`

Enviar:
- essa linha;
- o resumo da jornada;
- qualquer oferta perdida percebida;
- qualquer valor que pareça incorreto.

Se a rodada vier limpa, congelamos o Offer Engine v1 e começamos a 0.6.

## Não há

- SQL;
- migration Supabase;
- variável Vercel;
- mudança de package;
- OneSignal;
- cobrança;
- mudança visual do app nesta versão.
