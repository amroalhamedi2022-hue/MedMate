package com.medmate.personal.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.medmate.personal.study.StudyKind
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Implements(value = PdfRenderer::class, minSdk = 21, isInAndroidSdk = true)
class FourPagePdfRendererShadow {
    @Implementation protected fun __constructor__(input: ParcelFileDescriptor) = Unit
    @Implementation fun getPageCount(): Int = 4
    @Implementation fun close() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [FourPagePdfRendererShadow::class])
class BackupRoundTripTest {
    private lateinit var context: Context
    private lateinit var pdf: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        reset()
        pdf = File(context.cacheDir, "four-pages.pdf")
        requireNotNull(javaClass.getResourceAsStream("/four-pages.pdf")).use { input ->
            pdf.outputStream().use { input.copyTo(it) }
        }
    }

    @After fun tearDown() = reset()

    @Test fun pdfImportTextIndexingAndSearchUseExactPages() = runBlocking {
        val store = ChapterStore(context)
        val imported = store.importPdf(Uri.fromFile(pdf))
        assertFalse(imported.alreadyPresent)
        assertEquals(4, imported.chapter.pageCount)
        assertTrue(store.importPdf(Uri.fromFile(pdf)).alreadyPresent)

        val summary = store.indexText(imported.chapter)
        assertEquals(4, summary.indexed)
        assertEquals(4, summary.withText)
        assertEquals(1, store.searchPages(imported.chapter.sha256, "glomerular").single().pageIndex)
        assertTrue(requireNotNull(store.readPageText(imported.chapter.sha256, 3)).contains("corticospinal"))
    }

    @Test fun backupRestoresAllDataPreservesLocalNotesAndRejectsContradictoryReview() = runBlocking {
        val chapters = ChapterStore(context)
        val chapter = chapters.importPdf(Uri.fromFile(pdf)).chapter
        chapters.saveNote(chapter.sha256, 2, "Local source note")
        val study = StudyStore(context)
        study.add(chapter, 0, StudyKind.FLASHCARD, "Valve?", "Mitral")
        val mcq = study.add(chapter, 1, StudyKind.MCQ, "Filtration site?", options =
            listOf("Tubule", "Glomerulus", "Ureter", "Pelvis"), correctIndex = 1,
            explanation = "The glomerulus filters plasma")
        study.review(mcq, rating = 0, selectedIndex = 0)
        val quote = "\""
        study.add(chapter, 3, StudyKind.MCQ, quote.repeat(4000), options = listOf(
            "A" + quote.repeat(999), "B" + quote.repeat(999),
            "C" + quote.repeat(999), "D" + quote.repeat(999)), correctIndex = 2,
            explanation = "\\".repeat(6000))

        val archive = File(context.cacheDir, "full.zip")
        val exported = BackupManager(context).export(Uri.fromFile(archive))
        assertEquals(1, exported.chapters)
        assertEquals(1, exported.notes)
        assertEquals(3, exported.studyItems)
        assertEquals(1, exported.reviews)
        assertTrue(largestStudyLine(archive) > 25_000)

        MedMateDatabase(context).writableDatabase.execSQL("DELETE FROM chapters")
        File(context.filesDir, "original_documents").deleteRecursively()
        val restored = BackupManager(context).restore(Uri.fromFile(archive))
        assertEquals(1, restored.newChapters)
        assertEquals(1, restored.newNotes)
        assertEquals(3, restored.newStudyItems)
        assertEquals(1, restored.newReviews)
        assertEquals("Local source note", ChapterStore(context).readNote(chapter.sha256, 2))
        assertEquals(1, StudyStore(context).counts(chapter.sha256).mistakes)

        ChapterStore(context).saveNote(chapter.sha256, 2, "newer device note")
        val repeated = BackupManager(context).restore(Uri.fromFile(archive))
        assertEquals(0, repeated.newChapters)
        assertEquals(0, repeated.newNotes)
        assertEquals(1, repeated.preservedNotes)
        assertEquals("newer device note", ChapterStore(context).readNote(chapter.sha256, 2))

        val inconsistent = File(context.cacheDir, "inconsistent.zip")
        rewriteZip(archive, inconsistent) { name, bytes ->
            if (name != "study_reviews.jsonl") bytes else {
                val row = JSONObject(bytes.toString(Charsets.UTF_8).trim())
                row.put("rating", 3)
                (row.toString() + "\n").toByteArray()
            }
        }
        expectFailure { runBlocking { BackupManager(context).restore(Uri.fromFile(inconsistent)) } }
        assertEquals("newer device note", ChapterStore(context).readNote(chapter.sha256, 2))
        assertEquals(3, StudyStore(context).list(chapter.sha256).size)
    }

    @Test fun fiveOptionQuestionRoundTripsAndWrongChoiceRemainsWrong() = runBlocking {
        val chapter = ChapterStore(context).importPdf(Uri.fromFile(pdf)).chapter
        val store = StudyStore(context)
        val itemId = store.add(chapter, 1, StudyKind.MCQ, "Correct is E?", options =
            listOf("First", "Second", "Third", "Fourth", "Fifth"), correctIndex = 4,
            explanation = "Book explicitly lists E")
        store.review(itemId, rating = 0, selectedIndex = 3)
        val backup = File(context.cacheDir, "five-options.zip")
        BackupManager(context).export(Uri.fromFile(backup))
        MedMateDatabase(context).writableDatabase.execSQL("DELETE FROM chapters")
        File(context.filesDir, "original_documents").deleteRecursively()
        assertEquals(1, BackupManager(context).restore(Uri.fromFile(backup)).newReviews)
        val restored = StudyStore(context).list(chapter.sha256).single()
        assertEquals(5, restored.options.size)
        assertEquals(4, restored.correctIndex)
        assertEquals(1, StudyStore(context).counts(chapter.sha256).mistakes)

        // An invalid index or fabricated correct/incorrect state must not corrupt live data.
        val bad = File(context.cacheDir, "five-invalid-review.zip")
        rewriteZip(backup, bad) { name, bytes ->
            if (name != "study_reviews.jsonl") bytes else {
                val row = JSONObject(bytes.toString(Charsets.UTF_8).trim())
                row.put("selectedIndex", 5)
                (row.toString() + "\n").toByteArray()
            }
        }
        expectFailure { runBlocking { BackupManager(context).restore(Uri.fromFile(bad)) } }
        assertEquals(1, StudyStore(context).counts(chapter.sha256).mistakes)
    }

    @Test fun historicalVersionOnePdfAndNotesStillRestore() = runBlocking {
        val bytes = pdf.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val manifest = JSONObject().put("format", "MedMatePersonalBackup").put("version", 1)
            .put("createdAt", 1L).put("chapters", JSONArray().put(JSONObject()
                .put("sha256", sha).put("title", "Legacy chapter").put("bytes", bytes.size)
                .put("pageCount", 4).put("lastPage", 2).put("addedAt", 1L)))
        val legacy = File(context.cacheDir, "legacy-v1.zip")
        ZipOutputStream(legacy.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("documents/$sha.pdf")); zip.write(bytes); zip.closeEntry()
            zip.putNextEntry(ZipEntry("notes.jsonl")); zip.write(JSONObject().put("sha256", sha)
                .put("page", 2).put("note", "legacy note").put("updatedAt", 2L)
                .toString().plus("\n").toByteArray()); zip.closeEntry()
        }
        val report = BackupManager(context).restore(Uri.fromFile(legacy))
        assertEquals(1, report.newChapters)
        assertEquals(1, report.newNotes)
        assertEquals("legacy note", ChapterStore(context).readNote(sha, 2))
        assertTrue(StudyStore(context).list(sha).isEmpty())
    }

    private fun largestStudyLine(file: File): Int {
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return 0
                if (entry.name == "study_items.jsonl") return zip.bufferedReader().readLines().maxOf { it.length }
            }
        }
    }

    private fun rewriteZip(source: File, target: File, edit: (String, ByteArray) -> ByteArray) {
        ZipInputStream(source.inputStream()).use { input -> ZipOutputStream(target.outputStream()).use { output ->
            while (true) {
                val entry = input.nextEntry ?: break
                output.putNextEntry(ZipEntry(entry.name))
                output.write(edit(entry.name, input.readBytes()))
                output.closeEntry(); input.closeEntry()
            }
        } }
    }

    private fun reset() {
        runCatching { context.deleteDatabase("medmate.db") }
        runCatching { File(context.filesDir, "original_documents").deleteRecursively() }
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected operation to fail") } catch (_: IllegalArgumentException) { }
    }
}
