# CHANGELOG — 0.27.0-RC3.2

## Leitor / integridade

- adiciona arbitragem pré-dedupe para colisões Uber x 99 do mesmo frame;
- evita persistir duas cópias do mesmo card apenas porque a plataforma divergiu;
- preserva duas ofertas simultâneas reais quando as evidências são distintas;
- filtra candidato Uber que carrega identidade forte 99 sem ação Uber inequívoca;
- fallback textual Uber agora exige `Aceitar`, `Selecionar`, `Exclusivo` ou `Radar de viagens`;
- categoria Uber isolada deixa de ser prova suficiente de nova oferta.

A plataforma permanece como **metadado de integridade**, não como dimensão obrigatória da inteligência regional nesta fase.

## Qualidade geotemporal

O diagnóstico v6 passa a medir separadamente a matéria-prima necessária para o motor de inteligência:

- `valid_observed_at`;
- `with_pickup_label`;
- `with_destination_label`;
- `with_both_labels`;
- `with_pickup_cell`;
- `with_destination_cell`;
- `with_both_cells`;
- `resolved_contexts`;
- `high_confidence_contexts`;
- `destination_continuity_ready`;
- `route_intelligence_ready`.

`route_intelligence_ready` exige horário válido + células de embarque e destino + contexto de alta confiança + geocode resolvido. É uma métrica diagnóstica: **não bloqueia HUD nem persistência**.

A probabilidade de receber nova oferta continua usando a fonte correta de disponibilidade real: `zone_exposures`, que mede célula GPS do motorista + tempo disponível até a próxima oferta.

## Diagnóstico

- schema principal passa a `sr-rotas-diagnostic-v6`;
- reportes manuais persistentes por jornada;
- vários reportes consecutivos, sem cooldown operacional;
- uma única exportação final contém todos os reportes da jornada;
- trace separado por versão + jornada;
- novos agregados locais de plataforma, completude financeira e qualidade geotemporal.

## Backend / Supabase

- remove restrição obsoleta de uma seleção de relatório por jornada;
- múltiplas ofertas da mesma jornada podem permanecer selecionadas;
- corrige a causa identificada dos `500` em `offers/report-selection`.

## UI

- status da tela Agora reduzido para uma única linha funcional;
- sem detalhamento da pendência no card;
- Recuperar leitura aparece somente quando aplicável.

## Próxima etapa planejada

- RC3.3: segunda passagem OCR em **shadow mode**, sobre o próprio frame da MediaProjection quando a leitura primária falhar ou vier sem contexto;
- sem acesso amplo à galeria nesta fase;
- resultados shadow serão medidos antes de poderem corrigir HUD/persistência.
