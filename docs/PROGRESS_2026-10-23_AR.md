# MedMate — Progress: v0.9.0 Local Brain Experimental

## ما تم فعليًا داخل هذه الدفعة

| القسم | التنفيذ | التحقق |
| --- | --- | --- |
| الحفاظ على قاعدة بيانات v3 وملف التوقيع | لا تغيير للمخطط أو بيانات المستخدم، versionCode=12 | `keytool` يؤكد نفس SHA-256 لشهادة v0.6.1 |
| المقتطفات المحلية المركزة | `ExtractiveTutor.kt` + UI تعرض اقتباسات وصفحات من PDF | Kotlin/JUnit + اختبار مصدر نصي؛ Android UI لم يختبر |
| استيراد نموذج محلي GGUF | تدفق 64KiB، اسم بصمة SHA256، منع تكرار، حذف .part في الفشل/الإلغاء | 9 Kotlin smoke tests، JUnit test؛ لا يشمل نموذجًا حقيقيًا |
| تحميل GGUF وتوليد جواب محلي | `LlamaAndroidPort` متصل بمكتبة Maven مثبتة؛ Context=2048, threads=4, maxTokens=400 | fake backend lifecycle 5 checks؛ **المكتبة الأصلية لم تُشغل** |
| إدارة RAM للنموذج | تفريغ يدوي وعند مغادرة شاشة القارئ، قفل عمليات النموذج | محاكاة دورة الحياة فقط |
| Cloud AI | بقي اختياريًا وبموافقة صريحة، HTTPS فقط | فحوص أمن محلية سابقة؛ اختبار خدمة فعلية ناقص |
| MCQ وFlashcards والملاحظات | حافظنا على ميزات v0.8.1 والاختبارات | فحوص محلية وSQL ناجحة |
| Android Build v0.9 | المحاولة توقفت عند تنزيل Gradle wrapper | **لم يتم تجميع APK جديد** |

## نتائج الاختبارات المرصودة

- `scripts/test-local-brain-v09.sh`: 6 ExtractiveTutor + 9 import checks ناجحة.
- `scripts/test-llama-adapter-contract.sh`: 5 fake-backend lifecycle checks ناجحة. هذا اختبار ABI-Kotlin وهمي، لا يثبت API/AAR على Android.
- `scripts/test-brain.sh`: 18 brain/security assertions PASS.
- `scripts/test-drafts.sh`: offline and generated drafts PASS.
- `scripts/test-study-filter.sh`, `scripts/test-export.sh`, `scripts/test-backup-lines.sh`: PASS.
- `scripts/test-database-contract.py`, `scripts/test-migrations.py`: PASS.
- `scripts/validate-merged-contracts.py`: 14 regression guards PASS.
- `./gradlew --offline :app:assembleDebug`: فشل في تنزيل Gradle distribution، قبل أن يبدأ Android build.

## الإجراءات المطلوبة للإغلاق

1. تنفيذ Gradle assemble + lint + Robolectric في بيئة Android SDK/شبكة ورفع تقارير الاختبارات الحقيقية.
2. مراجعة provenance وSHA-256 الخاصة بـAAR من Maven Central، وفحص المكتبة ومكوناتها Native، وإعادة تقييم مصدر llama.cpp الرسمي مقابل الحزمة المستخدمة.
3. تجربة نموذج GGUF صغير حقيقي على Xiaomi Pad 5، وتوثيق التوافق مع Qwen وذاكرة RAM وسرعة توليد الرموز والحرارة والانقطاعات.
4. دراسة 20 سؤالًا طبيًا على PDF Orthopedics معروف مع تدقيق تطابق الشرح والمصدر بشريًا (الاستشهاد البنيوي ليس فحص صحة).
5. تحديث التطبيق الموجود فقط بعد بناء وتوقيع نسخة متوافقة وتجربة نسخة احتياطية قابلة للاسترجاع.
6. بعد إثبات Local Brain: توليد MCQ/High-Yield على الجهاز، FSRS الحقيقي، Exam Planner، ثم تحسين الواجهة Liquid Aurora.

**الحالة النهائية لهذه الدفعة: Source integrated and locally tested; NOT an APK or proven real local LLM.**
