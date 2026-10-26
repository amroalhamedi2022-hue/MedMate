package com.medmate.personal.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.graphics.pdf.PdfRenderer
import com.medmate.personal.domain.SearchSnippet
import com.medmate.personal.brain.PageSource
import com.medmate.personal.brain.PdfCoverageAudit
import com.medmate.personal.reader.PdfTextExtractor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Imported documents are app-private immutable originals, named by their content digest. */
data class Chapter(
    val sha256: String,
    val title: String,
    val storedName: String,
    val bytes: Long,
    val pageCount: Int,
    val lastPage: Int,
    val addedAt: Long,
)

data class ImportResult(val chapter: Chapter, val alreadyPresent: Boolean)

data class PageSearchHit(val pageIndex: Int, val excerpt: String)

data class IndexSummary(val indexed: Int, val withText: Int, val total: Int)

class ChapterStore(context: Context) {
    private val app = context.applicationContext
    private val database = MedMateDatabase(app)
    private val documentDir = File(app.filesDir, "original_documents").apply { mkdirs() }
    private val tempDir = File(app.cacheDir, "imports").apply { mkdirs() }

    fun fileFor(chapter: Chapter): File = File(documentDir, chapter.storedName)

    suspend fun listChapters(): List<Chapter> = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        db.rawQuery("SELECT sha256,title,stored_name,bytes,page_count,last_page,added_at FROM chapters ORDER BY added_at DESC", null)
            .use { cursor -> buildList {
                while (cursor.moveToNext()) add(Chapter(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getLong(3), cursor.getInt(4), cursor.getInt(5), cursor.getLong(6)
                ))
            } }
    }

    /** Streaming I/O, no input size cap. Only original PDF data goes into app storage. */
    suspend fun importPdf(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val displayName = displayName(app.contentResolver, uri)
        val temp = File(tempDir, "${UUID.randomUUID()}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val source = app.contentResolver.openInputStream(uri)
                ?: error("تعذّر فتح الملف المحدد")
            var byteCount = 0L
            source.use { input -> FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read == 0) continue
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    byteCount += read
                }
                output.fd.sync()
            } }
            require(byteCount >= 5) { "الملف فارغ أو غير صالح" }
            val header = ByteArray(minOf(1024L, byteCount).toInt())
            java.io.DataInputStream(FileInputStream(temp)).use { input -> input.readFully(header) }
            require(header.toString(Charsets.ISO_8859_1).contains("%PDF-")) {
                "هذا الملف ليس PDF صالحًا"
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            find(sha)?.let { existing ->
                val original = fileFor(existing)
                require(original.isFile && original.length() == existing.bytes && fileDigest(original) == sha) {
                    "نسخة الشبتر المحفوظة ناقصة أو تالفة؛ استرجع نسخة احتياطية قبل المتابعة"
                }
                return@withContext ImportResult(existing, true)
            }

            val fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            val pageCount = try { PdfRenderer(fd).use { it.pageCount } }
                catch (e: Exception) { fd.close(); throw e }
            PdfCoverageAudit.requireSafePageCount(pageCount)
            val target = File(documentDir, "$sha.pdf")
            if (!target.exists()) {
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = false)
                }
            } else {
                // Previous interrupted import may have left an orphan: never trust it by name alone.
                require(target.length() == byteCount && fileDigest(target) == sha) {
                    "يوجد ملف محلي تالف باسم الشبتر؛ لن نستبدله تلقائيًا"
                }
            }
            val title = displayName.removeSuffix(".pdf").removeSuffix(".PDF").take(180).ifBlank { "شبتر جديد" }
            val addedAt = System.currentTimeMillis()
            val values = android.content.ContentValues().apply {
                put("sha256", sha); put("title", title); put("stored_name", target.name)
                put("bytes", byteCount); put("page_count", pageCount)
                put("last_page", 0); put("added_at", addedAt)
            }
            database.writableDatabase.insertWithOnConflict(
                "chapters", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
            )
            ImportResult(requireNotNull(find(sha)), false)
        } finally {
            temp.delete()
        }
    }

    /** Search results point to the actual 0-based PDF page, not guessed printed labels. */
    suspend fun searchPages(sha256: String, query: String): List<PageSearchHit> = withContext(Dispatchers.IO) {
        val needle = query.trim().take(120)
        if (needle.length < 2) return@withContext emptyList()
        database.readableDatabase.rawQuery(
            """SELECT page_index, text_content FROM page_text
               WHERE chapter_sha256=? AND instr(lower(text_content), lower(?))>0
               ORDER BY page_index LIMIT 100""".trimIndent(),
            arrayOf(sha256, needle)
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) add(PageSearchHit(
                cursor.getInt(0), SearchSnippet.make(cursor.getString(1), needle)
            ))
        } }
    }

    /** Read only the current chapter's already-indexed pages. No cross-document mixing. */
    suspend fun indexedPageSources(sha256: String): List<PageSource> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT page_index, text_content FROM page_text WHERE chapter_sha256=? ORDER BY page_index",
            arrayOf(sha256)
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                add(PageSource(sha256, cursor.getInt(0), cursor.getString(1)))
            }
        } }
    }

    suspend fun readPageText(sha256: String, page: Int): String? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT text_content FROM page_text WHERE chapter_sha256=? AND page_index=?",
            arrayOf(sha256, page.toString())
        ).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    suspend fun indexingStatus(sha256: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT COUNT(*), SUM(CASE WHEN length(trim(text_content))>0 THEN 1 ELSE 0 END) FROM page_text WHERE chapter_sha256=?",
            arrayOf(sha256)
        ).use { it.moveToFirst(); it.getInt(0) to it.getInt(1) }
    }

    /** Resume-safe, explicit indexing: one PDFBox document, one saved row per PDF page. */
    suspend fun indexText(chapter: Chapter, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }): IndexSummary =
        withContext(Dispatchers.IO) {
            // Reject legacy or tampered metadata before querying/indexing a pathological document.
            PdfCoverageAudit.requireSafePageCount(chapter.pageCount)
            val db = database.writableDatabase
            val already = mutableSetOf<Int>()
            db.rawQuery("SELECT page_index FROM page_text WHERE chapter_sha256=?", arrayOf(chapter.sha256)).use {
                while (it.moveToNext()) already.add(it.getInt(0))
            }
            var processed = already.size
            var withText = 0
            PdfTextExtractor(app, fileFor(chapter)).use { extractor ->
                require(extractor.pageCount == chapter.pageCount) { "عدد صفحات الملف تغيّر" }
                for (page in 0 until chapter.pageCount) {
                    currentCoroutineContext().ensureActive()
                    if (page !in already) {
                        val value = extractor.page(page)
                        val row = android.content.ContentValues().apply {
                            put("chapter_sha256", chapter.sha256)
                            put("page_index", page)
                            put("text_content", value)
                            put("extracted_at", System.currentTimeMillis())
                        }
                        db.insertWithOnConflict("page_text", null, row, SQLiteDatabase.CONFLICT_REPLACE)
                        if (value.isNotBlank()) withText++
                        processed++
                    }
                    if (page % 4 == 0 || page == chapter.pageCount - 1) onProgress(processed, chapter.pageCount)
                }
            }
            val result = indexingStatus(chapter.sha256)
            IndexSummary(result.first, result.second, chapter.pageCount)
        }

    suspend fun readNote(sha256: String, page: Int): String = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT note_text FROM page_notes WHERE chapter_sha256=? AND page_index=?",
            arrayOf(sha256, page.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "" }
    }

    suspend fun saveNote(sha256: String, page: Int, text: String) = withContext(Dispatchers.IO) {
        requirePage(sha256, page)
        val values = android.content.ContentValues().apply {
            put("chapter_sha256", sha256); put("page_index", page)
            put("note_text", text); put("updated_at", System.currentTimeMillis())
        }
        check(database.writableDatabase.insertWithOnConflict(
            "page_notes", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        ) != -1L) { "تعذّر حفظ الملاحظة" }
        Unit
    }

    suspend fun updateLastPage(sha256: String, page: Int) = withContext(Dispatchers.IO) {
        requirePage(sha256, page)
        val values = android.content.ContentValues().apply { put("last_page", page) }
        check(database.writableDatabase.update("chapters", values, "sha256=?", arrayOf(sha256)) == 1) {
            "تعذّر حفظ موضع القراءة"
        }
        Unit
    }

    private fun requirePage(sha256: String, page: Int) {
        val chapter = find(sha256) ?: throw IllegalArgumentException("الشبتر غير موجود")
        require(page in 0 until chapter.pageCount) { "رقم الصفحة غير صالح" }
    }

    private fun find(sha: String): Chapter? = database.readableDatabase.rawQuery(
        "SELECT sha256,title,stored_name,bytes,page_count,last_page,added_at FROM chapters WHERE sha256=?",
        arrayOf(sha)
    ).use { cursor -> if (!cursor.moveToFirst()) null else Chapter(
        cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3),
        cursor.getInt(4), cursor.getInt(5), cursor.getLong(6)
    ) }

    private fun fileDigest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "شبتر جديد.pdf"
    }
}
