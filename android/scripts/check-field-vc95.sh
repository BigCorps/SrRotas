#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if grep -Fq 'versionCode=96;' android/app/build.gradle.kts; then
 exec bash android/scripts/check-field-vc96.sh
fi
python - <<'PY'
import fnmatch,pathlib,re,subprocess
base='a91c0de7145b53355893a15828628aea5a4db911'
reader='1421f512d966101cc6bbd0dfda52cf0626a9c4dd'
app='android/app/src/main/java/com/bigcorps/driveraimvp/'
def old(path,sha=base): return subprocess.check_output(['git','show',sha+':'+path],text=True)
def source(name): return pathlib.Path(app+name).read_text()
def require(text,*tokens):
 for t in tokens: assert t in text,'Contrato ausente: '+t
patterns=['MediaProjectionOcrService.kt','UberM1TemporalRecoveryV1.kt','DriverPlatformOfferRouter.kt','UberScreenGate.kt','UberSpatialParser0221.kt','MoneyRoleResolver030.kt','OfferParser.kt','OfferDeduplicator.kt','OfferIntegrityGate*','OfferAdmission*','ReaderLab027036.kt','Reader2*','ShadowOfferRecovery*']
def frozen(path): return any(fnmatch.fnmatch(pathlib.Path(path).name,p) for p in patterns)
original={f for f in subprocess.check_output(['git','ls-tree','-r','--name-only',reader,'--',app],text=True).splitlines() if frozen(f)}
current={str(p) for p in pathlib.Path(app).rglob('*') if p.is_file() and frozen(str(p))}
assert original==current and len(original)==18,'Inventário Reader vc92 alterado'
for f in sorted(original):
 if f.endswith('/MediaProjectionOcrService.kt'): continue
 assert pathlib.Path(f).read_bytes()==subprocess.check_output(['git','show',reader+':'+f]),'Reader congelado: '+f
# Única exceção: preâmbulo de sessão e inserções EXATAS de lifecycle/diagnóstico.
# Todo o restante, incluindo resets normais da sessão e onImage/queue/process/gates/dedupe,
# precisa voltar byte a byte ao Service funcional vc92 após remover essas inserções.
s=source('MediaProjectionOcrService.kt'); baseline=old(app+'MediaProjectionOcrService.kt',reader)
require(s,'EXTRA_FORCE_FRESH_PROJECTION','reuseExisting(forceFresh','getMediaProjection(resultCode, resultData)',
 'currentCallback(mediaProjection, projection)','stale_projection_callback_ignored',
 'releaseProjection("projection_superseded", endJourneyIfOwned = false)')
start='    private fun startProjectionFromIntent(intent: Intent) {'
end='        frameChangeDetector.reset()\n        performance.reset()'
a=s.index(start);b=s.index(end,a);oa=baseline.index(start);ob=baseline.index(end,oa)
# Este trecho não pode introduzir trabalho Reader; só autorização/teardown da sessão.
assert not re.search(r'(?i)(parser|recogniz|processBitmap|onImage|prepareForOcr|queueOrProcess|dedup|admission|sampling|bitmap|m1Temporal)',s[a:b])
s=s[:a]+baseline[oa:ob]+s[b:]
insertions=[
 '        const val EXTRA_FORCE_FRESH_PROJECTION = "force_fresh_projection"\n',
 '                FieldCaptureRecoveryDiagnosticV1.record("technical_recovery_requested")\n',
 '        if (forceFresh) FieldCaptureRecoveryDiagnosticV1.record("fresh_projection_started")\n',
 '                    if (!FieldCaptureLifecycleV1.currentCallback(mediaProjection, projection)) return\n',
 '''                    if (!FieldCaptureLifecycleV1.currentCallback(mediaProjection, projection)) {
                        FieldCaptureRecoveryDiagnosticV1.record("stale_projection_callback_ignored")
                        return
                    }
''',
]
for insertion in insertions:
 assert insertion in s,'Lifecycle inesperado: '+insertion
 s=s.replace(insertion,'')
s=s.replace('releaseProjection("virtual_display_start_failed", endJourneyIfOwned = !forceFresh)','releaseProjection("virtual_display_start_failed")')
s=s.replace('    private fun reconfigureCapturedContent(width: Int, height: Int) {\n        val handler = worker ?: return\n        val expectedProjection = projection ?: return\n        handler.post {\n            if (!FieldCaptureLifecycleV1.currentCallback(expectedProjection, projection)) return@post','    private fun reconfigureCapturedContent(width: Int, height: Int) {\n        val handler = worker ?: return\n        handler.post {\n            if (projection == null) return@post')

assert s==baseline,'Alteração no Service fora da exceção estrita lifecycle/recovery'
# Single OCR, R2 shadow e Hybrid OFF permanecem no conteúdo congelado.
require(source('Reader2Accumulator032.kt'),'put("promotion_effect", false)')
require(source('Reader2Consensus0321.kt'),'put("controlled_hybrid_effect", false)')
gradle=pathlib.Path('android/app/build.gradle.kts').read_text()
require(gradle,'versionCode=95;','versionName="0.33.18-field"','13.6.1')
assert gradle.replace('versionCode=95;','versionCode=94;').replace('versionName="0.33.18-field"','versionName="0.33.17-field"')==old('android/app/build.gradle.kts'),'Gradle fora de versão/dependência alterada'
mini=source('RadarMiniMapViewV1.kt')
require(mini,'https://tiles.openfreemap.org/styles/liberty','addOnDidFinishLoadingMapListener',
 'MapView.OnDidFinishRenderingFrameListener','addOnDidFinishRenderingMapListener','addOnRenderErrorListener',
 'addOnDidFailLoadingMapListener','renderGate.fully(fully, renderSurfaceReady())','8_000L','removeCallbacks(renderTimeout)',
 '!renderSurfaceReady()','showMapFallback(','val eligible = styleReady && renderGate.submitted')
style=mini.split('ready.setStyle(')[1].split('    override fun onMeasure')[0]
assert 'View.GONE' not in style,'Fallback escondido em styleLoaded'
# Cartografia, desenho de POIs, estilo e dependência não mudam.
assert mini.split('    private fun addRadius(')[1]==old(app+'RadarMiniMapViewV1.kt').split('    private fun addRadius(')[1]
render=mini.split('    private fun renderOnMap()')[1].split('    private fun renderSurfaceReady')[0]
assert render.split('        ready.clear()')[1].strip()==old(app+'RadarMiniMapViewV1.kt').split('        ready.clear()')[1].split('    private fun addRadius(')[0].strip()
require(source('JourneyCoordinator.kt'),'explicitRideSelection(', 'RideSelection.SAME',
 'beginTransaction()','setTransactionSuccessful()','endTransaction()','replaced_by_new_ride')
hud=source('JourneyBubbleController.kt'); require(hud,'card.addView(operationalRideControls','REALIZADA','NÃO REALIZADA',
 'CORRIDA ATIVA','ESTOU NESSA CORRIDA','ReportSelection0211.toggle','ReaderLab027036.m1Enabled(context)',
 'CaptureRecoveryActivity0270.open(context, source = "hud_quick_restart")')
assert 'OUTRA CORRIDA ATIVA' not in hud
require(source('DiagnosticControls0270.kt'),'createScreenCaptureIntent','EXTRA_FORCE_FRESH_PROJECTION, true')
require(source('ConsolidatedMainActivity027037.kt'),'EXTRA_FORCE_FRESH_PROJECTION, wasRecovery',
 'onPostResume()','radarSurface.resume()','radarSurface.consume(request)','awaitRadarSurface','postOnAnimation','now_entry')
main=source('ConsolidatedMainActivity027037.kt')
for insertion in [
 '        if (wasRecovery) FieldCaptureRecoveryDiagnosticV1.record("fresh_projection_authorized")\n',
 '            putExtra(MediaProjectionOcrService.EXTRA_FORCE_FRESH_PROJECTION, wasRecovery)\n',
 '        if (recovery) FieldCaptureRecoveryDiagnosticV1.record("fresh_projection_requested")\n',
 '        if (wasRecovery && existingJourney == null) {\n            toast("Não há jornada aberta para recuperar.")\n            return\n        }\n',
]:
 assert insertion in main
 main=main.replace(insertion,'')
assert main==old(app+'ConsolidatedMainActivity027037.kt'),'Activity surface lifecycle fora do escopo'
require(source('RadarContextualDiagnosticV1.kt'),'eta_delta_seconds','surface_open_source','surface_render_deferred',
 'map_style_loaded','map_loading_finished','map_first_frame','map_fully_rendered','map_render_timeout',
 'map_render_error','map_load_error','last_map_render_error')
require(source('RadarContextualPanelV1.kt'),'renderOrDefer()','renderPendingIfReady()','!surfaceResumed',
 'pendingGeneration!=requestGeneration','!isAttachedToWindow','stage.height<=0')
# Surface/assistant/launcher não são escopo vc95, incluindo BAL e once-per-ride/ETA.
for f in ['RadarSurfaceCoordinatorV1.kt','RadarContextualPanelV1.kt','RadarDestinationLauncherV1.kt',
 'RadarDestinationEntryV1.kt','DestinationRadarAssistantBridgeV1.kt','DestinationRadarAssistantRendererV1.kt',
 'DestinationRadarRuntimeBridgeV1.kt','DestinationRadarRuntimeV1.kt','RadarContextualFlagsV1.kt']:
 assert source(f)==old(app+f),'Regressão fora de escopo: '+f
assert not re.search(r'openRadar\(|startActivity\(',source('DestinationRadarRuntimeBridgeV1.kt')),'Auto-launch'
allowed={'CHANGELOG.md','README-CONTINUIDADE.md','docs/contracts/CAPTURE-RESILIENCE.md','android/app/build.gradle.kts',
 'android/scripts/check-field-vc95.sh',
 *('android/scripts/check-radar-contextual-v'+str(n)+'.sh' for n in range(2,7)),
 'android/app/src/test/java/com/bigcorps/driveraimvp/Field95RegressionContractTest.kt',
 'android/app/src/test/java/com/bigcorps/driveraimvp/RadarContextualField2ContractTest.kt',
 'android/app/src/test/java/com/bigcorps/driveraimvp/RadarContextualField6ContractTest.kt',
 *(app+n for n in ['RadarMiniMapViewV1.kt','RadarMapRenderGateV1.kt','RadarContextualDiagnosticV1.kt',
 'JourneyBubbleController.kt','JourneyCoordinator.kt','JourneyStateMachine.kt','MediaProjectionOcrService.kt',
 'DiagnosticControls0270.kt','ConsolidatedMainActivity027037.kt','CaptureResilience0311.kt','FieldCaptureLifecycleV1.kt','HudRideReplacementConfirmationV1.kt'])}
changed=set(subprocess.check_output(['git','diff','--name-only',base],text=True).splitlines())
changed.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','--','android/app/src','android/scripts','docs'],text=True).splitlines())
assert changed<=allowed,'Fora do escopo vc95: '+str(changed-allowed)
assert not subprocess.check_output(['git','diff','--name-only',base,'--','backend','supabase','migrations','.github/workflows'],text=True).strip()
# Pós-review: patch permanece mínimo e não altera versão nem contrato de captura já aprovado.
review='d3863382e5d22c4fe70adba9f3173144c1748d3f'
patch_allowed={'README-CONTINUIDADE.md','CHANGELOG.md','android/scripts/check-field-vc95.sh',
 'android/app/src/test/java/com/bigcorps/driveraimvp/Field95RegressionContractTest.kt',
 *(app+n for n in ['FieldCaptureLifecycleV1.kt','MediaProjectionOcrService.kt','RadarMapRenderGateV1.kt',
 'RadarMiniMapViewV1.kt','RadarContextualDiagnosticV1.kt','JourneyBubbleController.kt','JourneyCoordinator.kt',
 'JourneyStateMachine.kt','HudRideReplacementConfirmationV1.kt'])}
patch=set(subprocess.check_output(['git','diff','--name-only',review],text=True).splitlines())
patch.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','--','android/app/src','android/scripts','docs'],text=True).splitlines())
assert patch<=patch_allowed,'Fora das três ressalvas review: '+str(patch-patch_allowed)
assert source('MediaProjectionOcrService.kt').split(end,1)[1]==old(app+'MediaProjectionOcrService.kt',review).split(end,1)[1],'Service fora do preâmbulo lifecycle'
rejection=source('MediaProjectionOcrService.kt').split('        if (freshRejected)')[1].split('        if (projection != null)')[0]
require(rejection,'shouldStopAfterRejectedFresh(freshRejected, projection != null)','fresh_projection_rejected_no_session','if (consentAuthorized) stopForeground(Service.STOP_FOREGROUND_REMOVE)','stopSelf()')
assert 'startAsForeground(' not in rejection and 'releaseProjection(' not in rejection
require(source('JourneyCoordinator.kt'),'allowReplace: Boolean = false','expectedCurrentRideId: String? = null','previous?.localOfferId != expectedCurrentRideId')
assert source('JourneyActionReceiver.kt')==old(app+'JourneyActionReceiver.kt',review),'Notification fora de escopo'
require(hud,'TROCAR PARA ESTA CORRIDA','CONFIRMAR TROCA','allowReplace = confirmation == HudRideReplacementConfirmationV1.Click.CONFIRMED','replacementConfirmation.clear()')
require(source('HudRideReplacementConfirmationV1.kt'),'WINDOW_MS = 7_000L','p.currentId != currentId','nowMs >= p.expiresAt')
assert 'SharedPreferences' not in source('HudRideReplacementConfirmationV1.kt')
require(mini,'armLoadTimeout()','15_000L','renderGate.renderWaiting','cancelMapTimeouts()','map_late_render_recovered')
attach=mini.split('    override fun onAttachedToWindow()')[1].split('    override fun onDetachedFromWindow()')[0]
assert 'armRenderTimeout()' not in attach
require(source('RadarMapRenderGateV1.kt'),'timedOut = true','timedOut = false','loadingWaiting','renderWaiting')
assert 'fail()' not in source('RadarMapRenderGateV1.kt').split('    fun timeout()')[1].split('    fun release()')[0]
for path in ['README-CONTINUIDADE.md','CHANGELOG.md']:
 head=pathlib.Path(path).read_text(); prev=old(path,review)
 if path.startswith('README'):
  marker='vc95 — Field vc95 —'; ending='\nvc94 —'
  assert head.split(marker)[0]==prev.split(marker)[0]
  assert head.split(ending)[1:]==prev.split(ending)[1:],'README histórico alterado'
 else:
  assert head.split('\n## 0.33.17')[1:]==prev.split('\n## 0.33.17')[1:],'CHANGELOG histórico alterado'
# Patch FGS isolado: mapa, turnover, callers e todo código Reader ficam intactos.
fgs_base='9d06f58fb97c6ab78351c593d6f923ce91928679'
fgs_allowed={'README-CONTINUIDADE.md','CHANGELOG.md','android/scripts/check-field-vc95.sh',
 'android/app/src/test/java/com/bigcorps/driveraimvp/Field95RegressionContractTest.kt',
 app+'MediaProjectionOcrService.kt',app+'FieldCaptureLifecycleV1.kt'}
assert set(subprocess.check_output(['git','diff','--name-only',fgs_base],text=True).splitlines())<=fgs_allowed
service=source('MediaProjectionOcrService.kt')
opening=service.split(start)[1].split(end)[0]
require(opening,'val consentAuthorized = resultCode == Activity.RESULT_OK && resultData != null',
 'shouldAcknowledgeForeground(consentAuthorized, projection != null)')
assert opening.index('startAsForeground()') < opening.index('val journeyOpen') < opening.index('if (freshRejected)')
assert 'getMediaProjection(' not in opening
assert rejection.index('stopForeground(') < rejection.index('stopSelf()')
for caller in ['ConsolidatedMainActivity027037.kt','DiagnosticControls0270.kt']:
 text=source(caller).split('override fun onActivityResult(')[1]
 denied=text.split('if (resultCode != RESULT_OK || data == null) {')[1].split('\n        }')[0]
 assert 'return' in denied and 'startForegroundService(' not in denied
 assert text.index('if (resultCode != RESULT_OK || data == null)') < text.index('startForegroundService(')
 assert source(caller)==old(app+caller,fgs_base)
print('Field vc95 guard OK: 17 Reader byte-idênticos + Service lifecycle-only; surface/BAL/provider/backend/workflows preservados.')
PY
