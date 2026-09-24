#!/usr/bin/env bash
set -euo pipefail

SRC="app/src/main/java/com/bigcorps/driveraimvp"
fail() { echo "::error::$1"; exit 1; }

APP="$SRC/SrRotasApplication.kt"
MAIN="$SRC/MainActivity.kt"
NOW="$SRC/NowPanel027037.kt"
SETTINGS="$SRC/SettingsPanel027037.kt"
HISTORY="$SRC/RideHistoryPanel027035.kt"
READER="$SRC/ReaderLab027036.kt"
BUBBLE="$SRC/JourneyBubbleController.kt"
FLOATING="$SRC/FloatingWindowSettingsActivity027034.kt"
DISPATCHER="$SRC/OfferDispatcher.kt"
DIAGNOSTIC="$SRC/ReaderLabCombinedDiagnostic0270361.kt"
MEDIA="$SRC/MediaProjectionOcrService.kt"
SHADOW_RECOVERY="$SRC/ShadowOfferRecovery027033.kt"
ADMISSION="$SRC/OfferAdmissionGate029.kt"
ADMISSION030="$SRC/OfferAdmissionGate030.kt"
READER2="$SRC/Reader2Shadow030.kt"
READER2_MONEY="$SRC/Reader2MoneyShadow030.kt"
READER2_PARALLEL="$SRC/Reader2Parallel031.kt"
READER2_ACCUMULATOR="$SRC/Reader2Accumulator032.kt"
READER2_CONSENSUS="$SRC/Reader2Consensus0321.kt"
MONEY_ROLES="$SRC/MoneyRoleResolver030.kt"
SPATIAL_ISOLATION="$SRC/OfferSpatialIsolation0221.kt"
UBER_DETECTOR="$SRC/UberOfferDetector.kt"
UBER_SPATIAL="$SRC/UberSpatialParser0221.kt"
CAPTURE_RESILIENCE="$SRC/CaptureResilience0311.kt"
RECOVERY_SUPERVISOR="$SRC/ReaderRecoverySupervisor027036.kt"
DIAGNOSTIC_CONTROLS="$SRC/DiagnosticControls0270.kt"
ROADMAP="../ROADMAP-CANONICO.md"

# Nenhum polish visual de versão antiga pode voltar ao runtime.
for symbol in \
  'NowPanelPolish0262.install' \
  'FieldValidationPolish0263.install' \
  'FieldValidationPolish0264.install' \
  'FieldValidationPolish0265.install' \
  'BubbleRuntimePolish0265.install' \
  'ReleasePolish0270.install' \
  'Rc35UiPolish027035.install' \
  'Rc36ClosingPolish027036.install' \
  'Rc361FieldFixes0270361.install'; do
  if grep -Fq "$symbol" "$APP"; then fail "Runtime legado reintroduzido: $symbol"; fi
done

grep -Fq 'ConsolidatedMainActivity027037' "$MAIN" || fail "MainActivity deixou de usar o shell consolidado"
grep -Fq 'getString("mode", MODE_M1)' "$READER" || fail "M1 deixou de ser o modo seguro padrão"
grep -Fq 'ReaderLabCombinedDiagnostic0270361.share' "$SETTINGS" || fail "Configurações deixou de usar o diagnóstico M1/M2 combinado"
grep -Fq 'RideOperationalStatus.NOT_COMPLETED' "$HISTORY" || fail "Histórico perdeu a possibilidade de desfazer corrida realizada"

if grep -Fq 'Visualizar Radar' "$NOW"; then fail "Visualizar Radar voltou para Agora"; fi
if grep -Fq 'regiões em destaque' "$NOW"; then fail "Contador de regiões voltou para Agora"; fi
if grep -Fq 'postDelayed' "$NOW" || grep -Fq 'Handler(' "$NOW"; then fail "Agora não pode ter ticker visual"; fi

# 0.28: regressões reais de campo fazem parte do contrato arquitetural.
grep -Fq 'setCompactPanel(compactPanel.isChecked)' "$FLOATING" || fail "Opção de painel compacto desapareceu das Configurações"
grep -Fq 'prefs.compactPanel()' "$BUBBLE" || fail "JourneyBubbleController deixou de ser dono do modo compacto"
grep -Fq 'sr028_region_search_toggle' "$NOW" || fail "Pesquisar região deixou de ser colapsável"
grep -Fq 'OfferIntegrityGuard028.accept' "$DISPATCHER" || fail "Gate de integridade 0.28 deixou de rodar antes do HUD/persistência"
grep -Fq 'offer_integrity_028' "$DIAGNOSTIC" || fail "Diagnóstico deixou de exportar offer_integrity_028"

# 0.29: confiabilidade M1 baseada no diagnóstico real da 0.28.
if grep -Fq 'resetOcrPipeline("watchdog_semantic_gap")' "$MEDIA"; then
  fail "Gap semântico voltou a reiniciar ML Kit; reset é reservado a stall/no-progress técnico"
fi
grep -Fq 'gap semântico observado; pipeline OCR preservado' "$MEDIA" || fail "Política semântica 0.29 desapareceu"
if grep -Fq 'TextRecognition' "$SHADOW_RECOVERY" || grep -Fq 'client.process(' "$SHADOW_RECOVERY"; then
  fail "ShadowOfferRecovery voltou a executar um segundo ML Kit concorrente"
fi
grep -Fq 'disabled_in_029' "$SHADOW_RECOVERY" || fail "Shadow recovery 0.29 deixou de declarar supressão"
grep -Fq 'OfferAdmissionGate029.admit' "$DISPATCHER" || fail "Ponto canônico de admissão do Dispatcher foi alterado"
grep -Fq 'other-text-fallback' "$ADMISSION" || fail "Fallback genérico sem rota perdeu proteção histórica 0.29"
grep -Fq 'offer_admission_029' "$DIAGNOSTIC" || fail "Diagnóstico deixou de exportar offer_admission_029"

# 0.30: Core Reader modular. O gate 0.29 vira fachada compatível; 0.30 decide.
grep -Fq 'OfferAdmissionGate030.admit' "$ADMISSION" || fail "Fachada 0.29 deixou de delegar para admissão 0.30"
grep -Fq 'runtime_delegated_to_030' "$ADMISSION" || fail "Diagnóstico 0.29 não declara delegação 0.30"
grep -Fq 'if (factorTenish(a.fare, b.fare)) add("fare")' "$ADMISSION030" || fail "Admissão 0.30 não protege conflito decimal da tarifa"
grep -Fq 'ACCEPT_CONFIRMED_DECIMAL_CHANGE' "$ADMISSION030" || fail "Mudança decimal confirmada perdeu estado explícito"
grep -Fq 'REJECT_DECIMAL_CONFLICT' "$ADMISSION030" || fail "Conflito decimal 0.30 deixou de ser retido"
grep -Fq 'Reader2Shadow030.observe' "$ADMISSION030" || fail "Reader 2 shadow deixou de observar antes da decisão oficial"

# Reader 2 shadow compartilha a observação espacial do M1 e não pode executar OCR próprio.
grep -Fq 'Reader2Shadow030.captureSpatial' "$UBER_SPATIAL" || fail "Handoff espacial M1 -> Reader 2 shadow desapareceu"
grep -Fq 'shared_m1_spatial_ocr' "$READER2" || fail "Reader 2 shadow perdeu declaração de fonte compartilhada"
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer('; do
  if grep -Fq "$forbidden" "$READER2"; then fail "Reader 2 shadow violou isolamento: $forbidden"; fi
done
grep -Fq 'official_persistence' "$READER2" || fail "Reader 2 não declara persistência oficial OFF"
grep -Fq 'admission_influence' "$READER2" || fail "Reader 2 não declara influência de admissão OFF"
grep -Fq 'reader2_shadow_030' "$DIAGNOSTIC" || fail "Diagnóstico não exporta reader2_shadow_030"
grep -Fq 'offer_admission_030' "$DIAGNOSTIC" || fail "Diagnóstico não exporta offer_admission_030"

# 0.30 Field2: papel monetário vem antes de criar âncoras/cards Uber.
grep -Fq 'MoneyRoleResolver030.primaryFare' "$UBER_DETECTOR" || fail "Detector Uber voltou a selecionar tarifa sem MoneyRoleResolver"
grep -Fq 'MoneyRoleResolver030.primarySpatialFareLines(lines)' "$UBER_SPATIAL" || fail "Parser Uber não filtra fare lines por papel monetário"
grep -Fq 'MoneyRoleResolver030.primarySpatialFareLines(lines)' "$SPATIAL_ISOLATION" || fail "Isolamento espacial voltou a usar todo R$ como divisor Uber"
grep -Fq 'PROMOTION_BONUS' "$MONEY_ROLES" || fail "MoneyRoleResolver perdeu papel explícito de promoção/bônus"
grep -Fq 'first_plain_money_in_card' "$MONEY_ROLES" || fail "MoneyRoleResolver perdeu identidade textual da tarifa principal"
grep -Fq 'Reader2MoneyShadow030.observe' "$UBER_SPATIAL" || fail "Shadow monetário Field2 deixou de observar cards Uber"
grep -Fq 'reader2_money_shadow_030_field2' "$DIAGNOSTIC" || fail "Diagnóstico não exporta shadow monetário Field2"
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer('; do
  if grep -Fq "$forbidden" "$READER2_MONEY"; then fail "Reader 2 money shadow violou isolamento: $forbidden"; fi
done

# 0.31: Reader 2 paralelo recebe o OCR espacial antes da formação/rejeição M1.
grep -Fq 'Reader2Parallel031.inspectFrame' "$UBER_SPATIAL" || fail "Reader 2 paralelo 0.31 não recebe o frame espacial"
grep -Fq 'Reader2Parallel031.observeM1' "$UBER_SPATIAL" || fail "Reader 2 paralelo 0.31 não compara o resultado M1"
r2_line=$(grep -n -m1 'Reader2Parallel031.inspectFrame' "$UBER_SPATIAL" | cut -d: -f1)
m1_line=$(grep -n -m1 'MoneyRoleResolver030.primarySpatialFareLines(lines)' "$UBER_SPATIAL" | cut -d: -f1)
if [[ -z "$r2_line" || -z "$m1_line" || "$r2_line" -ge "$m1_line" ]]; then
  fail "Reader 2 paralelo precisa observar antes da seleção de tarifa M1"
fi
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer(' 'OfferDispatcher' 'OfferParser.parse(' 'UberOfferDetector.detect(' 'MoneyRoleResolver030'; do
  if grep -Fq "$forbidden" "$READER2_PARALLEL"; then fail "Reader 2 paralelo violou independência/isolamento: $forbidden"; fi
done
grep -Fq 'independent_candidate_builder' "$READER2_PARALLEL" || fail "Reader 2 paralelo não declara builder independente"
grep -Fq 'can_observe_m1_rejected_frames' "$READER2_PARALLEL" || fail "Reader 2 paralelo perdeu observação de frames rejeitados pelo M1"
grep -Fq 'reader2_only_candidates' "$READER2_PARALLEL" || fail "Reader 2 paralelo perdeu contador reader2_only_candidates"
grep -Fq 'reader2_parallel_031' "$DIAGNOSTIC" || fail "Diagnóstico não exporta reader2_parallel_031"

# 0.32: Reader 2 accumulator combina apenas campos ausentes em memória e não promove oferta.
grep -Fq 'Reader2Accumulator032.observe(parallel' "$UBER_SPATIAL" || fail "Reader 2 accumulator 0.32 não recebe candidatos pré-M1"
grep -Fq 'Reader2Accumulator032.observeM1' "$UBER_SPATIAL" || fail "Reader 2 accumulator 0.32 não compara resultado oficial M1"
grep -Fq 'Reader2Accumulator032.resetRuntime' "$ADMISSION030" || fail "Accumulator 0.32 não é resetado no início da sessão Reader"
grep -Fq 'reader2_accumulator_032' "$DIAGNOSTIC" || fail "Diagnóstico não exporta reader2_accumulator_032"
grep -Fq 'promotion_effect", false' "$READER2_ACCUMULATOR" || fail "Accumulator não declara promoção oficial OFF"
grep -Fq 'observations >= 2' "$READER2_ACCUMULATOR" || fail "Promotion readiness perdeu exigência de repetição"
grep -Fq 'WINDOW_MS = 4_500L' "$READER2_ACCUMULATOR" || fail "Janela curta do accumulator 0.32 foi alterada sem contrato"
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer(' 'OfferDispatcher'; do
  if grep -Fq "$forbidden" "$READER2_ACCUMULATOR"; then fail "Reader 2 accumulator violou isolamento: $forbidden"; fi
done
[[ -f "$ROADMAP" ]] || fail "ROADMAP-CANONICO.md ausente"
# 0.32.1: consenso temporal confirma Reader2-only core-completo em frames distintos, ainda sem publicação.
grep -Fq 'Reader2Consensus0321.observe(accumulated, offers, frameHeight)' "$UBER_SPATIAL" || fail "Consensus 0.32.1 não recebe o resultado Reader 2 + M1"
grep -Fq 'Reader2Consensus0321.resetRuntime' "$ADMISSION030" || fail "Consensus 0.32.1 não é resetado no início da sessão Reader"
grep -Fq 'reader2_consensus_0321' "$DIAGNOSTIC" || fail "Diagnóstico não exporta reader2_consensus_0321"
grep -Fq 'controlled_hybrid_effect", false' "$READER2_CONSENSUS" || fail "Consensus 0.32.1 não declara Hybrid oficial OFF"
grep -Fq 'MIN_DISTINCT_MS = 450L' "$READER2_CONSENSUS" || fail "Consensus perdeu proteção contra repetição do mesmo frame"
grep -Fq 'WINDOW_MS = 8_000L' "$READER2_CONSENSUS" || fail "Janela temporal do consensus foi alterada sem contrato"
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer(' 'OfferDispatcher'; do
  if grep -Fq "$forbidden" "$READER2_CONSENSUS"; then fail "Reader 2 consensus violou isolamento: $forbidden"; fi
done
[[ -f "$ROADMAP" ]] || fail "ROADMAP-CANONICO.md ausente"
grep -Fq 'CANONICAL_VERSION: 2026-09-24.1' "$ROADMAP" || fail "Roadmap canônico sem versão esperada"
grep -Fq 'CURRENT_STAGE: 0.32.1 Field — Reader 2 Consensus' "$ROADMAP" || fail "Roadmap canônico não aponta a etapa atual"

# 0.31.1: Capture Resilience preserva a jornada e exige nova autorização para nova sessão.
grep -Fq 'CaptureResilience0311.sync(context)' "$RECOVERY_SUPERVISOR" || fail "Supervisor não sincroniza Capture Resilience"
grep -Fq 'journeyId == null || !projectionActive' "$RECOVERY_SUPERVISOR" || fail "Supervisor perdeu guard contra recuperação silenciosa sem MediaProjection"
grep -Fq 'sr0311_resume_capture' "$NOW" || fail "Agora não oferece Retomar captura quando a projeção cai"
grep -Fq 'CaptureResilience0311.markResumeRequested' "$DIAGNOSTIC_CONTROLS" || fail "Fluxo de reautorização não registra pedido de retomada"
grep -Fq 'createScreenCaptureIntent' "$DIAGNOSTIC_CONTROLS" || fail "Retomada deixou de solicitar nova autorização MediaProjection"
grep -Fq 'A jornada continua aberta' "$DIAGNOSTIC_CONTROLS" || fail "Mensagem de retomada não declara preservação da jornada"
grep -Fq 'capture_resilience_0311' "$DIAGNOSTIC" || fail "Diagnóstico não exporta capture_resilience_0311"
grep -Fq 'silent_token_reuse' "$CAPTURE_RESILIENCE" || fail "Capture Resilience não declara política de token"
grep -Fq 'requires_user_consent_for_new_projection' "$CAPTURE_RESILIENCE" || fail "Capture Resilience não declara novo consentimento"
if grep -Fq 'JourneyCoordinator.endJourney' "$DIAGNOSTIC_CONTROLS"; then
  fail "Fluxo de retomada não pode encerrar a jornada"
fi

# Histórico é área congelada nesta etapa e não pode receber acoplamento de Reader/admissão 0.30.
if grep -Fq 'Reader2Shadow030' "$HISTORY" || grep -Fq 'OfferAdmissionGate030' "$HISTORY" || grep -Fq 'MoneyRoleResolver030' "$HISTORY" || grep -Fq 'Reader2MoneyShadow030' "$HISTORY" || grep -Fq 'Reader2Parallel031' "$HISTORY" || grep -Fq 'Reader2Accumulator032' "$HISTORY" || grep -Fq 'Reader2Consensus0321' "$HISTORY"; then
  fail "Histórico recebeu acoplamento indevido ao Core Reader 0.30"
fi

# Os símbolos legados permanecem somente como stubs pequenos para compatibilidade.
for f in \
  NowPanelPolish0262.kt FieldValidationPolish0263.kt FieldValidationPolish0264.kt \
  FieldValidationPolish0265.kt BubbleRuntimePolish0265.kt ReleasePolish0270.kt \
  Rc35UiPolish027035.kt Rc36ClosingPolish027036.kt Rc361FieldFixes0270361.kt Rc36MainOverlay027036.kt; do
  bytes=$(wc -c < "$SRC/$f")
  if (( bytes > 4000 )); then fail "$f deixou de ser stub de compatibilidade ($bytes bytes)"; fi
done

echo "Architecture guard OK: Reader 2 Consensus 0.32.1 shadow, roadmap canônico versionado, Capture Resilience estável e Histórico congelado."
