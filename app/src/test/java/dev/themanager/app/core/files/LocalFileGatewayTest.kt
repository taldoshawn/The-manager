package dev.themanager.app.core.files

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LocalFileGatewayTest {
    private val gateway = LocalFileGateway()

    @Test
    fun `create copy move and atomic write work`() = runTest {
        val root = Files.createTempDirectory("the-manager-test")
        try {
            val left = Files.createDirectory(root.resolve("left"))
            val right = Files.createDirectory(root.resolve("right"))
            val archive = Files.createDirectory(root.resolve("archive"))
            val file = gateway.createFile(left.toString(), "hello.txt")
            gateway.writeBytesAtomically(file.path, "olá".toByteArray())
            assertEquals("olá", gateway.readBytes(file.path).toString(Charsets.UTF_8))

            val copied = gateway.copy(file.path, right.toString())
            assertTrue(Files.exists(right.resolve("hello.txt")))
            assertEquals(gateway.sha256(file.path), gateway.sha256(copied.path))

            val moved = gateway.move(copied.path, archive.toString())
            assertEquals(archive.resolve("hello.txt").toString(), moved.path)
            assertTrue(Files.exists(archive.resolve("hello.txt")))
            assertFalse(Files.exists(right.resolve("hello.txt")))
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
