# Sr. Rotas 0.27.0-rc1 — roteiro de validação

Base: 0.27.0-alpha2 / commit f594a9f
Versão: 0.27.0-rc1
versionCode: 55

## 1. Leitor — não regredir

A alpha2 melhorou a leitura e o RC1 NÃO altera MediaProjection, OCR, parser,
FrameChangeDetector ou shadow validator.

- Rodar jornada longa.
- Uber normal + Radar + 99.
- Se falhar: Registrar falha e Reiniciar leitura pela notificação.
- Confirmar que a jornada não termina ao reiniciar somente o leitor.

## 2. Seleções independentes

Na janela flutuante:

- Marcar corrida A.
- Marcar corrida B.
- A e B devem continuar selecionadas.
- Desmarcar A não pode desmarcar B.
- Confirmar persistência após abrir/fechar a janela.

No estado operacional:

- Corrida A -> Realizada.
- Corrida B -> Realizada.
- Confirmar ambas no Histórico/Estatísticas.

## 3. Janela flutuante compacta

Configurações -> Janela flutuante:

- Compacta (padrão do RC1).
- Padrão.
- Alternar os dois modos.
- Confirmar que nenhum texto/ação importante desaparece.
- Validar celular e tablet.

## 4. Assistente Ativo

A janela deve mostrar somente:

"Você está fazendo uma corrida?"

e um botão:

"Quero uma dica de local"

- Mover o ícone flutuante antes de a sugestão aparecer.
- O Assistente deve nascer ao lado da posição atual.
- Arrastar o ícone enquanto o Assistente está aberto: o balão deve acompanhá-lo.
- Tocar fora: fechar e tratar como motorista em corrida.
- Tocar "Quero uma dica de local": abrir Agora para a sugestão regional.

## 5. Jornadas

Em Estatísticas -> Jornadas:

- conferir Início e Fim explícitos;
- viagens realizadas;
- faturamento digitalizado/realizado;
- odômetro inicial/final;
- distância rodada;
- gastos;
- combustível/recarga;
- dados digitalizados.

A faixa "Odômetros registrados" deve aparecer mesmo sem registros, explicando
como adicionar dados.

Abrir "Completar / corrigir dados" no tema escuro e confirmar que todos os
campos permanecem legíveis.

## 6. Digitalizar Jornada

- Abrir a tela correta da Uber por baixo.
- Sr. Rotas -> Digitalizar jornada.
- Autorizar MediaProjection.
- O Sr. Rotas deve sair da frente automaticamente.
- A captura deve ocorrer com a Uber visível.
- Depois do OCR, o Sr. Rotas deve voltar com a revisão.
- Confirmar e salvar.

## 7. Destino no mapa

- O botão não deve piscar entre habilitado/desabilitado na mesma oferta.
- Quando houver texto de destino, deve ficar disponível de forma estável.
- Abrir e conferir que o app de mapas recebe o texto original do destino.
- Comparar especialmente o caso em que antes abriu cerca de uma quadra distante.

## 8. Fonte

Nenhuma troca foi feita no RC1. A fonte representa poucos KB e não era a causa
relevante do tamanho/desempenho; não introduzir regressão visual desnecessária.

## 9. Critério para promover a 0.27.0

Promover para 0.27.0 estável somente se:

- leitura não regredir em relação à alpha2;
- reinício do leitor preservar jornada;
- seleções A/B forem independentes;
- Digitalizar Jornada não exigir minimizar manualmente;
- Jornadas estiverem legíveis/completas;
- Assistente estiver compacto e posicionado corretamente;
- destino não piscar e abrir melhor no mapa.
