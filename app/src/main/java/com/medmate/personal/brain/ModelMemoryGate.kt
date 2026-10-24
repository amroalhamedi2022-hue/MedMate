package com.medmate.personal.brain

/** Pre-flight conservative estimate, not a native peak-memory measurement.
 *  This reduces accidental OOM; loading still needs device-level verification.
 */
object ModelMemoryGate {
    const val MIB = 1_048_576L
    private const val RESERVED = 384L * MIB
    private const val MIN_SPARE = 256L * MIB

    fun estimatedRequiredBytes(modelBytes: Long): Long {
        require(modelBytes > 0) { "ملف النموذج غير صالح" }
        require(modelBytes <= LocalModelImport.MAX_BYTES) { "النموذج كبير جدًا" }
        // 1.35x file + working/context buffer; integer math avoids overflow at 8 GiB.
        return modelBytes + (modelBytes * 35 / 100) + RESERVED
    }
    fun canAttemptLoad(modelBytes: Long, availableBytes: Long, lowMemory: Boolean): Boolean =
        availableBytes > 0 && !lowMemory &&
            availableBytes >= estimatedRequiredBytes(modelBytes) + MIN_SPARE
}
