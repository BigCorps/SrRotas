#!/usr/bin/env bash
set -euo pipefail

BASE_SHA="923d2cd2cbdafec6d9137574a55e222e1641c189"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

fail(){ echo "Radar Contextual vc90 guard FAIL: $*" >&2; exit 1; }

APP="android/app/src/main/java/com/bigcorps/driveraimvp"
HUD="$APP/JourneyBubbleController.kt"
PANEL="$APP/RadarContextualPanelV1.kt"
MAP="$APP/RadarMiniMapViewV1.kt"
LAUNCHER="$APP/RadarDestinationLauncherV1.kt"
RENDERER="$APP/DestinationRadarAssistantRendererV1.kt"
RUNTIME="$APP/DestinationRadarRuntimeV1.kt"
DIAG="$APP/RadarContextualDiagnosticV1.kt"
GRADLE="android/app/build.gradle.kts"

grep -Eq 'versionCode=(90|91|92|93|94|95)(;|[[:space:]])' "$GRADLE" || fail "versionCode não é 90/91"
grep -Eq 'versionName="0.33.(13|14|15|16|17|18)-field"' "$GRADLE" || fail "versionName não é Field 3/4"

grep -Fq 'ESTOU NESSA CORRIDA' "$HUD" || fail "HUD não tem aceite explícito da corrida"
grep -Fq 'JourneyCoordinator.markDoingRide' "$HUD" || fail "HUD não liga oferta ao currentRide"
grep -Fq 'RadarDestinationLauncherV1.openRadar' "$HUD" || fail "HUD não tem CTA Radar seguro"

grep -Fq 'PendingIntent.getActivity' "$LAUNCHER" || fail "launcher não usa PendingIntent"
grep -Fq 'setPendingIntentCreatorBackgroundActivityStartMode' "$LAUNCHER" || fail "creator BAL opt-in ausente"
grep -Fq 'setPendingIntentBackgroundActivityStartMode' "$LAUNCHER" || fail "sender BAL opt-in ausente"
grep -Fq 'RadarDestinationLauncherV1.openRadar' "$RENDERER" || fail "Assistente real não usa launcher"
grep -Fq 'RadarDestinationLauncherV1.openDemo' "$RENDERER" || fail "Assistente DEMO não usa launcher"
if grep -Fq 'context.startActivity(' "$RENDERER"; then fail "renderer ainda abre Activity diretamente"; fi

grep -Fq 'private var map: RadarMiniMapViewV1? = null' "$PANEL" || fail "MapLibre ainda não é lazy"
grep -Fq 'private fun ensureMap()' "$PANEL" || fail "ensureMap ausente"
grep -Fq 'private fun releaseMap(reason:' "$PANEL" || fail "releaseMap ausente"
grep -Fq 'renderPendingIfReady()' "$PANEL" || fail "resposta UI ainda pode recriar mapa fora da tela"
grep -Fq 'return@getMapAsync' "$MAP" || fail "callback MapLibre pode sobreviver ao release"
grep -Fq 'return@setStyle' "$MAP" || fail "callback de estilo pode sobreviver ao release"
if grep -Fq 'private val map = RadarMiniMapViewV1(context)' "$PANEL"; then fail "MapLibre continua eager"; fi

grep -Fq 'RadarContextualDiagnosticV1.mapReady' "$MAP" || fail "map_ready não instrumentado"
grep -Fq 'RadarContextualDiagnosticV1.mapReleased' "$MAP" || fail "map_released não instrumentado"
grep -Fq 'RadarContextualDiagnosticV1.specResolved' "$RUNTIME" || fail "spec_resolved não instrumentado"
grep -Fq 'ride_mark_requested' "$DIAG" || fail "cadeia ride_mark ausente"
grep -Fq 'backend_query' "$DIAG" || fail "cadeia backend_query ausente"
grep -Fq 'map_active_ms_current' "$DIAG" || fail "telemetria lazy map ausente"

# O vc90 não pode corrigir a regressão percebida alterando parser/Reader no escuro.
# Ele reduz o risco introduzido pelo MapLibre eager e mantém o núcleo de leitura idêntico ao vc89.
FROZEN=(
  "android/app/src/main/java/com/bigcorps/driveraimvp/MediaProjectionOcrService.kt"
  "android/app/src/main/java/com/bigcorps/driveraimvp/DriverPlatformOfferRouter.kt"
  "android/app/src/main/java/com/bigcorps/driveraimvp/UberScreenGate.kt"
  "android/app/src/main/java/com/bigcorps/driveraimvp/UberSpatialParser0221.kt"
  "android/app/src/main/java/com/bigcorps/driveraimvp/OfferParser.kt"
  "android/app/src/main/java/com/bigcorps/driveraimvp/OfferDeduplicator.kt"
  "android/app/src/main/java/com/bigcorps/driveraimvp/ReaderLab027036.kt"
)
for f in "${FROZEN[@]}"; do
  if grep -Eq 'versionCode=(92|93|94|95)'  "$GRADLE" && [[ "$f" == *MediaProjectionOcrService.kt ]]; then
    # vc92 autoriza apenas recovery temporal; v4 verifica o patch exato do Service.
    bash android/scripts/check-radar-contextual-v4.sh
    continue
  fi
  git diff --quiet "$BASE_SHA" -- "$f" || fail "núcleo de leitura alterado: $f"
done

git diff --quiet "$BASE_SHA" -- 'supabase/migrations/**' || fail "migration alterada no vc90"
git diff --quiet "$BASE_SHA" -- 'backend/**' || fail "backend alterado no vc90"

echo "Radar Contextual vc90 guard OK: currentRide explícito, launcher BAL seguro, MapLibre lazy e Reader/parser/dedupe preservados."
