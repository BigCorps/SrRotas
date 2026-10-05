#!/usr/bin/env bash
set -euo pipefail
SRC="app/src/main/java/com/bigcorps/driveraimvp"
fail(){ echo "::error::$1"; exit 1; }
GRADLE="app/build.gradle.kts"; APP="$SRC/SrRotasApplication.kt"; MAIN="$SRC/MainActivity.kt"; NOW="$SRC/NowPanel027037.kt"; SETTINGS="$SRC/SettingsPanel027037.kt"; HISTORY="$SRC/RideHistoryPanel027035.kt"; READER="$SRC/ReaderLab027036.kt"; DIAGNOSTIC="$SRC/ReaderLabCombinedDiagnostic0270361.kt"; ACTIVE_ASSISTANT_POLISH="$SRC/ActiveAssistantPolish0265.kt"; DEVICE_IDENTITY="$SRC/DeviceIdentity10B.kt"; ACCESS_RESOLVER="$SRC/AccessResolver10B.kt"; EXPOSURE_REPAIR="$SRC/ExposureQueueRepair0338.kt"; CONTINUITY="../README-CONTINUIDADE.md"

VERSION_CODE="$(grep -oE 'versionCode=[0-9]+' "$GRADLE" | head -1 | cut -d= -f2)"
VERSION_NAME="$(grep -oE 'versionName="[^"]+"' "$GRADLE" | head -1 | sed -E 's/versionName="([^"]+)"/\1/')"
[[ -n "$VERSION_CODE" && -n "$VERSION_NAME" ]] || fail "Não foi possível resolver versionName/versionCode do Gradle"

grep -Fq 'ConsolidatedMainActivity027037' "$MAIN" || fail "MainActivity deixou de usar shell consolidado"
grep -Fq 'getString("mode", MODE_M1)' "$READER" || fail "M1 deixou de ser modo seguro padrão"
grep -Fq 'ReaderLabCombinedDiagnostic0270361.share' "$SETTINGS" || fail "Configurações perdeu diagnóstico combinado"
grep -Fq 'RideOperationalStatus.NOT_COMPLETED' "$HISTORY" || fail "Histórico perdeu correção de corrida"
grep -Fq 'ActiveAssistantPolish0265.install(this)' "$APP" || fail "Assistente Ativo não instalado"
grep -Fq 'label = "IGNORAR"' "$ACTIVE_ASSISTANT_POLISH" || fail "Assistente perdeu IGNORAR"
grep -Fq 'label = "VER"' "$ACTIVE_ASSISTANT_POLISH" || fail "Assistente perdeu VER"
[[ -f "$DEVICE_IDENTITY" ]] || fail "DeviceIdentity10B ausente"
[[ -f "$ACCESS_RESOLVER" ]] || fail "AccessResolver10B ausente"
grep -Fq 'Settings.Secure.ANDROID_ID' "$DEVICE_IDENTITY" || fail "Device identity deixou de usar ANDROID_ID"
if grep -Fq 'TelephonyManager' "$DEVICE_IDENTITY" || grep -Fq 'getImei' "$DEVICE_IDENTITY" || grep -Fq 'Build.getSerial' "$DEVICE_IDENTITY"; then fail "Identificador invasivo reintroduzido"; fi
grep -Fq 'AccessResolver10B.sync(this)' "$APP" || fail "Application não adota Access Resolver"
grep -Fq 'access_resolver_10b' "$DIAGNOSTIC" || fail "Diagnóstico não exporta Access Resolver"
[[ -f "$EXPOSURE_REPAIR" ]] || fail "ExposureQueueRepair0338 ausente"
grep -Fq 'put("sync_state", 2)' "$EXPOSURE_REPAIR" || fail "Reparo de exposure deixou de preservar em quarentena"
grep -Fq 'invalid_exposure_fields' "$EXPOSURE_REPAIR" || fail "Reparo perdeu validação de fields"
grep -Fq 'invalid_exposure_window' "$EXPOSURE_REPAIR" || fail "Reparo perdeu validação de janela"
grep -Fq 'ExposureQueueRepair0338.run(this, force=true)' "$APP" || fail "Reparo de exposure não roda no startup"
grep -Fq 'exposure_queue_repair_0338' "$DIAGNOSTIC" || fail "Diagnóstico não exporta reparo de exposure"

[[ -f "$CONTINUITY" ]] || fail "README-CONTINUIDADE.md ausente"
grep -Fq 'SINGLE_SOURCE_OF_TRUTH: true' "$CONTINUITY" || fail "README canônico não se declara fonte única"
grep -Fq 'CURRENT_HEAD_STAGE:' "$CONTINUITY" || fail "README canônico sem HEAD stage"
grep -Fq "HEAD atual: \`$VERSION_NAME / versionCode $VERSION_CODE\`" "$CONTINUITY" || fail "README canônico não registra o HEAD atual $VERSION_NAME/vc$VERSION_CODE"
grep -Fq 'Base Android homologada: `0.33.6-field / versionCode 83`' "$CONTINUITY" || fail "README não preserva field homologado"

FORBIDDEN="$(find .. -maxdepth 1 -type f \( \
  -name 'APLICAR-*' -o -name 'FASE-*' -o -name 'PHASE-*' -o -name 'QA-*' -o \
  -name 'TESTE-*' -o -name 'VALIDACAO-*' -o -name 'VALIDATION_REPORT*' -o \
  -name 'LEIA-PRIMEIRO*' -o -name 'LEIA_PRIMEIRO*' -o -name 'FIX_*' -o \
  -name 'HANDOFF-*' -o -name 'ROADMAP-*' -o \
  -name 'PATCH-MANIFEST*' -o -name 'PATCH_MANIFEST*' -o \
  -name 'MANIFEST-*' -o -name 'SRROTAS-*MANIFEST*' \
\) -print)"
[[ -z "$FORBIDDEN" ]] || fail "Documentação operacional paralela proibida na raiz: $FORBIDDEN"

if grep -Fq 'Reader2Shadow030' "$HISTORY" || grep -Fq 'OfferAdmissionGate030' "$HISTORY" || grep -Fq 'Reader2Parallel031' "$HISTORY" || grep -Fq 'Reader2Accumulator032' "$HISTORY" || grep -Fq 'Reader2Consensus0321' "$HISTORY"; then fail "Histórico recebeu acoplamento Reader experimental"; fi
echo "Architecture guard OK: M1/History congelados; README canônico único; HEAD $VERSION_NAME/vc$VERSION_CODE; Access Resolver preservado."
