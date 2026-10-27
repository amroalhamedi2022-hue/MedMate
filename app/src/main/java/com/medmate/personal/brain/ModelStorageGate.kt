package com.medmate.personal.brain

/** Disk-space preflight for user-selected local GGUF files; never downloads a model. */
object ModelStorageGate {
    const val RESERVE_BYTES = 64L * 1024L * 1024L
    const val CHECK_INTERVAL_BYTES = 4L * 1024L * 1024L

    fun canStart(availableBytes: Long, expectedModelBytes: Long): Boolean =
        expectedModelBytes in LocalModelImport.MIN_BYTES..LocalModelImport.MAX_BYTES &&
            availableBytes >= RESERVE_BYTES &&
            expectedModelBytes <= availableBytes - RESERVE_BYTES

    fun canContinue(availableBytes: Long, nextChunkBytes: Long): Boolean =
        nextChunkBytes >= 0L &&
            availableBytes >= RESERVE_BYTES &&
            nextChunkBytes <= availableBytes - RESERVE_BYTES
}
