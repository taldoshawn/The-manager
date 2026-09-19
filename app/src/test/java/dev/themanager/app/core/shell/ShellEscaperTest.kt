package dev.themanager.app.core.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShellEscaperTest {
    @Test
    fun `single quote is safely escaped`() {
        assertEquals("'a'\"'\"'b; ${'$'}(id)'", ShellEscaper.quote("a'b; ${'$'}(id)"))
    }

    @Test
    fun `nul is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ShellEscaper.quote("bad\u0000path") }
    }
}
