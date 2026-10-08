#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if grep -Fq 'versionCode=96;' android/app/build.gradle.kts; then
 bash android/scripts/check-field-vc96.sh
 echo 'Radar guard histórico preservado pelos contratos cumulativos vc96.'
 exit 0
fi
READER_BASE=1421f512d966101cc6bbd0dfda52cf0626a9c4dd
BASE=9b3f42ba67fcb9b36d3ca1e673a6dd8138533d6e
APP=android/app/src/main/java/com/bigcorps/driveraimvp
fail(){ echo "Radar vc94 guard FAIL: $*" >&2; exit 1; }
if grep -Fq 'versionCode=95;' android/app/build.gradle.kts; then
 bash android/scripts/check-field-vc95.sh
 echo 'Radar v6 checks preserved through vc95 surface/Reader guard.'
 exit 0
fi
grep -Fq 'versionCode=94;' android/app/build.gradle.kts || fail 'versão'
grep -Fq 'versionName="0.33.17-field"' android/app/build.gradle.kts || fail 'nome versão'
python - "$READER_BASE" "$APP" <<'PY'
import fnmatch,pathlib,subprocess,sys
base,folder=sys.argv[1:]
patterns=['MediaProjectionOcrService.kt','UberM1TemporalRecoveryV1.kt','DriverPlatformOfferRouter.kt','UberScreenGate.kt','UberSpatialParser0221.kt','MoneyRoleResolver030.kt','OfferParser.kt','OfferDeduplicator.kt','OfferIntegrityGate*','OfferAdmission*','ReaderLab027036.kt','Reader2*','ShadowOfferRecovery*']
def frozen(name): return any(fnmatch.fnmatch(pathlib.Path(name).name,p) for p in patterns)
old=set(f for f in subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--',folder],text=True).splitlines() if frozen(f))
new=set(str(f) for f in pathlib.Path(folder).rglob('*') if f.is_file() and frozen(str(f)))
assert old and old==new,'Inventário Reader diferente do vc92'
for f in sorted(old):
 assert subprocess.check_output(['git','show',base+':'+f])==pathlib.Path(f).read_bytes(),'READER VC92 CONGELADO: '+f
print('Reader vc92 idêntico:',len(old),'arquivos')
PY

for token in 'onPostResume()' 'radarSurface.resume()' 'radarSurface.consume(request)' 'awaitRadarSurface' 'postOnAnimation' 'now_entry'; do
 grep -Fq "$token" "$APP/ConsolidatedMainActivity027037.kt" || fail "surface: $token"
done
for token in 'renderOrDefer()' 'renderPendingIfReady()' '!surfaceResumed' 'pendingGeneration!=requestGeneration' '!isAttachedToWindow' 'stage.height<=0'; do
 grep -Fq "$token" "$APP/RadarContextualPanelV1.kt" || fail "defer: $token"
done
grep -Fq -- '-10*60_000L..18*60_000L' "$APP/DestinationRadarAssistantBridgeV1.kt" || fail 'ETA window'
grep -Fq 'eta_delta_seconds' "$APP/RadarContextualDiagnosticV1.kt" || fail 'ETA diagnostic'
for token in 'surface_open_source' 'surface_open_state' 'surface_render_deferred' 'map_created_count' 'last_map_release_reason'; do
 grep -Fq "$token" "$APP/RadarContextualDiagnosticV1.kt" || fail "diagnostic: $token"
done
if grep -Eq 'openRadar\(|startActivity\(' "$APP/DestinationRadarRuntimeBridgeV1.kt"; then fail 'auto-launch'; fi
# Mudança MapLibre exclusivamente na razão de release; BAL permanece byte a byte.
python - "$BASE" "$APP" <<'PYCODE'
import pathlib,subprocess,sys
base,app=sys.argv[1:]
def old(name): return subprocess.check_output(['git','show',base+':'+app+'/'+name],text=True)
head=pathlib.Path(app+'/RadarMiniMapViewV1.kt').read_text()
head=head.replace('fun release(reason:String="released")','fun release()').replace('mapReleased(reason)','mapReleased()')
assert head==old('RadarMiniMapViewV1.kt'),'Provider/style/lifecycle MapLibre alterados além do diagnóstico'
launcher=pathlib.Path(app+'/RadarDestinationLauncherV1.kt').read_text()
assert launcher.split('        val creatorOptions =')[1]==old('RadarDestinationLauncherV1.kt').split('        val creatorOptions =')[1],'BAL alterado'
PYCODE
while IFS= read -r file; do
 case "$file" in
 CHANGELOG.md|README-CONTINUIDADE.md|android/app/build.gradle.kts|android/scripts/check-radar-contextual-v[23456].sh|android/app/src/test/java/com/bigcorps/driveraimvp/RadarContextualField[234567]ContractTest.kt) ;;
 "$APP"/ConsolidatedMainActivity027037.kt|"$APP"/RadarSurfaceCoordinatorV1.kt|"$APP"/RadarDestinationEntryV1.kt|"$APP"/RadarDestinationLauncherV1.kt|"$APP"/DestinationRadarAssistantBridgeV1.kt|"$APP"/DestinationRadarAssistantRendererV1.kt|"$APP"/RadarContextualPanelV1.kt|"$APP"/RadarContextualDiagnosticV1.kt|"$APP"/RadarContextualHomologationV1.kt|"$APP"/RadarMiniMapViewV1.kt) ;;
 *) fail "fora do escopo vc94: $file" ;;
 esac
done < <({ git diff --name-only "$BASE"; git ls-files --others --exclude-standard -- android/app/src android/scripts; } | sort -u)
echo 'Radar vc94 guard OK: surface/defer/ETA; 18 Reader congelados, backend/Supabase/workflows/BAL/provider intactos.'
