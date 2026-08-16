# Validação do patch Sr. Rotas 2.0 Alpha

## Validado no pacote

- XML do Android bem-formado;
- JSON do PWA/TWA válido;
- estrutura de assets e tamanhos dos ícones;
- parser Kotlin isolado com exemplos simulados;
- arquivos TypeScript/TSX passam por parsing/transpilação de sintaxe;
- migration incremental separada da migration inicial;
- patch preserva a estrutura do primeiro ZIP e pode ser extraído por cima.

## Validação que depende do seu ambiente Android

O build final precisa do Android SDK/API 36 e das dependências Gradle/Maven. Execute:

```bash
cd android
./gradlew :app:assembleDebug
```

O teste decisivo é no aparelho real: a captura de tela autorizada pelo Android e o layout atual do Uber Driver podem variar por versão/aparelho.

## Observação Gradle

O primeiro ZIP já contém scripts `gradlew`/`gradlew.bat` que baixam `gradle-8.13-wrapper.jar` se ele ainda não existir. O download acontece na primeira execução com internet.
