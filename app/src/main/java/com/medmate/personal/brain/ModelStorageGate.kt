package com.medmate.personal.brain

/** Disk-space preflight for user-selected local GGUF files; never downloads a model. */
object ModelStorageGate {
    const val RESERVE_BYTES = 64L * 1024L * 1024L
    const val CHECK_INTERVAL_BYTES = 4L * 1024L * 1024L

    fun canStart(availableBytes: Long, expectedModelBytes: Long): Boolean =
        expectedModelBytes in LocalModelImport.MIN_BYTES..LocalModelImport.MAX_BYTES &&
            availableBytes >= RESERVE_BYTES &&
            expectedModelBytes <= availableBytes - RESERVE_BYTES

    /**
     * When the provider size is known, require room for every remaining byte plus
     * the reserve. When unknown, retain an extra check-interval of headroom so a
     * long gap between filesystem probes cannot consume the reserve.
     */
    fun canContinue(
        availableBytes: Long,
        nextChunkBytes: Long,
        expectedRemainingBytes: Long? = null
    ): Boolean {
        if (nextChunkBytes < 0L || availableBytes < RESERVE_BYTES) return false
        val headroom = availableBytes - RESERVE_BYTES
        return if (expectedRemainingBytes != null) {
            expectedRemainingBytes >= nextChunkBytes && expectedRemainingBytes <= headroom
        } else {
            headroom >= CHECK_INTERVAL_BYTES &&
                nextChunkBytes <= headroom - CHECK_INTERVAL_BYTES
        }
    }
}
