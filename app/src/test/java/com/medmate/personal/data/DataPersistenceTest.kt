package com.medmate.personal.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medmate.personal.study.StudyKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataPersistenceTest {
    private lateinit var context: Context
    private val sha = "a".repeat(64)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        reset()
    }

    @After fun tearDown() = reset()

    @Test fun notesAndReadingPositionPersistAndRejectInvalidPages() = runBlocking {
        seedChapter(sha, 4)
        val first = ChapterStore(context)
        first.saveNote(sha, 2, "mitral stenosis")
        first.updateLastPage(sha, 3)

        val reopened = ChapterStore(context)
        assertEquals("mitral stenosis", reopened.readNote(sha, 2))
        assertEquals(3, reopened.listChapters().single().lastPage)
        expectFailure { runBlocking { reopened.saveNote(sha, 4, "outside") } }
        expectFailure { runBlocking { reopened.updateLastPage("f".repeat(64), 0) } }
        assertEquals("mitral stenosis", reopened.readNote(sha, 2))
    }

    @Test fun reviewsAreAtomicAndReviewedAnswersAreLocked() = runBlocking {
        val chapter = seedChapter(sha, 4)
        val store = StudyStore(context)
        val id = store.add(chapter, 1, StudyKind.MCQ, "Best diagnosis?", options =
            listOf("Aortic stenosis", "Mitral stenosis", "Pericarditis", "Pneumonia"),
            correctIndex = 1, explanation = "Opening snap")
        store.review(id, rating = 2, selectedIndex = 1)
        assertEquals(1, store.counts(sha).mcqs)
        assertEquals(0, store.counts(sha).mistakes)
        expectFailure {
            runBlocking {
                store.update(id, sha, "Best diagnosis?", "", listOf("Aortic stenosis",
                    "Hypertrophic cardiomyopathy", "Pericarditis", "Pneumonia"), 1, "changed")
            }
        }
        val preserved = store.list(sha).single()
        assertEquals("Mitral stenosis", preserved.options[1])
        assertEquals(1, preserved.review.repetitions)
    }

    @Test fun fifthMcqOptionCanBeCorrectAndReviewChecksActualChoiceCount() = runBlocking {
        val chapter = seedChapter(sha, 4)
        val store = StudyStore(context)
        val id = store.add(chapter, 2, StudyKind.MCQ, "Five choices?", options =
            listOf("A", "B", "C", "D", "E"), correctIndex = 4,
            explanation = "The source explicitly indicates E")
        store.review(id, rating = 2, selectedIndex = 4)
        assertEquals(1, store.list(sha).single().review.repetitions)
        expectFailure { runBlocking { store.review(id, rating = 2, selectedIndex = 5) } }
        assertEquals(1, store.list(sha).single().review.repetitions)
    }

    @Test fun realHelperMigratesVersionOneAndTwoWithoutDroppingRows() {
        for (oldVersion in listOf(1, 2)) {
            reset()
            val db = context.openOrCreateDatabase("medmate.db", Context.MODE_PRIVATE, null)
            db.execSQL("""CREATE TABLE chapters (sha256 TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,
                stored_name TEXT NOT NULL,bytes INTEGER NOT NULL,page_count INTEGER NOT NULL,
                last_page INTEGER NOT NULL DEFAULT 0,added_at INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE page_notes (chapter_sha256 TEXT NOT NULL,page_index INTEGER NOT NULL,
                note_text TEXT NOT NULL,updated_at INTEGER NOT NULL,
                PRIMARY KEY(chapter_sha256,page_index),FOREIGN KEY(chapter_sha256) REFERENCES chapters(sha256) ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX idx_chapters_added ON chapters(added_at DESC)")
            if (oldVersion == 2) db.execSQL("""CREATE TABLE page_text (chapter_sha256 TEXT NOT NULL,
                page_index INTEGER NOT NULL,text_content TEXT NOT NULL,extracted_at INTEGER NOT NULL,
                PRIMARY KEY(chapter_sha256,page_index),FOREIGN KEY(chapter_sha256) REFERENCES chapters(sha256) ON DELETE CASCADE)""")
            db.execSQL("INSERT INTO chapters VALUES(?,?,?,?,?,?,?)",
                arrayOf<Any>(sha, "Legacy", "$sha.pdf", 9L, 4, 1, 10L))
            db.execSQL("INSERT INTO page_notes VALUES(?,?,?,?)", arrayOf<Any>(sha, 1, "keep me", 20L))
            db.version = oldVersion
            db.close()

            val upgraded = MedMateDatabase(context).writableDatabase
            assertEquals(3, upgraded.version)
            assertEquals("keep me", upgraded.rawQuery("SELECT note_text FROM page_notes", null)
                .use { assertTrue(it.moveToFirst()); it.getString(0) })
            val tables = upgraded.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
            assertTrue(tables.containsAll(setOf("page_text", "study_items", "study_reviews")))
            upgraded.close()
        }
    }

    @Test fun emptyVersionTwoBackupRoundTripsAndMalformedRestorePreservesData() = runBlocking {
        val backup = java.io.File(context.cacheDir, "empty.zip")
        val manager = BackupManager(context)
        assertEquals(0, manager.export(android.net.Uri.fromFile(backup)).chapters)
        assertEquals(0, manager.restore(android.net.Uri.fromFile(backup)).newChapters)

        seedChapter(sha, 4)
        ChapterStore(context).saveNote(sha, 1, "must survive")
        val bad = java.io.File(context.cacheDir, "bad.zip")
        java.util.zip.ZipOutputStream(bad.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write("{\"format\":\"wrong\",\"version\":2,\"chapters\":[]}".toByteArray())
            zip.closeEntry()
        }
        expectFailure { runBlocking { manager.restore(android.net.Uri.fromFile(bad)) } }
        assertEquals("must survive", ChapterStore(context).readNote(sha, 1))
    }

    private fun seedChapter(value: String, pages: Int): Chapter {
        val chapter = Chapter(value, "Test chapter", "$value.pdf", 9, pages, 0, 1)
        val row = ContentValues().apply {
            put("sha256", value); put("title", chapter.title); put("stored_name", chapter.storedName)
            put("bytes", chapter.bytes); put("page_count", pages); put("last_page", 0); put("added_at", 1)
        }
        assertNotEquals(-1L, MedMateDatabase(context).writableDatabase.insertOrThrow("chapters", null, row))
        return chapter
    }

    private fun reset() {
        runCatching { context.deleteDatabase("medmate.db") }
        java.io.File(context.filesDir, "original_documents").deleteRecursively()
        java.io.File(context.cacheDir, "imports").deleteRecursively()
        java.io.File(context.cacheDir, "empty.zip").delete()
        java.io.File(context.cacheDir, "bad.zip").delete()
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected operation to fail") } catch (_: IllegalArgumentException) { }
    }
}
