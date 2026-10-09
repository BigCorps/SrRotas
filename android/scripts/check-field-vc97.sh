#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
python - <<'PY'
import pathlib,subprocess,fnmatch,re
base='877bb13406ecbe002439facb0980ebfe3272e2cd'
reader='1421f512d966101cc6bbd0dfda52cf0626a9c4dd'
app='android/app/src/main/java/com/bigcorps/driveraimvp/'
test='android/app/src/test/java/com/bigcorps/driveraimvp/'
def old(p,sha=base): return subprocess.check_output(['git','show',sha+':'+p]).decode()
def src(n): return pathlib.Path(app+n+'.kt').read_text()
def need(s,*tokens):
 for t in tokens: assert t in s,'Contrato ausente: '+t
patterns=['MediaProjectionOcrService.kt','UberM1TemporalRecoveryV1.kt','DriverPlatformOfferRouter.kt','UberScreenGate.kt','UberSpatialParser0221.kt','MoneyRoleResolver030.kt','OfferParser.kt','OfferDeduplicator.kt','OfferIntegrityGate*','OfferAdmission*','ReaderLab027036.kt','Reader2*','ShadowOfferRecovery*']
def frozen(p): return any(fnmatch.fnmatch(pathlib.Path(p).name,x) for x in patterns)
inventory={p for p in subprocess.check_output(['git','ls-tree','-r','--name-only',reader,'--',app],text=True).splitlines() if frozen(p)}
current={str(p) for p in pathlib.Path(app).rglob('*.kt') if frozen(str(p))}
assert inventory==current and len(inventory)==18
for p in inventory:
 assert pathlib.Path(p).read_text()==old(p,base if p.endswith('/MediaProjectionOcrService.kt') else reader),'Reader alterado: '+p
for n in ['FieldCaptureLifecycleV1','CaptureResilience0311','DriverAccessibilityService',
 'SpatialOfferParser','UberDigitizationCaptureService026','JourneyStateMachine','JourneyActionReceiver','HudRideReplacementConfirmationV1',
 'ReportSelection0211','RadarSurfaceCoordinatorV1','RadarDestinationLauncherV1','RadarDestinationEntryV1','DestinationRadarInteractionV1',
 'RadarContextualFlagsV1','RadarContextualIntegrationV1','RadarMapRenderGateV1','RadarMapBitmapSurfaceV1','RadarMapBitmapHealthV1','RadarMapLastEpisodeV1']:
 assert src(n)==old(app+n+'.kt'),'Congelado: '+n
need(src('Reader2Accumulator032'),'put("promotion_effect", false)')
need(src('Reader2Consensus0321'),'put("controlled_hybrid_effect", false)')
assert src('MediaProjectionOcrService').count('TextRecognition.getClient(')==1
# Exact native lifecycle remains untouched; callsite insertion only fences the new diagnostic worker.
recovery=src('DiagnosticControls0270').replace('        if (!ScreenshotRescanGateV1.allowForeignStart(this)) { finish(); return }\n','')
assert recovery==old(app+'DiagnosticControls0270.kt')
coordinator=src('JourneyCoordinator').replace('        FieldPipelineTraceV1.event(app, localOfferId, "RIDE_SELECTED", "selected")\n','')
assert coordinator==old(app+'JourneyCoordinator.kt')
local=src('LocalStore')
for insertion in ['    private val traceContext = context.applicationContext\n\n','            FieldPipelineTraceV1.offer(traceContext, o)\n','        FieldPipelineTraceV1.context(traceContext, localId, context)\n']:
 local=local.replace(insertion,'')
assert local==old(app+'LocalStore.kt'),'Persistência/admissão oficial alterada'
assert src('RadarDestinationContextV1').split('    fun fromOffer(')[1]==old(app+'RadarDestinationContextV1.kt').split('    fun fromOffer(')[1]
assert src('RadarContextualClientV1').split('    private fun parse(')[1].split('    private fun request(')[0]==old(app+'RadarContextualClientV1.kt').split('    private fun parse(')[1].split('    private fun request(')[0]
bridge=src('DestinationRadarAssistantBridgeV1')
assert bridge.split('    // JVM puro;')[1]==old(app+'DestinationRadarAssistantBridgeV1.kt').split('    // JVM puro;')[1],'Política/cooldown mudou'
mini=src('RadarMiniMapViewV1')
mini=mini.replace(', private val traceOfferId: String? = null','')
mini=mini.replace('''            visible = {
                FieldPipelineTraceV1.event(context,traceOfferId,"MAP_BITMAP_VISIBLE","bitmap_visible_cartography_unverified")
                fallback.visibility = View.GONE
            },''','            visible = { fallback.visibility = View.GONE },')
assert mini==old(app+'RadarMiniMapViewV1.kt'),'Mapa fora do escopo diagnóstico'
build=pathlib.Path('android/app/build.gradle.kts').read_text()
assert build.replace('versionCode=97;','versionCode=96;').replace('versionName="0.33.20-field"','versionName="0.33.19-field"')==old('android/app/build.gradle.kts')
need(build,'versionCode=97;','versionName="0.33.20-field"','android-sdk-opengl:13.6.1')
ui=src('OfferRescanActivityV1');gate=src('ScreenshotRescanGateV1');trace=src('FieldPipelineTraceV1')
need(ui,'ACTION_OPEN_DOCUMENT','FLAG_GRANT_READ_URI_PERMISSION','EXTRA_ALLOW_MULTIPLE,false','uri.scheme != "content"',
 'Confirmar rescan desta imagem','PrivateScreenshotIndexV1.resolve','ScreenshotRescanGateV1.acquire(this)',
 'Tasks.await(recognizer.process','SpatialOfferParser.parse','offers.size <= 1','image.recycle()','recognizer.close()',
 'ScreenshotRescanGateV1.complete()','WeakReference(this)','Desativar rescan (rollback)')
for t in ['saveOffer(', 'markDoingRide(', 'sendOffer(', 'BackendClient', 'OfferAdmission', 'FLAG_GRANT_WRITE', 'takePersistableUriPermission', 'MediaStore', 'compress(']: assert t not in ui,t
assert ui.count('TextRecognition.getClient(')==1
assert 'ScreenshotRescanGateV1.complete()' not in ui.split('override fun onDestroy()')[1]
need(gate,'@Synchronized fun foreignStart()', '@Synchronized fun acquire(', 'foreignRequested', 'repo.currentJourneyId().isNotBlank()',
 'repo.isProjectionActive()', 'getRunningServices', 'MODE_M1', '!enabled(context) || liveBlocked(context)')
for n in ['ConsolidatedMainActivity027037','DiagnosticControls0270','UberDigitizationActivity026','HistoricalScreenshotImporter']:
 need(src(n),'ScreenshotRescanGateV1.allowForeignStart(')
need(src('SrRotasApplication'),'ScreenshotRescanGateV1.seed(this)')
need(src('ScreenshotRescanImageV1'),'MAX_BYTES = 16 * 1024 * 1024','40_000_000L','inJustDecodeBounds = true','inSampleSize = sample','TAG_ORIENTATION')
need(src('PrivateScreenshotIndexV1'),'fullOfferId','it.parentFile == dir && it.isFile','index.length() > 30')
need(trace,'MAX_EPISODES = 12','MAX_EVENTS = 64','RETENTION_MS = 7','ArrayBlockingQueue<Runnable>(128)',
 'putString("episodes"','layers.put(safeStage','stage.takeIf { it in stages }','reason.takeIf { it in reasons }',
 'inferred_only_for_persisted_capture_offers_not_rejected_frames','zero_reason_not_exposed_by_backend','SHA-256')
for t in ['rawText','put("lat','put("lng','put("label','put("token','put("bitmap','.message','BackendClient']: assert t not in trace,t
need(src('RadarContextualDiagnosticV1'),'FieldPipelineTraceV1.snapshot(context)','RadarMapLastEpisodeV1.read(context)')
need(src('DestinationRadarAssistantRendererV1'),'ASSISTANT_RENDER_ATTEMPT','OnPreDrawListener','card.isAttachedToWindow','card.isShown','card.width > 0','card.height > 0','ASSISTANT_VIEW_CLICKED')
need(src('RadarContextualPanelV1'),'pendingGeneration!=requestGeneration','!isAttachedToWindow','renderOrDefer()','traceOfferId = value.localOfferId')
assert not re.search(r'openRadar\(|startActivity\(',src('DestinationRadarRuntimeBridgeV1')+src('DestinationRadarRuntimeV1'))
chrome=src('FloatingWindowChrome023')
need(chrome,'HudJourneyMenuStateV1()','"Jornada · expandir/recolher"','"Foto / Rescan"',
 'closeJourney(); actions.play()', 'closeJourney(); actions.pause()', 'closeJourney(); actions.stop()',
 'closeJourney(); DiagnosticQuickActions0270.restartReading(context)','collapseJourneyMenu(root: View?)')
hud=src('JourneyBubbleController')
assert 'operationalRideControls(' not in hud.split('    private fun offerRow(')[1].split('    private fun operationalRideControls(')[0]
need(hud,'box.addView(operationalRideControls(context, offer, outcome))','prefs.offerCount()', 'ReportSelection0211.toggle',
 'allowReplace = confirmation == HudRideReplacementConfirmationV1.Click.CONFIRMED')
assert hud.split('    private fun operationalRideControls(')[1].split('    private fun expandedOffer(')[0]==old(app+'JourneyBubbleController.kt').split('    private fun operationalRideControls(')[1].split('    private fun expandedOffer(')[0]
allowed={'README-CONTINUIDADE.md','CHANGELOG.md','android/app/build.gradle.kts','android/app/src/main/AndroidManifest.xml',
 *('android/scripts/check-radar-contextual-v'+str(i)+'.sh' for i in range(2,7)),
 *('android/scripts/check-field-vc'+str(i)+'.sh' for i in [95,96,97]),
 *(test+n+'.kt' for n in ['Field96RegressionContractTest','Field97RegressionContractTest','RadarContextualField2ContractTest']),
 *(app+n+'.kt' for n in ['FieldPipelineFactsV1','FieldPipelineTraceV1','ScreenshotRescanGateV1','ScreenshotRescanImageV1','ScreenshotRescanComparisonV1','OfferRescanActivityV1','PrivateScreenshotIndexV1','HudJourneyMenuStateV1',
 'LocalStore','JourneyCoordinator','RadarDestinationContextV1','RadarContextualClientV1','DestinationRadarRuntimeV1','DestinationRadarAssistantBridgeV1','RadarContextualDiagnosticV1','DestinationRadarAssistantRendererV1','RadarContextualPanelV1','RadarMiniMapViewV1','PrivateScreenshotStore','SrRotasApplication','ConsolidatedMainActivity027037','DiagnosticControls0270','UberDigitizationActivity026','HistoricalScreenshotImporter','FloatingWindowChrome023','JourneyBubbleController','DestinationRadarRuntimeBridgeV1'])}
changed=set(subprocess.check_output(['git','diff','--name-only',base],text=True).splitlines())
assert changed<=allowed,'Fora do escopo: '+str(changed-allowed)
for d in ['backend','supabase','.github/workflows','migrations']:
 assert not subprocess.check_output(['git','diff',base,'--',d]),d
manifest=pathlib.Path('android/app/src/main/AndroidManifest.xml').read_text()
assert manifest.replace('        <activity android:name=".OfferRescanActivityV1" android:exported="false" />\n','')==old('android/app/src/main/AndroidManifest.xml')
print('Field vc97 guard OK: Reader 18/18, captura/mapa/política congelados; trace/rescan/HUD com escopo restrito; backend/workflows intactos.')
PY
