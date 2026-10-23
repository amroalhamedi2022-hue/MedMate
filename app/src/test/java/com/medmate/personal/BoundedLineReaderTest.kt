package com.medmate.personal

import com.medmate.personal.data.BoundedLineReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class BoundedLineReaderTest {
    @Test fun unicodeAndNewlines() {
        val file = File.createTempFile("medmate-", ".jsonl")
        try {
            file.writeText("طب\r\nOrthopedics\nEnd", Charsets.UTF_8)
            val rows = mutableListOf<String>()
            BoundedLineReader.forEach(file, 100, rows::add)
            assertEquals(listOf("طب", "Orthopedics", "End"), rows)
        } finally { file.delete() }
    }

    @Test fun rejectsLongLinesBeforeDispatch() {
        val file = File.createTempFile("medmate-", ".jsonl")
        try {
            file.writeText("x".repeat(20_000))
            var dispatched = false
            assertThrows(IllegalArgumentException::class.java) {
                BoundedLineReader.forEach(file, 100) { dispatched = true }
            }
            assertEquals(false, dispatched)
        } finally { file.delete() }
    }
}
