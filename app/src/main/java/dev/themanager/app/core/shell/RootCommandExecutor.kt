package dev.themanager.app.core.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RootCommandExecutor : CommandExecutor {
    override suspend fun execute(
        script: String,
        stdin: ByteArray?,
        timeoutMillis: Long,
        maxOutputBytes: Int,
    ): CommandResult = withContext(Dispatchers.IO) {
        require(script.length <= 256 * 1024) { "Comando grande demais." }
        val process = ProcessBuilder("su", "-c", script)
            .redirectErrorStream(false)
            .start()
        try {
            coroutineScope {
                val stdout = async(Dispatchers.IO) { process.inputStream.use { it.readLimited(maxOutputBytes) } }
                val stderr = async(Dispatchers.IO) { process.errorStream.use { it.readLimited(512 * 1024) } }
                process.outputStream.use { output ->
                    if (stdin != null) output.write(stdin)
                }
                if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    process.destroy()
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                    throw IllegalStateException("O comando Root excedeu o tempo limite.")
                }
                CommandResult(process.exitValue(), stdout.await(), stderr.await())
            }
        } finally {
            runCatching { process.destroy() }
        }
    }

    suspend fun isAvailable(): Boolean = runCatching {
        val result = execute("id -u", timeoutMillis = 5_000, maxOutputBytes = 128)
        result.isSuccess && result.stdoutText.trim() == "0"
    }.getOrDefault(false)
}
