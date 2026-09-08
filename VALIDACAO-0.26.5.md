# Validação técnica do patch 0.26.5

## Base revisada
- Base do patch: `92a087424748665a865a529d5624a17295e104a0`.
- A base estava em `0.26.4-beta`, versionCode 51, com o Action #76 verde.
- Esta rodada sobe para `0.26.5-beta`, versionCode 52.

## Verificações executadas antes de empacotar
- 31 verificações estruturais passaram sem falha.
- Todos os PNGs do patch foram abertos/verificados e têm dimensões válidas.
- Todas as novas referências `R.drawable.sr0265_*` possuem recurso correspondente.
- Não existe TTF/OTF/WOFF no pacote.
- Não foi reintroduzido `AccessibilityService`.
- A normalização OCR 0.26.5 foi compilada isoladamente com Kotlin e executada com dois cenários:
  - repetição consecutiva do mesmo texto é removida;
  - o mesmo valor reaparecendo em cards separados é preservado.
- As regras puras do intervalo do Assistente (10/12/15 min e adaptação do cooldown legado) foram compiladas/executadas com stub de Context e passaram.
- O compilador Kotlin não apontou erro de sintaxe nos novos arquivos; a compilação Android completa não pode ser reproduzida neste ambiente por ausência do Android SDK/Gradle do projeto.

## Validação final obrigatória
O GitHub Action **Android CI + Field APK** continua sendo a prova final de integração contra toda a árvore Android. Após aplicar o ZIP, não distribuir o APK se qualquer etapa do Action ficar vermelha. O checklist de aparelho está em `TESTE-REAL-0.26.5.md`.

## Supabase
Nenhuma escrita, migration ou mudança de schema faz parte deste ZIP. A inspeção anterior mostrou odômetros existentes em `journey_vehicle_metrics`, incluindo registros parciais (somente inicial ou somente final), que agora passam a ter apresentação explícita no aplicativo.
