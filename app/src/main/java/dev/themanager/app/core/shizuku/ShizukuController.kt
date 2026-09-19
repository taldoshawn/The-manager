package dev.themanager.app.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dev.themanager.app.BuildConfig
import dev.themanager.app.privilege.IPrivilegedFileService
import dev.themanager.app.privilege.PrivilegedFileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

data class ShizukuState(
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val connected: Boolean = false,
    val uid: Int? = null,
    val message: String = "Shizuku não detectado",
)

class ShizukuController(private val context: Context) {
    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val service = MutableStateFlow<IPrivilegedFileService?>(null)
    private var registered = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshAndBindIfAllowed() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service.value = null
        _state.value = ShizukuState(message = "O serviço Shizuku foi encerrado")
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) bind()
            else _state.value = currentStatus("Permissão Shizuku negada")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!binder.pingBinder()) {
                _state.value = currentStatus("Binder privilegiado inválido")
                return
            }
            val remote = IPrivilegedFileService.Stub.asInterface(binder)
            service.value = remote
            val uid = runCatching { remote.uid() }.getOrNull()
            _state.value = currentStatus(if (uid == 0) "Shizuku conectado como root" else "Shizuku conectado como shell")
                .copy(connected = true, uid = uid)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service.value = null
            _state.value = currentStatus("Serviço privilegiado desconectado")
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, PrivilegedFileService::class.java.name))
            .daemon(false)
            .processNameSuffix("file_service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("the_manager_files_v1")
    }

    fun start() {
        if (registered) return
        registered = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshAndBindIfAllowed()
    }

    fun stop() {
        if (!registered) return
        registered = false
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun requestAccess() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            _state.value = ShizukuState(message = "Inicie o Shizuku antes de conectar")
            return
        }
        when {
            Shizuku.isPreV11() -> _state.value = currentStatus("Versão do Shizuku incompatível")
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bind()
            Shizuku.shouldShowRequestPermissionRationale() ->
                _state.value = currentStatus("Reative a permissão dentro do Shizuku")
            else -> Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    suspend fun requireService(): IPrivilegedFileService =
        withTimeout(8_000) { service.filterNotNull().first() }

    private fun refreshAndBindIfAllowed() {
        val status = currentStatus()
        _state.value = status
        if (status.permissionGranted) bind()
    }

    private fun bind() {
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure { _state.value = currentStatus("Falha ao iniciar serviço Shizuku") }
    }

    private fun currentStatus(messageOverride: String? = null): ShizukuState {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = alive && runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return ShizukuState(
            binderAlive = alive,
            permissionGranted = granted,
            connected = service.value != null,
            uid = _state.value.uid,
            message = messageOverride ?: when {
                !alive -> "Shizuku não detectado"
                !granted -> "Shizuku aguarda permissão"
                service.value == null -> "Conectando ao Shizuku…"
                else -> _state.value.message
            },
        )
    }

    private companion object {
        const val REQUEST_CODE = 4107
    }
}
