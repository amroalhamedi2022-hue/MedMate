package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class LocalBrainV09Test {
    @Test fun extractiveFindingsHaveVerbatimPageAnchors() {
        val items = listOf(
            Evidence("c:0:0", "c", 0, "Fracture of the forearm can occur after a fall. Neurovascular status needs documentation."),
            Evidence("c:3:0", "c", 3, "A fracture is disruption in the continuity of a bone. Management depends on the injury pattern.")
        )
        val brief = ExtractiveTutor.brief("Explain fracture", items)
        assertFalse(brief.isGenerative)
        assertTrue(brief.findings.isNotEmpty())
        brief.findings.forEach { finding ->
            assertTrue(items.any { it.pageIndex == finding.pageIndex && it.text.contains(finding.quote) })
            assertTrue(finding.marker.startsWith("S"))
        }
        assertEquals(0, ExtractiveTutor.brief("", items).findings.size)
    }

    @Test fun importerRejectsFalseGGUFAndDeduplicatesWithoutOverwrite() {
        val dir = Files.createTempDirectory("medmate-local-brain").toFile()
        try {
            val sample = ByteArray(256)
            "GGUF".toByteArray().copyInto(sample)
            sample[4] = 3
            sample[8] = 1 // tensor count
            sample[16] = 1 // metadata count
            val a = LocalModelImport.import(ByteArrayInputStream(sample), dir)
            val b = LocalModelImport.import(ByteArrayInputStream(sample), dir)
            assertFalse(a.reused)
            assertTrue(b.reused)
            assertEquals(a.sha256, b.sha256)
            assertEquals(1, dir.listFiles()?.size)
            LocalModelImport.validateForLoad(a.file)
            // A forged model can keep a valid GGUF header while corrupting tensor
            // bytes. It must never reach native llama.cpp merely on header checks.
            val original = a.file.readBytes()
            a.file.writeBytes(original.copyOf().apply { this[64] = 1 })
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.validateForLoad(a.file)
            }
            a.file.writeBytes(original)
            LocalModelImport.validateForLoad(a.file)
            val legacy = java.io.File(dir, "unverified.gguf")
            legacy.writeBytes(original)
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.validateForLoad(legacy)
            }
            legacy.delete()
            var bytesChecked = 0L
            assertThrows(IllegalStateException::class.java) {
                LocalModelImport.validateForLoad(a.file) {
                    bytesChecked = it
                    throw IllegalStateException("cancelled checksum")
                }
            }
            assertTrue(bytesChecked > 0L)
            val corrupt = java.io.File(dir, "corrupt.gguf")
            corrupt.writeBytes(sample.copyOf().apply { this[16] = 0 })
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.validateForLoad(corrupt)
            }
            corrupt.writeBytes(sample.copyOf(12))
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.validateForLoad(corrupt)
            }
            corrupt.delete()
            assertEquals(sample.size.toLong(), a.file.length())
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.import(ByteArrayInputStream(sample.copyOf().apply { this[8] = 0 }), dir)
            }
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.import(ByteArrayInputStream(sample.copyOf().apply { this[16] = 0 }), dir)
            }
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.import(ByteArrayInputStream(ByteArray(256)), dir)
            }
        } finally { dir.deleteRecursively() }
    }
}
