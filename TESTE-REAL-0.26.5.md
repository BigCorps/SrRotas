# Checklist de validação em aparelho — 0.26.5-beta

## 1. Identidade
- Ícone do launcher mantém personagem inteiro e proporção próxima ao IMG-02 em launcher quadrado/circular.
- Configurações mostra mascote no carro em “Sr. Rotas está pronto”.
- Tema claro usa assinatura azul; tema escuro usa assinatura branca/cinza.
- Janela flutuante usa o novo rosto do Sr. Rotas.
- IA usa personagem da lâmpada cortado aproximadamente acima da cintura.

## 2. HUD / leitura
- Testar pelo menos 10 ofertas UberX/Comfort/Black.
- Testar ofertas Radar/cards cinza.
- Testar 99, se disponível.
- Confirmar que uma oferta com `1 h + minutos` mantém a duração completa.
- Deixar a jornada ativa por tempo prolongado e confirmar que novas ofertas continuam chegando.
- Conferir se não aparecem ofertas inventadas a partir de duas ofertas diferentes na mesma tela.

## 3. Estatísticas → Jornadas
- Conferir a faixa “Odômetros registrados”.
- Validar dias em que existe somente odômetro inicial ou somente final.
- Abrir um registro, editar e confirmar atualização da tela.

## 4. IA
- O campo principal deve dizer “Como posso ajudar?”.
- Não deve existir um segundo campo “Pergunte ao Sr. Rotas” fixo no rodapé.
- Créditos devem aparecer como legenda pequena.
- Enviar uma pergunta e validar transição para conversa.

## 5. Agora
- No topo, puxar para baixo até “Solte para atualizar”.
- Soltar e confirmar nova consulta.
- Comparar Base Pessoal/Coletiva: R$/km, R$/hora e Busca/min. devem ter mesma organização.
- Conferir bordas vivas e fundos menos opacos.

## 6. Assistente Ativo
- Configurar 10, 12 e 15 minutos em rodadas diferentes.
- Esperar uma sugestão elegível.
- Confirmar balão pequeno apontando para o ícone.
- Arrastar o ícone e confirmar que o balão acompanha.
- Tocar fora; o balão deve fechar.
- Tocar em “Verificar locais para novas corridas”; deve abrir Agora em Momento sem filtro antigo de bairro.

## 7. Digitalização
- Digitalizar jornada: deve haver feedback de captura e tela de revisão.
- Digitalizar histórico: rolar a tela, finalizar, conferir contagem de quadros e registros.
- Validar especialmente valores repetidos em corridas diferentes: eles não podem ser removidos como duplicata de OCR.

## 8. Janela flutuante
- Abrir Mensagens com mais de 6 atalhos e rolar até o último; a lista não pode voltar para a sexta posição.
- Abrir Mais detalhes e conferir Busca/retirada + Destino.
- Conferir stats maiores e sublinhado de avaliação.
- UberX deve aparecer como `X`.
- Alterar Configurações → Fonte da janela flutuante entre Compacta/Padrão/Grande.
