# Continue MedMate Personal from v0.6.1-alpha

Open the attached v0.6.1 source ZIP as the authoritative project. Read `README.md`, `HANDOFF.md` and `docs/V061_VERIFICATION.md`; preserve the existing project, Liquid Aurora identity, SQLite v3 data and ZIP backup v1/v2 compatibility.

A real signed Debug APK has already been produced from a clean Gradle 8.13/JDK 17/SDK 36 build. Re-run `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` after changes. Do not claim device validation unless an emulator or Xiaomi Pad 5 was actually used.

Continue in this order: real-device reader/update testing; memory-bounded high-resolution PDF zoom and exact search/page anchoring; tested FSRS with a non-destructive transition; offline Exam Planner; optional AI that cites PDF pages and labels generated/external medical context. Keep core functions offline. Never send PDFs or study data to an online service without explicit opt-in, and do not create or upload a GitHub repository without user approval.
