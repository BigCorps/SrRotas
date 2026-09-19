# QA de campo — RC3.6.1

## Antes da jornada
- [ ] Atualizar por cima da RC3.6 sem limpar dados.
- [ ] Confirmar `0.27.0-rc3.6.1-field` / code 65.
- [ ] Configurações mostra somente uma `Janela flutuante` e uma `Plataformas`.
- [ ] Barra inferior funciona enquanto Configurações está aberta.
- [ ] Título, engrenagem e usuário ficam imóveis por pelo menos 30 s.
- [ ] Pop-ups novos/antigos seguem visual Sr. Rotas.
- [ ] `Leitor M1 × M2` em Comparativo.
- [ ] Abrir `Saúde do M2 ao vivo`, zerar contadores e então habilitar Acessibilidade.
- [ ] Voltar ao Sr. Rotas e iniciar jornada.

## Agora
- [ ] Trilho mostra Iniciar / estado / Encerrar sem pular.
- [ ] Odômetro inicial e abastecimento/recarga continuam funcionais.
- [ ] Pesquisar região é a entrada única; expandir mantém Momento/Hoje/Semana/Base/Perfil.
- [ ] Cards mostram R$/km, R$/min e distância quando ela estiver disponível.
- [ ] Cores da navegação: azul / roxo / magenta / laranja / verde.

## M1 × M2 — teste válido
- [ ] Com Uber aberto e jornada ativa, `Eventos Uber recebidos` do M2 sobe acima de zero.
- [ ] `Árvore lida` sobe acima de zero.
- [ ] Quando necessário, `Screenshots M2` registra tentativa/sucesso ou código de falha.
- [ ] Ofertas exibem M1, M2 ou M1+M2 sem duplicar oferta oficial.
- [ ] Conferir horário, embarque, tempo até embarque, destino e tempo total.
- [ ] Não usar Galeria como prova de Accessibility; galeria não gera evento do Uber.

## Janela flutuante
- [ ] Abrir/fechar normalmente.
- [ ] Expandir uma oferta.
- [ ] Abrir `Mais detalhes` repetidamente.
- [ ] Deixar mais detalhes parado por 2 minutos: nenhuma mudança periódica de largura/altura.
- [ ] Compacta e não compacta continuam distintas.
- [ ] Bordas verde/amarela/vermelha seguem limites do motorista.

## Histórico
- [ ] Marcar `Fiz essa corrida`.
- [ ] Card passa imediatamente a `✓ Corrida realizada` em verde.
- [ ] Sair e voltar ao Histórico: marca permanece.

## Estabilidade
- [ ] Jornada de pelo menos 2 h sem reiniciar app.
- [ ] Fazer/aceitar corrida e continuar recebendo próximas ofertas.
- [ ] Tela bloqueada/desbloqueada sem perder jornada.
- [ ] Nenhum P0/P1.

## Ao terminar
Compartilhar `Configurações > Diagnóstico de leitura` desta build. O JSON deve conter `reader_lab_0270361` e `m2_health`.
