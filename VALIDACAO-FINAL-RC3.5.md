# Validação final de empacotamento — RC3.5

## Resultado estático

**PASS** — pacote validado antes da compactação final.

- `versionCode = 63`;
- `versionName = 0.27.0-rc3.5`;
- 26 caminhos obrigatórios de código Android/workflow presentes e conferidos contra `ARQUIVOS-ANDROID-OBRIGATORIOS-RC3.5.txt`;
- workflow `Android CI + Field APK` incluído;
- `push` para `main` sem filtro `paths:`;
- `android-actions/setup-android@v4` com instalação explícita do SDK 36;
- AndroidManifest preserva permissões/services/receivers atuais e adiciona somente as Activities RC3.5;
- nenhuma permissão ampla de galeria/storage adicionada;
- código Android que não chegou ao GitHub no upload anterior da RC3.4 reincorporado;
- `SRROTAS-PLANO-MESTRE.md` atualizado para RC3.5 / versionCode 63;
- nenhum `OfferParser.kt`, `UberSpatialParser0221.kt`, `OfferDeduplicator.kt`, `MediaProjectionOcrService.kt`, `OfferIntegrityGate027033.kt`, `ShadowOfferRecovery027033.kt` ou `ReaderAutoFailure027033.kt` dentro do pacote;
- nenhum backend, migration ou SQL nesta entrega;
- nenhuma função de auto-accept, auto-reject ou auto-touch adicionada;
- checagem sintática Kotlin sem erro de parsing;
- nova página usa o título `Histórico de ofertas`, evitando a remarcação legada de `Histórico` para `Estatísticas`;
- cabeçalhos reservam espaço para a engrenagem de Configurações em telas estreitas.

## Validação ainda dependente do GitHub Actions

A compilação Android integral precisa ser confirmada pelo workflow automático após o upload:

```text
Unit tests
assembleDebug
assembleRelease
Verify release certificate
Upload artifact(s)
```

O commit não deve ser considerado pronto para instalação em campo antes desse Action ficar verde.
