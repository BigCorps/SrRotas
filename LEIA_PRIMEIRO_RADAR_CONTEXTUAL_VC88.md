# LEIA PRIMEIRO — Radar Contextual vc88

Base do pacote: `main` verde `925078db920ac56649b2b116a9b3e0e60054a76c`.

Este ZIP NÃO copia cegamente o handoff antigo. Ele preserva as correções posteriores do repositório e integra somente os pontos que faltavam.

## O que já foi feito no backend
R1 foi executado com simulação `BEGIN/ROLLBACK` antes da gravação real.
Estado final:
- `sr_radar_pois`: 4;
- aliases: 4;
- eventos ativos ligados: 7;
- eventos ativos sem POI: 0;
- decisões: 4 `created`, 3 `linked`;
- REVIEW com poi_id: 0;
- contextual_events: 0 antes do teste de campo;
- learning_rows: 0 antes do teste de campo.

Nenhuma migration precisa ser executada novamente.
Nenhum V7 foi reprocessado.

## O que o ZIP faz
- sobe Android para `0.33.11-field / vc88`;
- integra Radar Contextual no shell real;
- preserva RadarPanel027035 como rollback;
- inclui preview DEMO;
- inclui fases manuais UI → Runtime → Assistente;
- assistente usa o root do JourneyBubbleController;
- adiciona testes TS puros ao CI;
- adiciona testes Android de integração.

## Upload
Suba o conteúdo do ZIP na raiz do repositório, preservando os caminhos.
Aguarde os dois jobs:
- Web / Next.js build;
- Android / Field APK.

Baixe somente `sr-rotas-field-release-apk` quando o signing estiver `stable`.
