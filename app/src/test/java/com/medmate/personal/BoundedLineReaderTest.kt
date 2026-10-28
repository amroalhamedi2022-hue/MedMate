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

    @Test fun crlfAtExactLineLimitIsAccepted() {
        val file = File.createTempFile("medmate-", ".jsonl")
        try {
            file.writeText("abc\r\nxyz\r\n", Charsets.UTF_8)
            val rows = mutableListOf<String>()
            BoundedLineReader.forEach(file, 3, rows::add)
            assertEquals(listOf("abc", "xyz"), rows)
        } finally { file.delete() }
    }

    @Test fun malformedUtf8IsRejectedRatherThanReplaced() {
        val file = File.createTempFile("medmate-", ".jsonl")
        try {
            file.writeBytes(byteArrayOf(0x7b, 0xc3.toByte(), 0x28, 0x7d))
            assertThrows(java.nio.charset.MalformedInputException::class.java) {
                BoundedLineReader.forEach(file, 100) { }
            }
        } finally { file.delete() }
    }

    @Test fun bareCrCountsTowardsLimit() {
        val file = File.createTempFile("medmate-", ".jsonl")
        try {
            file.writeText("abc\rx\n", Charsets.UTF_8)
            assertThrows(IllegalArgumentException::class.java) {
                BoundedLineReader.forEach(file, 3) { }
            }
        } finally { file.delete() }
    }
}
