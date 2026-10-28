#!/usr/bin/env python3
"""Fail-closed parser for Gradle's resolved Bouncy Castle runtime dependency report."""
from __future__ import annotations
import re
import sys
from pathlib import Path

EXPECTED = "1.86"
MODULES = ("bcprov-jdk15to18", "bcpkix-jdk15to18", "bcutil-jdk15to18")
PATTERN = re.compile(
    r"org\.bouncycastle:(bcprov-jdk15to18|bcpkix-jdk15to18|bcutil-jdk15to18):([^\s]+)(?:\s*->\s*([^\s]+))?"
)

def parse_report(text: str) -> dict[str, list[tuple[str, str, str]]]:
    found = {name: [] for name in MODULES}
    for line in text.splitlines():
        for match in PATTERN.finditer(line):
            module, requested, selected = match.groups()
            found[module].append((requested, selected or requested, line.strip()))
    return found

def check_report(text: str) -> list[str]:
    found = parse_report(text)
    errors = []
    for module in MODULES:
        entries = found[module]
        if not entries:
            errors.append(f"{module}: missing from debugRuntimeClasspath report")
            continue
        wrong = [(requested, resolved, line) for requested, resolved, line in entries if resolved != EXPECTED]
        if wrong:
            versions = ", ".join(sorted({resolved for _, resolved, _ in wrong}))
            errors.append(f"{module}: expected resolved {EXPECTED}, observed {versions}")
    return errors

def main() -> int:
    if len(sys.argv) != 2:
        print("usage: bouncycastle_runtime_gate.py <gradle-dependencies-report>", file=sys.stderr)
        return 2
    report_path = Path(sys.argv[1])
    if not report_path.is_file():
        print(f"FAIL: dependency report not found: {report_path}", file=sys.stderr)
        return 2
    text = report_path.read_text(encoding="utf-8", errors="replace")
    errors = check_report(text)
    if errors:
        for error in errors:
            print("FAIL: " + error, file=sys.stderr)
        print("Resolved dependency report rejected.", file=sys.stderr)
        return 1
    found = parse_report(text)
    print("PASS: bcprov, bcpkix and bcutil all resolve to Bouncy Castle 1.86")
    for module in MODULES:
        versions = sorted({item[1] for item in found[module]})
        print(f"  {module}: {', '.join(versions)}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
