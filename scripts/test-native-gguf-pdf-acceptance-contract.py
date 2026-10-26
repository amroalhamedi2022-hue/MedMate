#!/usr/bin/env python3
"""Static wiring guard for the device-only synthetic-PDF + real-GGUF acceptance test.

This verifies that the test compiles against production PDF extraction, retrieval,
citation mapping and LlamaAndroidPort. It does not claim the instrumentation ran.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
test = (root / "app/src/androidTest/java/com/medmate/personal/brain/NativeGgufDeviceAcceptanceTest.kt").read_text(encoding="utf-8")
device_script = (root / "scripts/run-native-gguf-device-test.sh").read_text(encoding="utf-8")
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
}
for label, needle in required.items():
    assert needle in test, f"missing device acceptance stage: {label}"
assert "FakeLlama" not in test, "device acceptance must never use the fake backend"
assert "ggufPath" in test and "LlamaAndroidPort()" in test
assert "ggufPath" in device_script, "device runner must pass the user-supplied model path"
assert "sha256" in device_script.lower(), "device runner must verify the supplied model"
assert "trap " in device_script, "device runner must clean up the temporary model on exit"
print("PASS: synthetic PDF → production PDFBox extraction → retrieval → real GGUF JNI → citation/page-link checks are wired.")
print("NOT TESTED: this static guard does not execute Android instrumentation or a real GGUF model.")
