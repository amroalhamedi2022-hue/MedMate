#!/usr/bin/env python3
"""Verify the built APK against this branch's Gradle identity and offline policy."""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")


def gradle_value(pattern: str, label: str) -> str:
    match = re.search(pattern, GRADLE, re.MULTILINE)
    if not match:
        raise SystemExit(f"Could not read {label} from app/build.gradle.kts")
    return match.group(1)


APP_ID = gradle_value(r'^\\s*applicationId\\s*=\\s*"([^"]+)"', "applicationId")
VERSION_CODE = gradle_value(r'^\\s*versionCode\\s*=\\s*(\\d+)', "versionCode")
VERSION_NAME = gradle_value(r'^\\s*versionName\\s*=\\s*"([^"]+)"', "versionName")
MIN_SDK = gradle_value(r'^\\s*minSdk\\s*=\\s*(\\d+)', "minSdk")
TARGET_SDK = gradle_value(r'^\\s*targetSdk\\s*=\\s*(\\d+)', "targetSdk")

if len(sys.argv) != 3:
    raise SystemExit("usage: verify-apk.py APK AAPT")
apk, aapt = map(Path, sys.argv[1:])
if not apk.is_file() or apk.stat().st_size < 10_000:
    raise SystemExit("APK missing or unexpectedly small")
if not aapt.is_file():
    raise SystemExit(f"aapt executable not found: {aapt}")


def run(*args: str) -> str:
    return subprocess.check_output(
        [str(aapt), *args, str(apk)], text=True, stderr=subprocess.STDOUT
    )


badging = run("dump", "badging")
permissions = run("dump", "permissions")
expected_patterns = {
    "package/version": (
        rf"^package: name='{re.escape(APP_ID)}' .*"
        rf"versionCode='{re.escape(VERSION_CODE)}' "
        rf"versionName='{re.escape(VERSION_NAME)}'"
    ),
    "minSdkVersion": rf"^sdkVersion:'{re.escape(MIN_SDK)}'$",
    "targetSdkVersion": rf"^targetSdkVersion:'{re.escape(TARGET_SDK)}'$",
}
for label, pattern in expected_patterns.items():
    if not re.search(pattern, badging, re.MULTILINE):
        raise SystemExit(
            f"APK {label} does not match current Gradle configuration\n"
            f"Expected pattern: {pattern}\nActual aapt badging:\n{badging}"
        )
for forbidden in (
    "android.permission.INTERNET",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
):
    if forbidden in permissions:
        raise SystemExit(f"forbidden permission present: {forbidden}")
print("PASS: APK identity/version/SDK match Gradle; no forbidden network/broad-storage permissions")
print(next(line for line in badging.splitlines() if line.startswith("package:")))
print(permissions.strip() or "uses-permission: none")
