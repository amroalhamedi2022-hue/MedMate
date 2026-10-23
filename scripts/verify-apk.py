#!/usr/bin/env python3
"""Fail fast on accidental package/version/permission drift in a built debug APK."""
import re
import subprocess
import sys
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: verify-apk.py APK AAPT")
apk, aapt = map(Path, sys.argv[1:])
if not apk.is_file() or apk.stat().st_size < 10_000:
    raise SystemExit("APK missing or unexpectedly small")
def run(*args: str) -> str:
    return subprocess.check_output([str(aapt), *args, str(apk)], text=True, stderr=subprocess.STDOUT)
badging = run("dump", "badging")
permissions = run("dump", "permissions")
expected_patterns = {
    "package": r"^package: name='com\.medmate\.personal' .*versionCode='8' versionName='0\.6\.1-alpha'",
    "minSdkVersion": r"^sdkVersion:'26'$",
    "targetSdkVersion": r"^targetSdkVersion:'35'$",
}
for key, pattern in expected_patterns.items():
    if not re.search(pattern, badging, re.MULTILINE):
        raise SystemExit(f"missing expected {key}\n{badging}")
for forbidden in ("android.permission.INTERNET", "MANAGE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"):
    if forbidden in permissions:
        raise SystemExit(f"forbidden permission present: {forbidden}")
print("PASS: package, version, SDK range, and offline permission contract")
print(badging.splitlines()[0])
print(permissions.strip() or "uses-permission: none")
