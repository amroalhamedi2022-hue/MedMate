package com.medmate.personal.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medmate.personal.data.Chapter
import com.medmate.personal.data.ChapterStore
import com.medmate.personal.data.BackupManager
import com.medmate.personal.data.PageSearchHit
import com.medmate.personal.reader.PdfPageDecoder
import com.medmate.personal.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MedMateApp(store: ChapterStore) {
    MedMateTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val scope = rememberCoroutineScope()
            val chapters = remember { mutableStateListOf<Chapter>() }
            val context = androidx.compose.ui.platform.LocalContext.current
            val backups = remember(context) { BackupManager(context) }
            var selectedSha by rememberSaveable { mutableStateOf<String?>(null) }
            val selected = chapters.firstOrNull { it.sha256 == selectedSha }
            var working by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }
            suspend fun refresh() {
                val all = store.listChapters()
                chapters.clear(); chapters.addAll(all)
            }
            LaunchedEffect(Unit) { runCatching { refresh() }.onFailure { message = it.message } }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null && !working) scope.launch {
                    working = true
                    try {
                        val result = store.importPdf(uri)
                        refresh()
                        selectedSha = result.chapter.sha256
                        if (result.alreadyPresent) message = "الملف موجود مسبقًا، فتحنا نسختك المحفوظة"
                    } catch (e: Exception) {
                        message = e.localizedMessage ?: "تعذّر استيراد PDF"
                    } finally { working = false }
                }
            }
            val backupExport = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri != null && !working) scope.launch {
                    working = true
                    try {
                        val result = backups.export(uri)
                        message = "اكتمل النسخ: ${result.chapters} شبتر، ${result.notes} ملاحظة، ${result.studyItems} عنصر دراسة، ${result.reviews} مراجعة. النسخة غير مشفّرة."
                    } catch (e: Exception) { message = "فشل تصدير النسخة: ${e.localizedMessage}" }
                    finally { working = false }
                }
            }
            val backupImport = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null && !working) scope.launch {
                    working = true
                    try {
                        val result = backups.restore(uri)
                        refresh()
                        message = "تم الاسترجاع: ${result.newChapters} شبتر، ${result.newNotes} ملاحظة، ${result.newStudyItems} بطاقة وسؤال، ${result.newReviews} مراجعة. حفظنا ${result.preservedNotes} ملاحظة محلية دون تغيير."
                    } catch (e: Exception) { message = "فشل استرجاع النسخة: ${e.localizedMessage}" }
                    finally { working = false }
                }
            }
            Box(Modifier.fillMaxSize()) {
                LiquidBackdrop()
                if (selected == null) {
                    LibraryScreen(chapters, working, onImport = { picker.launch(arrayOf("application/pdf")) },
                        onExport = { backupExport.launch("MedMate-backup-${java.time.LocalDate.now()}.zip") },
                        onRestore = { backupImport.launch(arrayOf("*/*")) },
                        onOpen = { selectedSha = it.sha256 })
                } else {
                    val chapter = selected
                    key(chapter.sha256) {
                        ReaderScreen(chapter, store) { selectedSha = null; scope.launch { refresh() } }
                    }
                }
                message?.let { notice ->
                    AlertDialog(onDismissRequest = { message = null },
                        confirmButton = { TextButton(onClick = { message = null }) { Text("تمام") } },
                        title = { Text("MedMate") }, text = { Text(notice) })
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(chapters: List<Chapter>, working: Boolean,
                          onImport: () -> Unit, onExport: () -> Unit, onRestore: () -> Unit,
                          onOpen: (Chapter) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("MedMate  ✦", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("مساحة دراستك الطبية", color = Muted)
                }
                Text("● Offline Ready", color = Turquoise, fontSize = 12.sp)
            }
        }
        item {
            GlassPanel {
                Text("كل صفحة إلها حكايتها 🩺", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("ارفع شبترك، افتحه من آخر صفحة وصلت إلها، واكتب ملاحظاتك حتى بدون إنترنت.", color = Muted)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onImport, enabled = !working) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (working) "جاري العمل..." else "إضافة PDF")
                    }
                    Text("${chapters.size} شبتر", color = Muted)
                }
            }
        }
        item {
            GlassPanel {
                Text("حماية مكتبتك", fontWeight = FontWeight.SemiBold)
                Text("نسخة ZIP فيها ملفات PDF وملاحظاتك؛ غير مشفّرة، احتفظ فيها بمكان خاص وآمن.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, enabled = !working, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("نسخ احتياطي", maxLines = 1)
                    }
                    OutlinedButton(onClick = onRestore, enabled = !working, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("استرجاع", maxLines = 1)
                    }
                }
            }
        }
        item { Text("مكتبتي", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (chapters.isEmpty()) {
            item {
                GlassPanel {
                    Icon(Icons.Default.AutoStories, null, tint = Turquoise, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("لسه المكتبة فاضية", style = MaterialTheme.typography.titleMedium)
                    Text("اضغط «إضافة PDF» وبلّش أول شبتر.", color = Muted)
                }
            }
        } else {
            items(chapters, key = { it.sha256 }) { chapter ->
                GlassPanel(Modifier.fillMaxWidth().clickable { if (!working) onOpen(chapter) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = Sky, modifier = Modifier.size(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text("${chapter.pageCount} صفحة  ·  موضع القراءة: ${chapter.lastPage + 1}", color = Muted,
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { (chapter.lastPage + 1f) / chapter.pageCount },
                                modifier = Modifier.fillMaxWidth(), color = Turquoise)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Turquoise)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(chapter: Chapter, store: ChapterStore, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable(chapter.sha256) { mutableIntStateOf(chapter.lastPage.coerceIn(0, chapter.pageCount - 1)) }
    var showJump by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }
    var note by rememberSaveable(chapter.sha256, page) { mutableStateOf("") }
    var loaded by rememberSaveable(chapter.sha256, page) { mutableStateOf(false) }
    var savedText by rememberSaveable(chapter.sha256, page) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var studyTab by rememberSaveable(chapter.sha256) { mutableIntStateOf(4) }
    var query by rememberSaveable(chapter.sha256) { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<PageSearchHit>>(emptyList()) }
    var indexedPages by remember { mutableIntStateOf(0) }
    var pagesWithText by remember { mutableIntStateOf(0) }
    var indexing by remember { mutableStateOf(false) }
    var indexProgress by remember { mutableIntStateOf(0) }
    var pageText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chapter.sha256) {
        runCatching { store.indexingStatus(chapter.sha256) }.onSuccess {
            indexedPages = it.first; pagesWithText = it.second
        }
    }
    LaunchedEffect(chapter.sha256, page, indexedPages) {
        pageText = null
        try {
            pageText = store.readPageText(chapter.sha256, page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            info = e.localizedMessage ?: "تعذّر تحميل نص الصفحة"
        }
    }
    LaunchedEffect(chapter.sha256, query, indexedPages) {
        delay(220)
        searchHits = emptyList()
        try {
            searchHits = store.searchPages(chapter.sha256, query.take(120))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            info = e.localizedMessage ?: "تعذّر البحث في الشبتر"
        }
    }
    fun startIndex() {
        if (indexing) return
        scope.launch {
            indexing = true
            try {
                val result = store.indexText(chapter) { count, _ ->
                    withContext(Dispatchers.Main) { indexProgress = count }
                }
                indexedPages = result.indexed; pagesWithText = result.withText
                pageText = store.readPageText(chapter.sha256, page)
                info = if (result.withText == 0)
                    "ما وجدنا نصًا مدمجًا بهذا PDF. لو صفحاته صور، رح تحتاج OCR في المرحلة التالية."
                else "تم فهرسة ${result.indexed} صفحة؛ ${result.withText} فيها نص قابل للبحث."
            } catch (e: Exception) { info = "تعذّرت الفهرسة: ${e.localizedMessage}" }
            finally { indexing = false }
        }
    }

    LaunchedEffect(chapter.sha256, page) {
        if (!loaded) {
            val value = store.readNote(chapter.sha256, page)
            note = value; savedText = value; loaded = true
        }
    }
    suspend fun commit() {
        val snapshot = note
        if (loaded && snapshot != savedText) {
            saving = true
            try { store.saveNote(chapter.sha256, page, snapshot); savedText = snapshot }
            finally { saving = false }
        }
    }
    LaunchedEffect(chapter.sha256, page, note, loaded) {
        if (loaded && note != savedText) {
            delay(850)
            runCatching { commit() }.onFailure { info = it.localizedMessage ?: "فشل الحفظ التلقائي" }
        }
    }
    fun go(newPage: Int) {
        if (newPage !in 0 until chapter.pageCount) return
        scope.launch {
            try {
                commit()
                store.updateLastPage(chapter.sha256, newPage)
                page = newPage
            } catch (e: Exception) { info = e.localizedMessage ?: "فشل حفظ التقدم" }
        }
    }
    fun back() { scope.launch {
        try { commit(); store.updateLastPage(chapter.sha256, page); onClose() }
        catch (e: Exception) { info = e.localizedMessage ?: "تعذّر حفظ الملاحظة" }
    } }
    BackHandler { back() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "العودة") }
            Column(Modifier.weight(1f)) {
                Text(chapter.title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = {
                    jumpInput = (page + 1).toString()
                    showJump = true
                }, contentPadding = PaddingValues(0.dp)) {
                    Text("الصفحة ${page + 1} من ${chapter.pageCount}  ↗", color = Turquoise, fontSize = 12.sp)
                }
            }
            IconButton(onClick = { go(page - 1) }, enabled = loaded && page > 0 && !saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "الصفحة السابقة")
            }
            IconButton(onClick = { go(page + 1) }, enabled = loaded && page < chapter.pageCount - 1 && !saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "الصفحة التالية")
            }
        }
        LinearProgressIndicator(progress = { (page + 1f) / chapter.pageCount }, color = Turquoise,
            modifier = Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 800.dp
            val tools: @Composable (Modifier) -> Unit = { paneModifier ->
                StudyToolsPanel(
                    tab = studyTab, onTab = { studyTab = it },
                    note = note, onNote = { note = it }, loaded = loaded,
                    isSaved = note == savedText, saving = saving,
                    onSave = { scope.launch {
                        try { commit() } catch (e: Exception) { info = e.localizedMessage ?: "خطأ أثناء الحفظ" }
                    } },
                    query = query, onQuery = { query = it.take(120) }, hits = searchHits,
                    onJump = { go(it) },
                    indexedPages = indexedPages, pagesWithText = pagesWithText,
                    totalPages = chapter.pageCount, indexing = indexing, indexProgress = indexProgress,
                    onIndex = { startIndex() }, pageText = pageText,
                    chapter = chapter, page = page, onSourceJump = { go(it) }, chapterStore = store,
                    modifier = paneModifier
                )
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                PdfPagePanel(chapter, page, store, Modifier.weight(1.25f).fillMaxHeight())
                tools(Modifier.weight(.8f).fillMaxHeight())
            } else {
                var activeTab by rememberSaveable(chapter.sha256) { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = activeTab == 0, onClick = { activeTab = 0 }, label = { Text("صفحة PDF") })
                        FilterChip(selected = activeTab == 1, onClick = { activeTab = 1 }, label = { Text("أدوات الدراسة") })
                    }
                    if (activeTab == 0) PdfPagePanel(chapter, page, store, Modifier.fillMaxSize())
                    else tools(Modifier.fillMaxSize())
                }
            }
        }
    }
    if (showJump) {
        val target = jumpInput.toIntOrNull()
        AlertDialog(onDismissRequest = { showJump = false },
            title = { Text("انتقل لصفحة") },
            text = { OutlinedTextField(value = jumpInput,
                onValueChange = { jumpInput = it.filter(Char::isDigit).take(7) },
                label = { Text("رقم الصفحة من 1 إلى ${chapter.pageCount}") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )) },
            confirmButton = { TextButton(onClick = { showJump = false; go(target!! - 1) },
                enabled = target != null && target in 1..chapter.pageCount) { Text("انتقال") } },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("إلغاء") } })
    }
    info?.let { message -> AlertDialog(onDismissRequest = { info = null },
        title = { Text("تعذّر تنفيذ العملية") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { info = null }) { Text("تمام") } }) }
}

@Composable
private fun PdfPagePanel(chapter: Chapter, page: Int, store: ChapterStore, modifier: Modifier = Modifier) {
    var bitmap by remember(chapter.sha256, page) { mutableStateOf<Bitmap?>(null) }
    var error by remember(chapter.sha256, page) { mutableStateOf<String?>(null) }
    var scale by remember(chapter.sha256, page) { mutableFloatStateOf(1f) }
    var dx by remember(chapter.sha256, page) { mutableFloatStateOf(0f) }
    var dy by remember(chapter.sha256, page) { mutableFloatStateOf(0f) }
    LaunchedEffect(chapter.sha256, page) {
        try { bitmap = PdfPageDecoder.render(store.fileFor(chapter), page) }
        catch (e: Exception) { error = e.localizedMessage ?: "فشل عرض الصفحة" }
    }
    GlassPanel(modifier) {
        Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                bitmap == null -> CircularProgressIndicator()
                else -> Image(bitmap!!.asImageBitmap(), "صفحة PDF ${page+1}",
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(bitmap) { detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 3.5f)
                            dx = if (scale <= 1f) 0f else dx + pan.x
                            dy = if (scale <= 1f) 0f else dy + pan.y
                        } }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy),
                    contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
private fun StudyToolsPanel(
    tab: Int, onTab: (Int) -> Unit,
    note: String, onNote: (String) -> Unit, loaded: Boolean,
    isSaved: Boolean, saving: Boolean, onSave: () -> Unit,
    query: String, onQuery: (String) -> Unit, hits: List<PageSearchHit>,
    onJump: (Int) -> Unit, indexedPages: Int, pagesWithText: Int,
    totalPages: Int, indexing: Boolean, indexProgress: Int,
    onIndex: () -> Unit, pageText: String?,
    chapter: Chapter, page: Int, onSourceJump: (Int) -> Unit, chapterStore: ChapterStore,
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = tab == 0, onClick = { onTab(0) }, label = { Text("ملاحظاتي") })
            FilterChip(selected = tab == 1, onClick = { onTab(1) }, label = { Text("بحث") })
            FilterChip(selected = tab == 2, onClick = { onTab(2) }, label = { Text("نص الصفحة") })
            FilterChip(selected = tab == 3, onClick = { onTab(3) }, label = { Text("Study Studio") })
            FilterChip(selected = tab == 4, onClick = { onTab(4) }, label = { Text("🧠 اسأل MedMate") })
        }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> {
                Text("ملاحظات مرتبطة بالصفحة الحالية", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = note, onValueChange = onNote, enabled = loaded && !saving,
                    placeholder = { Text("شو أهم نقطة بهالصفحة؟") }, modifier = Modifier.fillMaxWidth(),
                    minLines = 4, maxLines = 15)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSave, enabled = loaded && !isSaved && !saving,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(if (isSaved) Icons.Default.CheckCircle else Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saving) "جاري الحفظ" else if (isSaved) "محفوظة" else "حفظ الملاحظة")
                }
            }
            1 -> {
                Text("البحث في النص الأصلي للشبتر", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("مفهرس $indexedPages من $totalPages صفحة · نص متاح في $pagesWithText",
                    color = Muted, fontSize = 12.sp)
                if (indexedPages < totalPages) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onIndex, enabled = !indexing, modifier = Modifier.fillMaxWidth()) {
                        Text(if (indexing) "جاري الفهرسة $indexProgress/$totalPages" else
                            if (indexedPages == 0) "فهرسة نص PDF" else "استكمال الفهرسة")
                    }
                    if (indexing) LinearProgressIndicator(
                        progress = { indexProgress.toFloat() / totalPages.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = query, onValueChange = onQuery, enabled = !indexing,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("ابحث: Osteoarthritis, fracture...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (query.trim().length >= 2 && hits.isEmpty())
                    Text("لا توجد نتائج في الصفحات المفهرسة.", color = Muted)
                else if (hits.isNotEmpty()) {
                    Text("${hits.size} نتيجة (حتى 100 صفحة)", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hits, key = { it.pageIndex }) { hit ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onJump(hit.pageIndex) },
                                shape = MaterialTheme.shapes.medium,
                                color = Color.White.copy(alpha = 0.07f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("صفحة ${hit.pageIndex + 1}", color = Turquoise, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(hit.excerpt, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                Text("النص المستخرج من صفحة PDF", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("هذا النص من الملف نفسه، وليس شرح AI.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                when {
                    pageText == null -> Text("الصفحة مش مفهرسة بعد. افتح تبويب البحث واضغط فهرسة.", color = Muted)
                    pageText.isBlank() -> Text("ما في نص مدمج بهذه الصفحة، غالبًا صورة أو سكانر. OCR لاحقًا.", color = Muted)
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(pageText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            4 -> BrainPanel(chapter, page, chapterStore, onSourceJump, Modifier.weight(1f))
            else -> StudyDeskPanel(chapter, page, onSourceJump, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NotesPanel(note: String, onChange: (String) -> Unit, loaded: Boolean,
                       isSaved: Boolean, saving: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EditNote, null, tint = Turquoise)
                Text("ملاحظات الصفحة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("ملاحظاتك مرتبطة بصفحة الـPDF نفسها.", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = note, onValueChange = onChange, enabled = loaded && !saving,
                placeholder = { Text("شو أهم نقطة بهالصفحة؟") }, modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 12)
            Button(onClick = onSave, enabled = loaded && !isSaved && !saving, modifier = Modifier.fillMaxWidth()) {
                Icon(if (isSaved) Icons.Default.CheckCircle else Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "جاري الحفظ" else if (isSaved) "محفوظة" else "حفظ الملاحظة")
            }
            Text("الشرح الذكي والاختبارات رح يتركّبوا هون بالمرحلة القادمة.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large,
        color = SurfaceGlass, tonalElevation = 0.dp) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
