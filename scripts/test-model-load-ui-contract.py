#!/usr/bin/env python3
"""Source-only regression for GGUF import/activation UI state. No APK/native inference."""
from pathlib import Path

source = Path("app/src/main/java/com/medmate/personal/ui/BrainPanel.kt").read_text(encoding="utf-8")
import_start = source.index("localImportBytes = imported.bytes")
import_end = source.index("var gateway by remember", import_start)
import_flow = source[import_start:import_end]
assert import_flow.index("savedModels = modelDirectory.listFiles()") < import_flow.index("requireEnoughRam(imported.file)"), (
    "Imported GGUF must stay selectable even if activation fails"
)
assert "localModelName = onDevice.loadedModelName" in import_flow, (
    "Import path must reflect the real native session after load/preflight"
)
assert "localModelName = null" not in import_flow, (
    "A failed preflight must not hide an already loaded model"
)

saved_start = source.index("savedModels.take(3).forEach")
saved_end = source.index("if (localModelName != null)", saved_start)
saved_flow = source[saved_start:saved_end]
assert "requireEnoughRam(file)" in saved_flow and "onDevice.loadModel(file)" in saved_flow
assert "localModelName = onDevice.loadedModelName" in saved_flow, (
    "Saved-model activation errors must preserve the native session display"
)
assert "localModelName = null" not in saved_flow

print("PASS: imported GGUF persists in model list; failed preflight keeps native-model UI consistent")
print("NOT TESTED: Android composition, native GGUF inference, real memory pressure")
