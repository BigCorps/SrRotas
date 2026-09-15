# QA — Sr. Rotas 0.27.0-RC3.4 — Fechamento Uber / UX

## 1. Versão
- [ ] Configurações / diagnóstico identifica `0.27.0-rc3.4`.
- [ ] versionCode = `62`.

## 2. Status / identidade
- [ ] Abrir Configurações com estado verde.
- [ ] “Sr. Rotas está pronto” usa o mascote correto 0.26.5.
- [ ] Alternar telas e voltar a Configurações.
- [ ] Forçar refresh/recriação da tela e confirmar que o mascote antigo não retorna.

## 3. Organização de Configurações
- [ ] Existe item independente “Janela flutuante”.
- [ ] “Configuração do HUD” não exibe mais a seção “Janela flutuante”.
- [ ] O card de HUD fala em métricas, limites e prévia — não em janela.
- [ ] Mensagens já validadas continuam no fluxo existente, sem regressão.
- [ ] Existe indicação “Uber suportado · 99 em implementação”.

## 4. Janela Flutuante
- [ ] A tela possui “Tamanho do botão”.
- [ ] A tela possui “Opacidade do botão”.
- [ ] A tela possui “Opacidade da janela”.
- [ ] Alterar tamanho do botão muda somente o botão.
- [ ] Alterar opacidade do botão muda somente o botão.
- [ ] Alterar opacidade da janela muda o painel expandido/trilho, não o botão.
- [ ] Fechar e reabrir o app preserva as três configurações.
- [ ] Ativar/desativar janela continua sem encerrar jornada/OCR.

## 5. Posição / bounds
- [ ] Não existe botão “Restaurar posição da janela” na nova experiência.
- [ ] Arrastar o botão para cada borda da tela não permite deixá-lo completamente fora da área visível.
- [ ] Rotacionar a tela mantém/corrige o botão dentro da área visível.
- [ ] Em tablet/tela dividida, repetir bordas + rotação.
- [ ] Reabrir o app com posição salva próxima da borda continua seguro.

## 6. Prévia do HUD
Testar Compacto, Normal e Grande.
- [ ] Estrutura visual da prévia corresponde ao HUD ao vivo.
- [ ] Tema claro/escuro acompanha o HUD real.
- [ ] Opacidade do HUD é refletida na prévia.
- [ ] Tipografia e cards são os mesmos do HUD real.
- [ ] Alterar métricas habilitadas atualiza a prévia.
- [ ] A prévia não dispara captura/OCR nem persiste oferta fictícia.

## 7. Regressão Uber — bloqueadora
Fazer uma jornada real com Uber.
- [ ] Oferta Uber completa gera HUD normalmente.
- [ ] Busca = motorista → passageiro.
- [ ] Destino/viagem = passageiro → destino.
- [ ] Resumo da Janela Flutuante continua correto.
- [ ] Destino continua com indicação visual esperada.
- [ ] Leitor continua operando após várias ofertas.
- [ ] Nenhuma regressão de tarifa/km/minutos/veredito.

## 8. 99 — não bloqueadora
- [ ] Interface informa 99 “em implementação”.
- [ ] Se houver 99 disponível, registrar comportamento e diagnóstico.
- [ ] Falha exclusiva da 99 não reprova RC3.4 para lançamento Uber.
- [ ] Qualquer regressão Uber reprova RC3.4.

## 9. Diagnóstico
Ao final do teste de campo:
- [ ] exportar Diagnóstico de leitura;
- [ ] guardar Bug Report do motorista;
- [ ] correlacionar falhas percebidas com reports automáticos/manuais;
- [ ] não criar RC3.5 apenas por alertas automáticos sem falha real correlacionada.

## 10. Critério de aprovação
RC3.4 é aprovada quando:
- [ ] itens visuais/UX acima passam;
- [ ] Uber segue estável;
- [ ] nenhuma alteração de reader/parsers ocorreu;
- [ ] `SRROTAS-PLANO-MESTRE.md` acompanha a entrega e está atualizado.
