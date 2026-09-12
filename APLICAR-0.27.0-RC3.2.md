# Sr. Rotas 0.27.0-RC3.2 — aplicar

Base esperada: `main` em `0acc22b103775b2e923388ce800e21def8e79cf6` (RC3.1).

Versão Android desta rodada:

- `versionCode = 60`
- `versionName = 0.27.0-rc3.2`

> Esta é a revisão geotemporal da RC3.2. Se você recebeu um ZIP RC3.2 anterior, descarte-o e use somente este pacote.

## Ordem recomendada

1. No Supabase de produção do Sr. Rotas, execute o SQL:
   `supabase/migrations/20260912_report_selection_multi_027032.sql`.
   Ele apenas remove o índice único antigo que limitava a seleção de relatório a uma oferta por jornada e cria um índice normal equivalente para consulta.
2. Extraia este ZIP na raiz de `BigCorps/SrRotas`, preservando as pastas e substituindo os arquivos existentes.
3. Envie/commit na `main`.
4. Aguarde **Android CI + Field APK** ficar verde e o deploy da Vercel ficar READY.
5. Instale o APK de campo por cima da RC3.1. Não limpe os dados do aplicativo.
6. Faça o protocolo de `TESTE-0.27.0-RC3.2.md`.
7. Ao final da jornada, exporte **um único diagnóstico** e envie o JSON para análise.

## Prioridade desta rodada

A plataforma (`Uber` / `99`) continua sendo preservada e corrigida como metadado de integridade, porque uma classificação errada não pode duplicar ou contaminar a mesma oferta. Porém, **a inteligência regional não deve depender de separar a demanda por plataforma nesta fase**.

Para inteligência, as dimensões prioritárias passam a ser tratadas explicitamente como fontes diferentes:

1. **célula de disponibilidade do motorista (`zone_exposures.cell`)** — onde o motorista estava disponível quando uma oferta chegou;
2. **embarque (`pickup_cell`)** — onde o passageiro seria buscado;
3. **destino (`destination_cell`)** — onde a corrida terminaria;
4. **tempo (`observed_at`, dia da semana e faixa horária)** — quando a oferta ocorreu.

O diagnóstico v6 agora mede a completude geotemporal das novas ofertas, sem alterar parser/HUD/verdict.

## O que esta rodada corrige

- arbitragem Uber x 99 antes da persistência quando os dois parsers produzem o mesmo card no mesmo frame;
- preservação de duas ofertas simultâneas reais quando os clusters/evidências são distintos;
- bloqueio do fallback Uber quando existe apenas nome de categoria sem ação inequívoca de oferta;
- gate Uber deixa de tratar `Comfort`, `Black`, `Electric` etc. como prova suficiente de nova oferta;
- diagnóstico v6 com múltiplos reportes na mesma jornada;
- cada reporte preserva janela técnica antes/depois e pode ser feito consecutivamente;
- uma única exportação ao fim inclui todos os reportes da jornada;
- trace Radar/HUD passa a ser separado por `versionCode + jornada`, evitando misturar rodada/build anterior;
- exportação passa a trazer agregados locais de Uber x 99, completude financeira **e qualidade geotemporal**;
- métricas de qualidade incluem horário válido, labels de embarque/destino, células, geocode resolvido e `route_intelligence_ready`;
- corrige o `500` de `/api/v1/offers/report-selection` causado pela restrição antiga de uma seleção por jornada;
- quadro de status da tela Agora fica compacto.

## Segunda leitura de imagens

**Não foi adicionado acesso amplo à galeria/pasta de screenshots nesta RC3.2.**

A próxima etapa recomendada é RC3.3 em modo shadow: quando a primeira leitura falhar ou vier sem contexto, uma segunda passagem OCR poderá usar o **mesmo frame já obtido pela MediaProjection**, preferencialmente recortado no painel candidato e mantido em memória/cache privado curto. Isso evita vasculhar fotos pessoais e evita introduzir uma permissão ampla de mídia.

O importador histórico manual existente continua separado e pode usar imagens explicitamente selecionadas pelo usuário.

## Guardrails preservados

Esta rodada **não altera**:

- `OfferParser.kt`;
- `UberSpatialParser0221.kt`;
- `OfferDeduplicator.kt`;
- `FrameChangeDetector`;
- sampling OCR de 250 ms;
- resolução máxima OCR;
- fórmulas financeiras;
- thresholds financeiros;
- lógica de aceitação/recusa de corrida (o Sr. Rotas continua sem tocar nos apps).

A migration é idempotente e não apaga ofertas nem seleções existentes.
