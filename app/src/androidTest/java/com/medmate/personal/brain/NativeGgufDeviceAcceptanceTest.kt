package com.medmate.personal.brain

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
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
            Log.i("MedMateNativeGGUF", "PASS sha256=${imported.sha256} bytes=${imported.bytes} " +
                "elapsedMs=${result.elapsedMs} answerChars=${result.answerChars} " +
                "pssBeforeKb=${result.memoryBefore?.appPssKb} pssAfterKb=${result.memoryAfter?.appPssKb} " +
                "availableBeforeBytes=${result.memoryBefore?.systemAvailableBytes} availableAfterBytes=${result.memoryAfter?.systemAvailableBytes}")
        } finally {
            try { port.unload() } finally { if (!imported.reused) imported.file.delete() }
        }
    }

    private fun sampleMemory(context: Context): NativeProbeMemory {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return NativeProbeMemory(Debug.getPss().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), info.availMem, info.lowMemory)
    }
}
