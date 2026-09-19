package dev.themanager.app.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FilePoliciesTest {
    @Test
    fun `valid file name is preserved`() {
        assertEquals("arquivo.txt", FileNamePolicy.requireValid(" arquivo.txt "))
    }

    @Test
    fun `traversal names are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FileNamePolicy.requireValid("../segredo") }
        assertThrows(IllegalArgumentException::class.java) { FileNamePolicy.requireValid("..") }
        assertThrows(IllegalArgumentException::class.java) { FileNamePolicy.requireValid("a/b") }
    }

    @Test
    fun `critical roots are protected`() {
        listOf("/", "/system", "/data", "/storage/emulated/0").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { DestructivePathPolicy.requireSafe(path) }
        }
    }

    @Test
    fun `copying into source is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DestinationPolicy.target("/tmp/source", "/tmp/source/child")
        }
    }
}
