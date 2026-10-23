import com.medmate.personal.data.BoundedLineReader
import java.io.File

fun main() {
    val sample = File.createTempFile("medmate-jsonl-", ".txt")
    try {
        sample.writeText("{\"note\":\"مرحبا\"}\r\n{\"note\":\"Medical terms\"}\n", Charsets.UTF_8)
        val lines = mutableListOf<String>()
        BoundedLineReader.forEach(sample, 100) { lines.add(it) }
        check(lines == listOf("{\"note\":\"مرحبا\"}", "{\"note\":\"Medical terms\"}"))
        sample.writeText("final row without newline", Charsets.UTF_8)
        val final = mutableListOf<String>()
        BoundedLineReader.forEach(sample, 100) { final.add(it) }
        check(final == listOf("final row without newline"))
        sample.writeText("x".repeat(100_000), Charsets.UTF_8)
        val error = runCatching { BoundedLineReader.forEach(sample, 50) { error("Oversized line reached consumer") } }
        check(error.exceptionOrNull() is IllegalArgumentException)
        // Input can contain a giant record, but no corresponding huge String is accumulated.
        sample.writeText("", Charsets.UTF_8)
        var called = false
        BoundedLineReader.forEach(sample, 50) { called = true }
        check(!called)
        println("PASS: bounded UTF-8 JSONL parsing, Arabic/English, CRLF, final line, oversized data")
    } finally { sample.delete() }
}
