# Teste real — Sr. Rotas 0.26.0-beta

Use este roteiro somente depois de o build Android e o deploy Vercel ficarem verdes e de os SQLs 04/07/08 estarem aplicados.

## 1. Instalação / atualização
- Atualizar o APK para `0.26.0-beta` (`versionCode 47`).
- Confirmar login, onboarding já existente e permissões.
- Confirmar que uma atualização sobre a versão anterior não perde preferências.

## 2. OCR e duração >1h — prioridade P0
Testar ofertas reais com:
- `1 hora e 10 minutos`;
- `1 hora e 20 minutos`;
- `1 hora e 29 minutos`;
- se aparecer, formato abreviado `1h29`.
Confirmar que R$/min, R$/h e veredito usam 70/80/89 minutos, e não somente 10/20/29.
Se o OCR estiver incompleto, o HUD deve esperar uma leitura melhor em vez de exibir cálculo curto claramente errado.

## 3. Visual / HUD
- Fundo claro quase branco e cores vivas.
- Tema escuro preservado.
- `Nova corrida no destino` sempre ocupa slot fixo; sem dados = cinza/`Sem dados`.
- Testar posição no topo, abaixo e oculto.
- Base Coletiva com roxo vivo, borda fina e texto branco.

## 4. Estatísticas
Conferir os seis destinos:
- Histórico de corridas;
- Comparativos;
- Análises;
- Categorias;
- Detalhes do período;
- Jornadas.
Confirmar que filtros aparecem em Detalhes do período, não na abertura.

## 5. Jornada / hodômetro / gastos
- Antes de iniciar: preencher km inicial e, opcionalmente, combustível/recarga.
- Confirmar que os campos somem durante a jornada.
- Encerrar informando km final.
- Repetir encerrando sem informar km final.
- Em Estatísticas > Jornadas, usar o editor para completar/corrigir depois.
- Testar ao menos um abastecimento em litros/R$ e, se possível, uma recarga em kWh/R$.
- Confirmar distância = km final - km inicial.

## 6. Assistente Ativo
- Com jornada ACTIVE e sem ofertas por cerca de 10 min, observar se surge sugestão regional válida.
- Confirmar região, distância e fonte.
- Testar `Ignorar`.
- Testar `Estou em corrida`: deve silenciar sem criar corrida realizada.
- Confirmar que PAUSED ou DOING_RIDE não gera sugestão.

## 7. Digitalização Uber
- Digitalizar um resumo de sessão/offline.
- Conferir prévia antes de salvar.
- Repetir a mesma tela e confirmar que não duplica.
- Digitalizar uma tela com duas ou mais corridas concluídas.
- Conferir valor/categoria/horário/rota de cada card.
- Repetir e confirmar dedupe.

## 8. Street View
- Em uma oferta com destino resolvido, confirmar botão `360° Ver destino`.
- Abrir e confirmar que o Google Maps vai para o destino correto.
- Em destino sem coordenada/confiança suficiente, confirmar que o botão não aparece.

## 9. Sr. Rotas Radar
- Confirmar que o Agora mostra a área do Radar sem afetar a Base Pessoal/Coletiva.
- Com `TICKETMASTER_API_KEY` configurada e cron já executado, conferir eventos próximos, fonte, confiança, horário/fim e distância.
- Confirmar que evento não altera o veredito financeiro de uma oferta.

## 10. Regressões
- Oferta exclusiva Uber.
- Oferta Radar cinza.
- Pausar/retomar/encerrar jornada.
- Bolha flutuante e mensagens.
- Histórico e confirmação `Fiz essa corrida` / `Não realizei`.
- Sincronização offline/online.
- Logout/login.

Registre prints e exemplos de qualquer leitura errada; principalmente duração >1h e layouts diferentes da tela de histórico da Uber.
