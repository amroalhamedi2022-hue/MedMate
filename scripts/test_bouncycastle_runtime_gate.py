#!/usr/bin/env python3
"""Regression tests for the Bouncy Castle dependency-resolution gate."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from bouncycastle_runtime_gate import check_report, parse_report

GOOD = """\
+--- org.bouncycastle:bcprov-jdk15to18:1.72 -> 1.86
+--- org.bouncycastle:bcpkix-jdk15to18:1.72 -> 1.86
\\--- org.bouncycastle:bcutil-jdk15to18:1.86
"""

class RuntimeGateTests(unittest.TestCase):
    def test_selected_186_passes_even_when_pdfbox_requests_172(self):
        self.assertEqual(check_report(GOOD), [])

    def test_missing_module_fails_closed(self):
        report = (
            "+--- org.bouncycastle:bcprov-jdk15to18:1.72 -> 1.86\n"
            "+--- org.bouncycastle:bcpkix-jdk15to18:1.72 -> 1.86\n"
        )
        self.assertTrue(any("bcutil-jdk15to18: missing" in e for e in check_report(report)))

    def test_unresolved_legacy_version_fails(self):
        report = GOOD.replace("bcprov-jdk15to18:1.72 -> 1.86", "bcprov-jdk15to18:1.72")
        self.assertTrue(any("bcprov-jdk15to18: expected resolved 1.86" in e for e in check_report(report)))

    def test_any_conflicting_resolved_occurrence_fails(self):
        report = GOOD + "\\--- org.bouncycastle:bcprov-jdk15to18:1.72\n"
        self.assertTrue(any("bcprov-jdk15to18: expected resolved 1.86" in e for e in check_report(report)))

    def test_parser_keeps_requested_and_selected_versions(self):
        entry = parse_report(GOOD)["bcprov-jdk15to18"][0]
        self.assertEqual(entry[0], "1.72")
        self.assertEqual(entry[1], "1.86")

if __name__ == "__main__":
    unittest.main()
