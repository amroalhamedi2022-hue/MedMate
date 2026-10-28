#!/usr/bin/env python3
"""Fail-closed source guard: Compose actions must not swallow coroutine cancellation."""
from pathlib import Path
import re

source_path = Path("app/src/main/java/com/medmate/personal/ui/BrainPanel.kt")
source = source_path.read_text(encoding="utf-8")
generic = len(re.findall(r"catch\\s*\\(e:\\s*Exception\\)", source))
safe = len(re.findall(
    r"catch\\s*\\(e:\\s*CancellationException\\)\\s*\\{\\s*throw e\\s*\\}\\s*catch\\s*\\(e:\\s*Exception\\)",
    source,
))
assert "import kotlinx.coroutines.CancellationException" in source, "CancellationException import missing"
assert generic == safe, f"{generic - safe} generic catch block(s) may swallow CancellationException"
assert generic >= 10, f"Expected to guard the current long-running Compose actions; only found {generic}"
print(f"PASS: {safe} BrainPanel coroutine error handlers rethrow cancellation before handling ordinary exceptions")
