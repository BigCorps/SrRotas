# Teste real — Sr. Rotas 0.26.2-beta

## P0 — HUD
- Rodar 20 ofertas reais consecutivas e registrar quantas geram HUD correto.
- Meta inicial: >=18/20, sem mistura de tarifa/km/tempo entre cards.
- Testar UberX, Comfort, Black, Radar e, se disponível, 99/outro app.
- Testar celular e tablet/multi-window se possível.
- Conferir corridas >1h.
- Conferir destino no painel flutuante: endereço incorreto é pior que `não identificado`.

## Screenshots
- Com opção ligada, confirmar imagem em `Imagens/SrRotas/Ofertas`.
- Desligar e confirmar que novas ofertas não criam screenshot visível.

## Estatísticas/Jornadas
- Tema escuro: filtros legíveis.
- Comparativos: gráfico de linha aparece.
- Análises: período inicial/final visível.
- Categorias: divisões entre linhas.
- Jornada encerrada: odo inicial/final, km, gastos, litros/kWh, custo, viagens realizadas e faturamento conhecido.

## Digitalização de jornada
- Abrir Resumo da sessão Uber.
- Câmera -> Digitalizar jornada.
- Conferir data, início/fim, faturamento, concluídas e oferecidas.
- Salvar somente após revisão.
- Abrir Jornadas e confirmar associação quando a correspondência temporal for inequívoca.

## Digitalização de histórico
- Câmera -> Digitalizar histórico.
- Rolar a tela lentamente por vários cards.
- Finalizar pela câmera ou notificação.
- Conferir total encontrado e revisar seleção.
- Validar que card parcialmente repetido entre quadros não duplica corrida.
- Conferir duração, km, dinâmico, extra, endereço e cancelamento.

## Janela/notificação
- Mais detalhes não deve repetir probabilidade.
- Indicador principal deve mostrar `Destino` / `Probabilidade`.
- Ativar notificação textual: 3 últimas corridas devem permanecer agrupadas.
- Testar Buscar, Destino e Combinado de cada registro.
- Desativar notificação textual: grupo deve desaparecer.

## Agora
- Tema escuro: Tudo pronto/OK com contorno verde vivo e sem preenchimento verde-água forte.
- Base Coletiva identificada em roxo/multicolorido oficial.
- Métrica deve aparecer como `Busca/min` e abaixo somente o número; distância continua em km.
