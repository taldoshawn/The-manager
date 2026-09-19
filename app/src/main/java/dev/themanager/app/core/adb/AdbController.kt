package dev.themanager.app.core.adb

import android.content.Context
import io.github.muntashirakon.adb.android.AndroidUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AdbState(
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String = "Desconectado",
)

class AdbController(private val context: Context) {
    private val manager by lazy { SecureAdbConnectionManager.get(context) }
    private val _state = MutableStateFlow(AdbState())
    val state: StateFlow<AdbState> = _state.asStateFlow()

    suspend fun pair(pairingPort: Int, code: String, host: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (pairingPort !in 1..65535 || !code.matches(Regex("\\d{6}"))) {
            return@withContext Result.failure(IllegalArgumentException("Porta ou código de pareamento inválido."))
        }
        _state.value = AdbState(busy = true, message = "Pareando…")
        runCatching {
            val resolvedHost = host?.takeIf { it.isNotBlank() } ?: AndroidUtils.getHostIpAddress(context)
            check(manager.pair(resolvedHost, pairingPort, code)) { "O Android recusou o pareamento." }
        }.onSuccess {
            _state.value = AdbState(message = "Pareado. Conecte usando a porta de depuração.")
        }.onFailure {
            _state.value = AdbState(message = it.safeMessage("Falha no pareamento"))
        }
    }

    suspend fun connect(connectPort: Int? = null, host: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = AdbState(busy = true, message = "Conectando…")
        runCatching {
            if (manager.isConnected) return@runCatching
            val connected = if (connectPort != null) {
                require(connectPort in 1..65535) { "Porta de conexão inválida." }
                val resolvedHost = host?.takeIf { it.isNotBlank() } ?: AndroidUtils.getHostIpAddress(context)
                manager.connect(resolvedHost, connectPort)
            } else {
                manager.autoConnect(context, 8_000)
            }
            check(connected || manager.isConnected) { "ADB não encontrado. Confira a porta de conexão." }
        }.onSuccess {
            _state.value = AdbState(connected = true, message = "ADB Wi-Fi conectado")
        }.onFailure {
            _state.value = AdbState(message = it.safeMessage("Falha na conexão"))
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { manager.disconnect() }
        _state.value = AdbState()
    }

    fun executor(): AdbCommandExecutor = AdbCommandExecutor(manager)

    private fun Throwable.safeMessage(prefix: String): String =
        "$prefix: ${message?.take(160) ?: javaClass.simpleName}"
}
