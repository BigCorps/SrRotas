# Teste real — Sr. Rotas 0.25.0-beta

Use este checklist no único APK gerado depois dos quatro ZIPs.

## P0 — leitura e estabilidade

- [ ] Oferta com duração acima de 1 hora: conferir `1h29`, `1 h 29 min` ou equivalente. O total deve considerar 89 minutos de viagem, somando a busca quando existir.
- [ ] Confirmar que R$/min e R$/h usam esse tempo total correto.
- [ ] Abrir vários cards cinza do Radar e confirmar que cards válidos aparecem no HUD mesmo quando o OCR perde parênteses/formatação.
- [ ] Confirmar que cards vizinhos do Radar não misturam preço, distância ou duração entre si.
- [ ] Manter uma jornada longa ativa e alternar entre Uber/99/Sr. Rotas/tela bloqueada. O OCR deve continuar lendo; se a superfície travar, a recuperação deve ocorrer sem encerrar manualmente a jornada.
- [ ] Se o Android revogar de fato a MediaProjection, o Sr. Rotas não deve fingir que a captura continua ativa.

## Inteligência

- [ ] Base pessoal continua mostrando os dados já existentes.
- [ ] Base coletiva mostra dados quando houver recorte seguro; o fallback pode ampliar a janela, mas nunca abaixo de 3 contribuidores distintos.
- [ ] Conferir Itaim Bibi/Perdizes/Vila Mariana quando fizer sentido para o horário/perfil.
- [ ] `Nova corrida no destino` aparece diretamente no HUD quando houver contexto e resposta disponível.
- [ ] Desligar `Nova corrida no destino` em Configurações e confirmar que o sinal deixa de aparecer sem alterar a oferta/veredito.
- [ ] Busca e destino continuam funcionando como na versão validada.

## Visual

- [ ] Histórico: moldura da Inteligência Coletiva está fina, mantendo o espectro aprovado.
- [ ] Agora: cards coletivos usam a mesma identidade do Histórico.
- [ ] Botão `Base coletiva` usa fundo no mesmo espectro e texto legível/mais escuro no tema claro.
- [ ] HUD: borda verde em média boa, amarela em média regular e vermelha em média ruim.
- [ ] A borda está mais visível, mas não ocupa espaço excessivo.
- [ ] Conferir HUD compacto, normal e grande, temas claro/escuro e modo de cores acessíveis.

## Regressão rápida

- [ ] Uber exclusivo.
- [ ] Uber Radar.
- [ ] 99/outro app disponível no aparelho.
- [ ] Iniciar/encerrar jornada.
- [ ] Janela flutuante.
- [ ] Mensagens rápidas.
- [ ] Histórico.
- [ ] Agora/Pesquisa.
- [ ] Configurações do HUD persistem após fechar e reabrir o app.
