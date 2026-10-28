package com.medmate.personal.brain

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the bounded local-file import boundary with synthetic headers only.
 * These fixtures are NOT complete GGUF models and must never be counted as
 * successful llama.cpp parsing or inference.
 */
class LocalModelImportTest {
    private fun syntheticHeader(
        version: Int = 3,
        tensors: Long = 1,
        metadata: Long = 1,
        size: Int = 160
    ): ByteArray {
        require(size >= 24)
        val bytes = ByteArray(size)
        byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
            .copyInto(bytes, destinationOffset = 0)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, version)
            putLong(8, tensors)
            putLong(16, metadata)
        }
        return bytes
    }

    private fun <T> inTempDir(block: (File) -> T): T {
        val directory = Files.createTempDirectory("medmate-gguf-boundary").toFile()
        return try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun importUsesContentAddressedNameAndDeduplicatesIdenticalBytes() = inTempDir { dir ->
        val bytes = syntheticHeader()
        val first = LocalModelImport.import(ByteArrayInputStream(bytes), dir)
        val second = LocalModelImport.import(ByteArrayInputStream(bytes), dir)

        assertEquals(bytes.size.toLong(), first.bytes)
        assertEquals(first.sha256 + ".gguf", first.file.name)
        assertArrayEquals(bytes, first.file.readBytes())
        assertFalse(first.reused)
        assertTrue(second.reused)
        assertEquals(first.sha256, second.sha256)
        assertEquals(1, dir.listFiles { _, name -> name.endsWith(".gguf") }?.size)
        assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".part") })
        // A syntactically plausible 24-byte header is not a full GGUF parser
        // result and says nothing about native model compatibility.
    }

    @Test fun malformedMagicVersionAndCountsFailBeforeAnyFinalFileIsKept() {
        val malformed = listOf(
            syntheticHeader().also { it[0] = 'X'.code.toByte() },
            syntheticHeader(version = 1),
            syntheticHeader(tensors = 0),
            syntheticHeader(metadata = 0)
        )
        malformed.forEach { bytes ->
            inTempDir { dir ->
                assertThrows(IllegalArgumentException::class.java) {
                    LocalModelImport.import(ByteArrayInputStream(bytes), dir)
                }
                assertEquals(0, dir.listFiles().orEmpty().size)
            }
        }
    }

    @Test fun modifiedStoredModelIsRejectedBeforeNativeLoad(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val changed = imported.file.readBytes()
        changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
        imported.file.writeBytes(changed)

        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.validateForLoad(imported.file)
        }
        Unit
    }

    @Test fun aHashNamedSymlinkCannotRedirectNativeLoadToItsTarget(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val target = File(dir, "detached-target.gguf")
        assertTrue(imported.file.renameTo(target))
        Files.createSymbolicLink(imported.file.toPath(), target.toPath())

        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.validateForLoad(imported.file)
        }
        Unit
    }

    @Test fun nonContentAddressedFilenameIsRejected(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val renamed = File(dir, "student-model.gguf")
        assertTrue(imported.file.renameTo(renamed))

        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.validateForLoad(renamed)
        }
        Unit
    }

    @Test fun hashNamedSymlinkCannotBeReusedAsImportedModel(): Unit = inTempDir { dir ->
        val bytes = syntheticHeader()
        val imported = LocalModelImport.import(ByteArrayInputStream(bytes), dir)
        val detached = File(dir, "detached-target.gguf")
        assertTrue(imported.file.renameTo(detached))
        Files.createSymbolicLink(imported.file.toPath(), detached.toPath())

        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.import(ByteArrayInputStream(bytes), dir)
        }
        assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".part") })
        Unit
    }


    @Test fun validatedSnapshotCarriesExactContentAddressAndStableFileMetadata(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val snapshot = LocalModelImport.validateForLoad(imported.file)
        assertEquals(imported.sha256, snapshot.sha256)
        assertEquals(imported.bytes, snapshot.bytes)
        assertEquals(imported.file.canonicalPath, snapshot.canonicalPath)
        assertEquals(imported.file.lastModified(), snapshot.lastModifiedMillis)
        LocalModelImport.requireSameFileSnapshot(snapshot, imported.file)
        Unit
    }

    @Test fun postLoadSnapshotRejectsSizeChangeBeforePublishingNativeIdentity(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val snapshot = LocalModelImport.validateForLoad(imported.file)
        imported.file.appendBytes(byteArrayOf(0x7f))
        imported.file.setLastModified(snapshot.lastModifiedMillis)
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.requireSameFileSnapshot(snapshot, imported.file)
        }
        Unit
    }

    @Test fun postLoadSnapshotRejectsSymlinkReplacement(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val snapshot = LocalModelImport.validateForLoad(imported.file)
        val target = File(dir, "detached-after-validation.gguf")
        assertTrue(imported.file.renameTo(target))
        Files.createSymbolicLink(imported.file.toPath(), target.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.requireSameFileSnapshot(snapshot, imported.file)
        }
        Unit
    }



    @Test fun cancellationAfterNativeLoadReleasesSessionBeforePublishingIdentity(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val snapshot = LocalModelImport.validateForLoad(imported.file)
        val session = Any()
        var released = false
        assertThrows(CancellationException::class.java) {
            verifyNativeSessionBeforePublishing(
                snapshot = snapshot,
                file = imported.file,
                session = session,
                ensureActive = { throw CancellationException("cancelled during native load") },
                releaseSession = { released = it === session }
            )
        }
        assertTrue(released)
        Unit
    }

    @Test fun changedModelAfterNativeLoadReleasesUnpublishedSession(): Unit = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val snapshot = LocalModelImport.validateForLoad(imported.file)
        imported.file.appendBytes(byteArrayOf(0x55))
        imported.file.setLastModified(snapshot.lastModifiedMillis)
        var released = false
        assertThrows(IllegalArgumentException::class.java) {
            verifyNativeSessionBeforePublishing(
                snapshot = snapshot,
                file = imported.file,
                session = Any(),
                ensureActive = {},
                releaseSession = { released = true }
            )
        }
        assertTrue(released)
        Unit
    }

    @Test fun repeatedZeroByteReadsAbortAndCleanTheTemporaryFile() = inTempDir { dir ->
        var reads = 0
        val stalled = object : InputStream() {
            override fun read(): Int = -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                return 0
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.import(stalled, dir)
        }
        assertEquals(9, reads)
        assertEquals(0, dir.listFiles().orEmpty().size)
    }

    @Test fun expectedSizeAndLowStorageAreRejectedBeforeCreatingTempAndCloseProvider() = inTempDir { dir ->
        val bytes = syntheticHeader()
        val provider = object : ByteArrayInputStream(bytes) {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.import(
                input = provider,
                directory = dir,
                expectedBytes = bytes.size.toLong(),
                availableSpace = { ModelStorageGate.RESERVE_BYTES + bytes.size - 1L }
            )
        }
        assertTrue("SAF provider stream must close even when preflight rejects", provider.closed)
        assertEquals("no partial model or temp file may be created", 0, dir.listFiles().orEmpty().size)
    }

    @Test fun inaccurateUnknownSizeStopsBeforeStorageReserveIsConsumed() = inTempDir { dir ->
        val bytes = syntheticHeader(size = 4 * 1024 * 1024 + 64 * 1024)
        var spaceChecks = 0
        val available = {
            spaceChecks++
            if (spaceChecks == 1) ModelStorageGate.RESERVE_BYTES + bytes.size.toLong()
            else ModelStorageGate.RESERVE_BYTES + 64 * 1024L - 1L
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.import(
                input = ByteArrayInputStream(bytes),
                directory = dir,
                availableSpace = available
            )
        }
        assertEquals("storage should be checked at start and after 4 MiB", 2, spaceChecks)
        assertEquals("partial import must be cleaned", 0, dir.listFiles().orEmpty().size)
    }

    @Test fun reportedSizeMustMatchActualStreamLength() = inTempDir { dir ->
        val bytes = syntheticHeader()
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.import(
                input = ByteArrayInputStream(bytes),
                directory = dir,
                expectedBytes = bytes.size.toLong() + 1L,
                availableSpace = { ModelStorageGate.RESERVE_BYTES + bytes.size + 4096L }
            )
        }
        assertEquals(0, dir.listFiles().orEmpty().size)
    }

    @Test fun importedModelCanBeDeletedToReclaimAppStorage() = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        assertTrue(imported.file.exists())
        LocalModelImport.deleteImported(imported.file, dir)
        assertFalse(imported.file.exists())
        assertEquals(0, dir.listFiles().orEmpty().size)
    }

    @Test fun deletionRefusesTheCurrentlyLoadedModel() = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.deleteImported(imported.file, dir, loadedModelName = imported.file.name)
        }
        assertTrue("the active model must remain on disk", imported.file.exists())
    }

    @Test fun deletionRefusesFilesOutsideTheManagedModelDirectory() = inTempDir { dir ->
        val otherDirectory = Files.createTempDirectory("medmate-outside-models").toFile()
        try {
            val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
            val outside = File(otherDirectory, imported.file.name)
            assertTrue(imported.file.copyTo(outside).exists())
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelImport.deleteImported(outside, dir)
            }
            assertTrue("outside file must remain untouched", outside.exists())
            assertTrue("managed import must remain untouched", imported.file.exists())
        } finally {
            otherDirectory.deleteRecursively()
        }
    }

    @Test fun deletionRefusesSymlinksAndUnrecognizedNames() = inTempDir { dir ->
        val imported = LocalModelImport.import(ByteArrayInputStream(syntheticHeader()), dir)
        val detached = File(dir, "detached.gguf")
        assertTrue(imported.file.renameTo(detached))
        val link = File(dir, imported.file.name)
        Files.createSymbolicLink(link.toPath(), detached.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.deleteImported(link, dir)
        }
        assertTrue(detached.exists())
        Files.delete(link.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelImport.deleteImported(detached, dir)
        }
        assertTrue(detached.exists())
    }

}
