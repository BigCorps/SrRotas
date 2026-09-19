# Sr. Rotas — 0.27.0 RC3.6.1 Field

Base esperada: commit `c300c40bdb9e6f3b47b2f097b482bf76be8b0ded` (RC3.6 Field / Action #100 verde).

## Como aplicar

Extraia este ZIP e envie **o conteúdo interno para a raiz do repositório** `BigCorps/SrRotas`, substituindo os arquivos existentes.

Versão desta rodada:
- `versionCode 65`
- `versionName 0.27.0-rc3.6.1-field`

Commit sugerido:

`Sr Rotas RC3.6.1 Field - field bugfixes and stable reader diagnostics`

## O que esta rodada corrige

1. Cabeçalho: elimina a disputa de padding entre RC3.5 (58 dp) e RC3.6 (6 dp), causa do título/ícones pulando.
2. Janela flutuante: remove os três pollings visuais concorrentes da árvore da janela (RC3.5/RC3.6/BubbleRuntime) e aplica M1/M2 + métricas no `OnPreDraw`, antes do frame ser mostrado.
3. Configurações: barra inferior recebe callback real de navegação; fecha a sobreposição e navega na mesma ação.
4. Histórico: `Fiz essa corrida` vira estado persistente visual `✓ Corrida realizada`, verde, depois de `COMPLETED`.
5. Configurações: esconde as entradas duplicadas antigas de `Janela flutuante` e `Plataformas`; mantém a tela única de Janela Flutuante com comportamento/aparência/Assistente/mensagens.
6. Pop-ups: `AlertDialog` recebe tema Sr. Rotas global com surface, tipografia, accent e cantos arredondados.
7. M2: adiciona contadores do AccessibilityService (conexão, eventos Uber, árvore, screenshot e ofertas) para separar `não acionou` de `acionou mas não reconheceu`.
8. Diagnóstico de leitura: o compartilhamento da tela Configurações passa a acrescentar `reader_lab_0270361` ao JSON.
9. Agora: controle de jornada em trilho horizontal inspirado na referência, pesquisa reforçada como entrada principal e cards regionais aproximados para `R$/km · R$/min · distância` quando há distância disponível.
10. Barra inferior: Estatísticas azul, IA roxo, Agora magenta, Histórico laranja e Radar verde, com ícones específicos.

## Importante sobre M2 e Galeria

Abrir um screenshot na Galeria **não aciona** o `AccessibilityService` do Uber. Esse comportamento era a razão de o teste por galeria não contabilizar M2 na RC3.6. O M2 mede a janela real do pacote `com.ubercab.driver` durante jornada ativa.

Nesta build há `Configurações > Saúde do M2 ao vivo` para confirmar imediatamente:
- se o serviço conectou;
- quantos eventos do Uber chegaram;
- quantas árvores foram lidas;
- quantos screenshots M2 foram obtidos;
- quantas ofertas completas vieram por árvore ou OCR visual.

## O que NÃO foi alterado

- `OfferParser` / parser version `sr-rotas-v0.5.4`;
- `UberOfferDetector`;
- `OfferDeduplicator`;
- fórmulas financeiras;
- Base Coletiva;
- persistência oficial: M2 continua shadow e não grava oferta oficial sozinho.

## Observação do diagnóstico recebido

O JSON da RC3.6 confirma que a jornada anexada foi processada apenas por `media-projection-ocr/uber` nos registros recentes e o diagnóstico antigo ainda não continha seção ReaderLab. Também houve 146 resets automáticos do OCR por `watchdog_semantic_gap` em cerca de 5,3 h, apesar de `ocr_failures=0`.

Nesta rodada não alteramos esse watchdog do M1 para não misturar uma mudança de captura com o primeiro teste real M1 × M2. O próximo diagnóstico RC3.6.1 deve ser usado para decidir se esse reset semântico precisa ser endurecido/removido antes da candidata final.
