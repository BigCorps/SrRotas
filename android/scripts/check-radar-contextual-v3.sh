#!/usr/bin/env bash
set -euo pipefail
BASE_SHA="6f1d0754ca3870177c616c26774f199246bc3d66"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if grep -Fq 'versionCode=97;' android/app/build.gradle.kts; then
 exec bash android/scripts/check-field-vc97.sh
fi
if grep -Fq 'versionCode=96;' android/app/build.gradle.kts; then
 bash android/scripts/check-field-vc96.sh
 echo 'Radar guard histórico preservado pelos contratos cumulativos vc96.'
 exit 0
fi
fail(){ echo "Radar Contextual vc91 guard FAIL: $*" >&2; exit 1; }
APP="android/app/src/main/java/com/bigcorps/driveraimvp"
PANEL="$APP/RadarContextualPanelV1.kt"
DIAG="$APP/RadarHudTrace024.kt"
grep -Eq 'versionCode=(91|92|93|94|95);' android/app/build.gradle.kts || fail 'versionCode != 91'
grep -Eq 'versionName="0.33.(14|15|16|17|18)-field"' android/app/build.gradle.kts || fail 'versionName incorreta'
if grep -Eq 'private (val|var) status' "$PANEL"; then fail 'status compartilhado'; fi
for token in 'val loading = SrUi023.body' 'addView(loading)' 'val generation = ++requestGeneration' 'generation != requestGeneration' 'return@fetch' 'invalidateRequests()' '!isShown' 'spec?.localOfferId != value.localOfferId' 'private var map: RadarMiniMapViewV1? = null' 'private fun releaseMap(reason:'; do
  grep -Fq "$token" "$PANEL" || fail "proteção ausente: $token"
done
grep -Fq 'ride.localOfferId' "$APP/RadarDestinationContextV1.kt" || fail 'currentRide.localOfferId perdido'
grep -Fq 'put("promotion_effect", false)' "$APP/Reader2Accumulator032.kt" || fail 'Reader2 promotion effect'
grep -Fq 'put("controlled_hybrid_effect", false)' "$APP/Reader2Consensus0321.kt" || fail 'Controlled Hybrid ligado'
for token in 'candidate_no_offer_count' 'integrity_reject_count' 'blocked_offers' 'fare_lines_zero' 'fare_present_cluster_zero' 'uber_anchor_samples' 'uber_anchor_geometry_pairs_lt_2' '99_anchor_samples' 'navigation_noise_samples'; do
  grep -Fq "$token" "$DIAG" || fail "diagnóstico ausente: $token"
done
# Whitelist relativa à main inicial: congela todo código operacional fora do escopo,
# incluindo M1, Reader2, backend, migrations, workflows e contratos públicos.
if grep -Eq 'versionCode=(92|93|94|95)'  android/app/build.gradle.kts; then
  # Exceção explícita vc92: os invariantes acima continuam; v4 congela o novo escopo.
  bash android/scripts/check-radar-contextual-v4.sh
else
while IFS= read -r file; do
  case "$file" in
    CHANGELOG.md|README-CONTINUIDADE.md|android/app/build.gradle.kts|android/scripts/check-radar-contextual-v2.sh|android/scripts/check-radar-contextual-v3.sh|android/app/src/test/java/com/bigcorps/driveraimvp/RadarContextualField2ContractTest.kt|android/app/src/test/java/com/bigcorps/driveraimvp/RadarContextualField4ContractTest.kt|android/app/src/main/java/com/bigcorps/driveraimvp/RadarContextualPanelV1.kt|android/app/src/main/java/com/bigcorps/driveraimvp/RadarHudTrace024.kt) ;;
    *) fail "arquivo fora do escopo vc91: $file" ;;
  esac
done < <(git diff --name-only "$BASE_SHA")
fi
echo 'Radar Contextual vc91 guard OK: reentrada, geração, mapa lazy e diagnóstico; M1/Reader2/backend preservados.'
