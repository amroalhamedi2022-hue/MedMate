package com.medmate.personal.data

import java.io.File

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
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val current = StringBuilder()
            while (true) {
                val next = reader.read()
                if (next == -1) {
                    if (current.isNotEmpty()) consume(stripCr(current))
                    break
                }
                if (next == '\n'.code) {
                    consume(stripCr(current))
                    current.setLength(0)
                } else {
                    require(current.length < maxChars) { "Backup record exceeds safe line length" }
                    current.append(next.toChar())
                }
            }
        }
    }

    private fun stripCr(line: StringBuilder): String =
        if (line.isNotEmpty() && line.last() == '\r') line.substring(0, line.length - 1)
        else line.toString()
}
