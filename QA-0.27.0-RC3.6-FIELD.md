# QA — Sr. Rotas 0.27.0 RC3.6 Field

## Gate CI

- [ ] Unit tests verdes
- [ ] compileDebugKotlin verde
- [ ] Debug APK gerado
- [ ] Field Release APK gerado
- [ ] assinatura estável validada
- [ ] instala por cima da RC3.5 sem limpar dados

## UX / navegação

- [ ] Configurações e Usuário aparecem integrados ao cabeçalho
- [ ] engrenagem antiga flutuante não aparece
- [ ] Configurações abre mantendo a barra inferior
- [ ] Usuário abre mantendo a barra inferior
- [ ] tocar em Estatísticas / IA / Agora / Histórico / Radar fecha Configurações/Usuário e navega
- [ ] telas secundárias de configuração possuem Voltar
- [ ] iniciar/encerrar/recuperar jornada aparece como controle compacto
- [ ] Agora possui somente um conceito de `Pesquisar região`
- [ ] Momento / Hoje / Semanal + Base Coletiva/Pessoal continuam acessíveis
- [ ] Estatísticas usa ícone de gráfico
- [ ] Radar usa ícone de radar
- [ ] Histórico permanece estruturalmente igual
- [ ] Sr. Rotas está maior na tela IA e sem corte seco aparente

## Janela flutuante

- [ ] parada por 2 minutos sem saltos/flicker perceptível
- [ ] abrir/recolher 20 vezes sem mudar posição sozinha
- [ ] chegada de novas ofertas não causa resize em duas etapas
- [ ] Mais detalhes preserva Busca / Destino
- [ ] R$/km usa borda verde/amarela/vermelha conforme limites do usuário
- [ ] R$/min usa borda verde/amarela/vermelha conforme limites do usuário
- [ ] R$/h usa borda verde/amarela/vermelha conforme limites do usuário
- [ ] km e min permanecem neutros (sem inventar limiar)
- [ ] etiqueta M1/M2/M1+M2 é discreta e legível

## Reader M1

- [ ] MediaProjection continua iniciando pelo fluxo oficial Android
- [ ] captura segue ativa durante jornada
- [ ] novas ofertas continuam sendo lidas enquanto existe corrida em andamento
- [ ] perda temporária de frames não encerra jornada
- [ ] supervisor consegue chamar recovery sem reiniciar o aplicativo
- [ ] nenhuma oferta histórica é tratada como live

## Reader M2

- [ ] serviço aparece em Acessibilidade do Android
- [ ] divulgação do Sr. Rotas aparece antes de abrir configurações do Android
- [ ] serviço está limitado ao pacote Uber Driver
- [ ] árvore de Acessibilidade é lida sem clicar/tocar no Uber
- [ ] screenshot de janela funciona em Android 14+ quando permitido
- [ ] fallback de screenshot funciona em API 30–33
- [ ] M2 não persiste oferta sozinho na base oficial
- [ ] M2 não aceita/recusa corrida
- [ ] M2 não interrompe M1

## Comparação de campo

Em pelo menos 3 jornadas reais, registrar:

- [ ] total detectado por M1
- [ ] total detectado por M2
- [ ] total pareado M1+M2
- [ ] core completo de M1
- [ ] core completo de M2
- [ ] horário da oferta
- [ ] local de embarque
- [ ] tempo até embarque
- [ ] local de destino
- [ ] tempo total
- [ ] ocorrências em que app precisaria ser reiniciado

### Gate de saída

Não promover para 1.0-RC enquanto houver:

- qualquer P0;
- P1 de captura/estabilidade;
- travamento que exija reset do app;
- perda recorrente de leitura sem recuperação;
- duplicação causada por M1+M2;
- divergência de jornada/dados após atualização.
