# Sr. Rotas 0.27.0-RC2 — roteiro de teste de campo

Base: `0.27.0-RC1` / commit `9bb226deaeab04044d8a7da00750f5a1b7a19ed4`.

## Objetivo desta RC

A RC2 corrige os problemas visuais/funcionais comprovados no teste da RC1 sem alterar o núcleo de leitura da alpha2. O foco continua sendo a confiabilidade da leitura em campo.

**Importante:** parser, thresholds, `MediaProjectionOcrService`, `FrameChangeDetector` e regras de OCR não foram alterados nesta RC. A RC2 reduz trabalho visual concorrente da janela flutuante, mas não declara o problema de leitura como resolvido sem evidência de campo.

---

## 1. PRIORIDADE ABSOLUTA — leitura das ofertas / HUD

Fazer uma jornada longa, preferencialmente com:

- Uber com card normal;
- Uber Radar/múltiplas ofertas;
- 99, quando disponível;
- tela alternando entre navegação e app de corrida como no uso real.

Para cada oferta visível no app de corrida, observar se o HUD do Sr. Rotas aparece.

### Se uma oferta aparecer e o HUD NÃO aparecer

1. **Não encerre a jornada.**
2. Abra a notificação do leitor e toque primeiro em **Registrar falha**.
3. Anote aproximadamente:
   - horário;
   - Uber ou 99;
   - card normal ou Radar;
   - se havia uma ou várias ofertas na tela.
4. Só depois, se necessário, toque em **Reiniciar leitura**.
5. Confirme que a jornada continua ativa e que novas ofertas voltam a ser lidas.
6. Ao final do teste, abrir **Configurações → Diagnóstico de leitura** e compartilhar o arquivo de diagnóstico junto com o relatório.

### Aprovação

- reiniciar leitura não encerra a jornada;
- leitor continua funcionando após uso prolongado;
- registrar qualquer oferta visual que não gere HUD para podermos localizar a etapa exata da falha pelo diagnóstico.

---

## 2. Janela compacta — estabilidade

Com **Janela flutuante = Compacta**:

1. abrir e fechar a janela várias vezes;
2. abrir diferentes ofertas;
3. abrir/fechar Mais detalhes;
4. abrir/fechar câmera;
5. abrir/fechar Mensagens;
6. marcar/desmarcar ofertas para relatório;
7. deixar a janela aberta por pelo menos 10 minutos durante jornada.

### Aprovação

- largura/tamanho não alternam entre dois estados;
- cards e controles não ficam “pulando” periodicamente;
- abrir câmera ou mensagens não produz alternância contínua de geometria;
- compactação permanece legível em celular e, se possível, tablet.

---

## 3. Aparência dos cards compactos

Confirmar em ofertas **X, Comfort, Black** e outros tipos disponíveis:

- não existe contorno verde/amarelo/vermelho ao redor do card da oferta;
- não existe faixa semafórica extra nos detalhes;
- a cor de avaliação permanece na **bolinha**;
- o **nome da categoria** usa a mesma cor da bolinha;
- o check `✓` está menor e não aumenta a altura do card;
- subcards de sinais não exibem borda verde/amarela/vermelha.

---

## 4. Oferta aberta — Destino e probabilidade

Abrir uma oferta com destino identificado.

### Esperado

- `Destino` continua pequeno;
- endereço/destino continua legível;
- probabilidade aparece abreviada como somente:
  - `% Baixo`
  - `% Médio`
  - `% Alto`
- enquanto ainda não houver resultado, pode aparecer `% Analisando…` ou `% Dados insuf.`;
- não deve aparecer o texto longo `Probabilidade de novas corridas` nessa apresentação compacta.

---

## 5. Botões inferiores

Validar os seis controles:

- Play;
- Pause;
- Stop;
- Histórico/Estatísticas;
- Digitalização/câmera;
- Mensagens.

### Aprovação

- ficaram discretamente menores;
- bordas/raios não parecem excessivos;
- ícones continuam fáceis de tocar;
- nenhuma ação mudou de função;
- botão Mensagens continua indicando estado aberto/fechado.

---

## 6. Menu da câmera

Tocar repetidamente no ícone da câmera.

### Esperado

- opções exibidas como **Jornada** e **Histórico**;
- não aparecem `Digitalizar jornada` / `Digitalizar histórico`;
- abrir/fechar menu não faz os elementos alternarem continuamente de posição/tamanho;
- Jornada continua abrindo a digitalização da sessão;
- Histórico continua abrindo a digitalização do histórico;
- se uma captura de histórico estiver ativa, `Finalizar histórico` continua funcionando.

---

## 7. Seleção múltipla para relatório — local + servidor

1. em uma mesma jornada, selecionar oferta **A**;
2. selecionar oferta **B**;
3. confirmar que A e B permanecem marcadas;
4. fechar/reabrir a janela;
5. aguardar sincronização;
6. conferir relatório/histórico que usa ofertas selecionadas;
7. desmarcar A e confirmar que B continua selecionada.

### Aprovação

- selecionar B não desmarca A;
- desmarcar A não altera B;
- comportamento persiste depois da sincronização;
- backend mantém múltiplas ofertas com `report_selected=true` na mesma jornada.

> Esta validação do servidor só vale depois que o commit da RC2 tiver sido publicado e o Vercel tiver concluído o deploy do backend.

---

## 8. Regressões obrigatórias

Confirmar também:

- REALIZADA/NÃO REALIZADA altera exatamente a oferta aberta;
- Digitalizar Jornada continua saindo para a Uber e voltando para revisão;
- ação de mapa de destino continua estável;
- Histórico abre normalmente;
- Mensagens rápidas continuam copiando;
- jornada inicia, pausa, retoma e encerra;
- tema Claro/Escuro não quebra contraste;
- nenhuma mudança de fonte/identidade inesperada.

---

## Critério para promover

A RC2 pode avançar quando:

1. a janela compacta estiver visualmente estável;
2. todas as mudanças do relatório estiverem corretas;
3. seleção múltipla persistir local e remotamente;
4. nenhuma regressão for criada na jornada;
5. o teste de leitura fornecer diagnóstico suficiente de qualquer HUD perdido.

Se ainda houver HUD perdido, **não tentar corrigir parser/threshold por tentativa**: enviar o diagnóstico de leitura desta RC2 para a próxima correção ser baseada na etapa real que falhou.
