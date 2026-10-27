package com.medmate.personal.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStorageGateTest {
    @Test fun acceptsExactFreeSpaceReserveBoundaries() {
        val bytes = LocalModelImport.MIN_BYTES
        assertTrue(ModelStorageGate.canStart(ModelStorageGate.RESERVE_BYTES + bytes, bytes))
        assertFalse(ModelStorageGate.canStart(ModelStorageGate.RESERVE_BYTES + bytes - 1L, bytes))
        val chunk = 64L * 1024L
        val unknownSizeHeadroom = ModelStorageGate.RESERVE_BYTES +
            ModelStorageGate.CHECK_INTERVAL_BYTES + chunk
        assertTrue(ModelStorageGate.canContinue(unknownSizeHeadroom, chunk))
        assertFalse(ModelStorageGate.canContinue(unknownSizeHeadroom - 1L, chunk))
        assertTrue(ModelStorageGate.canContinue(
            ModelStorageGate.RESERVE_BYTES + chunk, chunk, expectedRemainingBytes = chunk))
        assertFalse(ModelStorageGate.canContinue(
            ModelStorageGate.RESERVE_BYTES + chunk, chunk, expectedRemainingBytes = chunk + 1L))
    }

    @Test fun rejectsInvalidSizesAndOverflowShapedInputs() {
        assertFalse(ModelStorageGate.canStart(1024L, 0L))
        assertFalse(ModelStorageGate.canStart(Long.MAX_VALUE, Long.MAX_VALUE))
        assertFalse(ModelStorageGate.canStart(ModelStorageGate.RESERVE_BYTES, LocalModelImport.MIN_BYTES))
        assertFalse(ModelStorageGate.canContinue(0L, 1L))
        assertFalse(ModelStorageGate.canContinue(Long.MAX_VALUE, -1L))
    }
}
