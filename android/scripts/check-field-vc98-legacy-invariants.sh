#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
python3 - <<'PY'
import pathlib, subprocess
base = "91322b81aa5f20d0703b9a7a70d417e7ddb44d01"
app = "android/app/src/main/java/com/bigcorps/driveraimvp/"
# Cumulative checks against the vc97 baseline for historically frozen files.
names = [
 "MediaProjectionOcrService", "FieldCaptureLifecycleV1", "CaptureResilience0311",
 "RadarMiniMapViewV1", "RadarMapBitmapSurfaceV1", "RadarMapBitmapHealthV1",
 "RadarMapLastEpisodeV1", "RadarMapRenderGateV1", "RadarContextualClientV1",
 "ScreenshotRescanGateV1", "ScreenshotRescanExecutionV1", "ScreenshotRescanImageV1",
 "PrivateScreenshotIndexV1", "PrivateScreenshotStore", "JourneyStateMachine",
 "ReportSelection0211", "RadarContextualFlagsV1", "RadarDestinationLauncherV1",
]
for name in names:
    path = app + name + ".kt"
    expected = subprocess.check_output(["git", "show", base + ":" + path])
    actual = pathlib.Path(path).read_bytes()
    assert actual == expected, "Invariante histórica modificada: " + name
print("vc98: 18 invariantes históricas comparadas byte a byte com vc97.")
PY
