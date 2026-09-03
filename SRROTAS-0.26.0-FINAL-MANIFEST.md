# Sr. Rotas 0.26.0-beta — manifesto final

- Base: 0.25.0 final testada
- Merge: ZIP 01 → ZIP 08, etapa mais recente vence
- versionCode: 47
- versionName: 0.26.0-beta
- Alterações remotas executadas durante a montagem: não

## Sobreposições resolvidas
- `android/app/src/main/java/com/bigcorps/driveraimvp/HistoryPanel.kt`: ZIPs 02, 03, 04, 05, 07 → vencedor ZIP 07
- `android/app/src/main/java/com/bigcorps/driveraimvp/Hud025Renderer.kt`: ZIPs 02, 08 → vencedor ZIP 08
- `android/app/src/main/java/com/bigcorps/driveraimvp/JourneyFlow026.kt`: ZIPs 05, 06 → vencedor ZIP 06
- `android/app/src/main/java/com/bigcorps/driveraimvp/SettingsHub023.kt`: ZIPs 02, 06 → vencedor ZIP 06
- `android/app/src/main/java/com/bigcorps/driveraimvp/SrUi023.kt`: ZIPs 01, 02, 05, 08 → vencedor ZIP 08

## SQL manual
- `SQL-EXECUTAR-0.26.0-ZIP-04.sql`
- `SQL-EXECUTAR-0.26.0-ZIP-07.sql`
- `SQL-EXECUTAR-0.26.0-ZIP-08.sql`

## Observação de validação
Os arquivos vencedores foram preservados byte a byte a partir dos ZIPs de estágio já validados. A auditoria final verificou versão, SQL/migrations, assinaturas cumulativas, ausência de conflitos e integridade do arquivo. O clone integral adicional do GitHub não pôde ser realizado no ambiente final por indisponibilidade de resolução de rede; execute o build Android/Vercel após o upload.
