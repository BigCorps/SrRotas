# QA — Sr. Rotas 0.27.0-RC3

## Base conferida
- GitHub main: `1fcf02756c8427b714dc2353761302745824801a`
- Base: `0.27.0-rc2-diag1`, versionCode 57
- Patch: `0.27.0-rc3`, versionCode 58

## Validações locais concluídas
- AndroidManifest.xml: XML válido.
- `sr27_ic_bug.xml`: vector XML válido.
- Manifest JSON do patch: JSON válido.
- SHA256 interno: todos os arquivos conferidos.
- Kotlin parser `DriverPlatformOfferRouter.kt`: compilado isoladamente com stubs das dependências do projeto, sem erro de sintaxe/tipo no arquivo.
- Teste funcional executável do parser 99:
  - fixture clássico: PASS;
  - tempo/distância separados em linhas: PASS;
  - 99Plus + R$/km com `Escolher` perdido pelo OCR em tela com navegação: PASS;
  - tela genérica/Maps com números semelhantes sem âncora 99: REJEITADA (PASS).
- Demais Kotlin alterados: verificação do parser Kotlin sem erros sintáticos (`expecting`, tokens inesperados, comentário não fechado); referências Android/projeto ficam irresolvidas fora do classpath completo, como esperado.

## Guardrails conferidos
Não foram incluídos no patch:
- `OfferParser.kt`;
- `UberSpatialParser0221.kt`;
- `FrameChangeDetector.kt`;
- `MediaProjectionOcrService.kt`.

Também não houve:
- mudança de sampling do frame;
- mudança do limite de resolução OCR;
- mudança de ML Kit;
- SQL/Supabase;
- backend/Vercel;
- escrita remota no GitHub.

## Validação ainda obrigatória
O build Android completo não foi executado localmente porque a entrega é somente o overlay do patch, sem checkout Gradle completo/dependências do repositório no ambiente atual.

Depois do upload, o workflow **Android CI + Field APK** deve validar compilação, testes e APK release. Só liberar a RC3 para campo se o workflow ficar verde.
