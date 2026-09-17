# LEIA PRIMEIRO — RC3.5

Este ZIP é consolidado. Ele contém **código Android que não chegou ao GitHub no upload anterior da RC3.4**.

## Upload
1. Extraia o ZIP.
2. Abra a pasta extraída.
3. Envie **todo o conteúdo interno** para a raiz do repositório `BigCorps/SrRotas`, preservando `.github/` e `android/`.
4. Substitua os arquivos existentes e adicione os novos. Não apague os demais arquivos do repositório.
5. Commit sugerido: `Sr. Rotas 0.27.0 RC3.5 - reorganizacao estrutural e UX`.

## MUITO IMPORTANTE
Depois do upload, o commit deve conter todos os caminhos de `ARQUIVOS-ANDROID-OBRIGATORIOS-RC3.5.txt`.

A auditoria do pacote exige **26 caminhos obrigatórios de código/workflow**. Confira o commit contra `ARQUIVOS-ANDROID-OBRIGATORIOS-RC3.5.txt` antes de instalar o APK.

O Action deve iniciar **automaticamente**; não use Run workflow de imediato.

## Banco/backend
Não há SQL nem alteração de backend nesta entrega.

## Reader
Não foram incluídos OfferParser, UberSpatialParser, OfferDeduplicator, MediaProjectionOcrService ou os componentes RC3.3 de shadow/integrity. A coleta continua sendo investigada separadamente por diagnóstico.
