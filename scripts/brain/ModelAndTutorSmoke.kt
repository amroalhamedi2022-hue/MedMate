import com.medmate.personal.brain.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.File
import java.nio.file.Files

private fun gguf(size: Int = 256): ByteArray = ByteArray(size).also {
    "GGUF".toByteArray(Charsets.US_ASCII).copyInto(it)
    it[4] = 3 // little-endian GGUF version 3
    it[8] = 1 // one tensor, little endian
    it[16] = 1 // one metadata entry, little endian
}
private fun expectFailure(block: () -> Any) {
    check(runCatching { block() }.isFailure) { "Operation unexpectedly succeeded" }
}
fun main() {
    val chapter = "sha-example"
    val e = listOf(
        Evidence("sha-example:8:0", chapter, 8, "Volkmann contracture is a complication of ischemia.\nImmediate recognition of compartment syndrome is important.", 1.0),
        Evidence("sha-example:9:0", chapter, 9, "A fracture is a break in the continuity of bone. Fracture management depends on the patient's injuries.", 0.5)
    )
    val found = ExtractiveTutor.brief("Explain fracture", e)
    check(!found.isGenerative)
    check(found.findings.isNotEmpty() && found.findings[0].pageNumber == 10)
    check(found.findings.all { f -> e.any { it.pageIndex == f.pageIndex && it.text.contains(f.quote) } })
    check(ExtractiveTutor.brief("", e).findings.isEmpty())
    check(ExtractiveTutor.brief("fracture", emptyList()).findings.isEmpty())
    expectFailure { ExtractiveTutor.brief("fracture", e, 0) }
    check(ExtractiveTutor.brief("fracture", e, 1).findings.size == 1)
    println("ExtractiveTutor: 6 checks passed")

    val temp = Files.createTempDirectory("medmate-gguf-test").toFile()
    try {
        val good = gguf()
        val first = LocalModelImport.import(ByteArrayInputStream(good), temp)
        check(!first.reused && first.bytes == 256L && first.file.isFile)
        val second = LocalModelImport.import(ByteArrayInputStream(good), temp)
        check(second.reused && second.sha256 == first.sha256)
        expectFailure { LocalModelImport.import(ByteArrayInputStream("not a gguf".toByteArray()), temp) }
        expectFailure { LocalModelImport.import(ByteArrayInputStream(ByteArray(256)), temp) }
        expectFailure { LocalModelImport.import(ByteArrayInputStream(gguf().apply { this[4] = 9 }), temp) }
        expectFailure { LocalModelImport.import(ByteArrayInputStream(gguf().apply { this[8] = 0 }), temp) }
        expectFailure { LocalModelImport.import(ByteArrayInputStream(gguf().apply { this[16] = 0 }), temp) }
        expectFailure { LocalModelImport.import(ByteArrayInputStream(gguf().apply {
            for (i in 8..15) this[i] = 0xff.toByte()
        }), temp) }
        expectFailure { LocalModelImport.import(ByteArrayInputStream(gguf().copyOf(12)), temp) }
        val lengthBefore = first.file.length()
        expectFailure { LocalModelImport.import(ByteArrayInputStream(good), temp) { if (it > 0) error("cancel") } }
        check(first.file.length() == lengthBefore)
        // Some Android document providers temporarily return zero bytes without EOF.
        // Recover from a short pause, but never spin forever on a broken provider.
        val flaky = object : InputStream() {
            private var pauses = 0
            private var offset = 0
            override fun read(): Int = if (offset < good.size) good[offset++].toInt() and 0xff else -1
            override fun read(bytes: ByteArray, start: Int, length: Int): Int {
                if (pauses++ < 3) return 0
                if (offset >= good.size) return -1
                val count = minOf(length, good.size - offset)
                good.copyInto(bytes, start, offset, offset + count)
                offset += count
                return count
            }
        }
        val recovered = LocalModelImport.import(flaky, temp)
        check(recovered.reused && recovered.sha256 == first.sha256)
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, start: Int, length: Int): Int = 0
        }
        expectFailure { LocalModelImport.import(stalled, temp) }
        check(temp.listFiles().orEmpty().none { it.name.endsWith(".part") })
        val files = temp.listFiles().orEmpty()
        check(files.size == 1 && files.single().name.endsWith(".gguf"))
        first.file.writeBytes(gguf().apply { this[9] = 1 })
        expectFailure { LocalModelImport.import(ByteArrayInputStream(good), temp) }
        println("LocalModelImport: 16 checks passed (synthetic GGUF header, no real model)")
    } finally { temp.deleteRecursively() }
}
