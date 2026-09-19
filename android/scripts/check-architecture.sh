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

# Os símbolos legados permanecem somente como stubs pequenos para compatibilidade.
for f in \
  NowPanelPolish0262.kt FieldValidationPolish0263.kt FieldValidationPolish0264.kt \
  FieldValidationPolish0265.kt BubbleRuntimePolish0265.kt ReleasePolish0270.kt \
  Rc35UiPolish027035.kt Rc36ClosingPolish027036.kt Rc361FieldFixes0270361.kt Rc36MainOverlay027036.kt; do
  bytes=$(wc -c < "$SRC/$f")
  if (( bytes > 4000 )); then fail "$f deixou de ser stub de compatibilidade ($bytes bytes)"; fi
done

echo "Architecture guard OK: shell único, sem polishes visuais concorrentes."
