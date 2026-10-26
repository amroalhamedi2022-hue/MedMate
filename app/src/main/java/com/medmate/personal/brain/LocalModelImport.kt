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
        var headerValidated = false
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
                        // Validate as soon as 24 bytes arrive, BEFORE writing the
                        // rest of a potentially multi-GB malformed upload to disk.
                        // A provider returning tiny chunks is supported as well.
                        if (!headerValidated && count + n >= head.size) {
                            validateHeader(head)
                            headerValidated = true
                        }
                        output.write(buffer, 0, n)
                        sha.update(buffer, 0, n)
                        count += n
                        onBytes(count)
                    }
                    output.fd.sync()
                }
            }
            require(count >= MIN_BYTES) { "الملف صغير جدًا ليكون نموذج GGUF" }
            require(headerValidated) { "ترويسة GGUF ناقصة" }
            val digest = sha.digest().joinToString("") { "%02x".format(it) }
            val target = File(directory, "$digest.gguf")
            if (target.exists() || java.nio.file.Files.isSymbolicLink(target.toPath())) {
                // A hash-shaped symlink must never be reported as a reusable imported model.
                // Reject even dangling links instead of silently replacing or following them.
                require(!java.nio.file.Files.isSymbolicLink(target.toPath()) && target.isFile &&
                    target.length() == count && digestFile(target) == digest) {
                    "يوجد ملف نموذج تالف أو رابط رمزي، لن نكتب فوقه تلقائيًا"
                }
                return ImportedGguf(target, digest, count, true)
            }
            require(temp.renameTo(target)) { "تعذّر حفظ النموذج؛ لن نترك ملفًا ناقصًا" }
            return ImportedGguf(target, digest, count, false)
        } finally {
            temp.delete()
        }
    }

    /**
     * Validate a previously imported model again immediately before invoking native
     * llama.cpp. A saved file may have been truncated or replaced after import.
     * Verifies the original content-addressed filename and the entire SHA-256
     * digest before native parsing, catching corruption beyond the GGUF header.
     * The callback supports coroutine cancellation during multi-GB disk reads.
     * This is NOT a full GGUF parse, authenticity proof or actual inference.
     */
    fun validateForLoad(file: File, onBytes: (Long) -> Unit = {}) {
        // Do not let a hash-shaped symlink redirect native loading outside the
        // content-addressed model file, even if its target currently hashes OK.
        require(!java.nio.file.Files.isSymbolicLink(file.toPath())) {
            "ملف النموذج لا يجوز أن يكون رابطًا رمزيًا"
        }
        require(file.isFile && file.extension.equals("gguf", ignoreCase = true)) {
            "اختر ملف GGUF محليًا صالحًا"
        }
        require(file.length() in MIN_BYTES..MAX_BYTES) {
            "حجم ملف GGUF غير صالح أو خارج الحد المسموح"
        }
        val header = ByteArray(24)
        FileInputStream(file).use { stream ->
            var offset = 0
            while (offset < header.size) {
                val count = stream.read(header, offset, header.size - offset)
                require(count > 0) { "ترويسة GGUF ناقصة" }
                offset += count
            }
        }
        validateHeader(header)
        val expectedDigest = file.nameWithoutExtension
        require(expectedDigest.length == 64 && expectedDigest.all { it in '0'..'9' || it in 'a'..'f' }) {
            "اسم النموذج غير موثوق؛ أعد استيراد ملف GGUF من داخل التطبيق"
        }
        require(digestFile(file, onBytes) == expectedDigest) {
            "تغيّر محتوى النموذج المحفوظ؛ أعد استيراده قبل التشغيل"
        }
    }

    private fun validateHeader(head: ByteArray) {
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
    }

    private fun digestFile(file: File, onBytes: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val bytes = ByteArray(64 * 1024)
            var count = 0L
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                if (n > 0) {
                    digest.update(bytes, 0, n)
                    count += n
                    onBytes(count)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
