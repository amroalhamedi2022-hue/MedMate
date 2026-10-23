package com.medmate.personal.ui

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.medmate.personal.study.StudyExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medmate.personal.data.Chapter
import com.medmate.personal.data.StudyStore
import com.medmate.personal.study.*
import com.medmate.personal.ui.theme.Muted
import com.medmate.personal.ui.theme.Turquoise
import kotlinx.coroutines.launch

/** Hand-authored, page-anchored content. Does not fabricate medical answers or need Internet. */
@Composable
fun StudyDeskPanel(chapter: Chapter, page: Int, onJump: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { StudyStore(context) }
    val scope = rememberCoroutineScope()
    var items by remember(chapter.sha256) { mutableStateOf<List<StudyItem>>(emptyList()) }
    var wrong by remember(chapter.sha256) { mutableStateOf<List<WrongAnswer>>(emptyList()) }
    var counts by remember(chapter.sha256) { mutableStateOf(StudyCounts()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var mode by remember(chapter.sha256) { mutableIntStateOf(0) }
    var adding by remember(chapter.sha256) { mutableStateOf<StudyKind?>(null) }
    var editing by remember(chapter.sha256) { mutableStateOf<StudyItem?>(null) }
    var searchStudy by remember(chapter.sha256) { mutableStateOf("") }
    var currentPageOnly by remember(chapter.sha256) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<StudyItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }

    suspend fun refresh() {
        items = store.list(chapter.sha256)
        counts = store.counts(chapter.sha256)
        wrong = store.mistakes(chapter.sha256)
        now = System.currentTimeMillis()
    }
    fun submit(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); refresh(); revealed = false; selected = -1 }
            catch (ex: Exception) { error = ex.localizedMessage ?: "تعذّر حفظ التغيير" }
            finally { busy = false }
        }
    }
    // Both exports use Android's system picker; no storage or network permissions needed.
    fun fileName(suffix: String): String {
        val safe = chapter.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(52)
        return "MedMate-${safe.ifBlank { "Chapter" }}$suffix"
    }
    val ankiExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { uri ->
        if (uri != null && !busy) submit {
            val cards = store.list(chapter.sha256)
            val tsv = withContext(Dispatchers.Default) { StudyExport.ankiBasicTsv(chapter.title, cards) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(tsv) }
                    ?: throw IllegalStateException("تعذّر فتح ملف التصدير")
            }
            notice = "تم تصدير ${cards.count { it.kind != StudyKind.HIGH_YIELD }} بطاقة وسؤال كملف TSV. عند استيرادها في Anki اختر Basic وعمود Front/Back."
        }
    }
    val notesExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && !busy) submit {
            val cards = store.list(chapter.sha256)
            val text = withContext(Dispatchers.Default) { StudyExport.highYieldMarkdown(chapter.title, cards) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                    ?: throw IllegalStateException("تعذّر فتح ملف التصدير")
            }
            notice = "تم تصدير ${cards.count { it.kind == StudyKind.HIGH_YIELD }} نقطة High-Yield مع صفحات مصدرها."
        }
    }
    LaunchedEffect(chapter.sha256) {
        try { refresh() } catch (e: Exception) { error = e.localizedMessage }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Study Studio 🩺", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("معلومات من صفحات المصدر، ومسودات AI لا تحفظ إلا بعد مراجعتك.", color = Muted,
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("High-Yield", "Flashcards", "MCQ", "جلسة مراجعة", "أخطائي").forEachIndexed { index, label ->
                FilterChip(selected = mode == index, onClick = { mode = index; revealed = false; selected = -1 },
                    label = { Text(label) })
            }
        }
        Text("${counts.highYield} نقطة مهمة  ·  ${counts.flashcards} بطاقة  ·  ${counts.mcqs} سؤال  ·  ${counts.due} مستحق",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { ankiExporter.launch(fileName("-anki.tsv")) },
                enabled = !busy && counts.flashcards + counts.mcqs > 0, modifier = Modifier.weight(1f)) {
                Text("تصدير Anki", maxLines = 1)
            }
            OutlinedButton(onClick = { notesExporter.launch(fileName("-high-yield.md")) },
                enabled = !busy && counts.highYield > 0, modifier = Modifier.weight(1f)) {
                Text("تصدير High-Yield", maxLines = 1)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (mode) {
            in 0..2 -> {
                val kind = when (mode) {
                    0 -> StudyKind.HIGH_YIELD
                    1 -> StudyKind.FLASHCARD
                    else -> StudyKind.MCQ
                }
                Button(onClick = { adding = kind }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(when(kind) {
                        StudyKind.HIGH_YIELD -> "+ إضافة معلومة مهمة من صفحة ${page + 1}"
                        StudyKind.FLASHCARD -> "+ بطاقة من صفحة ${page + 1}"
                        StudyKind.MCQ -> "+ سؤال MCQ من صفحة ${page + 1}"
                    })
                }
                OutlinedTextField(
                    value = searchStudy, onValueChange = { searchStudy = it.take(120) },
                    label = { Text("ابحث في أسئلتي وملاحظاتي") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = currentPageOnly,
                    onClick = { currentPageOnly = !currentPageOnly },
                    label = { Text(if (currentPageOnly) "صفحة ${page + 1} فقط" else "كل صفحات الشبتر") }
                )
                val subset = remember(items, kind, page, currentPageOnly, searchStudy) {
                    StudyFilter.select(items, kind, if (currentPageOnly) page else null, searchStudy)
                }
                Text("${subset.size} نتيجة", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (subset.isEmpty()) Text("ما في عناصر مطابقة. جرّب تغيير البحث أو فلتر الصفحة.", color = Muted)
                else {
                    val visible = subset.take(300)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visible.size, key = { visible[it].id }) { index ->
                            val item = visible[index]
                            StudyCard(item) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onJump(item.pageIndex) }) { Text("صفحة ${item.pageIndex + 1}") }
                                    TextButton(onClick = { editing = item }, enabled = !busy) { Text("تعديل") }
                                    TextButton(onClick = { confirmDelete = item }, enabled = !busy) {
                                        Text("حذف", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        if (subset.size > visible.size) item {
                            Text("تظهر أول 300 نتيجة فقط. ضيّق البحث لرؤية المزيد.", color = Muted)
                        }
                    }
                }
            }
            3 -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { submit { } }, enabled = !busy) { Text("تحديث المستحق") }
                val due = items.filter { it.kind != StudyKind.HIGH_YIELD && it.review.dueAt <= now }
                    .sortedWith(compareBy<StudyItem> { it.review.dueAt }.thenBy { it.createdAt })
                val current = due.firstOrNull()
                if (current == null) {
                    Text("خلصت مراجعاتك المستحقة! 🌿", fontWeight = FontWeight.SemiBold)
                    Text("أي بطاقة جديدة بتظهر هون مباشرة، واللي راجعتها بتظهر بموعدها.", color = Muted)
                } else {
                    Text("المستحق الآن: ${due.size}", color = Turquoise)
                    StudyCard(current) {
                        if (current.kind == StudyKind.FLASHCARD) {
                            if (!revealed) Button(onClick = { revealed = true }) { Text("أظهر الإجابة") }
                            else {
                                Text("الإجابة: ${current.answer}", fontWeight = FontWeight.Bold)
                                if (current.explanation.isNotBlank()) Text(current.explanation)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    listOf("مجدّدًا" to 0, "صعب" to 1, "جيد" to 2, "سهل" to 3).forEach { (txt, rate) ->
                                        OutlinedButton(onClick = { submit { store.review(current.id, rate) } }, enabled = !busy) {
                                            Text(txt)
                                        }
                                    }
                                }
                            }
                        } else {
                            current.options.forEachIndexed { index, choice ->
                                OutlinedButton(onClick = { if (!revealed) selected = index },
                                    enabled = !revealed && !busy, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected == index) Turquoise.copy(alpha = 0.18f)
                                            else Color.Transparent)) {
                                    Text("${index + 1}. $choice")
                                }
                            }
                            if (!revealed) Button(onClick = { revealed = true }, enabled = selected in current.options.indices && !busy) {
                                Text("تحقق من الإجابة")
                            } else {
                                Text(if (selected == current.correctIndex) "✅ صحيح" else "❌ راجع المعلومة", fontWeight = FontWeight.Bold)
                                Text("الإجابة الصحيحة: ${current.options[current.correctIndex]}")
                                if (current.explanation.isNotBlank()) Text("التفسير: ${current.explanation}")
                                Button(onClick = { submit {
                                    store.review(current.id, if (selected == current.correctIndex) 2 else 0, selected)
                                } }, enabled = !busy) { Text("احفظ النتيجة وانتقل") }
                            }
                        }
                        TextButton(onClick = { onJump(current.pageIndex) }) { Text("راجع المصدر · صفحة ${current.pageIndex + 1}") }
                    }
                }
                }
            }
            else -> {
                if (wrong.isEmpty()) Text("ولا غلط مسجل لحد الآن ✨", color = Muted)
                else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wrong.forEach { mistake ->
                        StudyCard(mistake.item) {
                            Text("إجابتك: ${mistake.item.options.getOrNull(mistake.selectedIndex) ?: "—"}")
                            Text("الصحيح: ${mistake.item.options[mistake.item.correctIndex]}", color = Turquoise)
                            if (mistake.item.explanation.isNotBlank()) Text(mistake.item.explanation)
                            TextButton(onClick = { onJump(mistake.item.pageIndex) }) {
                                Text("راجع صفحة ${mistake.item.pageIndex + 1}")
                            }
                        }
                    }
                }
            }
        }
    }
    adding?.let { kind ->
        AddStudyDialog(kind, page, onDismiss = { adding = null }, onSubmit = { prompt, answer, options, correct, explanation ->
            adding = null
            submit { store.add(chapter, page, kind, prompt, answer, options, correct, explanation) }
        })
    }
    editing?.let { item ->
        AddStudyDialog(item.kind, item.pageIndex, existing = item,
            onDismiss = { editing = null },
            onSubmit = { prompt, answer, options, correct, explanation ->
                editing = null
                submit { store.update(item.id, chapter.sha256, prompt, answer, options, correct, explanation) }
            })
    }
    confirmDelete?.let { item ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("حذف العنصر؟") },
            text = { Text("رح تنحذف البطاقة وسجل مراجعاتها نهائيًا. ملف PDF وملاحظات الصفحات ما بتتأثر.") },
            confirmButton = { TextButton(onClick = {
                confirmDelete = null; submit { store.delete(item.id) }
            }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("إلغاء") } })
    }
    notice?.let { msg ->
        AlertDialog(onDismissRequest = { notice = null }, title = { Text("تم التصدير") },
            text = { Text(msg) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("تمام") } })
    }
    error?.let { msg ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("MedMate") },
            text = { Text(msg) }, confirmButton = { TextButton(onClick = { error = null }) { Text("تمام") } })
    }
}

@Composable
private fun StudyCard(item: StudyItem, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White.copy(alpha = 0.07f), shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("صفحة ${item.pageIndex + 1} · ${item.kind.name}", style = MaterialTheme.typography.labelSmall, color = Turquoise)
            Text(item.prompt, fontWeight = FontWeight.SemiBold)
            if (item.kind == StudyKind.HIGH_YIELD && item.explanation.isNotBlank()) Text(item.explanation, color = Muted)
            content()
        }
    }
}

@Composable
private fun AddStudyDialog(
    kind: StudyKind, page: Int, onDismiss: () -> Unit,
    onSubmit: (String, String, List<String>, Int, String) -> Unit,
    existing: StudyItem? = null,
) {
    var prompt by remember(kind, page, existing?.id) { mutableStateOf(existing?.prompt ?: "") }
    var answer by remember(kind, page, existing?.id) { mutableStateOf(existing?.answer ?: "") }
    var explanation by remember(kind, page, existing?.id) { mutableStateOf(existing?.explanation ?: "") }
    var correct by remember(kind, page, existing?.id) { mutableIntStateOf(existing?.correctIndex?.takeIf { it >= 0 } ?: 0) }
    val options = remember(kind, page, existing?.id) {
        mutableStateListOf(*(existing?.options?.takeIf { it.size in 4..5 } ?: listOf("", "", "", "")).toTypedArray())
    }
    val answersLocked = existing != null &&
        (existing.review.repetitions > 0 || existing.review.lapses > 0)
    var validation by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("${if (existing != null) "تعديل · " else ""}${when (kind) {
            StudyKind.HIGH_YIELD -> "معلومة High-Yield"
            StudyKind.FLASHCARD -> "بطاقة سؤال / جواب"
            StudyKind.MCQ -> "سؤال اختيار من متعدد"
        }} · صفحة ${page + 1}") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = prompt, onValueChange = { prompt = it },
                    label = { Text(if (kind == StudyKind.HIGH_YIELD) "النقطة المهمة" else "السؤال") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
                if (kind == StudyKind.FLASHCARD) {
                    OutlinedTextField(value = answer, onValueChange = { answer = it },
                        label = { Text("الإجابة") }, enabled = !answersLocked,
                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
                if (kind == StudyKind.MCQ) {
                    Text("الاختيارات · اضغط الدائرة جنب الإجابة الصحيحة", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                    if (!answersLocked) {
                        TextButton(onClick = {
                            if (options.size == 4) options.add("")
                            else { options.removeAt(4); if (correct > 3) correct = 0 }
                        }) { Text(if (options.size == 4) "+ إضافة خيار خامس" else "− إزالة الخيار الخامس") }
                    }
                    options.indices.forEach { i ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = correct == i, onClick = { correct = i }, enabled = !answersLocked)
                            OutlinedTextField(value = options[i], onValueChange = { options[i] = it },
                                label = { Text("الخيار ${i+1}") }, enabled = !answersLocked,
                                modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                }
                OutlinedTextField(value = explanation, onValueChange = { explanation = it },
                    label = { Text(if (kind == StudyKind.HIGH_YIELD) "توضيح (اختياري)" else "التفسير (اختياري)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("المصدر: PDF · صفحة ${page+1}. راجع صحة المحتوى الطبي قبل حفظه.", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
                if (existing != null && kind != StudyKind.HIGH_YIELD) {
                    Text("حماية سجل المراجعات: بعد أول مراجعة، يمكنك تعديل صياغة السؤال والتفسير، " +
                         "لكن تغيير الإجابة أو الخيارات يحتاج بطاقة جديدة.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (validation != null) Text(validation!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val choices = if (kind == StudyKind.MCQ) options.toList() else emptyList()
            try {
                StudyValidation.check(kind, prompt, answer, choices, correct, explanation)
                onSubmit(prompt, answer, choices, if (kind == StudyKind.MCQ) correct else -1, explanation)
            } catch (e: IllegalArgumentException) { validation = e.localizedMessage }
        }) { Text(if (existing != null) "حفظ التعديل" else "حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
