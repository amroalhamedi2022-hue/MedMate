# MedMate v0.7 Brain Preview — source and roadmap

This package is based on the **last accessible v0.6 source**, not on the Work-signed v0.6.1 binary. It is not yet an Android-tested APK.

## Try the new flow after a successful build
1. Import a medical PDF and open the chapter.
2. The study tools tab opens first; select **🧠 اسأل MedMate** if needed.
3. Enter a concrete question (Arabic or English). The app indexes embedded page text on demand and shows matching excerpts, with clickable exact PDF pages.
4. For an *optional* cloud explanation, select a private **HTTPS OpenAI-compatible gateway**, name its configured model, consent to sending the question + retrieved excerpts, then press the explicit request button.
5. Evaluate the cloud reply and open the citations. Citation-marker checks cannot prove medical accuracy.

For a personal cloud setup, use a secured server-side relay to the model provider. Do not hardcode a provider API key in the APK. The optional gateway token is only held in session memory.

**No local GGUF model runtime is bundled.** Local search/retrieval is real and requires no Internet; generative on-device model inference is the next separate milestone. We will use the official llama.cpp Android integration as a reference, pin a verified commit and benchmark a compact model on Pad 5 before advertising local AI.

## Safety & compatibility
- `INTERNET` is newly present, needed exclusively for user-triggered remote inference; reading, notes and retrieval remain offline.
- Existing SQLite schema is unchanged (v3); import and study backups remain as in v0.6.
- **Do not uninstall or replace existing v0.6.1 without verified backup and matched APK signing keys.** Package identity is unchanged, but unsigned/differently signed debug builds cannot update a previously signed installation.
- No automatic switch from local to cloud.

## Development status
- `docs/BRAIN_REBUILD_PLAN.md`: checklist and exit gates.
- `docs/BRAIN_TEST_RESULTS.md`: tests completed and tests not run.
- `scripts/test-brain.sh`: pure Kotlin verification.
- No Android SDK/Gradle or real connected HTTPS model server in this conversation's execution environment.
