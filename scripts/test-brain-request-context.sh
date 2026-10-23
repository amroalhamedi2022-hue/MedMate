#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc app/src/main/java/com/medmate/personal/brain/BrainRequestIdentity.kt \
  scripts/brain/BrainRequestIdentitySmoke.kt -include-runtime -d "$out/context.jar"
java -jar "$out/context.jar"
python3 - <<'PYTEST'
from pathlib import Path
ui=Path('app/src/main/java/com/medmate/personal/ui/BrainPanel.kt').read_text()
assert 'https://api.openai.com/v1/chat/completions' not in ui
assert 'OpenAI API مباشرة' not in ui
assert ui.count('requestIdentity.matches(chapter.sha256, question, pageOnly, currentPage)') == 2
assert ui.count('BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)') == 2
print('PASS: both cloud UI result paths guard stale source and no paid API promotion')
PYTEST
