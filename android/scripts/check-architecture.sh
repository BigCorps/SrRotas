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
SCREENSHOT_STORE="$SRC/PrivateScreenshotStore.kt"
SCREENSHOT_GUARD="$SRC/ScreenshotStorageGuard033.kt"
SRUI="$SRC/SrUi023.kt"
DEVELOP_STATUS="$SRC/DevelopStatus033.kt"
RESPONSIVE_POLICY="$SRC/ResponsivePolicy033.kt"
UIKIT="$SRC/UiKit.kt"
PREFLIGHT="$SRC/JourneyPreflight027037.kt"
ROADMAP="../ROADMAP-CANONICO.md"

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
grep -Fq 'ReaderLabCombinedDiagnostic0270361.share' "$SETTINGS" || fail "Configurações deixou de usar o diagnóstico combinado"
grep -Fq 'RideOperationalStatus.NOT_COMPLETED' "$HISTORY" || fail "Histórico perdeu a possibilidade de desfazer corrida realizada"

if grep -Fq 'Visualizar Radar' "$NOW"; then fail "Visualizar Radar voltou para Agora"; fi
if grep -Fq 'regiões em destaque' "$NOW"; then fail "Contador de regiões voltou para Agora"; fi
if grep -Fq 'postDelayed' "$NOW" || grep -Fq 'Handler(' "$NOW"; then fail "Agora não pode ter ticker visual"; fi

# 0.28
grep -Fq 'setCompactPanel(compactPanel.isChecked)' "$FLOATING" || fail "Opção de painel compacto desapareceu"
grep -Fq 'prefs.compactPanel()' "$BUBBLE" || fail "JourneyBubbleController deixou de ser dono do modo compacto"
grep -Fq 'sr028_region_search_toggle' "$NOW" || fail "Pesquisar região deixou de ser colapsável"
grep -Fq 'OfferIntegrityGuard028.accept' "$DISPATCHER" || fail "Gate de integridade 0.28 deixou de rodar"
grep -Fq 'offer_integrity_028' "$DIAGNOSTIC" || fail "Diagnóstico deixou de exportar offer_integrity_028"

# 0.29
if grep -Fq 'resetOcrPipeline("watchdog_semantic_gap")' "$MEDIA"; then
  fail "Gap semântico voltou a reiniciar ML Kit"
fi
grep -Fq 'gap semântico observado; pipeline OCR preservado' "$MEDIA" || fail "Política semântica 0.29 desapareceu"
if grep -Fq 'TextRecognition' "$SHADOW_RECOVERY" || grep -Fq 'client.process(' "$SHADOW_RECOVERY"; then
  fail "ShadowOfferRecovery voltou a executar segundo ML Kit"
fi
grep -Fq 'disabled_in_029' "$SHADOW_RECOVERY" || fail "Shadow recovery 0.29 deixou de declarar supressão"
grep -Fq 'OfferAdmissionGate029.admit' "$DISPATCHER" || fail "Ponto canônico de admissão foi alterado"
grep -Fq 'other-text-fallback' "$ADMISSION" || fail "Fallback genérico sem rota perdeu proteção"
grep -Fq 'offer_admission_029' "$DIAGNOSTIC" || fail "Diagnóstico deixou de exportar offer_admission_029"

# 0.30
grep -Fq 'OfferAdmissionGate030.admit' "$ADMISSION" || fail "Fachada 0.29 deixou de delegar para 0.30"
grep -Fq 'runtime_delegated_to_030' "$ADMISSION" || fail "Diagnóstico 0.29 não declara delegação 0.30"
grep -Fq 'if (factorTenish(a.fare, b.fare)) add("fare")' "$ADMISSION030" || fail "Admissão 0.30 não protege conflito decimal da tarifa"
grep -Fq 'ACCEPT_CONFIRMED_DECIMAL_CHANGE' "$ADMISSION030" || fail "Mudança decimal confirmada perdeu estado explícito"
grep -Fq 'REJECT_DECIMAL_CONFLICT' "$ADMISSION030" || fail "Conflito decimal 0.30 deixou de ser retido"
grep -Fq 'Reader2Shadow030.observe' "$ADMISSION030" || fail "Reader 2 shadow deixou de observar antes da decisão oficial"

for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer('; do
  if grep -Fq "$forbidden" "$READER2"; then fail "Reader 2 shadow violou isolamento: $forbidden"; fi
done
grep -Fq 'Reader2Shadow030.captureSpatial' "$UBER_SPATIAL" || fail "Handoff espacial M1 -> Reader 2 desapareceu"
grep -Fq 'shared_m1_spatial_ocr' "$READER2" || fail "Reader 2 perdeu fonte compartilhada"
grep -Fq 'official_persistence' "$READER2" || fail "Reader 2 não declara persistência OFF"
grep -Fq 'admission_influence' "$READER2" || fail "Reader 2 não declara admissão OFF"

# Money Roles / Field2
grep -Fq 'MoneyRoleResolver030.primaryFare' "$UBER_DETECTOR" || fail "Detector Uber voltou a selecionar tarifa sem MoneyRoleResolver"
grep -Fq 'MoneyRoleResolver030.primarySpatialFareLines(lines)' "$UBER_SPATIAL" || fail "Parser Uber não filtra fare lines"
grep -Fq 'MoneyRoleResolver030.primarySpatialFareLines(lines)' "$SPATIAL_ISOLATION" || fail "Isolamento espacial voltou a usar todo R$"
grep -Fq 'PROMOTION_BONUS' "$MONEY_ROLES" || fail "MoneyRoleResolver perdeu promoção/bônus"
grep -Fq 'Reader2MoneyShadow030.observe' "$UBER_SPATIAL" || fail "Shadow monetário deixou de observar"
grep -Fq 'reader2_money_shadow_030_field2' "$DIAGNOSTIC" || fail "Diagnóstico perdeu shadow monetário"

# 0.31 Reader 2 Parallel
grep -Fq 'Reader2Parallel031.inspectFrame' "$UBER_SPATIAL" || fail "Reader 2 paralelo não recebe frame"
grep -Fq 'Reader2Parallel031.observeM1' "$UBER_SPATIAL" || fail "Reader 2 paralelo não compara M1"
r2_line=$(grep -n -m1 'Reader2Parallel031.inspectFrame' "$UBER_SPATIAL" | cut -d: -f1)
m1_line=$(grep -n -m1 'MoneyRoleResolver030.primarySpatialFareLines(lines)' "$UBER_SPATIAL" | cut -d: -f1)
if [[ -z "$r2_line" || -z "$m1_line" || "$r2_line" -ge "$m1_line" ]]; then fail "Reader 2 precisa observar antes do M1"; fi
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer(' 'OfferDispatcher' 'OfferParser.parse(' 'UberOfferDetector.detect(' 'MoneyRoleResolver030'; do
  if grep -Fq "$forbidden" "$READER2_PARALLEL"; then fail "Reader 2 paralelo violou isolamento: $forbidden"; fi
done

# 0.32 Accumulator
grep -Fq 'Reader2Accumulator032.observe(parallel' "$UBER_SPATIAL" || fail "Accumulator 0.32 não recebe candidatos"
grep -Fq 'Reader2Accumulator032.observeM1' "$UBER_SPATIAL" || fail "Accumulator 0.32 não compara M1"
grep -Fq 'Reader2Accumulator032.resetRuntime' "$ADMISSION030" || fail "Accumulator 0.32 não é resetado"
grep -Fq 'reader2_accumulator_032' "$DIAGNOSTIC" || fail "Diagnóstico não exporta accumulator"
grep -Fq 'promotion_effect", false' "$READER2_ACCUMULATOR" || fail "Accumulator não declara promoção OFF"
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer(' 'OfferDispatcher'; do
  if grep -Fq "$forbidden" "$READER2_ACCUMULATOR"; then fail "Accumulator violou isolamento: $forbidden"; fi
done

# 0.32.1 Consensus
grep -Fq 'Reader2Consensus0321.observe(accumulated, offers, frameHeight)' "$UBER_SPATIAL" || fail "Consensus não recebe Reader 2 + M1"
grep -Fq 'Reader2Consensus0321.resetRuntime' "$ADMISSION030" || fail "Consensus não é resetado"
grep -Fq 'reader2_consensus_0321' "$DIAGNOSTIC" || fail "Diagnóstico não exporta consensus"
grep -Fq 'controlled_hybrid_effect", false' "$READER2_CONSENSUS" || fail "Consensus não declara Hybrid OFF"
for forbidden in 'TextRecognition.getClient' 'TextRecognizer' 'client.process(' 'LocalStore' 'BackendClient' 'OverlayController' 'sendOffer(' 'saveOffer(' 'OfferDispatcher'; do
  if grep -Fq "$forbidden" "$READER2_CONSENSUS"; then fail "Consensus violou isolamento: $forbidden"; fi
done

# 0.31.1 Capture Resilience
grep -Fq 'CaptureResilience0311.sync(context)' "$RECOVERY_SUPERVISOR" || fail "Supervisor não sincroniza Capture Resilience"
grep -Fq 'journeyId == null || !projectionActive' "$RECOVERY_SUPERVISOR" || fail "Supervisor perdeu guard sem MediaProjection"
grep -Fq 'sr0311_resume_capture' "$NOW" || fail "Agora não oferece Retomar captura"
grep -Fq 'CaptureResilience0311.markResumeRequested' "$DIAGNOSTIC_CONTROLS" || fail "Fluxo de reautorização não registra pedido"
grep -Fq 'createScreenCaptureIntent' "$DIAGNOSTIC_CONTROLS" || fail "Retomada deixou de pedir autorização"
grep -Fq 'capture_resilience_0311' "$DIAGNOSTIC" || fail "Diagnóstico não exporta capture resilience"
if grep -Fq 'JourneyCoordinator.endJourney' "$DIAGNOSTIC_CONTROLS"; then fail "Retomada não pode encerrar jornada"; fi

# 0.33 Release Prep Pack 1: storage + responsividade + Develop Mode compacto.
[[ -f "$SCREENSHOT_GUARD" ]] || fail "ScreenshotStorageGuard033 ausente"
grep -Fq 'ScreenshotStorageGuard033.allow' "$SCREENSHOT_STORE" || fail "Screenshots não passam pelo dedupe 0.33"
grep -Fq 'MAX_VISIBLE_FILES = 180' "$SCREENSHOT_STORE" || fail "Retenção visível 0.33 ausente"
grep -Fq 'JPEG_QUALITY = 72' "$SCREENSHOT_STORE" || fail "Compressão JPEG 0.33 alterada sem contrato"
grep -Fq 'screenshot_storage_033' "$DIAGNOSTIC" || fail "Diagnóstico não exporta screenshot_storage_033"
grep -Fq 'ResponsivePolicy033.contentWidthDp' "$SRUI" || fail "SrUi não usa política responsiva 0.33"
grep -Fq 'maxDp: Int = 1440' "$RESPONSIVE_POLICY" || fail "Política responsiva 0.33 ausente"
grep -Fq 'maxDp: Int = 1440' "$SRUI" || fail "Cap responsivo voltou a ser estreito"
grep -Fq 'DevelopStatus033.label' "$NOW" || fail "Jornada Develop Mode não usa status compacto"
grep -Fq 'OK ✓ — M1/2.0' "$DEVELOP_STATUS" || fail "Status compacto M1/2.0 ausente"
grep -Fq 'setTextColor(palette(context).ink)' "$UIKIT" || fail "Input compartilhado perdeu contraste de texto"
grep -Fq 'setHintTextColor(if (dark)' "$UIKIT" || fail "Input compartilhado perdeu contraste dark de hint"
grep -Fq 'setTextColor(SrUi023.palette(context).ink)' "$PREFLIGHT" || fail "Pré-jornada perdeu contraste dark de input"

[[ -f "$ROADMAP" ]] || fail "ROADMAP-CANONICO.md ausente"
grep -Fq 'CANONICAL_VERSION: 2026-09-24.2' "$ROADMAP" || fail "Roadmap canônico sem versão 0.33"
grep -Fq 'CURRENT_STAGE: 0.33.0 Field — Release Prep Pack 1' "$ROADMAP" || fail "Roadmap não aponta 0.33"

# Histórico funcional permanece congelado; mudança compartilhada de largura é visual deliberada.
if grep -Fq 'Reader2Shadow030' "$HISTORY" || grep -Fq 'OfferAdmissionGate030' "$HISTORY" || grep -Fq 'Reader2Parallel031' "$HISTORY" || grep -Fq 'Reader2Accumulator032' "$HISTORY" || grep -Fq 'Reader2Consensus0321' "$HISTORY"; then
  fail "Histórico recebeu acoplamento indevido ao Reader experimental"
fi

for f in \
  NowPanelPolish0262.kt FieldValidationPolish0263.kt FieldValidationPolish0264.kt \
  FieldValidationPolish0265.kt BubbleRuntimePolish0265.kt ReleasePolish0270.kt \
  Rc35UiPolish027035.kt Rc36ClosingPolish027036.kt Rc361FieldFixes0270361.kt Rc36MainOverlay027036.kt; do
  bytes=$(wc -c < "$SRC/$f")
  if (( bytes > 4000 )); then fail "$f deixou de ser stub de compatibilidade ($bytes bytes)"; fi
done

echo "Architecture guard OK: 0.33 storage/UI avançam em paralelo ao Reader 2 Consensus; captura e Histórico preservados."
