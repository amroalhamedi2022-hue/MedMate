package com.medmate.personal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupArchiveLimitsTest {
    @Test fun emptyBackupIsAllowed() {
        BackupArchiveLimits.requireSafeDocumentBytes(emptyList())
    }

    @Test fun acceptsOnePdfAtThePerFileBoundaryAndAggregateBoundary() {
        BackupArchiveLimits.requireSafeDocumentBytes(listOf(BackupArchiveLimits.MAX_PDF_BYTES))
        BackupArchiveLimits.requireSafeDocumentBytes(List(4) { BackupArchiveLimits.MAX_PDF_BYTES })
    }

    @Test fun rejectsTinyNegativeAndOversizedPdfDeclarations() {
        listOf(-1L, 0L, 4L, BackupArchiveLimits.MAX_PDF_BYTES + 1).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) {
                BackupArchiveLimits.requireSafeDocumentBytes(listOf(bytes))
            }
        }
    }

    @Test fun rejectsAggregatePdfPayloadAboveTwoGiB() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchiveLimits.requireSafeDocumentBytes(List(5) { BackupArchiveLimits.MAX_PDF_BYTES })
        }
    }

    @Test fun rejectsLongOverflowAsAnOversizedIndividualPdf() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchiveLimits.requireSafeDocumentBytes(listOf(Long.MAX_VALUE))
        }
    }
}
