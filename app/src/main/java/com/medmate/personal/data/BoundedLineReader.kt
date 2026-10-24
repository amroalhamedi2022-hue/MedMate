package com.medmate.personal.data

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

/**
 * Parse exported JSONL files with bounded allocations.
 *
 * BufferedReader.readLine() constructs the entire string before the caller can
 * validate its length. An attacker-controlled 256 MB JSONL record can therefore
 * cause excessive allocations or OOM during a restore. This scanner caps each
 * UTF-16 line before accumulating another character. No Android dependencies.
 */
internal object BoundedLineReader {
    fun forEach(file: File, maxChars: Int, consume: (String) -> Unit) {
        require(maxChars > 0) { "Invalid maximum line length" }
        // Default UTF-8 decoders replace malformed bytes with U+FFFD. Backups
        // must fail closed rather than silently changing notes or study cards.
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        InputStreamReader(file.inputStream(), decoder).buffered().use { reader ->
            val current = StringBuilder()
            var pendingCr = false
            fun append(char: Char) {
                require(current.length < maxChars) { "Backup record exceeds safe line length" }
                current.append(char)
            }
            while (true) {
                val next = reader.read()
                if (next == -1) {
                    if (pendingCr) append('\r')
                    if (current.isNotEmpty()) consume(current.toString())
                    break
                }
                if (next == '\n'.code) {
                    // A CR in CRLF is a delimiter, not part of the JSON record.
                    // A line exactly maxChars long must still be accepted.
                    consume(current.toString())
                    current.setLength(0)
                    pendingCr = false
                    continue
                }
                if (pendingCr) {
                    append('\r') // A bare CR counts toward the line bound.
                    pendingCr = false
                }
                if (next == '\r'.code) pendingCr = true
                else append(next.toChar())
            }
        }
    }
}
