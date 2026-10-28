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

data class ValidatedGgufSnapshot internal constructor(
    val canonicalPath: String,
    val sha256: String,
    val bytes: Long,
    val lastModifiedMillis: Long
)


object LocalModelImport {
    const val MIN_BYTES = 128L
    const val MAX_BYTES = 8L * 1024 * 1024 * 1024
    private const val MAX_CONSECUTIVE_EMPTY_READS = 8
    private val magic = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())

    /** Uses atomic temp file and digest. Caller handles coroutine cancellation via onBytes. */
    fun import(
        input: InputStream,
        directory: File,
        onBytes: (Long) -> Unit = {},
        expectedBytes: Long? = null,
        availableSpace: () -> Long = { directory.usableSpace }
    ): ImportedGguf {
        require(directory.isDirectory || directory.mkdirs()) { "تعذّر إنشاء مجلد النماذج" }
        val sha = MessageDigest.getInstance("SHA-256")
        var count = 0L
        val head = ByteArray(24) // GGUF magic/version + tensor and metadata counts
        var headerValidated = false
        var temp: File? = null
        try {
            // Close the provider stream even when a reported size is invalid or
            // the storage preflight rejects the import before creating a temp file.
            input.use { source ->
                if (expectedBytes != null) {
                    require(expectedBytes in MIN_BYTES..MAX_BYTES) {
                        "حجم ملف GGUF المعلن غير صالح أو أكبر من الحد المسموح"
                    }
                    require(ModelStorageGate.canStart(availableSpace(), expectedBytes)) {
                        "المساحة الحرة غير كافية لاستيراد النموذج مع هامش أمان 64 MiB"
                    }
                }
                val temporary = File.createTempFile("gguf-import-", ".part", directory)
                temp = temporary
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var emptyReads = 0
                    var lastSpaceCheck = 0L
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
                        if (expectedBytes != null) {
                            require(count <= expectedBytes - n) {
                                "حجم النموذج الفعلي أكبر من الحجم الذي أعلنه مزوّد الملف"
                            }
                        }
                        // Providers may omit or lie about OpenableColumns.SIZE. Recheck
                        // free space at start and every 4 MiB, preserving a 64 MiB
                        // reserve so a large import cannot silently fill app storage.
                        if (count == 0L || count - lastSpaceCheck >= ModelStorageGate.CHECK_INTERVAL_BYTES) {
                            require(ModelStorageGate.canContinue(availableSpace(), n.toLong(), expectedBytes?.minus(count))) {
                                "المساحة الحرة أصبحت غير كافية أثناء الاستيراد؛ أُوقف قبل استهلاك هامش الأمان"
                            }
                            lastSpaceCheck = count
                        }
                        for (i in 0 until n) if (count + i < head.size) head[(count + i).toInt()] = buffer[i]
                        // Validate as soon as 24 bytes arrive, BEFORE writing the rest
                        // of a potentially multi-GB malformed upload to disk.
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
            require(expectedBytes == null || count == expectedBytes) {
                "حجم النموذج الفعلي لا يطابق الحجم الذي أعلنه مزوّد الملف"
            }
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
            require(requireNotNull(temp).renameTo(target)) { "تعذّر حفظ النموذج؛ لن نترك ملفًا ناقصًا" }
            return ImportedGguf(target, digest, count, false)
        } finally {
            temp?.delete()
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
    fun validateForLoad(file: File, onBytes: (Long) -> Unit = {}): ValidatedGgufSnapshot {
        // Reject links before resolving the canonical path. Capture file identity
        // before hashing so ordinary swaps/modifications during validation fail closed.
        require(!java.nio.file.Files.isSymbolicLink(file.toPath())) {
            "ملف النموذج لا يجوز أن يكون رابطًا رمزيًا"
        }
        require(file.isFile && file.extension.equals("gguf", ignoreCase = true)) {
            "اختر ملف GGUF محليًا صالحًا"
        }
        val canonicalPath = file.canonicalPath
        val initialBytes = file.length()
        val initialModified = file.lastModified()
        require(initialBytes in MIN_BYTES..MAX_BYTES) {
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
        require(!java.nio.file.Files.isSymbolicLink(file.toPath()) && file.isFile &&
            file.canonicalPath == canonicalPath && file.length() == initialBytes &&
            file.lastModified() == initialModified) {
            "تغيّر ملف النموذج أثناء التحقق؛ أعد استيراده قبل التشغيل"
        }
        return ValidatedGgufSnapshot(canonicalPath, expectedDigest, initialBytes, initialModified)
    }

    /**
     * Check the same private file is still present after native loading and before
     * publishing model identity. This is a path/size/timestamp race guard, not a
     * second full-file hash and not proof of successful inference.
     */
    fun requireSameFileSnapshot(snapshot: ValidatedGgufSnapshot, file: File) {
        require(!java.nio.file.Files.isSymbolicLink(file.toPath())) {
            "ملف النموذج لا يجوز أن يكون رابطًا رمزيًا"
        }
        require(file.isFile && file.extension.equals("gguf", ignoreCase = true) &&
            file.nameWithoutExtension == snapshot.sha256 &&
            file.canonicalPath == snapshot.canonicalPath && file.length() == snapshot.bytes &&
            file.lastModified() == snapshot.lastModifiedMillis) {
            "تغيّر ملف النموذج أثناء التحميل؛ أعد استيراده قبل تشغيل الاستدلال"
        }
    }

    /**
     * Remove an app-private, content-addressed import to reclaim device storage.
     * A full hash is intentionally not required here so a corrupted imported model
     * can still be deleted safely. Never follow links or delete outside the managed directory.
     */
    fun deleteImported(file: File, directory: File, loadedModelName: String? = null) {
        require(!java.nio.file.Files.isSymbolicLink(directory.toPath())) {
            "مجلد النماذج لا يجوز أن يكون رابطًا رمزيًا"
        }
        require(directory.isDirectory) { "مجلد النماذج غير موجود" }
        val root = directory.canonicalFile
        require(!java.nio.file.Files.isSymbolicLink(file.toPath())) {
            "لن نحذف ملف نموذج عبر رابط رمزي"
        }
        val target = file.canonicalFile
        require(target.parentFile == root) { "لن نحذف ملفًا خارج مجلد النماذج الخاص بالتطبيق" }
        require(target.isFile && target.extension.equals("gguf", ignoreCase = true)) {
            "الملف المحدد ليس نموذج GGUF محفوظًا"
        }
        require(target.nameWithoutExtension.matches(Regex("^[0-9a-f]{64}$"))) {
            "اسم النموذج غير مطابق لبصمة الاستيراد؛ لم يُحذف الملف"
        }
        require(target.name != loadedModelName) {
            "أفرغ النموذج المحمّل أولًا قبل حذفه"
        }
        require(target.delete()) { "تعذّر حذف ملف النموذج من مساحة التطبيق" }
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
