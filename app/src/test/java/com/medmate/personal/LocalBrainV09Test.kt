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
