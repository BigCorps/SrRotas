#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
READER_BASE=1421f512d966101cc6bbd0dfda52cf0626a9c4dd
TASK_BASE=72f91843068d5036e7265e5c97e67a635fa4ec7a
APP=android/app/src/main/java/com/bigcorps/driveraimvp
fail(){ echo "Radar Contextual vc93 guard FAIL: $*" >&2; exit 1; }
grep -Eq 'versionCode=(93|94);' android/app/build.gradle.kts || fail 'versionCode != 93'
grep -Eq 'versionName="0.33.(16|17)-field"' android/app/build.gradle.kts || fail 'versionName incorreta'
# Compara conteúdo E inventário ao vc92 (inclusive Reader2*/Gate*/Admission* novos).
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
for token in 'STRONG, DISCOVERY, REGION' 'cooldownKey' 'region_signal_generated' 'cooldown_mesma_corrida'; do
 grep -Fq "$token" "$APP/DestinationRadarAssistantBridgeV1.kt" || fail "assistente: $token"
done
for token in 'field_r4_migration_applied' 'assistant_kind' 'region_signal_generated' 'region_signal_rendered' 'region_view_clicked'; do
 grep -Fq "$token" "$APP/RadarContextualDiagnosticV1.kt" || fail "diagnóstico: $token"
done
grep -Fq 'migrateField93IfNeeded(context)' "$APP/RadarContextualIntegrationV1.kt" || fail 'migração desconectada'
grep -Fq 'manualStageChosen(context)' "$APP/RadarContextualFlagsV1.kt" || fail 'escolha manual sem proteção'
grep -Fq 'Nenhuma oportunidade mapeada nesta região no momento.' "$APP/RadarContextualPanelV1.kt" || fail 'empty state ausente'
grep -Fq 'VER REGIÃO DO DESTINO' "$APP/JourneyBubbleController.kt" || fail 'HUD zero opportunities'
if grep -Eq 'openRadar\(|startActivity\(|WindowManager\.(LayoutParams|addView)' "$APP/DestinationRadarRuntimeBridgeV1.kt"; then fail 'runtime auto-launch/overlay'; fi
grep -Fq 'put("promotion_effect", false)' "$APP/Reader2Accumulator032.kt" || fail 'Reader2 promotion'
grep -Fq 'put("controlled_hybrid_effect", false)' "$APP/Reader2Consensus0321.kt" || fail 'Controlled Hybrid'
# Launcher BAL e provider/lifecycle MapLibre permanecem sem mudança.
if grep -Fq 'versionCode=94;' android/app/build.gradle.kts; then
    bash android/scripts/check-radar-contextual-v6.sh
else
git diff --quiet "$READER_BASE" -- "$APP/RadarDestinationLauncherV1.kt" "$APP/RadarMiniMapViewV1.kt" || fail 'launcher/provider alterado'
while IFS= read -r file; do
 case "$file" in
 CHANGELOG.md|README-CONTINUIDADE.md|android/app/build.gradle.kts|android/scripts/check-radar-contextual-v[2345].sh|android/app/src/test/java/com/bigcorps/driveraimvp/RadarContextualField[26]ContractTest.kt) ;;
 "$APP"/DestinationRadarAssistantBridgeV1.kt|"$APP"/DestinationRadarAssistantRendererV1.kt|"$APP"/DestinationRadarInteractionV1.kt|"$APP"/JourneyBubbleController.kt|"$APP"/RadarContextualPanelV1.kt|"$APP"/RadarContextualDiagnosticV1.kt|"$APP"/RadarContextualFlagsV1.kt|"$APP"/RadarContextualIntegrationV1.kt) ;;
 *) fail "arquivo fora do escopo vc93: $file" ;;
 esac
done < <({ git diff --name-only "$TASK_BASE"; git ls-files --others --exclude-standard -- android/app/src android/scripts; } | sort -u)
fi
echo 'Radar Contextual vc93 guard OK: REGION, migração única, Reader congelado, backend/workflows preservados.'
