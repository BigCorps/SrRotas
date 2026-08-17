# Aplicar — Sr. Rotas 0.5.3 Alpha

Patch incremental sobre a `main` 0.5.2.

## O que muda

- `versionCode = 8`
- `versionName = 0.5.3-alpha`
- `parser_version = sr-rotas-v0.5.3`
- fila OCR com dois slots: primeiro frame novo + frame mais recente;
- redução moderada do bitmap antes do ML Kit quando a tela passa de 2100 px no maior lado;
- corrige OCR `1l minutos` / `ll minutos`;
- reconhece categoria `Electric`;
- bloqueia prints de ofertas vistos em Recentes/WhatsApp/Galeria por contexto de interface;
- dedupe curto tolerante a frame completo -> parcial;
- aplica a arte oficial no launcher e no logo interno do Alpha.

## Aplicação

1. Extrair este ZIP na raiz do repositório.
2. Substituir os arquivos existentes.
3. Commit/push na `main`.
4. Aguardar o workflow `Android Debug APK`.
5. Confirmar:
   - `Test parser = success`
   - `Build debug APK = success`
   - `Upload APK = success`

## Teste de campo

Enviar depois:
- linha `DESEMPENHO OCR`;
- resumo da jornada;
- quantidade de corridas que ele percebeu perder;
- qualquer leitura estranha.

Também confirmar visualmente:
- novo ícone no launcher;
- novo personagem no topo do app.

## Sem mudanças nesta entrega

- SQL / migration;
- Supabase;
- Vercel;
- cobrança;
- OneSignal;
- novo layout do HUD.

A reformulação visual do HUD/cards continua reservada para a 0.6.
