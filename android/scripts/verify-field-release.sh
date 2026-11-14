#!/usr/bin/env bash
# Public verification only. Signing material remains exclusively in Actions Secrets / runner temp.
set -euo pipefail
APK=app/build/outputs/apk/release/app-release.apk
TOOLS="$ANDROID_HOME/build-tools/36.0.0"
EXPECTED_CERT=cc4476c4eb3ac725b692914488282f0ac70a83b3682ca6b7ac0f841f9de2117c
OUT=app/build/outputs/apk/release
"$TOOLS/apksigner" verify --verbose --print-certs "$APK" | tee "$OUT/field-signing-certificate.txt"
grep -Fxq "Signer #1 certificate SHA-256 digest: $EXPECTED_CERT" "$OUT/field-signing-certificate.txt"
"$TOOLS/aapt2" dump badging "$APK" > "$OUT/apk-badging.txt"
export SR_RELEASE_EXPECTED_CERT="$EXPECTED_CERT"
python3 - <<'PY'
from pathlib import Path
import hashlib, json, os, re
out = Path('app/build/outputs/apk/release')
badging = (out / 'apk-badging.txt').read_text()
fields = dict(re.findall(r"(name|versionCode|versionName)='([^']*)'", badging.splitlines()[0]))
assert fields == {'name': 'com.srrotas.app', 'versionCode': '114', 'versionName': '0.33.37-field'}, fields
assert 'application-debuggable' not in badging, 'Release APK is debuggable'
cert = (out / 'field-signing-certificate.txt').read_text()
assert 'Number of signers: 1' in cert, 'Unexpected signer count'
config = Path('app/build/generated/source/buildConfig/release/com/srrotas/app/BuildConfig.java').read_text()
assert 'R3_LAB = false' in config, 'Lab must never be the operational release'
apk = out / 'app-release.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(out / 'app-release.apk.sha256').write_text(f'{digest}  app-release.apk\n')
metadata = {'file': apk.name, 'applicationId': fields['name'], 'versionCode': int(fields['versionCode']),
    'versionName': fields['versionName'], 'debuggable': False, 'r3_lab': False,
    'bytes': apk.stat().st_size, 'sha256': digest, 'certificate_sha256': os.environ['SR_RELEASE_EXPECTED_CERT'],
    'code_head': os.environ['SR_RELEASE_CODE_SHA']}
(out / 'release-verification.json').write_text(json.dumps(metadata, indent=2) + '\n')
print('FIELD_RELEASE_VERIFIED ' + json.dumps(metadata))
PY
bash scripts/check-apk-size.sh
