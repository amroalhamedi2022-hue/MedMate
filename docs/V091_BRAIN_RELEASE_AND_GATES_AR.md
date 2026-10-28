# MedMate v0.9.1 — Local-first Study Drafts / Release Gates

## الحالة المعتمدة (2026-10-23)

**نسخة مصدر فقط**. لم يتم تجميع APK أو تشغيل GGUF على جهاز في هذه الدفعة.
المصدر مبني على v0.9 الذي كان أصلًا فوق v0.6.1 Work. استُبقي مخطط SQLite v3، ونسخ الاحتياط v2،
ومفتاح توقيع Debug نفسه كما في v0.6.1 (SHA-256 لملف keystore:
`9d2516ab985a545a7f0bc91cbec958682323af33075d28673d9aa07ec8f70abc`).

## التغييرات المنفّذة

1. **Local Study Draft Generation**: زر لإنشاء مسودات High-Yield / Flashcards / MCQ محليًا بعد
   تحميل نموذج GGUF واسترجاع مقاطع موثقة. يستخدم `StudyDraftPrompt.prepare` و`GeneratedDraftJson`.
   لا يوجد نقل سحابي ضمن هذا الزر. إن لم يكن هناك نموذج جاهز أو مصدر، يبقى الزر غير فعال.
2. **قناة توحيد المسودات**: محرك Cloud الحالي ومسار Local الجديد يمران بنفس مدقق JSON والبنية والمراجع.
   يتم إنشاء عناصر الدراسة كـ *مسودات* فقط، وللمستخدم زر رفض أو اعتماد ومراجعة الصفحة.
3. **Supporting quote**: أضيف `supporting_quote` إلزاميًا بمقدار 16–500 حرف مع `source_id` لكل
   عنصر توليدي. يُطابَق الاقتباس نصيًا (مع تطبيع الفراغات وحالة الأحرف) على **المقتطف
   الذي قُدّم للنموذج**، ولا يؤخذ رقم صفحة من إجابة AI. الغائب/المختلق يُرفض آليًا.
   هذه فحوص منشأ، **وليست تحققًا طبيًا أو دلاليًا من صحة الاستنتاج**.
4. **حفظ الاقتباس مع المراجعة**: وقت اعتماد مسودة، يُضاف نص الاقتباس إلى حقل التفسير الحالي
   عبر `StudyEvidenceNote` (بحد 6000 حرف). لا حاجة لهجرة قاعدة البيانات.
5. **Local inference task profiles**: `BrainTask.EXPLAIN` (400 token) و`STUDY_DRAFTS` (768 token)،
   مع system prompts مختلفة ومحتوى source مستقل عن تعليمات الشرح؛ المكتبة المجانية تعيد جوابًا كاملًا دون streaming.
6. **Pre-flight RAM check**: يفحص Android `ActivityManager.MemoryInfo` قبل تحميل GGUF.
   تقدير متحفظ = حجم النموذج × 1.35 + 384 MiB + 256 MiB حر؛ هذا
   فحص أولي وليس قياس peak RAM ولا يحمي من كل OOM.
7. **اختبارات إضافية** لتنسيق المخرجات، الاقتباسات، حفظ المصدر، ذاكرة الجهاز، ومسار التوليد الوهمي.
8. **versionCode = 13**، مع عدم تغيير Application ID أو مخطط SQLite أو آلية backup.

## تحقق فعلي في هذه الجلسة

- `scripts/test-brain.sh`: نجح (18 assertions).
- `scripts/test-drafts.sh`: نجح (offline parser و generated schema + quote checks).
- `scripts/test-llama-adapter-contract.sh`: نجح باستخدام **FakeLlama**؛ اختبار تفسير وتوليد JSON وفك تحميل.
- `scripts/test-local-brain-v09.sh`: نجح (extractive quotes وGGUF import بالمدخلات الاصطناعية).
- `scripts/test-model-memory.sh`: نجح (6 checks).
- `scripts/test-study-evidence.sh`: نجح (5 checks).
- `scripts/test-domain.sh`: نجح.
- `scripts/test-study-filter.sh`: نجح.
- `scripts/test-export.sh`: نجح.
- `scripts/test-backup-lines.sh`: نجح.
- `scripts/test-database-contract.py` و`test-migrations.py`: نجحا لمحاكاة v1→v3 وv2→v3.
- `scripts/validate-merged-contracts.py`: نجح بعد تصحيح شرط رقم الإصدار القديم.
- **Kotlin type-check** لمسار `GeneratedDraftJson` و`CloudBrainGateway`: نجح باستخدام
  واجهة `org.json` مؤقتة بتوقيعات الدوال فقط، **وليس اختبار JSON runtime**.
- `scripts/test-merged.sh` الشامل توقف بحد وقت البيئة بعد اجتياز المجموعات الأولى؛ لذلك
  شُغّلت معظم المجموعات التالية بصورة منفصلة كما بالأعلى.
- `./gradlew --offline :app:testDebugUnitTest :app:assembleDebug`: **فشل قبل التجميع** لأن
  تنزيل Gradle 8.13 من services.gradle.org غير متاح (`UnknownHostException`).
- لم تُشغّل اختبارات `GeneratedDraftJsonTest` (Robolectric) بعد؛ هذه في المصدر ومجدولة للبناء.

## بوابات إلزامية قبل تسليم APK جديد

1. تشغيل Gradle test/lint/assemble من SDK Android موثوق، والتحقق من توافق AAR
   `dev.ffmpegkit-maintained:llama-android:0.1.1` ودواله ومن مصدره وترخيصه وبصمته.
   هذه مكتبة طرف ثالث قليلة الانتشار؛ لا نعتبرها إنتاجية قبل مراجعتها. يظل استخدام
   ربط llama.cpp الرسمي بديلًا أقوى إن فشل التحقق.
2. اختبار `GeneratedDraftJsonTest` وRobolectric وPDF Import وReview + Backup عمليًا.
3. تثبيت تحديث Debug موقّع بنفس الشهادة على نسخة v0.6.1 مع بيانات اختبارية وBackup مراجع.
4. تجربة نموذج GGUF صغير حقيقي محليًا على Xiaomi Pad 5: TTFT، tokens/sec، RAM،
   حرارة الجهاز، استهلاك البطارية، الكراش والإلغاء وتحرير Native RAM.
5. تجربة مسار Cloud بمفتاح جلسة مخصص وإذن المستخدم، واختبار رفض JSON أو quote مزوّر.
6. تقييم 20 مثالًا طبيًا على الأقل من صفحات مصدر حقيقي بواسطة مراجعة بشرية:
   صحة المعلومة، التفسير الفلسطيني، وضوح المصادر، وسلامة مفتاح MCQ.

## ترتيب التطوير التالي

- **P0**: بناء/اختبار Android Native أولًا، إصلاح ربط GGUF الحقيقي، ودعم حالات توقفه.
- **P0**: ربط مسار شرح Local وLocal Drafts بمصدر PDF حقيقي وتدقيق بشري.
- **P1**: تحسين كشف ادعاءات غير مستندة لمقتطف المصدر، وتجزئة/بحث أكثر دلالية ومقاييس استرجاع.
- **P1**: FSRS الحقيقي، Exam Planner، دفاتر الأخطاء مع اختبارات هجرة غير مدمرة.
- **P2**: OCR للملفات المصورة، وتحديث Liquid Aurora الكامل وقراءة PDF عالية الدقة.

**لا تبدّل APK القديم بعد**: هذه النسخة Source فقط، والتحقق من الشهادة لا يعوّض
التحقق من توقيع APK مبني فعليًا أو سلامة التحديث على الجهاز.


## 2026-10-27 — Current v0.9.1 device-runner gate (CI PASS; device NOT TESTED)

- The runner now rejects a device whose `ro.product.cpu.abilist64` does not contain `arm64-v8a`, before build/install/model transfer. The fake-ADB regression proves an `x86_64`-only target exits 7 with no Gradle invocation, APK installation, GGUF push or instrumentation.
- **PASS — CI 40456263759**, source/test SHA `f681e164e5889221d51f549fd40bab5ba25c2168`: 156 tests, 0 failures/ignored; Android Debug and AndroidTest APK assembly and manifest/permission gates passed. APK SHA-256 values are recorded in `docs/DEVICE_GGUF_ACCEPTANCE.md`.
- The APK contains the ARM64 llama.cpp/ggml native libraries, but no model weights. The harness has only been tested with synthetic GGUF headers/Fake ADB.
- Remaining release gates: real GGUF inference and PDF page-two citation on Xiaomi Pad 5; live cloud consent/denial; frame-time/RAM/thermal/battery measurements; clinical review; and final signing/upgrade verification.
