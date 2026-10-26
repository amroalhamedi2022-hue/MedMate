package com.medmate.personal.brain

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.medmate.personal.reader.PdfTextExtractor
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

/**
 * Device-only acceptance. Uses the real LlamaAndroidPort/JNI path and never a fake port.
 * The only evidence passage is NativeInferenceProbe's fresh synthetic calibration text.
 */
@RunWith(AndroidJUnit4::class)
class NativeGgufDeviceAcceptanceTest {
    @Test
    fun realGgufLoadsAndPassesFreshSyntheticCalibration() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val path = InstrumentationRegistry.getArguments().getString("ggufPath")
            ?: error("NOT TESTED: supply ggufPath with scripts/run-native-gguf-device-test.sh")
        val source = File(path).canonicalFile
        val allowedDir = requireNotNull(context.getExternalFilesDir("native-test")).canonicalFile
        assertEquals("GGUF must be the user-supplied file in app-specific external storage", allowedDir, source.parentFile)
        assertTrue("GGUF file is missing on device", source.isFile)
        assertTrue("Expected a .gguf file", source.extension.equals("gguf", ignoreCase = true))
        assertTrue("GGUF size outside safe import limits", source.length() in LocalModelImport.MIN_BYTES..LocalModelImport.MAX_BYTES)
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        assertTrue("Insufficient free RAM for a safe native-load attempt",
            ModelMemoryGate.canAttemptLoad(source.length(), before.availMem, before.lowMemory))
        val imported = withContext(Dispatchers.IO) {
            LocalModelImport.import(FileInputStream(source), File(context.filesDir, "models"))
        }
        val port = LlamaAndroidPort()
        try {
            port.loadModel(imported.file)
            assertEquals(imported.sha256, port.loadedModelSha256)
            val result = NativeInferenceProbe.run(port) { sampleMemory(context) }
            assertTrue("Production native backend/model identity not confirmed", result.nativeBackendConfirmed)
            assertTrue("Fresh random calibration code not returned", result.calibrationPhrasePresent)
            assertTrue("Citation syntax did not pass", result.citationSyntaxValid)
            assertTrue("Full source/prompt echo detected", !result.promptEchoDetected)
            assertTrue("Real native probe failed", result.diagnosticPassed)
            assertEquals(imported.sha256, result.nativeModelSha256)
            assertEquals(imported.bytes, result.nativeModelBytes ?: -1L)
            assertRealPdfCitationRoundTrip(context, port)
            Log.i("MedMateNativeGGUF", "PASS sha256=${imported.sha256} bytes=${imported.bytes} " +
                "elapsedMs=${result.elapsedMs} answerChars=${result.answerChars} " +
                "pssBeforeKb=${result.memoryBefore?.appPssKb} pssAfterKb=${result.memoryAfter?.appPssKb} " +
                "availableBeforeBytes=${result.memoryBefore?.systemAvailableBytes} availableAfterBytes=${result.memoryAfter?.systemAvailableBytes}")
        } finally {
            var unloadFailure: Throwable? = null
            try { port.unload() } catch (failure: Throwable) { unloadFailure = failure }
            val identityCleared = port.loadedModelName == null &&
                port.loadedModelSha256 == null && port.loadedModelBytes == null
            val temporaryModelRemoved = imported.reused ||
                !imported.file.exists() || imported.file.delete()
            unloadFailure?.let { throw it }
            assertTrue("Loaded GGUF identity must clear after unload", identityCleared)
            assertTrue("A test-only imported model must be removed after unload", temporaryModelRemoved)
        }
    }


    /**
     * Device-only PDF-to-native-inference round trip. The PDF is generated inside
     * app cache from a fresh synthetic identifier; no personal document is read,
     * packaged, logged, or transmitted. This exercises PDFBox, page extraction,
     * source retrieval, prompt preparation, the real JNI model and page-link mapping.
     */
    private suspend fun assertRealPdfCitationRoundTrip(context: Context, port: LlamaAndroidPort) {
        val randomBytes = ByteArray(6).also { java.security.SecureRandom().nextBytes(it) }
        val code = "PDF-" + randomBytes.joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }.uppercase(java.util.Locale.ROOT)
        val pdfFile = File(context.cacheDir, "medmate-synthetic-pdf-" + code.lowercase(java.util.Locale.ROOT) + ".pdf")
        try {
            writeSyntheticPdf(context, pdfFile, code)
            val pages = PdfTextExtractor(context, pdfFile).use { reader ->
                assertEquals("synthetic PDF should contain two pages", 2, reader.pageCount)
                val firstPage = reader.page(0)
                val secondPage = reader.page(1)
                assertFalse("page 1 must not contain the calibration identifier", firstPage.contains(code))
                assertTrue("PDFBox did not extract the identifier from page 2", secondPage.contains(code))
                listOf(firstPage, secondPage)
            }
            val extracted = pages[1]
            val chapterId = "device-pdf-" + code
            val question = "What is the calibration identifier on page 2 of this synthetic PDF? Return the exact identifier and cite [S1]."
            val pageSources = pages.mapIndexed { index, text -> PageSource(chapterId, index, text) }
            // Restrict evidence to zero-based page index 1. The returned S1 must
            // still map to the human-facing PDF page number 2, not page 1.
            val found = StudyBrain.search(chapterId, pageSources, question, onlyPage = 1)
            assertEquals("production PDF text must be retrievable from page 2", BrainSearchStatus.FOUND, found.status)
            assertTrue("retrieved evidence must retain the page-2 identifier",
                found.evidence.any { it.pageIndex == 1 && it.text.contains(code) })
            val prepared = requireNotNull(StudyBrain.prepareSourceOnly(chapterId, question, found, maxEvidenceChars = 1000)) {
                "production source-only prompt preparation returned no evidence"
            }
            assertTrue("model prompt must present the PDF evidence", prepared.presentedEvidence.isNotEmpty())
            val before = sampleMemory(context)
            val started = System.nanoTime()
            val answer = port.generate(prepared) { }
            val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
            val after = sampleMemory(context)
            assertTrue("real native answer did not preserve the fresh PDF identifier", answer.contains(code, ignoreCase = true))
            val citationCheck = CitationValidator.check(answer, prepared.presentedEvidence.size)
            assertTrue("real native answer failed citation validation: " + citationCheck.reason, citationCheck.accepted)
            val links = SourceCitationLinks.resolve(answer, prepared.presentedEvidence)
            assertTrue("S1 must navigate to actual PDF page 2", links.any { it.marker == "S1" && it.pageNumber == 2 })
            assertTrue("native answer must not echo the entire PDF excerpt", !answer.contains(extracted, ignoreCase = true))
            Log.i("MedMateNativeGGUF", "PASS syntheticPdfPages=2 citationPage=2 syntheticPdfBytes=" + pdfFile.length() +
                " extractedChars=" + extracted.length + " elapsedMs=" + elapsed +
                " pssBeforeKb=" + (before?.appPssKb ?: -1) + " pssAfterKb=" + (after?.appPssKb ?: -1))
        } finally {
            pdfFile.delete()
        }
    }

    private fun writeSyntheticPdf(context: Context, file: File, code: String) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument().use { document ->
            addSyntheticPage(document, "Synthetic study page 1. No calibration identifier is present.")
            addSyntheticPage(document, "Synthetic study page 2. Calibration identifier: " + code + ". This is a test fixture, not clinical advice.")
            document.save(file)
        }
    }

    private fun addSyntheticPage(document: PDDocument, text: String) {
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 12f)
            stream.newLineAtOffset(48f, 740f)
            stream.showText(text)
            stream.endText()
        }
    }

    private fun sampleMemory(context: Context): NativeProbeMemory {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return NativeProbeMemory(Debug.getPss().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), info.availMem, info.lowMemory)
    }
}
