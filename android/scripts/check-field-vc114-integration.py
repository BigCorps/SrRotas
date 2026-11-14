#!/usr/bin/env python3
"""Audit the three reviewed inputs without replacing their existing regression tests."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
def git(*args):
    return subprocess.check_output(['git', '-C', str(root), *args])

base = 'e9f7982c990ebfdfcd7014897bc16a9b586cbd15'
inputs = {
    13: '1b5a6954bc78595c4af05dafd65e6741c0ebbfb5',
    14: '0b8acdfea186fed5a4b74ef08e08d648e08a85cc',
    17: '0a7d7ca3ee7592bb77bb7c755078c158591f46c5',
}
shell = 'android/app/src/main/java/com/bigcorps/driveraimvp/ConsolidatedMainActivity027037.kt'
checked = 0
for pr, sha in inputs.items():
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', sha, 'HEAD'], check=True)
    paths = git('diff', '--name-only', base, sha).decode().splitlines()
    for path in paths:
        if path.startswith('android/') and path.endswith(('.kt', '.xml')) and path != shell:
            assert (root / path).read_bytes() == git('show', f'{sha}:{path}'), f'PR #{pr} changed unexpectedly: {path}'
            checked += 1

# All conflicts in the shell must preserve both independent sets of behavior.
s = (root / shell).read_text()
recovery = s.split('fun recoverCurrentJourneyWithM1()', 1)[1].split('private fun requestCapturePermission', 1)[0]
assert recovery == git('show', f'{inputs[13]}:{shell}').decode().split('fun recoverCurrentJourneyWithM1()', 1)[1].split('private fun requestCapturePermission', 1)[0]
consent = s.split('override fun onActivityResult', 1)[1].split('private fun buildUi', 1)[0]
original_consent = git('show', f'{inputs[13]}:{shell}').decode().split('override fun onActivityResult', 1)[1].split('private fun buildUi', 1)[0]
assert consent == original_consent, 'MediaProjection consent/recovery behavior changed during integration'
for signature, following in [('override fun onResume()', 'override fun onPostResume()'),
                             ('override fun onPause()', 'override fun onSaveInstanceState')]:
    if signature in s and following in s:
        assert s.split(signature, 1)[1].split(following, 1)[0] == git('show', f'{inputs[17]}:{shell}').decode().split(signature, 1)[1].split(following, 1)[0]
assert 'RadarTwoTabsPanelV1(this)' in s and 'RadarVisualLayoutV1.enabled(this)' in s
assert 'RadarContextualFlagsV1.uiEnabled(this)' not in s
assert 'MainActivity.BUBBLE_ACTION_RECOVER_M1 ->' in s
assert 'current?.localOfferId!=request.spec.localOfferId' in s
assert not git('diff', '--name-only', base, 'HEAD', '--', 'backend', 'supabase').strip(), 'Backend/database changed'
print(f'vc114 integration guard OK: {checked} Android inputs unchanged, both shell paths preserved, backend/database unchanged.')
