package com.medmate.personal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.medmate.personal.brain.*
import com.medmate.personal.data.Chapter
import com.medmate.personal.data.ChapterStore
import com.medmate.personal.data.StudyStore
import com.medmate.personal.study.StudyKind
import com.medmate.personal.ui.theme.Muted
import com.medmate.personal.ui.theme.Turquoise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Actual source-grounded study interaction; no fabricated offline generation. */
@Composable
fun BrainPanel(chapter: Chapter, currentPage: Int, store: ChapterStore, onJump: (Int) -> Unit,
               modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val studyStore = remember(appContext) { StudyStore(appContext) }
    var offlineDrafts by remember(chapter.sha256) { mutableStateOf<List<OfflineDraft>>(emptyList()) }
    var ignoredDrafts by remember(chapter.sha256) { mutableStateOf(setOf<String>()) }
    var draftNotice by remember { mutableStateOf<String?>(null) }
    var draftBusyId by remember { mutableStateOf<String?>(null) }
    var question by remember(chapter.sha256) { mutableStateOf("") }
    var pageOnly by remember(chapter.sha256) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var results by remember(chapter.sha256) { mutableStateOf<BrainSearchResult?>(null) }
    var pdfCoverage by remember(chapter.sha256) { mutableStateOf<PdfTextCoverage?>(null) }
    var prepared by remember(chapter.sha256) { mutableStateOf<PreparedPrompt?>(null) }
    // Preview is tied to this exact prepared prompt; a changed PDF/question
    // must be reviewed again before a new cloud consent can be issued.
    var cloudPreviewExpanded by remember(chapter.sha256, prepared) { mutableStateOf(false) }
    var cloudPreviewReviewed by remember(chapter.sha256, prepared) { mutableStateOf(false) }
    var cloudAnswer by remember(chapter.sha256) { mutableStateOf<String?>(null) }
    var validation by remember { mutableStateOf<CitationCheck?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGateway by remember { mutableStateOf(false) }
    var showFullExcerpts by remember(chapter.sha256) { mutableStateOf(false) }
    val onDevice = remember { LlamaAndroidPort() }
    val arm64Capable = remember { Build.SUPPORTED_ABIS.contains("arm64-v8a") }
    // Release native model memory whenever this reading panel leaves composition.
    LaunchedEffect(onDevice) {
        try { awaitCancellation() } finally {
            withContext(NonCancellable) { onDevice.unload() }
        }
    }
    var localModelName by remember { mutableStateOf<String?>(null) }
    val modelDirectory = remember(appContext) { File(appContext.filesDir, "models") }
    fun requireEnoughRam(file: File) {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val status = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        require(ModelMemoryGate.canAttemptLoad(file.length(), status.availMem, status.lowMemory)) {
            "الذاكرة المتاحة قليلة لتحميل هذا النموذج بأمان. فرّغ التطبيقات أو اختر نموذج GGUF أصغر."
        }
    }
    var savedModels by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(modelDirectory) {
        savedModels = withContext(Dispatchers.IO) {
            modelDirectory.listFiles()?.filter { it.isFile && it.extension == "gguf" }
                ?.sortedByDescending { it.lastModified() }?.take(8).orEmpty()
        }
    }
    var localBusy by remember { mutableStateOf(false) }
    var localImportBytes by remember { mutableLongStateOf(0L) }
    var localAnswer by remember(chapter.sha256) { mutableStateOf<String?>(null) }
    var localCitationCheck by remember { mutableStateOf<CitationCheck?>(null) }
    var localInferenceAudit by remember(chapter.sha256) { mutableStateOf<LocalInferenceAudit?>(null) }
    var localCitationLinks by remember(chapter.sha256) { mutableStateOf<List<SourceCitationLink>>(emptyList()) }
    var cloudCitationLinks by remember(chapter.sha256) { mutableStateOf<List<SourceCitationLink>>(emptyList()) }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !localBusy) scope.launch {
            localBusy = true; localImportBytes = 0L; error = null
            try {
                var lastReported = 0L
                val imported = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    val stream = appContext.contentResolver.openInputStream(uri)
                        ?: error("تعذّر فتح ملف النموذج")
                    LocalModelImport.import(stream, modelDirectory) { bytes ->
                        coroutine.ensureActive()
                        if (bytes - lastReported >= 4L * 1_048_576) {
                            localImportBytes = bytes
                            lastReported = bytes
                        }
                    }
                }
                localImportBytes = imported.bytes
                // Import and activation are separate operations. A successful
                // import must remain selectable even when RAM preflight or
                // native GGUF loading fails on this attempt.
                savedModels = modelDirectory.listFiles()?.filter { it.isFile && it.extension == "gguf" }
                    ?.sortedByDescending { it.lastModified() }?.take(8).orEmpty()
                requireEnoughRam(imported.file)
                onDevice.loadModel(imported.file)
                localModelName = onDevice.loadedModelName
            } catch (e: Exception) {
                // A RAM preflight failure does not unload the previous model;
                // reflect actual native session state instead of hiding it.
                localModelName = onDevice.loadedModelName
                error = "لم يتم تحميل النموذج المحلي: ${e.localizedMessage}"
            } finally { localBusy = false }
        }
    }
    var gateway by remember { mutableStateOf("") }
    var gatewayModel by remember { mutableStateOf("") }
    // Deliberately transient, never saved to Compose's saved state or preferences.
    var sessionToken by remember { mutableStateOf("") }
    var explicitConsent by remember { mutableStateOf(false) }
    // In-memory only; one grant authorizes one exact endpoint/model/PDF request.
    var cloudConsent by remember { mutableStateOf<CloudTransferConsent?>(null) }
    var lastQuestion by remember { mutableStateOf("") }
    var lastPageOnly by remember { mutableStateOf(false) }
    var lastSourcePage by remember { mutableIntStateOf(-1) }
    var generatedDrafts by remember(chapter.sha256) { mutableStateOf<List<GeneratedDraft>>(emptyList()) }
    var rejectedGenerated by remember { mutableIntStateOf(0) }
    var generatedBy by remember(chapter.sha256) { mutableStateOf("") }
    var reviewedGenerated by remember(chapter.sha256) { mutableStateOf(setOf<Int>()) }

    // Read cached extraction metadata without triggering expensive PDFBox work.
    // Indexing is an explicit user action, or part of the existing source search.
    LaunchedEffect(chapter.sha256) {
        val pages = store.indexedPageSources(chapter.sha256)
        pdfCoverage = withContext(Dispatchers.Default) {
            PdfCoverageAudit.inspect(chapter.sha256, chapter.pageCount, pages)
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🧠 اسأل MedMate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("بنبدأ بمقاطع موثقة من الشبتر، وبعدها بتختار Local AI أو Cloud AI إذا احتجت شرحًا مولّدًا.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        pdfCoverage?.let { report ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("📑 تغطية النص القابل للبحث في PDF", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${report.readablePages}/${report.totalPages} صفحة فيها نص كافٍ · " +
                            "${report.indexedPages} مفهرسة · ${report.percentReadable}% قابلة للبحث",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (report.missingPages > 0) {
                        Text("${report.missingPages} صفحة لم تُفهرس بعد. البحث فيها قد يفوّت أدلة.",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                        OutlinedButton(
                            enabled = !busy && !localBusy && draftBusyId == null,
                            onClick = {
                                busy = true; error = null; progress = report.indexedPages
                                scope.launch {
                                    try {
                                        store.indexText(chapter) { indexed, _ ->
                                            withContext(Dispatchers.Main) { progress = indexed }
                                        }
                                        val pages = store.indexedPageSources(chapter.sha256)
                                        pdfCoverage = withContext(Dispatchers.Default) {
                                            PdfCoverageAudit.inspect(chapter.sha256, chapter.pageCount, pages)
                                        }
                                    } catch (e: Exception) {
                                        error = "فشل فحص تغطية النص: ${e.localizedMessage}"
                                    } finally { busy = false }
                                }
                            }
                        ) { Text(if (busy) "جاري فهرسة الصفحات… ${progress}/${chapter.pageCount}"
                            else "افحص النص في كل صفحات PDF") }
                    }
                    if (report.emptyPages + report.lowTextPages > 0) {
                        Text(
                            "${report.emptyPages} صفحة بلا نص مستخرج و${report.lowTextPages} بنص قليل جدًا. " +
                                "قد تكون صورًا وتحتاج OCR؛ هذا الفحص لا ينفّذ OCR ولا يثبت جودة النص.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (report.garbledPages > 0) {
                        Text(
                            "${report.garbledPages} صفحة فيها نص PDF تالف بوضوح (رموز بديلة/محارف تحكم). " +
                                "استُبعدت من أدلة الشرح الآلي؛ افتح الصفحات وافحصها أو استخدم OCR موثوقًا.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (report.firstUnusablePages.isNotEmpty()) {
                        Text("افتح صفحات بحاجة للفحص:", style = MaterialTheme.typography.bodySmall)
                        report.firstUnusablePages.take(5).forEach { page ->
                            TextButton(onClick = { onJump(page) }) { Text("صفحة ${page + 1} ↗") }
                        }
                    }
                }
            }
        }

        OutlinedTextField(question, onValueChange = { question = it }, enabled = !busy && !localBusy,
            label = { Text("شو بدك تفهم من الشبتر؟") },
            placeholder = { Text("مثلاً: اشرحلي Volkmann's contracture") },
            minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pageOnly, enabled = !busy && !localBusy, onCheckedChange = { pageOnly = it })
            Text("ابحث في الصفحة ${currentPage + 1} فقط", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            if (busy) return@Button
            busy = true; progress = 0; results = null; prepared = null; cloudAnswer = null
            generatedDrafts = emptyList(); rejectedGenerated = 0; reviewedGenerated = emptySet(); generatedBy = ""
            validation = null; localAnswer = null; localCitationCheck = null; localInferenceAudit = null
            localCitationLinks = emptyList(); cloudCitationLinks = emptyList()
            error = null; explicitConsent = false; cloudConsent = null
            val capturedQuestion = question.trim()
            val capturedOnly = pageOnly
            val capturedPage = currentPage
            scope.launch {
                try {
                    // Indexing is resumable and offline, running on the I/O dispatcher.
                    val status = store.indexingStatus(chapter.sha256)
                    if (status.first < chapter.pageCount) store.indexText(chapter) { indexed, _ ->
                        withContext(Dispatchers.Main) { progress = indexed }
                    }
                    val pages = store.indexedPageSources(chapter.sha256)
                    pdfCoverage = withContext(Dispatchers.Default) {
                        PdfCoverageAudit.inspect(chapter.sha256, chapter.pageCount, pages)
                    }
                    val answer = withContext(Dispatchers.Default) {
                        StudyBrain.search(chapter.sha256, pages, capturedQuestion,
                            if (capturedOnly) capturedPage else null)
                    }
                    results = answer
                    lastQuestion = capturedQuestion
                    lastPageOnly = capturedOnly
                    lastSourcePage = capturedPage
                    prepared = StudyBrain.prepareSourceOnly(chapter.sha256, capturedQuestion, answer)
                } catch (e: Exception) { error = e.localizedMessage ?: "فشل البحث في الشبتر" }
                finally { busy = false }
            }
        }, enabled = !busy && !localBusy && question.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "بفهرس وببحث… $progress/${chapter.pageCount}" else "ابحث في مصادر الشبتر (Offline)")
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("📖 استخراج أدوات الدراسة من الـPDF — بدون AI", fontWeight = FontWeight.SemiBold)
        Text("بنستخرج التعريفات والأسئلة الموجودة حرفيًا في الشبتر. راجع كل مسودة وصفحتها قبل الحفظ؛ ما بنولّد جواب طبي بالتخمين.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            enabled = !busy && !localBusy && draftBusyId == null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; error = null; draftNotice = null
                scope.launch {
                    try {
                        val status = store.indexingStatus(chapter.sha256)
                        if (status.first < chapter.pageCount) store.indexText(chapter) { i, _ -> progress = i }
                        val pages = store.indexedPageSources(chapter.sha256)
                        val allDrafts = withContext(Dispatchers.Default) { OfflineStudyDrafts.extract(pages, 120) }
                        val existing = studyStore.list(chapter.sha256)
                        offlineDrafts = allDrafts.filter { draft -> existing.none { item ->
                            item.pageIndex == draft.pageIndex && item.prompt.trim().equals(draft.prompt.trim(), ignoreCase = true)
                        } }
                        draftNotice = "${offlineDrafts.size} مسودة بحاجة مراجعة؛ المتشابهات المحفوظة تم تجاهلها"
                    } catch (e: Exception) { error = e.localizedMessage ?: "تعذّر استخراج المسودات" }
                    finally { busy = false }
                }
            }
        ) { Text("استخرج المسودات من صفحات الشبتر") }
        draftNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Turquoise) }
        offlineDrafts.filterNot { it.id in ignoredDrafts }.take(15).forEach { draft ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${when(draft.kind) {
                        DraftKind.DEFINITION_CARD -> "بطاقة تعريف"
                        DraftKind.SOURCE_MCQ -> "MCQ من الكتاب"
                        DraftKind.QUOTED_NOTE -> "اقتباس من المصدر"
                    }} · صفحة ${draft.pageNumber}", fontWeight = FontWeight.Bold, color = Turquoise)
                    Text(draft.prompt, style = MaterialTheme.typography.bodyMedium)
                    if (draft.options.isNotEmpty()) draft.options.forEachIndexed { index, option ->
                        Text("${'A' + index}) $option", style = MaterialTheme.typography.bodySmall)
                    }
                    if (draft.kind == DraftKind.SOURCE_MCQ) {
                        Text("مفتاح الكتاب: ${'A' + draft.correctIndex}", style = MaterialTheme.typography.bodySmall)
                    } else Text(draft.answer, style = MaterialTheme.typography.bodySmall)
                    if (draft.explanation.isNotBlank()) Text(draft.explanation, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onJump(draft.pageIndex) }) { Text("راجع صفحة ${draft.pageNumber}") }
                        TextButton(onClick = { ignoredDrafts = ignoredDrafts + draft.id }) { Text("تجاهل") }
                        Button(enabled = !busy && draftBusyId == null, onClick = {
                            draftBusyId = draft.id; error = null
                            scope.launch {
                                try {
                                    // Recheck before insert, including accidental repeated taps.
                                    val existing = studyStore.list(chapter.sha256)
                                    val duplicate = existing.any { it.pageIndex == draft.pageIndex &&
                                        it.prompt.equals(draft.prompt, ignoreCase = true) }
                                    if (!duplicate) studyStore.add(
                                        chapter = chapter,
                                        page = draft.pageIndex,
                                        kind = when(draft.kind) {
                                            DraftKind.DEFINITION_CARD -> StudyKind.FLASHCARD
                                            DraftKind.SOURCE_MCQ -> StudyKind.MCQ
                                            DraftKind.QUOTED_NOTE -> StudyKind.HIGH_YIELD
                                        },
                                        prompt = draft.prompt,
                                        answer = draft.answer,
                                        options = draft.options,
                                        correctIndex = draft.correctIndex,
                                        explanation = draft.explanation
                                    )
                                    ignoredDrafts = ignoredDrafts + draft.id
                                    draftNotice = if (duplicate) "البطاقة موجودة من قبل" else "انحفظت المسودة بعد الموافقة — بتلاقيها في Study Studio"
                                } catch (e: Exception) { error = e.localizedMessage ?: "فشل حفظ المسودة" }
                                finally { draftBusyId = null }
                            }
                        }) { Text(if (draftBusyId == draft.id) "جاري الحفظ…" else "اعتماد وحفظ") }
                    }
                }
            }
        }
        if (offlineDrafts.count { it.id !in ignoredDrafts } > 15) {
            Text("تظهر أول 15 مسودة؛ اعتمد أو تجاهل العناصر لتظهر البقية.", color = Muted,
                style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        results?.let { result ->
            when (result.status) {
                BrainSearchStatus.TOO_SHORT -> Text("اكتب اسم مفهوم طبي أو سؤال فيه مصطلح محدد.")
                BrainSearchStatus.SCANNED_PDF_OR_MISSING_TEXT -> Text("ما فيه نص قابل للاستخراج في النطاق المطلوب. ملفات الصور تحتاج OCR.")
                BrainSearchStatus.CORRUPTED_PDF_TEXT -> Column {
                    Text(
                        "تم استبعاد ${result.excludedCorruptedPages} صفحة فيها نص PDF تالف؛ " +
                            "هذا مش دليل إن الصفحات صور وتحتاج OCR. راجع الأصل قبل الشرح.",
                        color = MaterialTheme.colorScheme.error
                    )
                    result.firstCorruptedPage?.let { page ->
                        TextButton(onClick = { onJump(page) }) {
                            Text("افتح أول صفحة تالفة: ${page + 1} ↗")
                        }
                    }
                }
                BrainSearchStatus.NO_MATCH -> Text("ما لقيت مقاطع مطابقة. جرّب المصطلح بالإنجليزي، أو ابحث بكل الشبتر.")
                BrainSearchStatus.FOUND -> {
                    if (result.excludedCorruptedPages > 0) {
                        Text("تنبيه: استُبعدت ${result.excludedCorruptedPages} صفحة تالفة من نطاق البحث؛ " +
                            "الشرح والاستشهادات تستخدم النص السليم فقط.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        result.firstCorruptedPage?.let { page ->
                            TextButton(onClick = { onJump(page) }) {
                                Text("راجع الصفحة التالفة ${page + 1} ↗")
                            }
                        }
                    }
                    Text("${result.evidence.size} مقاطع من الكتاب · ${result.shownPages} صفحة قابلة للبحث",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    val brief = remember(result, lastQuestion) {
                        ExtractiveTutor.brief(lastQuestion, result.evidence)
                    }
                    Text("📌 ركّز على هاي الجمل من المصدر", fontWeight = FontWeight.SemiBold)
                    Text("اقتباسات حرفية منتقاة من صفحات الشبتر، مش شرح AI ولا تفسير طبي.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (brief.findings.isEmpty()) {
                        Text("ما قدرت أستخرج جمل مركزة؛ افتح المقتطفات الكاملة أدناه.", color = Muted)
                    }
                    brief.findings.forEach { finding ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().clickable { onJump(finding.pageIndex) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("[${finding.marker}] صفحة ${finding.pageNumber}  ↗ افتح الكتاب",
                                    color = Turquoise, fontWeight = FontWeight.SemiBold)
                                Text(finding.quote, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    TextButton(onClick = { showFullExcerpts = !showFullExcerpts }) {
                        Text(if (showFullExcerpts) "إخفاء المقاطع الكاملة" else "عرض المقاطع الكاملة (${result.evidence.size})")
                    }
                    if (showFullExcerpts) result.evidence.forEachIndexed { i, hit ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().clickable { onJump(hit.pageIndex) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("[S${i + 1}] صفحة ${hit.pageNumber}  ↗ افتح المصدر",
                                    color = Turquoise, fontWeight = FontWeight.SemiBold)
                                Text(hit.text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("[S] يشير لصفحة PDF فعلية، مش توثيق صحة المعلومة طبيًا.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    HorizontalDivider()
                    Text("🧠 Local AI — نموذج على الجهاز", fontWeight = FontWeight.SemiBold)
                    Text("اختياري: استورد ملف GGUF مرة واحدة؛ بيشتغل محليًا بدون إرسال صفحاتك للإنترنت. " +
                        "هذه أول نسخة تجريبية لمحرك llama.cpp؛ السرعة والدقة واستهلاك RAM بحاجة اختبار على Pad 5.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (!arm64Capable) Text("هذه النسخة من المحرك المحلي تحتاج ARM64. القراءة والـCloud بيظلوا متاحين.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = arm64Capable && !busy && !localBusy,
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (localBusy) "جاري استيراد / تحميل النموذج (${localImportBytes / 1_048_576} MB)…"
                            else if (localModelName == null) "استيراد نموذج GGUF وتشغيله" else "النموذج المحمّل: ${localModelName!!.take(20)}")
                    }
                    if (localBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (savedModels.isNotEmpty()) {
                        Text("نماذج محفوظة على جهازك (${savedModels.size})", style = MaterialTheme.typography.bodySmall)
                        savedModels.take(3).forEach { file ->
                            OutlinedButton(enabled = arm64Capable && !busy && !localBusy,
                                modifier = Modifier.fillMaxWidth(), onClick = {
                                    localBusy = true; error = null
                                    scope.launch {
                                        try {
                                            requireEnoughRam(file)
                                            onDevice.loadModel(file)
                                            localModelName = file.name
                                        } catch (e: Exception) {
                                            // Preserve the visible name of a previously loaded
                                            // model if only the new model's RAM gate failed.
                                            localModelName = onDevice.loadedModelName
                                            error = "تعذّر تشغيل النموذج المحفوظ: ${e.localizedMessage}"
                                        } finally { localBusy = false }
                                    }
                                }) {
                                Text("تحميل ${file.name.take(12)}… (${file.length() / 1_048_576} MB)")
                            }
                        }
                    }
                    if (localModelName != null) {
                        TextButton(enabled = !localBusy && !busy, onClick = {
                            localBusy = true
                            scope.launch {
                                try { onDevice.unload(); localModelName = null }
                                catch (e: Exception) { error = "تعذّر تفريغ النموذج: ${e.localizedMessage}" }
                                finally { localBusy = false }
                            }
                        }) { Text("تفريغ النموذج من RAM (دون حذف الملف)") }
                    }
                    Button(enabled = arm64Capable && localModelName != null && prepared != null && !busy && !localBusy &&
                        question.trim() == lastQuestion && pageOnly == lastPageOnly &&
                        (!pageOnly || currentPage == lastSourcePage), modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val found = results ?: return@Button
                            val p = StudyBrain.prepareSourceOnly(chapter.sha256, lastQuestion, found,
                                maxEvidenceChars = 2400) ?: return@Button
                            val requestIdentity = BrainRequestIdentity(
                                chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)
                            localBusy = true; error = null; localAnswer = null
                            localCitationCheck = null; localInferenceAudit = null; localCitationLinks = emptyList()
                            scope.launch {
                                try {
                                    val startNanos = System.nanoTime()
                                    // This is the actual native llama.cpp port, not FakeLlama.
                                    val response = onDevice.generate(p) { }
                                    val elapsed = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(0L)
                                    val audit = LocalInferenceAudit.fromNativeResponse(
                                        onDevice.loadedModelName, elapsed, p, response)
                                    if (requestIdentity.matches(chapter.sha256, question, pageOnly, currentPage)) {
                                        localAnswer = response
                                        localCitationCheck = audit.citationCheck
                                        localInferenceAudit = audit
                                        localCitationLinks = SourceCitationLinks.resolve(response, p.presentedEvidence)
                                    } else {
                                        error = "تغيّر السؤال أو الصفحة أثناء الاستدلال؛ تجاهلنا النتيجة القديمة."
                                    }
                                } catch (e: Exception) { error = "تعذّر الشرح المحلي: ${e.localizedMessage}" }
                                finally { localBusy = false }
                            }
                        }) { Text("اشرحلي محليًا من صفحات الكتاب") }
                    OutlinedButton(enabled = arm64Capable && localModelName != null && prepared != null &&
                        !busy && !localBusy && question.trim() == lastQuestion && pageOnly == lastPageOnly &&
                        (!pageOnly || currentPage == lastSourcePage), modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val source = results ?: return@OutlinedButton
                            val original = StudyBrain.prepareSourceOnly(chapter.sha256, lastQuestion, source,
                                maxEvidenceChars = 2400) ?: return@OutlinedButton
                            val requestIdentity = BrainRequestIdentity(
                                chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)
                            localBusy = true; error = null; generatedDrafts = emptyList()
                            rejectedGenerated = 0; reviewedGenerated = emptySet()
                            scope.launch {
                                try {
                                    val task = StudyDraftPrompt.prepare(original)
                                    val raw = onDevice.generate(task) { }
                                    val outcome = GeneratedDraftJson.parseAndValidate(raw, original.presentedEvidence)
                                    // If the user changed the source while inference ran, discard stale drafts.
                                    if (requestIdentity.matches(chapter.sha256, question, pageOnly, currentPage)) {
                                        generatedDrafts = outcome.accepted
                                        rejectedGenerated = outcome.rejected
                                        generatedBy = "Local AI"
                                    } else {
                                        error = "تغيّر السؤال أو الصفحة أثناء توليد المسودات؛ تجاهلنا النتيجة القديمة."
                                    }
                                } catch (e: Exception) { error = "فشل إنشاء المسودات محليًا: ${e.localizedMessage}" }
                                finally { localBusy = false }
                            }
                        }) { Text("✨ توليد MCQ وFlashcards محليًا — للمراجعة") }
                    localAnswer?.let { reply ->
                        Text("شرح محلي مولّد — راجع المصدر", fontWeight = FontWeight.Bold)
                        localInferenceAudit?.let { audit ->
                            Text(
                                "فحص تشغيل GGUF محلي: ${audit.modelName.take(32)} · " +
                                    "${audit.elapsedMs} ms · ${audit.answerChars} حرف · " +
                                    "صفحات المصدر: ${audit.sourcePages.joinToString(", ")} · " +
                                    if (audit.syntaxPass) "صيغة الاستشهاد PASS" else "صيغة الاستشهاد FAIL",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (audit.syntaxPass) Turquoise else MaterialTheme.colorScheme.error
                            )
                            Text("هذا إثبات تشخيصي لطلب الاستدلال على هذا الجهاز فقط؛ " +
                                "لا يثبت الدقة الطبية ولا الأداء على أجهزة أخرى.",
                                style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Text(reply, style = MaterialTheme.typography.bodyMedium)
                        if (localCitationLinks.isNotEmpty()) {
                            Text("افتح صفحات PDF المرتبطة بعلامات الاستشهاد:", style = MaterialTheme.typography.bodySmall)
                            localCitationLinks.forEach { link ->
                                TextButton(onClick = { onJump(link.pageIndex) }) {
                                    Text("[${link.marker}] ← صفحة ${link.pageNumber}")
                                }
                            }
                        }
                        Text(if (localCitationCheck?.accepted == true)
                            "مراجع [S] موجودة، لكن صحتها الطبية لم تُثبت آليًا."
                        else "تحذير: الاستشهاد ناقص أو غير صالح؛ لا تعتمد الشرح للحفظ.",
                            color = if (localCitationCheck?.accepted == true) Muted else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { showGateway = !showGateway }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showGateway) "إخفاء إعدادات الذكاء السحابي" else "☁️ Cloud AI (اختياري وبموافقتك)")
                    }
                }
            }
        }
        if (showGateway && prepared != null) {
            HorizontalDivider()
            Text("Cloud AI — بوابة خاصة عبر HTTPS", fontWeight = FontWeight.Bold)
            Text("اختياري فقط: أدخل رابط بوابة HTTPS مجانية تديرها بنفسك. لا يوجد مزوّد افتراضي ولا API مدفوع. لن نرسل السؤال والمقاطع إلا بعد موافقتك الصريحة.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(gateway, onValueChange = { gateway = it; explicitConsent = false; cloudConsent = null }, modifier = Modifier.fillMaxWidth(),
                label = { Text("رابط بوابتك HTTPS") }, placeholder = { Text("https://example.org/v1/chat/completions") }, singleLine = true)
            OutlinedTextField(gatewayModel, onValueChange = { gatewayModel = it; explicitConsent = false; cloudConsent = null }, modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم النموذج عند البوابة") }, singleLine = true)
            OutlinedTextField(sessionToken, onValueChange = { sessionToken = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("رمز البوابة / مفتاح API — لهذه الجلسة فقط") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            // Show the ACTUAL bounded excerpts included in PreparedPrompt, not
            // an untruncated search-result preview or an entire private PDF.
            val cloudSources = prepared!!.presentedEvidence
            Text("سيُرسل السؤال: ${prepared!!.question} · ${cloudSources.size} مقطع/مقاطع من PDF. لا يُرفع الملف الكامل.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            OutlinedButton(onClick = {
                cloudPreviewExpanded = !cloudPreviewExpanded
                if (cloudPreviewExpanded) cloudPreviewReviewed = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (cloudPreviewExpanded) "إخفاء المقاطع المراجَعة" else
                    "راجع النص الفعلي المرسل قبل الموافقة")
            }
            if (cloudPreviewExpanded) {
                cloudSources.forEachIndexed { index, source ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("[S${index + 1}] صفحة ${source.pageNumber}",
                                fontWeight = FontWeight.SemiBold, color = Turquoise)
                            Text(source.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (!cloudPreviewReviewed) Text("راجع المقاطع أولًا لتفعيل الموافقة السحابية.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = explicitConsent, enabled = !busy && cloudPreviewReviewed, onCheckedChange = { approved ->
                    val grant = if (approved && prepared != null) runCatching {
                        CloudTransferConsent.grant(gateway, gatewayModel,
                            BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage),
                            CloudEvidenceSeal.of(prepared!!,
                                BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)))
                    }.getOrNull() else null
                    cloudConsent = grant
                    explicitConsent = grant != null
                    if (approved && grant == null) error = "تحقق من رابط HTTPS واسم النموذج والسؤال قبل الموافقة"
                })
                Text("أوافق على إرسال سؤالي والمقاطع المعروضة فقط إلى بوابتي السحابية. فاهم إن المحتوى ممكن يكون حساسًا وإن إجابة AI تحتاج مراجعة.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(enabled = !busy && explicitConsent && prepared != null &&
                cloudConsent?.permits(gateway, gatewayModel,
                    BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage),
                    prepared?.let { CloudEvidenceSeal.of(it,
                        BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)) } ?: "") == true &&
                question.trim() == lastQuestion && pageOnly == lastPageOnly &&
                (!pageOnly || currentPage == lastSourcePage),
                modifier = Modifier.fillMaxWidth(), onClick = {
                    val p = prepared ?: return@Button
                    val requestIdentity = BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)
                    val consent = cloudConsent ?: return@Button
                    val endpoint = gateway; val selectedModel = gatewayModel; val token = sessionToken
                    if (!consent.permits(endpoint, selectedModel, requestIdentity,
                        CloudEvidenceSeal.of(p, requestIdentity))) return@Button
                    cloudConsent = null; explicitConsent = false // one approval per transfer
                    busy = true; error = null; cloudAnswer = null; validation = null; cloudCitationLinks = emptyList()
                    scope.launch {
                        try {
                            val text = CloudBrainGateway().explain(endpoint, selectedModel, token, p, consent, requestIdentity)
                            // Do not attach a delayed cloud answer to a different question/page/chapter.
                            if (requestIdentity.matches(chapter.sha256, question, pageOnly, currentPage)) {
                                cloudAnswer = text
                                validation = CitationValidator.check(text, p.presentedEvidence.size)
                                cloudCitationLinks = SourceCitationLinks.resolve(text, p.presentedEvidence)
                            }
                        } catch (e: Exception) { error = e.localizedMessage ?: "تعذّر الحصول على الشرح" }
                        finally { busy = false }
                    }
                }) { Text("أرسل المقاطع لشرحها") }
            OutlinedButton(enabled = !busy && explicitConsent && prepared != null &&
                cloudConsent?.permits(gateway, gatewayModel,
                    BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage),
                    prepared?.let { CloudEvidenceSeal.of(it,
                        BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)) } ?: "") == true &&
                question.trim() == lastQuestion && pageOnly == lastPageOnly &&
                (!pageOnly || currentPage == lastSourcePage),
                modifier = Modifier.fillMaxWidth(), onClick = {
                    val p = prepared ?: return@OutlinedButton
                    val requestIdentity = BrainRequestIdentity(chapter.sha256, lastQuestion, lastPageOnly, lastSourcePage)
                    val consent = cloudConsent ?: return@OutlinedButton
                    val endpoint = gateway; val selectedModel = gatewayModel; val token = sessionToken
                    if (!consent.permits(endpoint, selectedModel, requestIdentity,
                        CloudEvidenceSeal.of(p, requestIdentity))) return@OutlinedButton
                    cloudConsent = null; explicitConsent = false // one approval per transfer
                    busy = true; error = null; generatedDrafts = emptyList(); rejectedGenerated = 0
                    reviewedGenerated = emptySet()
                    scope.launch {
                        try {
                            val response = CloudBrainGateway().generateDrafts(endpoint, selectedModel, token, p, consent, requestIdentity)
                            // Never offer stale drafts for saving after the user changed source context.
                            if (requestIdentity.matches(chapter.sha256, question, pageOnly, currentPage)) {
                                generatedDrafts = response.accepted
                                rejectedGenerated = response.rejected
                                generatedBy = "Cloud AI"
                            }
                        } catch (e: Exception) { error = e.localizedMessage ?: "تعذّر توليد المسودات" }
                        finally { busy = false }
                    }
                }) { Text("✨ توليد High-Yield وMCQ وFlashcards للمراجعة") }
        }
        if (generatedDrafts.isNotEmpty() || rejectedGenerated > 0) {
            HorizontalDivider()
            Text("مسودات $generatedBy — لا تحفظ دون مراجعة المصدر", fontWeight = FontWeight.Bold)
            Text("${generatedDrafts.size} اجتازت فحص البنية والمراجع · $rejectedGenerated رُفضت آليًا. هذا لا يثبت صحتها الطبية.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            generatedDrafts.forEachIndexed { index, draft ->
                if (index !in reviewedGenerated) Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${draft.candidate.kind} · صفحة ${draft.pageIndex + 1} · ${draft.candidate.sourceId}",
                            color = Turquoise, fontWeight = FontWeight.Bold)
                        Text(draft.candidate.prompt)
                        draft.candidate.options.forEachIndexed { n, text ->
                            Text("${'A' + n}) $text", style = MaterialTheme.typography.bodySmall)
                        }
                        if (draft.candidate.kind == GeneratedKind.MCQ) {
                            Text("الإجابة المقترحة: ${'A' + draft.candidate.correctIndex}", color = Muted)
                            Text(draft.candidate.explanation, style = MaterialTheme.typography.bodySmall)
                        } else if (draft.candidate.answer.isNotBlank()) Text(draft.candidate.answer)
                        Text("اقتباس حرفي للتحقق من المصدر: ${draft.sourceQuote}", color = Muted,
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onJump(draft.pageIndex) }) { Text("افتح المصدر") }
                            TextButton(onClick = { reviewedGenerated = reviewedGenerated + index }) { Text("رفض") }
                            Button(enabled = !busy && draftBusyId == null, onClick = {
                                draftBusyId = "ai:$index"; error = null
                                scope.launch {
                                    try {
                                        val c = draft.candidate
                                        val existing = studyStore.list(chapter.sha256)
                                        val dupe = existing.any { it.pageIndex == draft.pageIndex &&
                                            it.prompt.equals(c.prompt, ignoreCase = true) }
                                        if (!dupe) studyStore.add(chapter, draft.pageIndex,
                                            when(c.kind) {
                                                GeneratedKind.HIGH_YIELD -> StudyKind.HIGH_YIELD
                                                GeneratedKind.FLASHCARD -> StudyKind.FLASHCARD
                                                GeneratedKind.MCQ -> StudyKind.MCQ
                                            }, c.prompt, c.answer, c.options, c.correctIndex,
                                            StudyEvidenceNote.withSource(c.explanation, draft.sourceQuote))
                                        reviewedGenerated = reviewedGenerated + index
                                        draftNotice = if (dupe) "العنصر موجود" else "تم حفظ المسودة بعد موافقتك"
                                    } catch (e: Exception) { error = e.localizedMessage ?: "تعذّر الحفظ" }
                                    finally { draftBusyId = null }
                                }
                            }) { Text("اعتمد بعد المراجعة") }
                        }
                    }
                }
            }
        }
        cloudAnswer?.let { answer ->
            HorizontalDivider()
            Text("شرح AI — راجعه مع الكتاب", style = MaterialTheme.typography.titleMedium)
            Text(answer, style = MaterialTheme.typography.bodyMedium)
            if (cloudCitationLinks.isNotEmpty()) {
                Text("افتح صفحات PDF المرتبطة بعلامات الاستشهاد:", style = MaterialTheme.typography.bodySmall)
                cloudCitationLinks.forEach { link ->
                    TextButton(onClick = { onJump(link.pageIndex) }) {
                        Text("[${link.marker}] ← صفحة ${link.pageNumber}")
                    }
                }
            }
            Text(if (validation?.accepted == true) "علامات الاستشهاد ضمن المصادر المرسلة، لكن صحة كل ادعاء طبي لم تُثبت آليًا."
                else "تحذير: الإجابة لا تتضمن استشهادًا صالحًا بالمقاطع المتاحة؛ لا تعتمدها للحفظ.",
                color = if (validation?.accepted == true) Muted else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
