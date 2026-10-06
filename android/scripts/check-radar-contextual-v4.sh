#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
BASE_SHA=7660e9ff4bd60c6ec1c0b6b06b04f79dacd54110
APP=android/app/src/main/java/com/bigcorps/driveraimvp
fail(){ echo "Radar Contextual vc92 guard FAIL: $*" >&2; exit 1; }
grep -Fq 'versionCode=92' android/app/build.gradle.kts || fail 'versionCode != 92'
grep -Fq 'versionName="0.33.15-field"' android/app/build.gradle.kts || fail 'versionName incorreta'
for token in 'STRONG, DISCOVERY' 'backend_strong' 'discovery_opportunity_available' 'outside_discovery_eta_window' 'cooldown_mesma_oportunidade'; do
 grep -Fq "$token" "$APP/DestinationRadarAssistantBridgeV1.kt" || fail "assistente: $token"
done
if grep -Eq 'openRadar\(|startActivity\(|WindowManager\.(LayoutParams|addView)' "$APP/DestinationRadarRuntimeBridgeV1.kt"; then
 fail 'runtime auto-launch/overlay'
fi
for token in 'demo_preview_opened' 'demo_preview_rendered'; do
 grep -Fq "$token" "$APP/RadarContextualDiagnosticV1.kt" || fail "DEMO: $token"
done
grep -Fq 'smoothScrollTo(0, 0)' "$APP/RadarContextualPanelV1.kt" || fail 'DEMO sem scroll'
grep -Fq 'm1Temporal.observe' "$APP/MediaProjectionOcrService.kt" || fail 'recovery desconectado'
grep -Fq 'OfferParser.parse(' "$APP/MediaProjectionOcrService.kt" || fail 'parser oficial ausente'
grep -Fq 'offers.associateWith(OfferIntegrityGate027033::assess)' "$APP/MediaProjectionOcrService.kt" || fail 'integrity bypass'
grep -Fq 'dispatcher.submitStabilized(completeOffers)' "$APP/MediaProjectionOcrService.kt" || fail 'dispatch bypass'
if grep -Eq 'Reader2|TextRecognizer|Bitmap|TextRecognition|File\(' "$APP/UberM1TemporalRecoveryV1.kt"; then fail 'recovery usa Reader2/imagem/OCR/persistência'; fi
grep -Fq 'put("promotion_effect", false)' "$APP/Reader2Accumulator032.kt" || fail 'promotion Reader2'
grep -Fq 'put("controlled_hybrid_effect", false)' "$APP/Reader2Consensus0321.kt" || fail 'Controlled Hybrid'
# Todos os demais arquivos ficam idênticos à branch vc91, incluindo parser/gates,
# 99, Reader2, backend, migrations, workflows, finanças e provider MapLibre.
while IFS= read -r file; do
 case "$file" in
 CHANGELOG.md|README-CONTINUIDADE.md|android/app/build.gradle.kts|android/scripts/check-radar-contextual-v[234].sh|android/app/src/test/java/com/bigcorps/driveraimvp/RadarContextualField[25]ContractTest.kt) ;;
 "$APP"/DestinationRadarAssistantBridgeV1.kt|"$APP"/DestinationRadarAssistantRendererV1.kt|"$APP"/DestinationRadarRuntimeBridgeV1.kt|"$APP"/DestinationRadarRuntimeV1.kt|"$APP"/JourneyBubbleController.kt|"$APP"/RadarContextualPanelV1.kt|"$APP"/ConsolidatedMainActivity027037.kt|"$APP"/RadarContextualDiagnosticV1.kt|"$APP"/RadarHudTrace024.kt|"$APP"/UberM1TemporalRecoveryV1.kt|"$APP"/MediaProjectionOcrService.kt) ;;
 *) fail "arquivo fora do escopo vc92: $file" ;;
 esac
done < <(git diff --name-only "$BASE_SHA")
# Prova de single-heavy-OCR: nenhuma fábrica, client.process ou ImageInput nova.
python - "$BASE_SHA" "$APP/MediaProjectionOcrService.kt" <<'PY'
import re,subprocess,sys
base=subprocess.check_output(['git','show',sys.argv[1]+':'+sys.argv[2]],text=True)
head=open(sys.argv[2]).read()
for pattern in [r'TextRecognition\.getClient\(',r'client\.process\(',r'InputImage\.fromBitmap\(']:
 assert len(re.findall(pattern,head))==len(re.findall(pattern,base)),pattern
# O patch no Service apenas insere recovery/contadores antes dos gates existentes.
head=head.replace('        m1Temporal.clear()\n','')
head=head.replace('    private val m1Temporal = UberM1TemporalRecoveryV1()\n\n','')
head=head.replace('                var routed = DriverPlatformOfferRouter.parse(','                val routed = DriverPlatformOfferRouter.parse(')
head=re.sub(r'                // Recovery M1 usa somente.*?(?=                RadarHudTrace024.recordRoute\()', '',head,flags=re.S)
head=re.sub(r'                if \(temporalOffers.isNotEmpty\(\)\) RadarHudTrace024.record\(.*?\n                \)\n','',head,flags=re.S)
assert head==base,'Mudança no pipeline Service fora dos blocos recovery/diagnóstico'
PY
echo 'Radar Contextual vc92 guard OK: strong/discovery, HUD sem auto-launch, DEMO, recovery temporal single-OCR e gates preservados.'
