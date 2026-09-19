package dev.themanager.app.core.shell

data class CommandResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: ByteArray = byteArrayOf(),
) {
    val stdoutText: String get() = stdout.toString(Charsets.UTF_8)
    val stderrText: String get() = stderr.toString(Charsets.UTF_8)
    val isSuccess: Boolean get() = exitCode == 0
}

interface CommandExecutor {
    suspend fun execute(
        script: String,
        stdin: ByteArray? = null,
        timeoutMillis: Long = 30_000,
        maxOutputBytes: Int = 8 * 1024 * 1024,
    ): CommandResult
}
