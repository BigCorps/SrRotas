# Sr. Rotas 0.27.0-RC3 — Roteiro de validação de campo

## 1. Confirmar versão
Em diagnóstico ou informações do app:
- version_name: `0.27.0-rc3`
- version_code: `58`

## 2. Teste principal — Uber + 99 simultâneas
No Android/Samsung, ao autorizar a captura escolha **Tela inteira**. Selecionar apenas Uber ou apenas 99 limita a MediaProjection àquele aplicativo e não é um teste válido de monitoramento simultâneo.

Usar Uber + 99 em tela dividida pelo maior tempo possível.

Registrar separadamente:
- ofertas Uber vistas na tela;
- ofertas Uber que geraram HUD;
- ofertas 99 vistas na tela;
- ofertas 99 que geraram HUD.

Não é necessário contar todas em uma jornada longa. Uma amostra de 20 a 30 ofertas visíveis por plataforma já é útil.

## 3. Quando houver falha real
Falha real = oferta claramente visível no app de motorista e HUD do Sr. Rotas não apareceu.

1. Não encerrar a jornada.
2. Abrir a janela flutuante do Sr. Rotas.
3. Tocar no ícone de bug.
4. Tocar em `Reportar falha` imediatamente.
5. Aguardar 12 a 15 segundos.
6. Tocar novamente no bug > `Exportar diagnóstico`.
7. Compartilhar o arquivo `.json` gerado.
8. Informar junto: Uber/99, tipo da oferta se souber, e se o card ficou visível por vários segundos ou desapareceu rápido.

Se a leitura realmente tiver parado, só depois do registro usar `Reiniciar leitura`.

## 4. Validar a 99
Testar principalmente:
- 99Plus;
- 99Pop se aparecer;
- card com tempo/distância em linhas ou blocos visuais separados;
- tela dividida com Maps/Waze visível;
- card em que `Escolher` esteja visível;
- card em que serviço e R$/km estejam visíveis, mesmo que `Escolher` não seja reconhecido pelo OCR.

Falhas de leitura devem ser marcadas individualmente.

## 5. Regressão Uber
Confirmar que continuam funcionando:
- Exclusive;
- Radar;
- X/Comfort/Black/Electric quando aparecerem;
- HUD sem atraso anormal;
- nenhuma mistura de dados entre card Uber e card 99.

Se surgir HUD com valor obviamente incompatível com o card, tirar screenshot do card e exportar diagnóstico logo em seguida.

## 6. Botão de bug
Confirmar:
- ícone visível no rodapé do HUD expandido;
- não aumenta/pula a largura do painel;
- abre menu com Falha / Reiniciar leitura / Exportar diagnóstico;
- Reportar falha mostra confirmação;
- Exportar diagnóstico abre o compartilhamento Android com arquivo JSON, não texto gigante.

## 7. Notificação
Durante jornada ativa:
- confirmar que a notificação da jornada permanece;
- expandir e verificar `Reportar falha`;
- se a captura do Android for encerrada sozinha, a jornada deve continuar aberta;
- deve surgir acesso de contingência para reportar, reativar e exportar diagnóstico;
- `Reativar leitura` deve pedir nova autorização e manter a mesma jornada;
- ao encerrar a jornada, a notificação de contingência não deve permanecer.

## 8. Critério para próxima rodada
Enviar no mínimo:
- 1 diagnóstico após falha real da 99, se ocorrer;
- 1 diagnóstico após falha real da Uber, se ocorrer;
- uma avaliação simples: Uber melhor/igual/pior que DIAG1 e 99 melhor/igual/pior que DIAG1.

Se não houver falhas, exportar um diagnóstico ao fim de uma sessão longa em tela dividida.
