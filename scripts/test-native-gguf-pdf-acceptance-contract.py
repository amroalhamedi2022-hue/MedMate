#!/usr/bin/env python3
"""Static wiring guard for the device-only synthetic-PDF + real-GGUF acceptance test.

This verifies that the test compiles against production PDF extraction, retrieval,
citation mapping and LlamaAndroidPort. It does not claim the instrumentation ran.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
test = (root / "app/src/androidTest/java/com/medmate/personal/brain/NativeGgufDeviceAcceptanceTest.kt").read_text(encoding="utf-8")
device_script = (root / "scripts/run-native-gguf-device-test.sh").read_text(encoding="utf-8")
script_test = (root / "scripts/test-native-gguf-device-script.sh").read_text(encoding="utf-8")
required = {
    "real native port": "LlamaAndroidPort()",
    "production PDFBox extractor": "PdfTextExtractor(context, pdfFile)",
    "runtime-generated synthetic PDF": "PDDocument()",
    "production source retrieval": "StudyBrain.search(",
    "grounded prompt preparation": "StudyBrain.prepareSourceOnly(",
    "citation syntax gate": "CitationValidator.check(",
    "page navigation mapping": "SourceCitationLinks.resolve(",
    "two-page synthetic fixture": "addSyntheticPage(document, \"Synthetic study page 2.",
    "second-page extraction": "reader.page(1)",
    "page-scoped retrieval": "onlyPage = 1",
    "exact page-2-only marker assertion": 'assertEquals("the page-2-only request must resolve exactly one source marker", listOf("S1"), links.map { it.marker })',
    "human-facing page 2 mapping": 'assertEquals("S1 must navigate to human-facing PDF page 2", listOf(2), links.map { it.pageNumber })',
    "page-1 citation rejection": 'assertFalse("a page-1 citation must never be produced for this page-2-only answer", links.any { it.pageNumber == 1 })',
    "synthetic PDF cleanup assertion": 'assertTrue("synthetic PDF cache fixture must be removed after the native round-trip", pdfCleanupSucceeded)',
    "native calibration probe": "NativeInferenceProbe.run(port)",
    "native backend identity assertion": 'assertTrue("Production native backend/model identity not confirmed", result.nativeBackendConfirmed)',
    "native diagnostic must pass": 'assertTrue("Real native probe failed", result.diagnosticPassed)',
    "native model digest bound to loaded model": 'assertEquals(imported.sha256, result.nativeModelSha256)',
    "native model size bound to loaded model": 'assertEquals(imported.bytes, result.nativeModelBytes ?: -1L)',
    "fresh page-two identifier required": 'assertTrue("real native answer did not preserve the fresh PDF identifier", answer.contains(code, ignoreCase = true))',
    "only the supplied citation required": 'assertTrue("real native answer must explicitly cite the only supplied evidence", answer.contains("[S1]"))',
    "wrong-page citation rejected": 'assertFalse("page-1-only marker [S2] must not be invented for page-2 evidence", answer.contains("[S2]"))',
    "only page two enters the prompt": 'assertEquals("only the requested PDF page may enter the native prompt", listOf(1), prepared.presentedEvidence.map { it.pageIndex })',
    "full PDF excerpt echo rejected": 'assertTrue("native answer must not echo the entire PDF excerpt", !answer.contains(extracted, ignoreCase = true))',
    "loaded model identity cleared on unload": 'assertTrue("Loaded GGUF identity must clear after unload", identityCleared)',
    "temporary imported model cleanup": 'assertTrue("A test-only imported model must be removed after unload", temporaryModelRemoved)',
}
for label, needle in required.items():
    assert needle in test, f"missing device acceptance stage: {label}"
assert "FakeLlama" not in test, "device acceptance must never use the fake backend"
assert "ggufPath" in test and "LlamaAndroidPort()" in test
assert "ggufPath" in device_script, "device runner must pass the user-supplied model path"
assert "sha256" in device_script.lower(), "device runner must verify the supplied model"
assert "trap " in device_script, "device runner must clean up the temporary model on exit"
assert "FAILURES!!!" in device_script and "OK \\([1-9][0-9]* tests?\\)" in device_script, "runner must reject reported instrumentation failures and require a JUnit success summary"
assert "NativeGgufDeviceAcceptanceTest" in device_script, "runner must verify the expected native acceptance class was reported"
assert "FAKE_INSTRUMENT_MODE" in script_test and "instrumentation-reported-failure" in script_test and "instrumentation-missing-summary" in script_test, "shell regression must exercise failure and incomplete AndroidJUnitRunner output"
print("PASS: synthetic PDF → production PDFBox extraction → retrieval → real GGUF JNI → citation/page-link checks are wired.")
print("NOT TESTED: this static guard does not execute Android instrumentation or a real GGUF model.")
