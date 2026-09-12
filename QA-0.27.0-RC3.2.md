# QA — Sr. Rotas 0.27.0-RC3.2

## Base conferida

- `main`: `0acc22b103775b2e923388ce800e21def8e79cf6`;
- RC3.1: `versionCode 59`, `versionName 0.27.0-rc3.1`;
- RC3.2 revisada: `versionCode 60`, `versionName 0.27.0-rc3.2`.

## Evidências que motivaram a correção

- foi observada a mesma oferta com R$ 7,83 / 4,3 km / 18 min persistida como Uber e 99 com diferença de milissegundos;
- a chave normal do `OfferDeduplicator` inclui a plataforma, logo não poderia remover essa colisão por conta própria;
- o gate Uber aceitava nomes de categoria como âncora ampla;
- o diagnóstico exportado da RC3.1 não reteve os reportes manuais feitos no início da jornada;
- produção registrou repetidos `500` em `/api/v1/offers/report-selection`;
- a migration 0.21.1 ainda continha `ride_offers_one_report_selection_per_journey_idx`, incompatível com a regra RC2 de múltiplas seleções por jornada;
- auditoria da coleta mostrou que contexto geográfico já existe em escala relevante, mas precisa virar métrica explícita de qualidade para evoluir o motor regional.

## Validação da arquitetura de inteligência

Confirmado no código atual:

- `OfferContextExtractor0221` tenta extrair embarque e destino do mesmo card;
- `OfferContextGeocoder` transforma contexto válido em coordenadas/células;
- `RegionalExposureTracker` mede a célula GPS em que o motorista ficou disponível e fecha a exposição quando uma oferta aparece;
- backend cruza região, dia da semana e faixa horária e já calcula exposição/probabilidade regional;
- a plataforma não precisa ser a dimensão principal desses agregados.

Portanto, RC3.2 preserva plataforma por integridade, mas passa a medir a qualidade **geográfica + temporal** como prioridade de dados.

## Testes locais executados

- compilação Kotlin direcionada do `DriverPlatformOfferRouter.kt` RC3.2 com stubs: **OK**;
- compilação Kotlin direcionada de `CrossPlatformOfferArbitrator0270.kt`: **OK**;
- smoke de 99 flex split-line: **OK**;
- trusted pane 99 rejeita âncora Uber explícita: **OK**;
- popup `Comfort + valor`, sem geometria de oferta: **rejeitado**;
- categoria + valor + duas geometrias completas: **aceito como candidato**;
- colisão financeira idêntica Uber/99 com identidade 99 forte: **fica somente 99**;
- duas ofertas fortes e espacial/textualmente distintas com mesmos números: **ambas preservadas**;
- compilação dirigida de `FailureReportStore0270.kt` + `RadarHudTrace024.kt` com stubs Android/JSON: **OK**;
- compilação dirigida do `DiagnosticBundle.kt` revisado com `geo_temporal_quality`: **OK**;
- reconstrução do `NowPanel023.kt` original validada pelo blob SHA antes da alteração: **OK**.

## Screenshot / segunda leitura — decisão de segurança

Não foi adicionado `READ_MEDIA_IMAGES` ou acesso amplo à galeria.

A infraestrutura existente já possui:

- cache privado curto de screenshots reconhecidos;
- importador histórico manual para imagens explicitamente selecionadas;
- MediaProjection ativa durante a jornada.

Para recuperação automática, a alternativa preferida é segunda passagem sobre o próprio frame da MediaProjection, em background/shadow, evitando permissão ampla e concorrência desnecessária com o OCR primário.

## Ainda depende do GitHub Actions

O build Gradle Android, unit tests JUnit do projeto e APK assinado serão validados definitivamente pelo workflow **Android CI + Field APK** após o upload.

## Risco controlado

A arbitragem não remove a plataforma do dedupe global. Ela atua somente antes do dedupe, quando ambos os parsers acabaram de produzir ofertas no mesmo frame. Se os dois clusters têm identidade forte e diferente, ambos são preservados.

As novas métricas geotemporais são apenas observacionais e não alteram parser, HUD ou verdict.
