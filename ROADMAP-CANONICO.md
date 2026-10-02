# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-10-02.7
DATA_CANÔNICA: 02/10/2026
CURRENT_HEAD_STAGE: 0.33.11-field / versionCode 88 — Radar Contextual integrado para homologação R1→R4
CURRENT_FIELD_TEST: 0.33.8-field / versionCode 85 — VALIDADA EM CAMPO
BASE_HOMOLOGADA: 0.33.6-field / versionCode 83 — HOMOLOGADA
STATUS: RETA FINAL 1.0
README_CANONICO: README-CONTINUIDADE.md

## 0. Filosofia
ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR.
Integridade > estabilidade > contrato > compatibilidade > UX > novas funções.

## 1. Android
- 0.33.6/vc83 permanece a base formalmente homologada.
- 0.33.7/vc84 validou Device Identity/Access Resolver em dois aparelhos.
- 0.33.8/vc85 validou Sync Recovery + Crash Flush em campo.
- 0.33.10/vc87 fechou a reconciliação comercial/visual do Gate 5 em CI.
- 0.33.11/vc88 integra o Radar Contextual ao shell real, com rollout local seguro.
- M1 oficial; Reader2 shadow; Controlled Hybrid OFF.
- Histórico, Money e contratos de captura permanecem congelados.

## 2. Radar Contextual vc88
Arquitetura entregue:
- POI resolver e backend contextual já presentes;
- currentRide.localOfferId → oferta persistida → RideOffer.context preservado;
- RadarPanel027035 continua instanciado como rollback;
- UI contextual ligada ao shell somente quando uiEnabled=true;
- CTA discreto "Ver oportunidades no destino" aparece apenas durante corrida elegível;
- runtime permanece vivo quando a Activity vai para Uber/99;
- runtime não faz request sem corrida ativa com destino/ETA;
- assistente usa o host existente do JourneyBubbleController, sem segundo WindowManager;
- telemetria não envia OCR bruto, endereço nem trilha GPS;
- preview DEMO é local, marcado como DEMO e não envia telemetria.

## 3. Rollout R1→R4
R1 BACKEND — HOMOLOGADO EM 02/10/2026:
- 4 POIs canônicos criados a partir de evidência exata;
- 7 eventos ativos associados;
- 4 decisões created + 3 linked;
- 0 eventos ativos sem POI;
- 0 REVIEW com poi_id;
- duplicatas somente quando venue_name + endereço + coordenadas eram idênticos.

R2 UI:
- habilitar "1 · UI" no painel Field da aba Radar;
- valida Facetas 1–3;
- runtime e assistente continuam OFF.

R3 RUNTIME:
- habilitar "2 · Runtime" após R2 passar;
- cadência adaptativa: 5 min / 3 min / 90 s / 60 s conforme ETA;
- zero requests sem RadarDestinationSpecV1 válido.

R4 ASSISTENTE:
- habilitar "3 · Assistente" somente após R3;
- exige strong opportunity + confidence + ETA 4–18 min;
- IGNORAR | VER;
- host visual reutiliza JourneyBubbleController.

Rollback imediato:
- botão Rollback no Field APK;
- flags UI/runtime/assistant voltam false;
- runtime e card contextual são encerrados;
- RadarPanel027035 volta a ser a superfície ativa.

## 4. Segurança e privacidade
- Radar contextual depende de can_analytics.
- tabelas Radar continuam server-only via service_role.
- não adicionar políticas RLS fictícias apenas para remover INFO do Advisor.
- metadata de telemetria segue whitelist escalar.
- Learning/TTNR é observacional, não causal.
- V7 não é reprocessado.

## 5. Comercial / Access
Copiloto permanece gratuito.
Inteligência inclui Radar Contextual quando trial/assinatura permitir can_analytics.
R$ 9,90 / 30 dias homologado via Pix Banco Inter.
Access Resolver continua em observe durante esta homologação.

## 6. Gate vc88
Instalar por cima do vc87/vc86/vc85 sem limpar dados.

Obrigatório:
- CI Web + testes Radar verdes;
- Android unit tests verdes;
- Field APK assinado gerado;
- Reader/HUD sem regressão;
- R0 mostra Radar legado;
- preview DEMO reproduz mapa/cards/detalhe;
- R2 mostra CTA em corrida e abre destino real;
- R3 gera telemetria contextual sem interferir na leitura;
- R4 mostra assistente contextual no HUD existente;
- Rollback restaura Radar legado imediatamente.

## 7. Depois do Radar vc88
- teste completo de novo usuário ponta a ponta;
- offline→online;
- soak/bateria/sync/crash;
- observe→enforce controlado;
- Play Integrity soft;
- Data Safety/MediaProjection/FGS;
- assetlinks final/AAB/RC;
- zero P0/P1.

IMPLEMENTADO ≠ HOMOLOGADO.
HEAD ≠ BUILD EM CAMPO.
CI VERDE ≠ TESTE DE CAMPO.
