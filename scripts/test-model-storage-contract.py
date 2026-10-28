#!/usr/bin/env python3
"""Verify the Android GGUF picker actually uses the tested disk-space gate."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
ui = (root / "app/src/main/java/com/medmate/personal/ui/BrainPanel.kt").read_text(encoding="utf-8")
importer = (root / "app/src/main/java/com/medmate/personal/brain/LocalModelImport.kt").read_text(encoding="utf-8")
gate = (root / "app/src/main/java/com/medmate/personal/brain/ModelStorageGate.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/com/medmate/personal/brain/LocalModelImportTest.kt").read_text(encoding="utf-8")

assert "OpenableColumns.SIZE" in ui and "expectedBytes = expectedBytes" in ui
assert "ModelStorageGate.canStart(availableSpace(), expectedBytes)" in importer
assert "ModelStorageGate.canContinue(availableSpace(), n.toLong(), expectedBytes?.minus(count))" in importer
assert "count == expectedBytes" in importer
assert "CHECK_INTERVAL_BYTES" in importer and "RESERVE_BYTES" in gate
assert "expectedSizeAndLowStorageAreRejectedBeforeCreatingTempAndCloseProvider" in tests
assert "inaccurateUnknownSizeStopsBeforeStorageReserveIsConsumed" in tests
print("PASS: SAF size hint, upfront/streaming disk-space reserve, and cleanup regressions are wired to production GGUF import.")
print("NOT TESTED: physical-device free-space reporting or actual llama.cpp inference.")
