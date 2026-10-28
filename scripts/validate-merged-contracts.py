#!/usr/bin/env python3
"""Merge regression guards: verify Work v0.6.1 protections survive the Brain integration."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
def source(name):return (root/name).read_text()
b=source('app/src/main/java/com/medmate/personal/data/BackupManager.kt')
d=source('app/src/main/java/com/medmate/personal/data/ChapterStore.kt')
u=source('app/src/main/java/com/medmate/personal/ui/BrainPanel.kt')
c=source('app/src/main/AndroidManifest.xml')
g=source('app/build.gradle.kts')
assert 'db.beginTransactionNonExclusive()' in b and 'db.setTransactionSuccessful()' in b
assert 'BoundedLineReader.forEach(file, 128_000)' in b
assert 'require((rating != 0) == (correct == 1))' in b
assert 'Triple(kind, correctIndex, j.getJSONArray("options").length())' in b
assert 'chosen in 0 until optionCount' in b
assert 'requirePage(sha256, page)' in d
assert 'suspend fun indexedPageSources(sha256: String)' in d
assert 'lastSourcePage' in u and 'currentPage == lastSourcePage' in u
assert 'android.permission.INTERNET' in c and 'MANAGE_EXTERNAL_STORAGE' not in c
assert 'rootProject.file("signing/medmate-debug.keystore")' in g
assert int(re.search(r'versionCode\s*=\s*(\d+)', g).group(1)) >= 12
assert 'dev.ffmpegkit-maintained:llama-android:0.1.1' in g
assert 'LlamaAndroidPort' in u and 'ExtractiveTutor.brief' in u
assert 'StudyDraftPrompt.prepare' in u and 'GeneratedDraftJson.parseAndValidate' in u
assert 'StudyEvidenceNote.withSource' in u
assert 'ModelMemoryGate.canAttemptLoad' in u
assert 'supportingQuote' in source('app/src/main/java/com/medmate/personal/brain/GeneratedStudyDrafts.kt')
print('PASS: merged regression/security contracts (original and v0.9.1 additional guards)')
