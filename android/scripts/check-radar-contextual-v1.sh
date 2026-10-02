#!/usr/bin/env bash
set -euo pipefail
SRC="app/src/main/java/com/bigcorps/driveraimvp"
fail(){ echo "::error::$1"; exit 1; }

required=(
  "$SRC/RadarContextualFlagsV1.kt"
  "$SRC/RadarContextualPanelV1.kt"
  "$SRC/RadarDestinationContextV1.kt"
  "$SRC/RadarDestinationEntryV1.kt"
  "$SRC/DestinationRadarRuntimeV1.kt"
  "$SRC/DestinationRadarAssistantRendererV1.kt"
  "$SRC/RadarContextualHomologationV1.kt"
  "$SRC/RadarPanel027035.kt"
)
for f in "${required[@]}"; do [[ -f "$f" ]] || fail "Radar Contextual: arquivo ausente $f"; done

MAIN="$SRC/ConsolidatedMainActivity027037.kt"
APP="$SRC/SrRotasApplication.kt"
FLAGS="$SRC/RadarContextualFlagsV1.kt"
CTX="$SRC/RadarDestinationContextV1.kt"
RENDERER="$SRC/DestinationRadarAssistantRendererV1.kt"

grep -Fq 'RadarContextualHostV1' "$MAIN" || fail "Shell não implementa host contextual"
grep -Fq 'RadarPanel027035' "$MAIN" || fail "Rollback RadarPanel027035 foi removido"
grep -Fq 'RadarContextualPanelV1' "$MAIN" || fail "Painel contextual não está no shell"
grep -Fq 'RadarDestinationEntryV1' "$MAIN" || fail "CTA de destino não está no fluxo"
grep -Fq 'RadarContextualIntegrationV1.syncRuntime(this)' "$APP" || fail "Runtime não é sincronizado no startup"
grep -Fq 'snapshot.currentRide' "$CTX" || fail "Destino deixou de partir da corrida ativa"
grep -Fq 'ride.localOfferId' "$CTX" || fail "Destino perdeu vínculo currentRide.localOfferId"
grep -Fq 'getBoolean(UI, false)' "$FLAGS" || fail "uiEnabled deixou de iniciar false"
grep -Fq 'getBoolean(RUNTIME, false)' "$FLAGS" || fail "runtimeEnabled deixou de iniciar false"
grep -Fq 'getBoolean(ASSISTANT, false)' "$FLAGS" || fail "assistantEnabled deixou de iniciar false"
grep -Fq 'JourneyBubbleController.show' "$RENDERER" || fail "Assistente contextual não reutiliza HUD existente"
if grep -Fq 'WindowManager.LayoutParams' "$RENDERER" || grep -Fq 'getSystemService(WindowManager' "$RENDERER"; then
  fail "Assistente contextual reintroduziu WindowManager próprio"
fi

grep -Fq 'RadarContextualIntegrationV1.rollback' "$SRC/RadarContextualHomologationV1.kt" || fail "Rollback Field ausente"
grep -Fq 'DEMO · sem dados reais' "$SRC/RadarContextualPanelV1.kt" || fail "Preview DEMO não está identificado"

echo "Radar Contextual integration guard OK: legado preservado, flags OFF por padrão, currentRide/localOfferId preservado, runtime/assistente conectados."
