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

# Local inference failures must stay local. The optional cloud route is a separate,
# consent-gated user action; a model/JNI error must never trigger silent upload.
local_start = source.index("val response = onDevice.generate(p)")
local_end = source.index("finally { localBusy = false }", local_start)
local_flow = source[local_start:local_end]
assert "CloudBrainGateway" not in local_flow, "Local explanation failure must not fall back to cloud"
assert 'catch (e: Exception) { error = "تعذّر الشرح المحلي:' in local_flow, (
    "Local inference errors should be surfaced as local errors"
)

draft_start = source.index("val raw = onDevice.generate(task)")
draft_end = source.index("catch (e: Exception)", draft_start)
local_draft_flow = source[draft_start:draft_end]
assert "CloudBrainGateway" not in local_draft_flow, "Local draft failure must not silently upload excerpts"

assert "CloudTransferConsent" in source and "CloudEvidenceSeal.of" in source, (
    "Cloud route must retain the explicit consent/evidence binding"
)
assert "CloudBrainGateway().explain" in source and "CloudBrainGateway().generateDrafts" in source, (
    "Optional cloud actions should remain explicit, separate UI operations"
)

# Async inference must compare against the newest Compose inputs, not the chapter/page
# values captured by the click-time callback. This applies to local explanations,
# local study drafts, cloud explanations and cloud study drafts.
for state in (
    "chapter.sha256", "question", "pageOnly", "currentPage"
):
    assert f"rememberUpdatedState({state})" in source, (
        f"Missing latest-state guard for async source context: {state}"
    )
latest_match = (
    "requestIdentity.matches(latestRequestChapterSha.value, "
    "latestRequestQuestion.value, latestRequestPageOnly.value, "
    "latestRequestCurrentPage.value)"
)
assert source.count(latest_match) == 4, (
    "All four async result paths must compare against latest chapter/question/page state"
)

print("PASS: local inference errors never silently route to cloud; cloud remains explicit and consent/evidence-bound")
print("PASS: all four async answer/draft paths reject results against the latest chapter/question/page context")

print("PASS: imported GGUF persists in model list; failed preflight keeps native-model UI consistent")
print("NOT TESTED: Android composition, native GGUF inference, real memory pressure")
