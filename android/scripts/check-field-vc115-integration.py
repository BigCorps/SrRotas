"""Candidate preservation guard: only reviewed evidence UI/code and explicit version changes."""
import subprocess
base='4a8f384c0cb755f93af315b651b08000b9f502fa'
reviewed='187fa7ee7875ea896b03dad87d671932563518b3'
root='android/app/src/'
changed=subprocess.check_output(['git','diff','--name-only',base,'--',root],text=True).splitlines()
evidence=['main/java/com/bigcorps/driveraimvp/'+f for f in ['OriginalCaptureLookupV1.kt','VerifiedCaptureSnapshotV1.kt','RideHistoryPanel027035.kt','r3lab/R3EvidenceViewerActivity.kt']]+['test/java/com/bigcorps/driveraimvp/'+f for f in ['OriginalCaptureLookupTest.kt','VerifiedCaptureSnapshotTest.kt']]
version_tests=['test/java/com/bigcorps/driveraimvp/'+f+'.kt' for f in ['Field101RadarStabilityContractTest','Field103NativePerformanceTest','Field99FeedbackContractTest','RadarContextualField2ContractTest']]
assert set(changed)<=set(root+f for f in evidence+version_tests), changed
for f in evidence:
 assert subprocess.check_output(['git','show',reviewed+':'+root+f])==open('../'+root+f,'rb').read(), f
for f in version_tests:
 before=subprocess.check_output(['git','show',base+':'+root+f],text=True).replace('versionCode=114','versionCode=115').replace('0.33.37-field','0.33.38-field')
 assert before==open('../'+root+f).read(), f
print('VC115 preservation: all other Android source (Reader/OCR/HUD/storage/consent/retention) identical to vc114; evidence identical to approved PR23.')
