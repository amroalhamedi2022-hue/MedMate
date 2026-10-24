package com.medmate.personal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.medmate.personal.study.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

/** Offline manual authoring, review history, and mistake journal; all linked to PDF page. */
class StudyStore(context: Context) {
    private val helper = MedMateDatabase(context.applicationContext)

    suspend fun add(chapter: Chapter, page: Int, kind: StudyKind, prompt: String,
                    answer: String = "", options: List<String> = emptyList(), correctIndex: Int = -1,
                    explanation: String = ""): String = withContext(Dispatchers.IO) {
        require(page in 0 until chapter.pageCount) { "رقم الصفحة غير صالح" }
        StudyValidation.check(kind, prompt, answer, options, correctIndex, explanation)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val data = ContentValues().apply {
            put("id", id); put("chapter_sha256", chapter.sha256); put("page_index", page)
            put("kind", kind.name); put("prompt", prompt.trim()); put("answer", answer.trim())
            put("options_json", JSONArray(options.map { it.trim() }).toString())
            put("correct_index", if (kind == StudyKind.MCQ) correctIndex else -1)
            put("explanation", explanation.trim()); put("created_at", now)
            put("due_at", now); put("interval_days", 0); put("ease", 2.3)
            put("repetitions", 0); put("lapses", 0)
        }
        require(helper.writableDatabase.insertOrThrow("study_items", null, data) != -1L)
        id
    }

    /**
     * Edit in place without silently invalidating previous review answers.
     * New/unreviewed items are fully editable. Reviewed items keep their graded answers,
     * but allow corrections to question wording and explanation.
     * The source page, kind, item ID and review schedule are always preserved.
     */
    suspend fun update(
        itemId: String,
        chapterSha: String,
        prompt: String,
        answer: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
    ): Unit = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val old = db.query("study_items", columns, "id=? AND chapter_sha256=?",
                arrayOf(itemId, chapterSha), null, null, null).use { c ->
                require(c.moveToFirst()) { "العنصر غير موجود في هذا الشبتر" }
                readItem(c)
            }
            StudyValidation.check(old.kind, prompt, answer, options, correctIndex, explanation)
            val reviewCount = db.rawQuery("SELECT COUNT(*) FROM study_reviews WHERE item_id=?",
                arrayOf(itemId)).use { c -> c.moveToFirst(); c.getInt(0) }
            val hasReviews = reviewCount > 0 || old.review.repetitions > 0 || old.review.lapses > 0
            require(StudyRevisionPolicy.allowRevision(old, answer, options, correctIndex, hasReviews)) {
                "لا يمكن تغيير إجابة عنصر تمت مراجعته لأن ذلك يغيّر معنى الإجابات القديمة. " +
                    "يمكنك تعديل السؤال والتفسير، أو إنشاء بطاقة جديدة بالإجابة المصحّحة."
            }
            val values = ContentValues().apply {
                put("prompt", prompt.trim())
                put("answer", answer.trim())
                put("options_json", JSONArray(options.map { it.trim() }).toString())
                put("correct_index", if (old.kind == StudyKind.MCQ) correctIndex else -1)
                put("explanation", explanation.trim())
            }
            check(db.update("study_items", values, "id=? AND chapter_sha256=?",
                arrayOf(itemId, chapterSha)) == 1) { "تعذّر تحديث العنصر" }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        Unit
    }

    suspend fun list(chapterSha: String, kind: StudyKind? = null): List<StudyItem> = withContext(Dispatchers.IO) {
        val where = if (kind == null) "chapter_sha256=?" else "chapter_sha256=? AND kind=?"
        val args = if (kind == null) arrayOf(chapterSha) else arrayOf(chapterSha, kind.name)
        helper.readableDatabase.query("study_items", columns, where, args, null, null,
            "page_index ASC, created_at ASC", "10000").use { cursor ->
            buildList { while (cursor.moveToNext()) add(readItem(cursor)) }
        }
    }

    suspend fun counts(chapterSha: String, now: Long = System.currentTimeMillis()): StudyCounts = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val kinds = mutableMapOf<String, Int>()
        db.rawQuery("SELECT kind,COUNT(*) FROM study_items WHERE chapter_sha256=? GROUP BY kind", arrayOf(chapterSha)).use { c ->
            while (c.moveToNext()) kinds[c.getString(0)] = c.getInt(1)
        }
        val due = db.rawQuery("SELECT COUNT(*) FROM study_items WHERE chapter_sha256=? AND kind!='HIGH_YIELD' AND due_at<=?",
            arrayOf(chapterSha, now.toString())).use { c -> c.moveToFirst(); c.getInt(0) }
        val mistakes = db.rawQuery("""SELECT COUNT(*) FROM study_reviews r JOIN study_items s ON s.id=r.item_id
            WHERE s.chapter_sha256=? AND r.selected_index>=0 AND r.was_correct=0""", arrayOf(chapterSha))
            .use { c -> c.moveToFirst(); c.getInt(0) }
        StudyCounts(kinds[StudyKind.HIGH_YIELD.name] ?: 0, kinds[StudyKind.FLASHCARD.name] ?: 0,
            kinds[StudyKind.MCQ.name] ?: 0, due, mistakes)
    }

    suspend fun mistakes(chapterSha: String): List<WrongAnswer> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("""SELECT ${columns.joinToString(",") { "s.$it" }},
            r.selected_index,r.reviewed_at FROM study_reviews r JOIN study_items s ON s.id=r.item_id
            WHERE s.chapter_sha256=? AND r.was_correct=0 AND r.selected_index>=0
            ORDER BY r.reviewed_at DESC LIMIT 100""", arrayOf(chapterSha)).use { c ->
            buildList {
                while (c.moveToNext()) add(WrongAnswer(readItem(c), c.getInt(columns.size), c.getLong(columns.size + 1)))
            }
        }
    }

    /** Review and history are written in one transaction to prevent duplicate taps creating inconsistent state. */
    suspend fun review(itemId: String, rating: Int, selectedIndex: Int = -1): ReviewState = withContext(Dispatchers.IO) {
        require(rating in 0..3)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val item = db.query("study_items", columns, "id=?", arrayOf(itemId), null, null, null)
                .use { c -> require(c.moveToFirst()) { "البطاقة غير موجودة" }; readItem(c) }
            require(item.kind != StudyKind.HIGH_YIELD) { "ملاحظات High-Yield ليست بطاقات مراجعة" }
            if (item.kind == StudyKind.MCQ) {
                require(selectedIndex in item.options.indices)
                // A wrong answer must never be promoted to a long review interval.
                require((selectedIndex == item.correctIndex) == (rating != 0)) {
                    "تقييم السؤال لا يطابق صحة الإجابة"
                }
            } else require(selectedIndex == -1)
            val now = System.currentTimeMillis()
            val result = ReviewPlanner.next(item.review, rating, now)
            val values = ContentValues().apply {
                put("due_at", result.dueAt); put("interval_days", result.intervalDays)
                put("ease", result.ease); put("repetitions", result.repetitions); put("lapses", result.lapses)
            }
            require(db.update("study_items", values, "id=?", arrayOf(itemId)) == 1)
            val wasCorrect = if (item.kind == StudyKind.MCQ) selectedIndex == item.correctIndex else rating != 0
            db.insertOrThrow("study_reviews", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString()); put("item_id", itemId)
                put("rating", rating); put("selected_index", selectedIndex)
                put("was_correct", if (wasCorrect) 1 else 0); put("reviewed_at", now)
            })
            db.setTransactionSuccessful()
            result
        } finally { db.endTransaction() }
    }

    suspend fun delete(itemId: String): Unit = withContext(Dispatchers.IO) {
        // FK cascading removes the review history for the deleted item as well.
        helper.writableDatabase.delete("study_items", "id=?", arrayOf(itemId))
        Unit
    }

    companion object {
        internal val columns = arrayOf("id", "chapter_sha256", "page_index", "kind", "prompt",
            "answer", "options_json", "correct_index", "explanation", "created_at", "due_at",
            "interval_days", "ease", "repetitions", "lapses")

        internal fun readItem(c: Cursor): StudyItem {
            val options = JSONArray(c.getString(6))
            return StudyItem(c.getString(0), c.getString(1), c.getInt(2),
                StudyKind.valueOf(c.getString(3)), c.getString(4), c.getString(5),
                (0 until options.length()).map { options.getString(it) }, c.getInt(7), c.getString(8),
                c.getLong(9), ReviewState(c.getLong(10), c.getInt(11), c.getDouble(12), c.getInt(13), c.getInt(14)))
        }
    }
}
