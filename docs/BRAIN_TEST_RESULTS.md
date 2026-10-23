# Brain v0.7 preview — verification record

Verified in this chat environment (Linux/JVM; no Android SDK or Gradle):
- Pure Kotlin brain core smoke: 15 assertions passed.
- New Brain pipeline + HTTPS URL policy smoke: 18 assertions passed.
- Existing scheduling, MCQ validation, study filtering, migration simulations: passed.
- Android Gradle compilation, Android Lint, emulator and Pad 5 tests: **NOT RUN**.
- HTTPS cloud request: **NOT TESTED against a real endpoint**.
- Local GGUF/llama.cpp generation: **NOT IMPLEMENTED**.
- No claim of real medical correctness: PDF evidence matching is lexical and citation check is syntactic, not clinical entailment.
- The only available source is v0.6; source of Work's v0.6.1 is not present, so these changes cannot be asserted to preserve all its patches yet.

Run `bash scripts/test-brain.sh` for reproducible JVM checks.
