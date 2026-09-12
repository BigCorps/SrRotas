# Teste de campo — Sr. Rotas 0.27.0-RC3.2

## Antes de sair

- confirmar em Configurações a versão `0.27.0-rc3.2`;
- manter os dados da RC3.1;
- no tablet, usar **tela inteira** quando for testar Uber + 99 simultaneamente;
- confirmar que o SQL `20260912_report_selection_multi_027032.sql` foi executado.

## 1. Qualidade da oferta e contexto — prioridade máxima

Em cada oferta reconhecida, além dos números financeiros, observar quando possível:

- se o embarque/local de busca corresponde ao card;
- se o destino corresponde ao card;
- se o HUD não misturou linhas de outro painel/app.

Se valor, distância, tempo, embarque ou destino estiverem errados, toque em **🐞 → Reportar falha** imediatamente.

Não é necessário exportar naquele momento. Continue dirigindo/testando.

## 2. 99

Durante a jornada, observar pelo menos 10 ofertas da 99 quando possível.

Conferir:

- valor da corrida;
- km de embarque;
- km de viagem;
- minutos de embarque e viagem;
- R$/km e R$/h;
- embarque e destino quando visíveis;
- plataforma no HUD deve continuar correta para evitar duplicação/contaminação, embora a inteligência regional agregue as plataformas nesta fase.

## 3. Uber

Observar pelo menos 5 ofertas Uber.

Confirmar que a leitura que já estava boa não regrediu.

Especialmente perto do fim de uma corrida, quando aparecer uma janela pequena contendo apenas categoria (`Comfort`, `Black`, etc.), confirmar que **isso não abre um novo HUD**.

Se abrir, Reportar falha.

## 4. Uber + 99 simultâneas

No tablet em tela dividida, tentar observar ao menos 3 situações em que os dois apps tenham informação/oferta simultaneamente.

Esperado:

- duas ofertas diferentes devem poder gerar dois registros;
- o mesmo card não deve aparecer duplicado como Uber e 99;
- se uma das duas estiver incompleta, a outra não deve ser descartada por causa disso.

## 5. Reportes consecutivos

Quando ocorrerem falhas em sequência:

1. toque em **Reportar falha** na primeira;
2. se outra falha aparecer logo depois, toque novamente sem esperar;
3. repita quantas vezes for necessário;
4. **não exporte entre elas**.

A mensagem deve indicar `Falha #N registrada` e permitir novo reporte imediatamente.

## 6. Exportação única + qualidade geotemporal

Ao final da jornada, use **Exportar diagnóstico** uma única vez.

No JSON RC3.2 esperamos:

- `schema = sr-rotas-diagnostic-v6`;
- `failure_reports_0270.report_count` maior que zero se houve reportes;
- um item independente para cada reporte;
- `radar_hud_trace_024.trace_scope` correspondente à versão/jornada atual;
- `offer_stats_0270.by_platform.uber` e `.99`;
- `offer_stats_0270.current_journey.offers`;
- contagens financeiras completas/incompletas;
- `offer_stats_0270.geo_temporal_quality`;
- `offer_stats_0270.current_journey.geo_temporal_quality`.

### Métricas principais a comparar

- `valid_observed_at` — ofertas com horário utilizável;
- `with_both_labels` — embarque + destino textuais;
- `with_both_cells` — embarque + destino geocodificados;
- `resolved_contexts`;
- `high_confidence_contexts`;
- `destination_continuity_ready`;
- `route_intelligence_ready`.

`route_intelligence_ready` não é uma taxa de leitura do HUD. É a quantidade de ofertas que já possuem matéria-prima forte para cruzamento rota + tempo. A probabilidade de nova oferta usa também `zone_exposures`, que mede onde o motorista estava disponível e por quanto tempo.

## 7. Seleção ✓ para relatório

Na mesma jornada, selecione pelo ✓ pelo menos 3 ofertas diferentes.

Esperado:

- todas podem permanecer selecionadas;
- nenhuma seleção anterior deve ser desmarcada automaticamente;
- não deve aparecer erro;
- o backend não deve mais responder `500` por unicidade da jornada.

## 8. Tela Agora

Confirmar o novo quadro compacto:

- Iniciar/Encerrar menor à esquerda;
- ícone + `Tudo OK` ou `Ação necessária` no centro;
- sem texto detalhando HUD/localização/captura;
- `Recuperar` somente quando a captura de uma jornada ativa estiver interrompida;
- altura claramente menor que na RC3.1.

## O que enviar depois

- o JSON exportado no fim da jornada;
- relatório textual do testador;
- screenshot manual apenas se houver HUD com valor/contexto incorreto e for seguro registrar.

Não é necessário conceder acesso do Sr. Rotas à galeria para este teste.
