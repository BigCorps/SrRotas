# Diagnóstico do cálculo de custos/lucro

## O que foi comprovado

O valor monetário **Lucro da corrida** é calculado como:

`tarifa - custo da corrida`

Com custo por km não negativo, esse valor não ultrapassa a tarifa.

O campo **Lucro por hora** é diferente: ele anualiza/horariza o lucro da corrida.
Uma corrida curta pode ter, por exemplo, cerca de R$ 21 de lucro e uma taxa superior
a R$ 120/h. A versão anterior mostrava esse número de forma fácil de confundir com
o lucro monetário da própria corrida. A 0.24.1 passa a escrever explicitamente `/h`.

## Custo total por km

O modelo vigente soma:
1. combustível/energia por km;
2. custos mensais rateados pelos km de trabalho do mês.

Assim, parcela/aluguel/assinatura informada no perfil entra no custo total. A nova
apresentação mostra separadamente custo de rodagem, fixos rateados e custo total.

## Ponto separado: modo Combinação

Foi identificado um problema de modelagem para híbridos em `Combinação`: a versão
atual soma integralmente a parcela líquida e a elétrica, sem perguntar a fração de
uso de cada fonte. Isso não causou o teste elétrico atual, mas deve ser corrigido
antes da RC com um rateio explícito de energia, em mudança própria de modelo/schema.
