# Sr.Rotas 0.25.0-beta — pacote final consolidado

Este ZIP é a consolidação das quatro rodadas 0.25.0. Ele **não contém os ZIPs anteriores dentro dele**: contém diretamente a versão final e deduplicada de cada arquivo nos caminhos corretos do repositório.

## Estado da versão

- `versionName`: **0.25.0-beta**
- `versionCode`: **46**
- base usada para as quatro rodadas: `0ce5f984e5bf8082f11fc36eb9982f811e286fa9`
- arquivos de produto consolidados: **23**

## O que está consolidado

1. **ZIP 01 — Parser/Radar**: duração com horas, cálculos derivados e Radar tolerante a OCR parcial sem perder isolamento dos cards.
2. **ZIP 02 — Jornada/OCR**: watchdog e recuperação segura do pipeline de captura/OCR durante jornadas longas.
3. **ZIP 03 — Inteligência**: fallback coletivo progressivo com mínimo de 3 participantes + “Nova corrida no destino” no HUD/configurações.
4. **ZIP 04 — Acabamento**: identidade coletiva, borda do HUD pela média visual e bump para 0.25.0-beta.

## Como aplicar

- Pode usar **este ZIP final sozinho** para substituir/adicionar os arquivos no GitHub.
- Se os ZIPs 01–04 já estiverem aplicados, este ZIP apenas reafirma o estado final esperado.
- Não inclua os manifests/checksums como substitutos de código: eles são documentação de auditoria.

## Supabase

Execute **uma única vez**, caso ainda não tenha executado:

`SQL-EXECUTAR-0.25.0-ZIP-03.sql`

Esse arquivo é byte a byte idêntico a `supabase/migrations/20260902_collective_fallback_025.sql`. Não execute os dois separadamente.

## APK

Depois do upload deste pacote e do SQL do ZIP 03, este é o estado preparado para gerar o único APK **0.25.0-beta** para o teste completo. Use `TESTE-REAL-0.25.0.md` como roteiro.

## Auditoria

- `SRROTAS-0.25.0-FINAL-MANIFEST.json`: origem, rodada vencedora, tamanho e SHA-256 de cada arquivo.
- `MANIFEST-SHA256-FINAL.txt`: hashes do pacote consolidado.
- Os `PATCH-MANIFEST-...` e `README-APLICAR-ZIP-...` das quatro rodadas foram preservados para não perder o histórico das decisões.
