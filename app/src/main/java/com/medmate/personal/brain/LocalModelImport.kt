package com.medmate.personal.brain

import java.io.File
import java.io.InputStream
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Safe GGUF file storage only. Importing a GGUF is NOT proof that llama.cpp can load it.
 * Runtime binding, architecture compatibility and RAM must be tested separately.
 * Stored filenames are derived from SHA-256, never from untrusted document names.
 */
data class ImportedGguf(val file: File, val sha256: String, val bytes: Long, val reused: Boolean)

object LocalModelImport {
    const val MIN_BYTES = 128L
    const val MAX_BYTES = 8L * 1024 * 1024 * 1024
    private const val MAX_CONSECUTIVE_EMPTY_READS = 8
    private val magic = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())

    /** Uses atomic temp file and digest. Caller handles coroutine cancellation via onBytes. */
    fun import(input: InputStream, directory: File, onBytes: (Long) -> Unit = {}): ImportedGguf {
        require(directory.isDirectory || directory.mkdirs()) { "تعذّر إنشاء مجلد النماذج" }
        val temp = File.createTempFile("gguf-import-", ".part", directory)
        val sha = MessageDigest.getInstance("SHA-256")
        var count = 0L
        val head = ByteArray(24) // GGUF magic/version + tensor and metadata counts
        try {
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var emptyReads = 0
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (n == 0) {
                            require(++emptyReads <= MAX_CONSECUTIVE_EMPTY_READS) {
                                "مصدر ملف النموذج توقف عن إرسال البيانات؛ أُلغِي الاستيراد بأمان"
                            }
                            continue
                        }
                        emptyReads = 0
                        require(count <= MAX_BYTES - n) { "النموذج أكبر من الحد المتاح" }
                        for (i in 0 until n) if (count + i < head.size) head[(count + i).toInt()] = buffer[i]
                        output.write(buffer, 0, n)
                        sha.update(buffer, 0, n)
                        count += n
                        onBytes(count)
                    }
                    output.fd.sync()
                }
            }
            require(count >= MIN_BYTES) { "الملف صغير جدًا ليكون نموذج GGUF" }
            require(head.copyOfRange(0, 4).contentEquals(magic)) { "الملف ليس GGUF" }
            val version = (head[4].toInt() and 0xff) or ((head[5].toInt() and 0xff) shl 8) or
                ((head[6].toInt() and 0xff) shl 16) or ((head[7].toInt() and 0xff) shl 24)
            require(version in 2..3) { "نسخة GGUF غير مدعومة للفحص الأولي ($version)" }
            // An inference model needs both tensors and metadata. Reject obvious
            // fake/truncated headers before persisting multi-GB files. This is
            // only a cheap header sanity check, NOT full GGUF parsing or proof
            // that llama.cpp can load the model on the device.
            val header = java.nio.ByteBuffer.wrap(head).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val tensors = header.getLong(8)
            val metadata = header.getLong(16)
            require(tensors in 1..10_000_000L && metadata in 1..1_000_000L) {
                "ترويسة GGUF غير صالحة: عدد tensors أو metadata غير منطقي"
            }
            val digest = sha.digest().joinToString("") { "%02x".format(it) }
            val target = File(directory, "$digest.gguf")
            if (target.exists()) {
                require(target.isFile && target.length() == count && digestFile(target) == digest) {
                    "يوجد ملف نموذج تالف، لن نكتب فوقه تلقائيًا"
                }
                return ImportedGguf(target, digest, count, true)
            }
            require(temp.renameTo(target)) { "تعذّر حفظ النموذج؛ لن نترك ملفًا ناقصًا" }
            return ImportedGguf(target, digest, count, false)
        } finally {
            temp.delete()
        }
    }

    private fun digestFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                if (n > 0) digest.update(bytes, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
