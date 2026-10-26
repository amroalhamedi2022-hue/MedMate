package com.medmate.personal.brain

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
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
}
