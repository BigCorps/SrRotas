# Sr. Rotas 0.27.0-RC2-DIAG1 — Teste de campo

## Finalidade

Esta versão não tenta corrigir o leitor. Ela serve para identificar exatamente em qual etapa uma oferta visível deixa de chegar ao HUD.

## Antes de sair

1. Confirmar versão instalada: `0.27.0-rc2-diag1`.
2. Iniciar jornada normalmente.
3. Autorizar a captura normalmente.
4. Usar o Sr. Rotas como no uso real, sem ficar abrindo telas de diagnóstico enquanto tudo estiver funcionando.

## Quando uma oferta aparecer e o HUD aparecer corretamente

Não fazer nada especial. Continuar a jornada.

## Quando uma oferta estiver claramente visível na Uber/99 e o HUD NÃO aparecer

1. Não encerrar a jornada.
2. Assim que perceber a falha, abrir a notificação do Sr. Rotas e tocar **Registrar falha**.
3. Tentar fazer isso o mais próximo possível do instante da falha, idealmente em poucos segundos.
4. **Não tocar em Reiniciar leitura antes de exportar o diagnóstico dessa falha.**
5. Esperar cerca de 12 a 15 segundos para o trace registrar também o que aconteceu logo depois.
6. Abrir Sr. Rotas → Configurações → Diagnóstico de leitura → Compartilhar diagnóstico.
7. Enviar o JSON completo.
8. Junto com o JSON, informar em uma linha:
   - Uber ou 99;
   - Exclusive ou Radar, se souber;
   - se o card ficou visível por vários segundos ou apareceu rapidamente;
   - se alguma parte do HUD chegou a piscar/aparecer.
9. Depois de salvar/enviar o diagnóstico, pode usar **Reiniciar leitura** se for necessário.

## Quantidade ideal

Precisamos de **3 falhas reais marcadas**, cada uma com seu diagnóstico exportado logo depois.

Se possível, variar os casos:
- uma oferta Exclusive da Uber;
- uma oferta Radar da Uber;
- uma oferta da 99.

Não é obrigatório encontrar os três tipos. O mais importante é marcar falhas reais em que o card estava visível, mas o HUD não apareceu.

## Muito importante

- Não marcar como falha apenas porque não havia oferta na tela.
- Não marcar durante Home/ocioso da Uber.
- Não marcar porque o HUD já tinha sido fechado manualmente para aquela mesma oferta.
- Não tocar em Reiniciar leitura entre a falha e o compartilhamento do JSON.
- Não precisa tirar screenshot para este teste, a menos que queira guardar como referência visual separada.

## O que será analisado

O relatório permitirá distinguir se a perda aconteceu em:

1. captura do frame;
2. OCR;
3. detecção da tarifa;
4. formação do cluster espacial;
5. geometria tempo/distância;
6. classificação da tela/card;
7. parser;
8. envio ao dispatcher;
9. deduplicação;
10. etapa de exibição do HUD.

A partir desses casos marcados, a próxima versão poderá corrigir somente a etapa comprovadamente problemática.
