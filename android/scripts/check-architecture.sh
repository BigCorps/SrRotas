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
MONEY_ROLES="$SRC/MoneyRoleResolver030.kt"
SPATIAL_ISOLATION="$SRC/OfferSpatialIsolation0221.kt"
UBER_DETECTOR="$SRC/UberOfferDetector.kt"
UBER_SPATIAL="$SRC/UberSpatialParser0221.kt"

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

# Histórico é área congelada nesta etapa e não pode receber acoplamento de Reader/admissão 0.30.
if grep -Fq 'Reader2Shadow030' "$HISTORY" || grep -Fq 'OfferAdmissionGate030' "$HISTORY" || grep -Fq 'MoneyRoleResolver030' "$HISTORY" || grep -Fq 'Reader2MoneyShadow030' "$HISTORY"; then
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

echo "Architecture guard OK: Core Reader 0.30 Field2, Money Roles ativo, shadows isolados e Histórico congelado."
