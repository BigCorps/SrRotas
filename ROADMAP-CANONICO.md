# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-09-26.10
DATA_CANÔNICA: 26/09/2026
CURRENT_HEAD_STAGE: 0.33.6-field / versionCode 83 + Pré-1.0 Web/Admin Branding
CURRENT_FIELD_TEST: 0.33.6-field / versionCode 83 — HOMOLOGADA
FIELD_COMMIT: f2d7166280f58b22d3ff047a39ea7a3eaf36b026
FIELD_CI: Action #135 — SUCCESS
STATUS: RETA FINAL 1.0

## 0. Filosofia
ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR.
Integridade > estabilidade > contrato > compatibilidade > UX > novas funções.

## 1. Android homologado
0.33.6/vc83: Assistente com VER/IGNORAR, métricas, energia e recovery validados. M1 oficial. Reader2 shadow. Controlled Hybrid OFF. Histórico congelado.

## 2. V7
Batch canônico `48323962-cabd-497b-890f-8315e0d0753a`. Não reprocessar. V7 é histórica/analítica; `ride_offers` é operacional real.

## 3. Três produtos
Android = operação. Web `/app` = complemento do mesmo produto. Admin `/admin` = console BigCorps.

Full-admin exatamente: `contato@bigcorps.com.br` e `jadielalmeida@gmail.com`.

## 4. Identidade visual
Esta decisão substitui a paleta Web antiga dos roadmaps anteriores. `/app` segue a identidade atual de `SrTheme024.kt`. O Admin usa o ícone verde fornecido em 26/09/2026 e paleta própria. Branding não autoriza reconstrução de fluxos.

## 5. Admin
P5-03 implementado: painel, usuários, operação/radar, financeiro, custos, V7, sistema, detalhe e ações seguras. Antes da 1.0 ainda revisar diagnósticos/feedback/crashes, custos externos e qualquer override financeiro auditável.

## 6. Segurança atual
Supabase Advisor: 5 funções search_path mutável (WARN), leaked password protection disabled (WARN), RLS/no-policy INFO no desenho server-only, 10 FKs sem covering index INFO.

## 7. Gate 1.0
Ver `CHECKLIST-1.0.0.md` e `ROADMAP-PLAYSTORE-1.0.md`.

Módulos independentes podem avançar em paralelo desde que não quebrem contratos congelados, integridade ou gates de homologação.

## 8. Regra final
IMPLEMENTADO ≠ HOMOLOGADO. HEAD ≠ BUILD EM CAMPO. CI VERDE ≠ TESTE DE CAMPO. A próxima APK deve ser RC/pré-1.0 ou correção Android realmente necessária.
