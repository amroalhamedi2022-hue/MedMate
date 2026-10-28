package com.medmate.personal.data

import android.content.Context
import com.medmate.personal.study.StudyKind
import com.medmate.personal.study.StudyValidation
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Local plain ZIP backups: content hashes, no network or permissions beyond SAF picker. */
data class BackupReport(val chapters: Int, val notes: Int, val studyItems: Int = 0, val reviews: Int = 0)
data class RestoreReport(val newChapters: Int, val newNotes: Int, val preservedNotes: Int,
    val newStudyItems: Int = 0, val newReviews: Int = 0)

class BackupManager(context: Context) {
    private val app = context.applicationContext
    private val dbHelper = MedMateDatabase(app)
    private val originals = File(app.filesDir, "original_documents").apply { mkdirs() }
    private val digestPattern = Regex("[a-f0-9]{64}")
    private val bufferSize = 64 * 1024

    /** Exports a portable archive; never updates or deletes the source data. */
    suspend fun export(destination: Uri): BackupReport = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val chapters = mutableListOf<Chapter>()
            db.rawQuery(
                "SELECT sha256,title,stored_name,bytes,page_count,last_page,added_at FROM chapters ORDER BY added_at", null
            ).use { c -> while (c.moveToNext()) chapters += Chapter(
                c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4),
                c.getInt(5), c.getLong(6)
            ) }
            BackupArchiveLimits.requireSafeDocumentBytes(chapters.map { it.bytes })
            val manifest = JSONObject().put("format", "MedMatePersonalBackup").put("version", 2)
                .put("createdAt", System.currentTimeMillis())
            val chaptersJson = JSONArray()
            chapters.forEach { ch ->
                require(digestPattern.matches(ch.sha256) && ch.storedName == "${ch.sha256}.pdf")
                chaptersJson.put(JSONObject()
                    .put("sha256", ch.sha256).put("title", ch.title).put("bytes", ch.bytes)
                    .put("pageCount", ch.pageCount).put("lastPage", ch.lastPage).put("addedAt", ch.addedAt))
            }
            manifest.put("chapters", chaptersJson)
            val out = app.contentResolver.openOutputStream(destination, "w") ?: error("تعذّر إنشاء ملف النسخة الاحتياطية")
            var notes = 0
            var studyItems = 0
            var reviews = 0
            out.use { raw -> ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                chapters.forEach { chapter ->
                    currentCoroutineContext().ensureActive()
                    val source = File(originals, chapter.storedName)
                    require(source.isFile && source.length() == chapter.bytes) { "ملف مفقود أو تالف: ${chapter.title}" }
                    zip.putNextEntry(ZipEntry("documents/${chapter.sha256}.pdf"))
                    FileInputStream(source).use { input ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(bufferSize)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                            zip.write(buffer, 0, count)
                        }
                        require(digest.hex() == chapter.sha256) { "فشل فحص سلامة الملف: ${chapter.title}" }
                    }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("notes.jsonl"))
                db.rawQuery(
                    "SELECT chapter_sha256,page_index,note_text,updated_at FROM page_notes ORDER BY chapter_sha256,page_index", null
                ).use { cursor -> while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val record = JSONObject()
                        .put("sha256", cursor.getString(0))
                        .put("page", cursor.getInt(1))
                        .put("note", cursor.getString(2))
                        .put("updatedAt", cursor.getLong(3))
                    zip.write((record.toString() + "\n").toByteArray(Charsets.UTF_8))
                    notes++
                } }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("study_items.jsonl"))
                db.rawQuery(
                    """SELECT id,chapter_sha256,page_index,kind,prompt,answer,options_json,correct_index,
                        explanation,created_at,due_at,interval_days,ease,repetitions,lapses
                        FROM study_items ORDER BY created_at""", null
                ).use { c -> while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val j = JSONObject().put("id", c.getString(0)).put("sha256", c.getString(1))
                        .put("page", c.getInt(2)).put("kind", c.getString(3))
                        .put("prompt", c.getString(4)).put("answer", c.getString(5))
                        .put("options", JSONArray(c.getString(6))).put("correctIndex", c.getInt(7))
                        .put("explanation", c.getString(8)).put("createdAt", c.getLong(9))
                        .put("dueAt", c.getLong(10)).put("intervalDays", c.getInt(11))
                        .put("ease", c.getDouble(12)).put("repetitions", c.getInt(13))
                        .put("lapses", c.getInt(14))
                    zip.write((j.toString() + "\n").toByteArray(Charsets.UTF_8))
                    studyItems++
                } }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("study_reviews.jsonl"))
                db.rawQuery(
                    "SELECT id,item_id,rating,selected_index,was_correct,reviewed_at FROM study_reviews ORDER BY reviewed_at", null
                ).use { c -> while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val j = JSONObject().put("id", c.getString(0)).put("itemId", c.getString(1))
                        .put("rating", c.getInt(2)).put("selectedIndex", c.getInt(3))
                        .put("wasCorrect", c.getInt(4)).put("reviewedAt", c.getLong(5))
                    zip.write((j.toString() + "\n").toByteArray(Charsets.UTF_8))
                    reviews++
                } }
                zip.closeEntry()
            } }
            db.setTransactionSuccessful()
            BackupReport(chapters.size, notes, studyItems, reviews)
            } finally {
                db.endTransaction()
            }
        }

    /** Safe merge: validate entire archive before any inserts; never replace existing notes. */
    suspend fun restore(source: Uri): RestoreReport = withContext(Dispatchers.IO) {
        val staging = File(app.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        // Roll back only files created by this restore if validation or DB commit fails.
        val createdDocuments = mutableListOf<File>()
        var completed = false
        try {
            val input = app.contentResolver.openInputStream(source) ?: error("تعذّر فتح النسخة الاحتياطية")
            val chapters = linkedMapOf<String, Chapter>()
            val seen = mutableSetOf<String>()
            var notesFile: File? = null
            var studyFile: File? = null
            var reviewsFile: File? = null
            var backupVersion = 0
            var entries = 0
            input.use { raw -> ZipInputStream(raw).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 10_007) { "عدد الملفات في النسخة غير منطقي" }
                    require(!entry.isDirectory) { "مجلد غير متوقع داخل النسخة" }
                    when {
                        entries == 1 && entry.name == "manifest.json" -> {
                            val bytes = readLimited(zip, 8 * 1024 * 1024)
                            val manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                            backupVersion = manifest.getInt("version")
                            require(manifest.getString("format") == "MedMatePersonalBackup" &&
                                backupVersion in 1..2) { "صيغة النسخة غير مدعومة" }
                            val rows = manifest.getJSONArray("chapters")
                            require(rows.length() <= 10_000)
                            for (i in 0 until rows.length()) {
                                val row = rows.getJSONObject(i)
                                val sha = row.getString("sha256")
                                require(digestPattern.matches(sha)) { "بصمة ملف غير سليمة" }
                                val title = row.getString("title").take(180)
                                val bytesLength = row.getLong("bytes")
                                val pageCount = row.getInt("pageCount")
                                val lastPage = row.getInt("lastPage")
                                require(bytesLength in 5L..BackupArchiveLimits.MAX_PDF_BYTES && pageCount > 0 && lastPage in 0 until pageCount)
                                require(!chapters.containsKey(sha)) { "ملف مكرر في الفهرس" }
                                chapters[sha] = Chapter(sha, title, "$sha.pdf", bytesLength,
                                    pageCount, lastPage, row.getLong("addedAt"))
                            }
                            // Validate the aggregate declared payload before any PDF bytes are staged.
                            BackupArchiveLimits.requireSafeDocumentBytes(chapters.values.map { it.bytes })
                        }
                        entry.name.startsWith("documents/") && notesFile == null -> {
                            val sha = entry.name.removePrefix("documents/").removeSuffix(".pdf")
                            require(entry.name == "documents/$sha.pdf" && chapters.containsKey(sha) && seen.add(sha)) {
                                "ملف غير معروف أو مكرر داخل النسخة"
                            }
                            val chapter = requireNotNull(chapters[sha])
                            val temp = File(staging, "$sha.pdf")
                            val digest = MessageDigest.getInstance("SHA-256")
                            var count = 0L
                            FileOutputStream(temp).use { output ->
                                val buffer = ByteArray(bufferSize)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    count += n
                                    require(count <= chapter.bytes) { "حجم PDF تجاوز المتوقع" }
                                    output.write(buffer, 0, n)
                                    digest.update(buffer, 0, n)
                                }
                                output.fd.sync()
                            }
                            require(count == chapter.bytes && digest.hex() == sha) { "بصمة PDF لا تطابق النسخة" }
                            val fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = try { PdfRenderer(fd) } catch (e: Exception) {
                                fd.close()
                                throw e
                            }
                            val actualPages = renderer.use { it.pageCount }
                            require(actualPages == chapter.pageCount) { "عدد صفحات PDF لا يطابق النسخة" }
                        }
                        entry.name == "notes.jsonl" && notesFile == null -> {
                            val temp = File(staging, "notes.jsonl")
                            FileOutputStream(temp).use { output ->
                                val buffer = ByteArray(bufferSize)
                                var total = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    require(total <= 256L * 1024 * 1024) { "حجم الملاحظات غير منطقي" }
                                    output.write(buffer, 0, n)
                                }
                            }
                            notesFile = temp
                        }
                        entry.name == "study_items.jsonl" && backupVersion == 2 &&
                            notesFile != null && studyFile == null -> {
                            val temp = File(staging, "study_items.jsonl")
                            copyLimited(zip, temp, 256L * 1024 * 1024)
                            studyFile = temp
                        }
                        entry.name == "study_reviews.jsonl" && backupVersion == 2 &&
                            studyFile != null && reviewsFile == null -> {
                            val temp = File(staging, "study_reviews.jsonl")
                            copyLimited(zip, temp, 256L * 1024 * 1024)
                            reviewsFile = temp
                        }
                        else -> error("ملف إضافي أو ترتيب غير مدعوم في النسخة: ${entry.name}")
                    }
                    zip.closeEntry()
                }
            } }
            require(entries >= 2 && seen == chapters.keys && notesFile != null &&
                (backupVersion == 1 || (studyFile != null && reviewsFile != null))) { "النسخة ناقصة" }
            // Validate all notes before touching the live database or documents.
            var noteCount = 0
            val noteKeys = mutableSetOf<Pair<String, Int>>()
            parseNotes(requireNotNull(notesFile), chapters) { sha, page, _, _ ->
                require(noteKeys.add(sha to page)) { "ملاحظة مكررة داخل النسخة" }
                noteCount++
                require(noteCount <= 500_000) { "عدد الملاحظات غير منطقي" }
            }
            // Validate the entire new-format content before modifying live files or DB.
            val itemIds = mutableSetOf<String>()
            val itemKinds = mutableMapOf<String, Triple<StudyKind, Int, Int>>()
            var studyCount = 0
            var reviewCount = 0
            if (backupVersion == 2) {
                parseStudyItems(requireNotNull(studyFile), chapters) { j, kind, correctIndex ->
                    val id = j.getString("id")
                    require(itemIds.add(id)) { "بطاقة مكررة في النسخة" }
                    itemKinds[id] = Triple(kind, correctIndex, j.getJSONArray("options").length())
                    studyCount++
                    require(studyCount <= 100_000) { "عدد بطاقات غير منطقي" }
                }
                val reviewIds = mutableSetOf<String>()
                parseStudyReviews(requireNotNull(reviewsFile), itemKinds) { j ->
                    require(reviewIds.add(j.getString("id"))) { "مراجعة مكررة في النسخة" }
                    reviewCount++
                    require(reviewCount <= 500_000) { "عدد مراجعات غير منطقي" }
                }
            }
            // Files go into immutable content-addressed names, with no overwrite of existing copies.
            for (ch in chapters.values) {
                val target = File(originals, ch.storedName)
                if (!target.exists()) {
                    createdDocuments += target
                    File(staging, ch.storedName).copyTo(target, overwrite = false)
                } else {
                    require(target.length() == ch.bytes && sha256(target) == ch.sha256) {
                        "الملف الموجود غير سليم: ${ch.title}"
                    }
                }
            }
            var addedChapters = 0
            var addedNotes = 0
            var addedStudy = 0
            var addedReviews = 0
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (ch in chapters.values) {
                    val values = android.content.ContentValues().apply {
                        put("sha256", ch.sha256); put("title", ch.title); put("stored_name", ch.storedName)
                        put("bytes", ch.bytes); put("page_count", ch.pageCount)
                        put("last_page", ch.lastPage); put("added_at", ch.addedAt)
                    }
                    if (db.insertWithOnConflict("chapters", null, values,
                            SQLiteDatabase.CONFLICT_IGNORE) != -1L) addedChapters++
                }
                parseNotes(requireNotNull(notesFile), chapters) { sha, page, note, updated ->
                    val values = android.content.ContentValues().apply {
                        put("chapter_sha256", sha); put("page_index", page)
                        put("note_text", note); put("updated_at", updated)
                    }
                    if (db.insertWithOnConflict("page_notes", null, values,
                            SQLiteDatabase.CONFLICT_IGNORE) != -1L) addedNotes++
                }
                if (backupVersion == 2) {
                    parseStudyItems(requireNotNull(studyFile), chapters) { j, _, _ ->
                        verifyExistingStudyItem(db, j)
                        val values = android.content.ContentValues().apply {
                            put("id", j.getString("id")); put("chapter_sha256", j.getString("sha256"))
                            put("page_index", j.getInt("page")); put("kind", j.getString("kind"))
                            put("prompt", j.getString("prompt")); put("answer", j.getString("answer"))
                            put("options_json", j.getJSONArray("options").toString())
                            put("correct_index", j.getInt("correctIndex")); put("explanation", j.getString("explanation"))
                            put("created_at", j.getLong("createdAt")); put("due_at", j.getLong("dueAt"))
                            put("interval_days", j.getInt("intervalDays")); put("ease", j.getDouble("ease"))
                            put("repetitions", j.getInt("repetitions")); put("lapses", j.getInt("lapses"))
                        }
                        if (db.insertWithOnConflict("study_items", null, values,
                                SQLiteDatabase.CONFLICT_IGNORE) != -1L) addedStudy++
                    }
                    parseStudyReviews(requireNotNull(reviewsFile), itemKinds) { j ->
                        verifyExistingReview(db, j)
                        val values = android.content.ContentValues().apply {
                            put("id", j.getString("id")); put("item_id", j.getString("itemId"))
                            put("rating", j.getInt("rating")); put("selected_index", j.getInt("selectedIndex"))
                            put("was_correct", j.getInt("wasCorrect")); put("reviewed_at", j.getLong("reviewedAt"))
                        }
                        if (db.insertWithOnConflict("study_reviews", null, values,
                                SQLiteDatabase.CONFLICT_IGNORE) != -1L) addedReviews++
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            completed = true
            RestoreReport(addedChapters, addedNotes, noteCount - addedNotes, addedStudy, addedReviews)
        } finally {
            if (!completed) createdDocuments.forEach { it.delete() }
            staging.deleteRecursively()
        }
    }

    /** Existing IDs may have newer local review progress, but their immutable content must match. */
    private fun verifyExistingStudyItem(db: SQLiteDatabase, incoming: JSONObject) {
        db.rawQuery(
            """SELECT chapter_sha256,page_index,kind,prompt,answer,options_json,
               correct_index,explanation,created_at FROM study_items WHERE id=?""",
            arrayOf(incoming.getString("id"))
        ).use { c ->
            if (!c.moveToFirst()) return
            val same = c.getString(0) == incoming.getString("sha256") &&
                c.getInt(1) == incoming.getInt("page") &&
                c.getString(2) == incoming.getString("kind") &&
                c.getString(3) == incoming.getString("prompt") &&
                c.getString(4) == incoming.getString("answer") &&
                JSONArray(c.getString(5)).toString() == incoming.getJSONArray("options").toString() &&
                c.getInt(6) == incoming.getInt("correctIndex") &&
                c.getString(7) == incoming.getString("explanation") &&
                c.getLong(8) == incoming.getLong("createdAt")
            require(same) { "تعارض في هوية بطاقة محفوظة؛ لم نغيّر بياناتك" }
        }
    }

    /** A repeated review UUID is safe to skip only when the entire event matches. */
    private fun verifyExistingReview(db: SQLiteDatabase, incoming: JSONObject) {
        db.rawQuery(
            """SELECT item_id,rating,selected_index,was_correct,reviewed_at
               FROM study_reviews WHERE id=?""",
            arrayOf(incoming.getString("id"))
        ).use { c ->
            if (!c.moveToFirst()) return
            val same = c.getString(0) == incoming.getString("itemId") &&
                c.getInt(1) == incoming.getInt("rating") &&
                c.getInt(2) == incoming.getInt("selectedIndex") &&
                c.getInt(3) == incoming.getInt("wasCorrect") &&
                c.getLong(4) == incoming.getLong("reviewedAt")
            require(same) { "تعارض في سجل مراجعة محفوظ؛ لم نغيّر بياناتك" }
        }
    }

    private fun parseNotes(file: File, chapters: Map<String, Chapter>, each: (String, Int, String, Long) -> Unit) {
        BoundedLineReader.forEach(file, 2_000_000) { line ->
            val j = JSONObject(line)
            val sha = j.getString("sha256")
            val chapter = chapters[sha] ?: error("ملاحظة مرتبطة بملف غير موجود")
            val page = j.getInt("page")
            require(page in 0 until chapter.pageCount) { "رقم صفحة غير صالح في الملاحظات" }
            each(sha, page, j.getString("note"), j.getLong("updatedAt"))
        }
    }
    private fun copyLimited(input: InputStream, target: File, limit: Long) {
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(bufferSize)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= limit) { "ملف النسخة أكبر من المسموح" }
                output.write(buffer, 0, n)
            }
        }
    }

    private fun parseStudyItems(file: File, chapters: Map<String, Chapter>,
                                each: (JSONObject, StudyKind, Int) -> Unit) {
        BoundedLineReader.forEach(file, 128_000) { line ->
            val j = JSONObject(line)
            require(uuidPattern.matches(j.getString("id")))
            val ch = chapters[j.getString("sha256")] ?: error("بطاقة مرتبطة بشبتر غير موجود")
            require(j.getInt("page") in 0 until ch.pageCount)
            val kind = StudyKind.valueOf(j.getString("kind"))
            val options = j.getJSONArray("options")
            val opts = (0 until options.length()).map { options.getString(it) }
            val ci = j.getInt("correctIndex")
            StudyValidation.check(kind, j.getString("prompt"), j.getString("answer"), opts,
                ci, j.getString("explanation"))
            require(j.getLong("createdAt") >= 0 && j.getLong("dueAt") >= 0)
            require(j.getInt("intervalDays") in 0..3650 &&
                j.getDouble("ease") in 1.3..3.2 &&
                j.getInt("repetitions") in 0..100_000 && j.getInt("lapses") >= 0)
            each(j, kind, ci)
        }
    }
    private fun parseStudyReviews(file: File, items: Map<String, Triple<StudyKind, Int, Int>>,
                                  each: (JSONObject) -> Unit) {
        BoundedLineReader.forEach(file, 5_000) { line ->
            val j = JSONObject(line)
            require(uuidPattern.matches(j.getString("id")))
            val (kind, answerIndex, optionCount) = items[j.getString("itemId")] ?: error("مراجعة بلا بطاقة")
            require(kind != StudyKind.HIGH_YIELD)
            val rating = j.getInt("rating")
            val chosen = j.getInt("selectedIndex")
            val correct = j.getInt("wasCorrect")
            require(rating in 0..3 && correct in 0..1 && j.getLong("reviewedAt") >= 0)
            require((rating != 0) == (correct == 1)) { "تقييم المراجعة لا يطابق نتيجتها" }
            if (kind == StudyKind.MCQ) require(chosen in 0 until optionCount && correct == if (chosen == answerIndex) 1 else 0)
            else require(chosen == -1 && correct == if (rating == 0) 0 else 1)
            each(j)
        }
    }
    private val uuidPattern = Regex("[0-9a-fA-F-]{36}")

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= max) { "ملف التعريف أكبر من المسموح" }
            result.write(buffer, 0, n)
        }
        return result.toByteArray()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(bufferSize)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.hex()
    }

    private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }
}
