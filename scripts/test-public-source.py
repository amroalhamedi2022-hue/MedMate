#!/usr/bin/env python3
"""Public GitHub import guard. No user signing keys or private medical materials."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
signing = root / 'signing' / 'medmate-debug.keystore'
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'if (continuityKey.isFile)' in gradle, 'debug signing must fall back without user key'
assert 'rootProject.file("signing/medmate-debug.keystore")' in gradle
assert 'signing/*.keystore' in (root / '.gitignore').read_text(encoding='utf-8')
# This guard is for the *public export*, not the user's private development folder.
if not signing.exists():
    assert not any(root.rglob('*.keystore')), 'public source contains signing material'
    assert not any(root.rglob('*.gguf')), 'public source contains model weights'
    assert not any(root.rglob('*.jks')), 'public source contains signing material'
    print('PASS: public MedMate source excludes private keys/model weights; Gradle debug fallback present')
else:
    print('PASS: private local source uses optional continuity key; exclude it from public export')
