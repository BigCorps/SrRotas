#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
python - <<'PY'
import fnmatch,pathlib,subprocess
base='91322b81aa5f20d0703b9a7a70d417e7ddb44d01'
reader='1421f512d966101cc6bbd0dfda52cf0626a9c4dd'
app='android/app/src/main/java/com/bigcorps/driveraimvp/'
tests='android/app/src/test/java/com/bigcorps/driveraimvp/'
def source(name): return pathlib.Path(app+name+'.kt').read_text()
def old(path,sha=base): return subprocess.check_output(['git','show',sha+':'+path])
def require(txt,*tokens):
 for token in tokens:
  assert token in txt, 'Faltou contrato: '+token
gradle=pathlib.Path('android/app/build.gradle.kts').read_text()
require(gradle,'versionCode=98;','versionName="0.33.21-field"','org.maplibre.gl:android-sdk-opengl:13.6.1')
patterns=['MediaProjectionOcrService.kt','UberM1TemporalRecoveryV1.kt','DriverPlatformOfferRouter.kt',
 'UberScreenGate.kt','UberSpatialParser0221.kt','MoneyRoleResolver030.kt','OfferParser.kt',
 'OfferDeduplicator.kt','OfferIntegrityGate*','OfferAdmission*','ReaderLab027036.kt',
 'Reader2*','ShadowOfferRecovery*']
def frozen(path): return any(fnmatch.fnmatch(pathlib.Path(path).name,pat) for pat in patterns)
inventory={p for p in subprocess.check_output(['git','ls-tree','-r','--name-only',reader,'--',app],text=True).splitlines() if frozen(p)}
current={str(p) for p in pathlib.Path(app).rglob('*.kt') if frozen(str(p))}
assert len(inventory)==18 and inventory==current,'Inventário Reader alterado'
for p in inventory:
 assert pathlib.Path(p).read_bytes()==old(p),'Reader congelado vc97 mudou: '+p
for name in ['FieldCaptureLifecycleV1','CaptureResilience0311','ScreenshotRescanGateV1','ScreenshotRescanExecutionV1',
 'RadarMiniMapViewV1','RadarMapBitmapSurfaceV1','RadarMapBitmapHealthV1','RadarMapLastEpisodeV1',
 'RadarContextualClientV1','JourneyStateMachine','JourneyActionReceiver',
 'ReportSelection0211','HudRideReplacementConfirmationV1','DestinationRadarInteractionV1',
 'LocalStore','PrivateScreenshotStore','RadarContextualFlagsV1','RadarDestinationLauncherV1']:
 assert pathlib.Path(app+name+'.kt').read_bytes()==old(app+name+'.kt'),'Fora escopo vc98: '+name
require(source('DestinationRadarAssistantBridgeV1'),'DestinationRadarFreshnessV1.blockReason',
 'Decision(null, temporalBlock)','backend_strong','cooldown_mesma_corrida')
require(source('DestinationRadarAssistantRendererV1'),'DestinationRadarFreshnessV1.blockReason',
 'RADAR_OPENED_BLOCKED','ASSISTANT_VISIBLE_BLOCKED','OnPreDrawListener')
require(source('RadarContextualIntegrationV1'),'DestinationRadarAssistantRendererV1.hide()',
 'DestinationRadarRuntimeV1.refreshNow()','DestinationRadarRuntimeV1.invalidatePending()')
require(source('DestinationRadarRuntimeV1'),'requestSequence.get()!=requestId',
 'requestSequence.incrementAndGet()','fun invalidatePending()','RadarDestinationContextV1.current(c)')
require(source('JourneyCoordinator'),'DestinationRadarAssistantRendererV1.hide()',
 'beginTransaction()','replaced_by_new_ride')
hud=source('JourneyBubbleController')
require(hud,'ReportSelection0211.toggle','isOperationalRide','"Radar"','"REALIZADA"','"NÃO REALIZADA"',
 'allowReplace = confirmation == HudRideReplacementConfirmationV1.Click.CONFIRMED')
controls=hud.split('    private fun operationalRideControls(')[1].split('    private fun currentRideActions(')[0]
assert '"CORRIDA ATIVA"' not in hud and 'VER REGIÃO DO DESTINO' not in controls,'HUD voltou ao texto antigo'
require(source('RadarContextualPanelV1'),'Tab.MAP','Tab.OPPORTUNITIES','Tab.EVENTS',
 'RadarCompactOverlayV1.arrival','RadarCompactOverlayV1.probability','val mapView = ensureMap()',
 'renderPendingIfReady()','RadarDestinationContextV1.current(context)')
require(source('RadarCompactOverlayV1'),'samples <= 0','ETA vencida','até 10 min')
require(source('NowPanel027037'),'journeyState.text = if (recoveryNeeded) "!"')
require(source('OfferRescanActivityV1'),'Revisar leitura','ScreenshotRescanGateV1.precheck')
require(source('FloatingWindowChrome023'),'"Histórico"','"Digitalização"','"Reportar"','"Exportar"',
 'R.drawable.sr23_float_restart',
 'DiagnosticQuickActions0270.restartReading(context)')
assert 'openRadar(' not in source('DestinationRadarRuntimeBridgeV1')
for p in ['backend','supabase','migrations','.github/workflows']:
 assert not subprocess.check_output(['git','diff','--name-only',base,'--',p],text=True).strip(),'Mudança backend ou workflow: '+p
allowed={'README-CONTINUIDADE.md','CHANGELOG.md','android/app/build.gradle.kts',
 'android/app/src/main/res/drawable/sr23_float_restart.xml',
 *('android/scripts/check-field-vc'+str(i)+'.sh' for i in range(95,99)),
 *('android/scripts/check-radar-contextual-v'+str(i)+'.sh' for i in range(2,7)),
 *(tests+n+'.kt' for n in ['Field95RegressionContractTest','Field96RegressionContractTest',
  'Field97RegressionContractTest','RadarContextualField2ContractTest',
  'Field98FreshnessContractTest','Field98RadarPresentationTest']),
 *(app+n+'.kt' for n in ['DestinationRadarFreshnessV1','RadarCompactOverlayV1','DestinationRadarAssistantBridgeV1',
  'DestinationRadarAssistantRendererV1','RadarContextualIntegrationV1','FieldPipelineTraceV1',
  'JourneyCoordinator','JourneyBubbleController','FloatingWindowChrome023','OfferRescanActivityV1',
  'NowPanel027037','RadarContextualPanelV1','DestinationRadarRuntimeV1'])}
changed=set(subprocess.check_output(['git','diff','--name-only',base],text=True).splitlines())
assert changed<=allowed,'Fora escopo vc98: '+str(changed-allowed)
print('Field vc98 static guard OK: Reader 18/18, geodata/maps/provider/capture frozen; UX/assistant confined.')
PY