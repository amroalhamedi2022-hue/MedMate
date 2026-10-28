# MedMate Personal v0.8.1 — merged source only

This is a reconciled **source code** release (not an APK), combining the user's Work-built v0.6.1 source with chat-built Brain v0.8. The v0.6.1 source and debug signing were kept as the base. Read `docs/MERGE_REPORT_2026-10-23_AR.md` for the full evidence ledger, limitations, data safety, and next gates.

Use `bash scripts/test-merged.sh` for all local non-Android smoke checks. On a configured Android SDK/JDK 17 build workstation run:

```bash
bash ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`gradle/wrapper/gradle-wrapper.jar` is included, but building requires downloading the pinned distribution and Android dependencies. Preserve the private signing keystore when upgrading the currently installed app, and back up data before installing any new APK.
