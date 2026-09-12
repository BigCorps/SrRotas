# QA — Sr. Rotas 0.27.0 RC3.1

## Evidência que motivou a mudança
No diagnóstico RC3 recebido:

- versão: `0.27.0-rc3` / code 58;
- captura ativa em 2880×1800, OCR 2100×1312;
- OCR sem falhas técnicas (`ocr_failures=0`);
- sessão anterior terminou com `last_platform=99` e `last_route_reason=candidato 99 aguardando geometria completa`;
- no trace recente havia frames com `fare_lines=1`, `clusters=1`, `geometry_pairs=2`, mas `99_anchor=false` e classificação `contexto desconhecido`;
- `manual_failure_marks=0` no diagnóstico atual.

Conclusão: gargalo principal deste erro = identidade/classificação da 99 antes do parser flexível, não falha geral de OCR.

## QA local executado
- compilação Kotlin isolada de `DriverPlatformOfferRouter.kt` com stubs mínimos: PASS;
- smoke test parser clássico 99: PASS;
- smoke test 99 com tempo/distância separados: PASS;
- smoke test 99 por painel confiável sem token de identidade no frame: PASS;
- smoke test de proteção: painel confiável com âncora Uber explícita é rejeitado como 99: PASS.

## Validação definitiva
O build Android completo deve ser validado pelo GitHub Actions após o upload, como nas versões anteriores.
