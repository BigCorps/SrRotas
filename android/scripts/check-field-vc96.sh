#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if grep -Fq 'versionCode=97;' android/app/build.gradle.kts; then
 exec bash android/scripts/check-field-vc97.sh
fi
python - <<'PY'
import fnmatch,pathlib,subprocess,re
base='5040760430d302479f4a389347ca493e49934581'
reader='1421f512d966101cc6bbd0dfda52cf0626a9c4dd'
app='android/app/src/main/java/com/bigcorps/driveraimvp/'
def old(p,sha=base): return subprocess.check_output(['git','show',sha+':'+p])
def source(n): return pathlib.Path(app+n).read_text()
def require(s,*tokens):
 for t in tokens: assert t in s,'Contrato ausente: '+t
patterns=['MediaProjectionOcrService.kt','UberM1TemporalRecoveryV1.kt','DriverPlatformOfferRouter.kt','UberScreenGate.kt','UberSpatialParser0221.kt','MoneyRoleResolver030.kt','OfferParser.kt','OfferDeduplicator.kt','OfferIntegrityGate*','OfferAdmission*','ReaderLab027036.kt','Reader2*','ShadowOfferRecovery*']
def frozen(p): return any(fnmatch.fnmatch(pathlib.Path(p).name,x) for x in patterns)
original={p for p in subprocess.check_output(['git','ls-tree','-r','--name-only',reader,'--',app],text=True).splitlines() if frozen(p)}
current={str(p) for p in pathlib.Path(app).rglob('*') if p.is_file() and frozen(str(p))}
assert original==current and len(original)==18
for p in original:
 assert pathlib.Path(p).read_bytes()==old(p,base if p.endswith('/MediaProjectionOcrService.kt') else reader),'Reader alterado: '+p
for n in ['FieldCaptureLifecycleV1.kt','CaptureResilience0311.kt','DiagnosticControls0270.kt','ConsolidatedMainActivity027037.kt',
 'JourneyCoordinator.kt','JourneyStateMachine.kt','JourneyActionReceiver.kt','HudRideReplacementConfirmationV1.kt',
 'RadarSurfaceCoordinatorV1.kt','RadarDestinationLauncherV1.kt','RadarDestinationEntryV1.kt',
 'DestinationRadarAssistantBridgeV1.kt','DestinationRadarAssistantRendererV1.kt','DestinationRadarRuntimeBridgeV1.kt',
 'DestinationRadarRuntimeV1.kt','RadarContextualFlagsV1.kt','ReportSelection0211.kt','PrivateScreenshotStore.kt']:
 assert pathlib.Path(app+n).read_bytes()==old(app+n),'Fora do escopo: '+n
require(source('Reader2Accumulator032.kt'),'put("promotion_effect", false)')
require(source('Reader2Consensus0321.kt'),'put("controlled_hybrid_effect", false)')
assert source('MediaProjectionOcrService.kt').count('TextRecognition.getClient(')==1
build=pathlib.Path('android/app/build.gradle.kts').read_text()
require(build,'versionCode=96;','versionName="0.33.19-field"','android-sdk-opengl:13.6.1')
assert build.replace('versionCode=96;','versionCode=95;').replace('versionName="0.33.19-field"','versionName="0.33.18-field"').encode()==old('android/app/build.gradle.kts')
mini=source('RadarMiniMapViewV1.kt'); bitmap=source('RadarMapBitmapSurfaceV1.kt'); episode=source('RadarMapLastEpisodeV1.kt')
require(mini,'https://tiles.openfreemap.org/styles/liberty','addOnCameraIdleListener','mapBitmapSurface',
 'requestBitmap()','renderSurfaceReady()','map_fully_callback','stateSnapshot','postDelayed(stateSnapshot, 8_000L)')
assert mini.split('    private fun addRadius(')[1]==old(app+'RadarMiniMapViewV1.kt').decode().split('    private fun addRadius(')[1]
fully=mini.split('mapView.addOnDidFinishRenderingMapListener')[1].split('mapView.addOnRenderErrorListener')[0]
assert 'View.GONE' not in fully
require(bitmap,'map.snapshot {','MapSnapshotter.Options(w, h)','withStyle(styleUri)','withCameraPosition(camera)',
 'pixelForLatLng','snapshotter?.cancel()','snapshotter !== task','token == generation','12_000L','3_000L','450L',
 'live_snapshot_blank','snapshotter_failed','fallback_text_shown','setImageBitmap(display)','isClickable = false','isFocusable = false',
 'visibility = INVISIBLE','1024.0','healthy(snapshot.bitmap)')
for token in ['FileOutputStream','compress(','TextRecognition','getSharedPreferences','startActivity(']: assert token not in bitmap
require(episode,'getSharedPreferences','putString(KEY','app_version','episode_started_at','demo','real','last_error','released_reason')
for token in ['centerLat','centerLng','destinationLabel','markers','Bitmap','OCR','http://','https://']: assert token not in episode
require(source('RadarContextualDiagnosticV1.kt'),'put("last_map_episode", RadarMapLastEpisodeV1.read(context))')
require(mini,'Mapa indisponível neste aparelho.','Maps/Waze continuam funcionando.')
hud=source('JourneyBubbleController.kt')
card=hud.split('    private fun offerRow(')[1].split('    private fun operationalRideControls(')[0]
assert 'operationalRideControls(' not in card
require(card,'if (expandedOfferId == offer.localId)','expandedOffer(context, offer, outcome)','ReportSelection0211.toggle')
expanded=hud.split('    private fun expandedOffer(')[1].split('    private fun routeActions(')[0]
require(expanded,'box.addView(operationalRideControls(context, offer, outcome))')
# Confirmação/troca e conclusão intactas; somente posição do bloco mudou.
assert hud.split('    private fun operationalRideControls(')[1].split('    private fun expandedOffer(')[0]==old(app+'JourneyBubbleController.kt').decode().split('    private fun operationalRideControls(')[1].split('    private fun expandedOffer(')[0]
require(hud,'store.offerByLocalId(it.localOfferId)','HudDisplayOffersV1.select(recent, sourceOffer, prefs.offerCount())','hudCurrentRideSourceMissing')
require(source('HudDisplayOffersV1.kt'),'distinctBy(id).take(limit.coerceAtLeast(0))')
footer=hud.split('    private fun footerControls(')[1].split('    private fun rebuildMessageRail(')[0]
assert 'Reiniciar captura' not in footer and 'CaptureRecoveryActivity' not in footer
chrome=source('FloatingWindowChrome023.kt');require(chrome,'"Reiniciar captura"','DiagnosticQuickActions0270.restartReading(context)')
assert chrome.replace('"Reiniciar captura"','"Reiniciar leitura"').encode()==old(app+'FloatingWindowChrome023.kt')
local=source('LocalStore.kt'); addition='''    /** Consulta pontual do card da currentRide; não muda captura, parser ou admissão. */
    fun offerByLocalId(localId: String): RideOffer? =
        queryOffers("local_id = ?", arrayOf(localId), "created_at_ms desc", 1).firstOrNull()

'''
assert local.replace(addition,'').encode()==old(app+'LocalStore.kt')
panel=source('RadarContextualPanelV1.kt')
assert panel.replace('RadarMiniMapViewV1(context, demo = demoMode).also','RadarMiniMapViewV1(context).also').encode()==old(app+'RadarContextualPanelV1.kt')
require(panel,'!isAttachedToWindow','stage.height<=0','pendingGeneration!=requestGeneration','renderOrDefer()')
assert not re.search(r'openRadar\(|startActivity\(',source('DestinationRadarRuntimeBridgeV1.kt'))
allowed={'README-CONTINUIDADE.md','CHANGELOG.md','android/app/build.gradle.kts','android/scripts/check-field-vc95.sh','android/scripts/check-field-vc96.sh',
 *('android/scripts/check-radar-contextual-v'+str(n)+'.sh' for n in range(2,7)),
 *('android/app/src/test/java/com/bigcorps/driveraimvp/'+n for n in ['Field95RegressionContractTest.kt','Field96RegressionContractTest.kt','RadarContextualField2ContractTest.kt']),
 *(app+n for n in ['RadarMiniMapViewV1.kt','RadarMapBitmapSurfaceV1.kt','RadarMapBitmapHealthV1.kt','RadarMapLastEpisodeV1.kt',
 'RadarContextualDiagnosticV1.kt','RadarContextualPanelV1.kt','JourneyBubbleController.kt','HudDisplayOffersV1.kt','LocalStore.kt','FloatingWindowChrome023.kt'])}
changed=set(subprocess.check_output(['git','diff','--name-only',base],text=True).splitlines())
changed.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','--','android/app/src','android/scripts','docs'],text=True).splitlines())
assert changed<=allowed,'Diff fora de vc96: '+str(changed-allowed)
assert not subprocess.check_output(['git','diff','--name-only',base,'--','backend','supabase','migrations','.github/workflows'],text=True).strip()
require(pathlib.Path('README-CONTINUIDADE.md').read_text(),'FIELD VC95 — NOVO REPORT DE CAMPO','Offer Screenshot Review V1','0.33.19-field / versionCode 96')
print('Field vc96 OK: bitmap/persistência/HUD; Reader vc92 e capture vc95 congelados; provider/backend/workflows intactos.')
PY
