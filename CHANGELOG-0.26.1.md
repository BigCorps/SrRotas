# Sr. Rotas 0.26.1-beta

- Prioriza confiabilidade do HUD após teste de campo da 0.26.0.
- Força OCR periódico de segurança em tela aparentemente estática.
- Isola cards simultâneos por faixas verticais entre tarifas.
- Impede fallback Uber -> `other` em frame Uber incompleto.
- Endurece rejeição de combinações OCR evidentemente cruzadas sem esconder uma corrida apenas por ser economicamente ruim.
- Amplia estabilização final para capturar a leitura periódica antes da persistência.
- Aceita tarifa OCR `$ 17,99` sem transformar bônus ou `$/km` em tarifa principal.
- Mantém suporte de duração 1h+ e contrato `parser_version=sr-rotas-v0.5.4`.
- Street View passa a usar slot fixo `Street View — Destino`.
- Renomeia continuidade para probabilidade de novas corridas.
- Restaura identidade multicolorida fina da Base Coletiva.
- Moderniza visual dos diálogos de jornada sem alterar regras.
- Limita trilho de mensagens rápidas a seis slots visíveis com scroll/setas.
- Adiciona cache de 5 min ao Radar no APK e refresh manual forçado.
- Adiciona ingestão autenticada de eventos externos e painel `/radar-admin`.
- Não cria tabelas nem exige SQL novo.
