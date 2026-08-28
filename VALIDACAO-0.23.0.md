# Validação local — 0.23.0-beta

Executado antes da geração do pacote final:

- XML parse: 50 arquivos XML de recursos válidos.
- `Hud023Spec`: compilação Kotlin isolada + runner: `HUD023 SPEC OK`.
- `MessageShortcutRules023`: compilação Kotlin isolada + runner: `MESSAGE023 RULES OK`.
- TypeScript/TSX: verificação de parser com `tsc`; nenhum erro sintático TS1xxx nos arquivos modificados.
- Kotlin Android: varredura de parser via `kotlinc`; nenhum erro `expecting`, `unexpected` ou `illegal escape`. Referências Android ficam sem resolução neste ambiente por ausência do Android SDK.
- Verificação de segurança visual: nenhum caminho `mipmap` ou `ic_launcher` está presente no pacote.
- Verificação de versão: `0.23.0-beta`, `versionCode 36`.
- Verificação de endereço: removido `value.take(110)` do controlador da janela flutuante.

Limitação: não foi possível executar `./gradlew` localmente por ausência de `ANDROID_HOME`/Android SDK. O GitHub Actions é a validação de compilação completa.
