# Validação executada antes de empacotar

## Passou

- XML Android parseado sem erros (`AndroidManifest.xml`, `accessibility_service_config.xml`, strings e tema).
- `gradlew` validado sintaticamente pelo shell.
- Parser Kotlin principal compilado isoladamente com `kotlinc` e executado com uma oferta simulada.
- Caso simulado: `R$ 32,50`, 2 km até embarque, 10 km de viagem e 23 min totais.
  - total: 12 km
  - R$/km: 2,71
  - R$/hora: 84,78
  - custo estimado com R$ 0,85/km: R$ 10,20
  - lucro estimado: R$ 22,30
- Arquivos TypeScript foram analisados pelo compilador global; não apareceram erros de sintaxe. A checagem completa de tipos exige `npm install` porque as dependências não estão disponíveis neste ambiente.
- Nenhuma chave, token, domínio final ou credencial real foi incluída.

## Não foi possível executar neste ambiente

- `./gradlew :app:assembleDebug`: este ambiente não possui Android SDK e não possui acesso Maven/Gradle à internet.
- `npm install && npm run build`: este ambiente não possui acesso ao registro npm.

O ZIP inclui as versões e instruções necessárias para executar esses dois builds no VS Code/Codespace com internet e Android SDK.
