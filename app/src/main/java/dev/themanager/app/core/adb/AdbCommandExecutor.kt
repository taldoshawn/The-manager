package dev.themanager.app.core.adb

import dev.themanager.app.core.shell.CommandExecutor
import dev.themanager.app.core.shell.CommandResult
import dev.themanager.app.core.shell.readLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AdbCommandExecutor(
    private val manager: SecureAdbConnectionManager,
) : CommandExecutor {
    override suspend fun execute(
        script: String,
        stdin: ByteArray?,
        timeoutMillis: Long,
        maxOutputBytes: Int,
    ): CommandResult = withContext(Dispatchers.IO) {
        check(manager.isConnected) { "ADB Wi-Fi não está conectado." }
        require(script.length <= 256 * 1024) { "Comando grande demais." }
        val marker = "__THE_MANAGER_EXIT_${UUID.randomUUID().toString().replace("-", "")}__"
        val wrapped = "(\n$script\n); tm_code=${'$'}?; printf '\\n$marker%s\\n' \"${'$'}tm_code\""
        val stream = manager.openStream("shell:$wrapped")
        val readerPool = Executors.newSingleThreadExecutor()
        try {
            stream.openOutputStream().use { output ->
                if (stdin != null) output.write(stdin)
                output.flush()
            }
            val future = readerPool.submit<ByteArray> {
                stream.openInputStream().use { it.readLimited(maxOutputBytes + 256) }
            }
            val raw = try {
                future.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                runCatching { stream.close() }
                future.cancel(true)
                throw IllegalStateException("O comando ADB excedeu o tempo limite.")
            }
            parse(raw, marker)
        } finally {
            runCatching { stream.close() }
            readerPool.shutdownNow()
        }
    }

    private fun parse(raw: ByteArray, marker: String): CommandResult {
        val text = raw.toString(Charsets.UTF_8)
        val markerIndex = text.lastIndexOf(marker)
        if (markerIndex < 0) throw IllegalStateException("ADB encerrou sem informar o resultado do comando.")
        val exitStart = markerIndex + marker.length
        val exitCode = text.substring(exitStart).trim().lineSequence().firstOrNull()?.toIntOrNull()
            ?: throw IllegalStateException("Código de saída ADB inválido.")
        val output = text.substring(0, markerIndex).trimEnd('\n', '\r').toByteArray(Charsets.UTF_8)
        return CommandResult(exitCode = exitCode, stdout = output)
    }
}
