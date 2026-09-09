package de.tadris.fitness.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }
    private fun digest(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).hexDigest()
    private fun partial() = File(temporary.root, "download.part")
    @Test fun completeFileHasVerifiedContentAndMonotonicProgress() = runBlocking {
        val progress = mutableListOf<Int>()
        val file = partial()
        copyUpdate(bytes.inputStream(), file, bytes.size.toLong(), digest(bytes), progress::add)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (a,b) -> a < b })
    }
    @Test fun truncatedOversizedAndCorruptedFilesAreDeleted() = runBlocking {
        for ((data, size, hash) in listOf(
            Triple(bytes.copyOf(100), bytes.size.toLong(), digest(bytes)),
            Triple(bytes, 100L, digest(bytes)),
            Triple(bytes, bytes.size.toLong(), "00".repeat(32)),
        )) {
            val file = partial()
            try {
                copyUpdate(data.inputStream(), file, size, hash) {}
                fail("Invalid file was accepted")
            } catch (error: UpdateException) { assertEquals(UpdateError.INVALID_DOWNLOAD, error.reason) }
            assertFalse(file.exists())
        }
    }
    @Test fun streamFailureDeletesPartialFile() = runBlocking {
        val file = partial()
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("Disconnected")
        }
        try { copyUpdate(broken, file, 1, "00".repeat(32)) {}; fail("Expected failure") }
        catch (_: IOException) { assertFalse(file.exists()) }
    }
    @Test fun cancellationDeletesPartialFile() = runBlocking {
        val file = partial()
        val task = launch(start = CoroutineStart.LAZY) {
            val context = currentCoroutineContext()
            copyUpdate(bytes.inputStream(), file, bytes.size.toLong(), digest(bytes)) {
                context.cancel()
            }
        }
        task.start(); task.join()
        assertTrue(task.isCancelled)
        assertFalse(file.exists())
    }
    @Test fun cachedFileIsRecheckedBeforeInstallation() {
        val file = partial().apply { writeBytes(bytes) }
        val release = UpdateRelease("0.2.0", 1622, "", "", bytes.size.toLong(), digest(bytes))
        verifyUpdateFile(file, release)
        file.writeBytes(bytes.clone().apply { this[0] = 99 })
        assertEquals(UpdateError.INVALID_DOWNLOAD,
            assertThrows(UpdateException::class.java) { verifyUpdateFile(file, release) }.reason)
        file.delete()
        assertThrows(UpdateException::class.java) { verifyUpdateFile(file, release) }
    }
}
