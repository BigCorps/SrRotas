# QA 0.33.0 Field — Release Prep Pack 1

O motorista não precisa anotar eventos durante a condução. Uso normal + bug report geral + JSON ao final continuam sendo o QA principal.

## Verificações visuais simples

- celular e tablet: cards devem usar melhor a largura, sem o corredor lateral excessivo anterior;
- modo escuro: textos e campos principais devem continuar legíveis;
- controle de jornada deve usar status compacto `OK ✓ — ...`;
- se a captura cair, `Retomar captura` continua disponível e a jornada permanece aberta.

## Screenshots

Não é necessário contar arquivos durante a jornada. O JSON deve incluir `screenshot_storage_033` com:

- `attempts`;
- `accepted`;
- `duplicate_skipped`;
- `private_files <= 30`;
- `visible_files <= 180`;
- `jpeg_quality = 72`;
- `crop_preserved = true`.

Esperado: em uma jornada com cards estáveis, `duplicate_skipped` deve subir e `accepted` ficar muito abaixo do número bruto de frames que repetem a mesma oferta.

## Reader 2

Continuar exportando `reader2_consensus_0321`. O 0.33 não muda sua regra e não exige teste separado.
