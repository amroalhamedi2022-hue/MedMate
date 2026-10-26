#!/usr/bin/env python3
"""Regression tests for the APK verifier using a fake aapt, not a fake APK build."""
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

def value(pattern):
    match = re.search(pattern, GRADLE, re.MULTILINE)
    assert match, f"Gradle value missing: {pattern}"
    return match.group(1)

app_id = value(r'^\s*applicationId\s*=\s*"([^"]+)"')
version_code = value(r'^\s*versionCode\s*=\s*(\d+)')
version_name = value(r'^\s*versionName\s*=\s*"([^"]+)"')
min_sdk = value(r'^\s*minSdk\s*=\s*(\d+)')
target_sdk = value(r'^\s*targetSdk\s*=\s*(\d+)')
verifier = ROOT / "scripts/verify-apk.py"

with tempfile.TemporaryDirectory(prefix="medmate-apk-verifier-") as td:
    tmp = Path(td)
    apk = tmp / "app-debug.apk"
    apk.write_bytes(b"x" * 12_000)
    aapt = tmp / "aapt"
    aapt.write_text(
        "#!/usr/bin/env python3\n"
        "import os,sys\n"
        "if sys.argv[1:3] == ['dump','badging']:\n"
        " print(os.environ['FAKE_BADGING'])\n"
        "elif sys.argv[1:3] == ['dump','permissions']:\n"
        " print(os.environ.get('FAKE_PERMISSIONS',''))\n"
        "elif sys.argv[1:3] == ['dump','xmltree']:\n"
        " print(os.environ.get('FAKE_XMLTREE','A: android:usesCleartextTraffic(0x010104ec)=false'))\n"
        "else: raise SystemExit(2)\n",
        encoding="utf-8",
    )
    aapt.chmod(0o755)

    def badging(package=app_id, code=version_code, name=version_name,
                minimum=min_sdk, target=target_sdk):
        return (
            f"package: name='{package}' versionCode='{code}' versionName='{name}' "
            "platformBuildVersionName='16' platformBuildVersionCode='36'\n"
            f"sdkVersion:'{minimum}'\n"
            f"targetSdkVersion:'{target}'\n"
        )

    def run(label, output, permissions="", expected=0, xmltree="A: android:usesCleartextTraffic(0x010104ec)=false"):
        env = os.environ.copy()
        env["FAKE_BADGING"] = output
        env["FAKE_PERMISSIONS"] = permissions
        env["FAKE_XMLTREE"] = xmltree
        result = subprocess.run(
            [sys.executable, str(verifier), str(apk), str(aapt)],
            cwd=ROOT, env=env, text=True, capture_output=True
        )
        assert (result.returncode == 0) == (expected == 0), (
            f"{label}: expected exit {expected}, got {result.returncode}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
        print(f"PASS: {label}")

    internet = "uses-permission: name='android.permission.INTERNET'"
    run("matches Gradle identity and explicit optional-cloud permission", badging(), internet)
    run("rejects stale version metadata", badging(code="8", name="0.6.1-alpha"), expected=1)
    run("rejects wrong package ID", badging(package="com.example.other"), expected=1)
    run("rejects target SDK drift", badging(target=str(int(target_sdk) - 1)), expected=1)
    run("rejects missing declared INTERNET permission", badging(), expected=1)
    run("rejects broad storage permission", badging(), internet + "\\nuses-permission: name='android.permission.READ_EXTERNAL_STORAGE'", 1)
    run("rejects cleartext-enabled APK", badging(), internet, 1,
        "A: android:usesCleartextTraffic(0x010104ec)=true")

print("PASS: APK verifier metadata, optional-cloud permission, cleartext and broad-storage regression suite")
