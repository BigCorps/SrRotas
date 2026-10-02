# Roteiro de teste — Jadiel — Radar Contextual vc88

## Instalação
1. Instalar vc88 POR CIMA do APK atual.
2. Não desinstalar e não limpar dados.
3. Confirmar sessão, histórico e configurações preservados.
4. Confirmar Reader/HUD normal antes de tocar no Radar.

## R0 — rollback/baseline
1. Abrir a aba Radar.
2. Deve aparecer o Radar antigo.
3. No rodapé Field deve existir "Homologação Radar Contextual".
4. Status esperado: `R0 · Radar legado`.

## DEMO visual — sem dirigir
1. Tocar `Prévia DEMO`.
2. Confirmar selo `DEMO · sem dados reais`.
3. Ver mapa centrado no destino, marcadores e cards.
4. Tocar um card/marcador.
5. Conferir detalhe `Por que está aqui?`.
6. Voltar/usar Rollback; Radar antigo deve reaparecer.
7. Com permissão de janela flutuante ativa, tocar `Assistente DEMO`.
8. Deve aparecer no mesmo HUD do Sr. Rotas um card `IGNORAR | VER`, sem segunda janela flutuante independente.

## R2 — UI real
1. Na aba Radar tocar `1 · UI`.
2. Status: `R2 · UI contextual`.
3. Runtime e Assistente permanecem desligados.
4. Iniciar jornada normalmente.
5. Aceitar uma corrida com destino/ETA reconhecidos.
6. Na tela Agora deve aparecer `Ver oportunidades no destino`.
7. Abrir.
8. Confirmar destino/ETA da corrida ativa.
9. Se houver POIs próximos, conferir mapa/cards/`Por que está aqui?`.
10. Reader/HUD precisa continuar capturando normalmente.

## R3 — runtime
Somente após R2 passar:
1. Tocar `2 · Runtime`.
2. Status: `R3 · UI + runtime`.
3. Voltar ao Uber/99 e seguir a corrida.
4. Não precisa manter o Sr. Rotas em primeiro plano.
5. Não deve haver mudança visual no Reader/HUD.
6. Ao terminar, enviar diagnóstico/horários aproximados para conferirmos telemetria no backend.

## R4 — assistente
Somente após R3 passar:
1. Tocar `3 · Assistente`.
2. Status: `R4 · UI + runtime + assistente`.
3. O card proativo só deve surgir se o backend devolver oportunidade forte e ETA 4–18 min.
4. `IGNORAR` fecha sem abrir Radar.
5. `VER` abre o Radar na oportunidade indicada.
6. O card deve usar o host do HUD existente.

## Rollback
A qualquer regressão:
1. abrir aba Radar;
2. tocar `Rollback`;
3. status volta para `R0 · Radar legado`;
4. runtime para;
5. assistente contextual some;
6. RadarPanel027035 volta imediatamente.

## P0/P1
Interromper teste e enviar diagnóstico se ocorrer:
- perda de captura;
- HUD congelado;
- jornada encerrada sozinha;
- oferta perdida após ativar Radar;
- crash;
- duplicação de janela flutuante;
- Radar apontando destino diferente da corrida ativa.
