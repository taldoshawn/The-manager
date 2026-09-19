package dev.themanager.app

import android.content.Context
import android.os.Environment
import dev.themanager.app.core.adb.AdbController
import dev.themanager.app.core.files.FileGateway
import dev.themanager.app.core.files.LocalFileGateway
import dev.themanager.app.core.files.ShellFileGateway
import dev.themanager.app.core.files.ShizukuFileGateway
import dev.themanager.app.core.model.AccessMode
import dev.themanager.app.core.shell.RootCommandExecutor
import dev.themanager.app.core.shizuku.ShizukuController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AppContainer(private val context: Context) {
    val shizuku = ShizukuController(context)
    val adb = AdbController(context)
    private val rootExecutor = RootCommandExecutor()

    private val localGateway = LocalFileGateway()
    private val shizukuGateway = ShizukuFileGateway(shizuku)
    private val rootGateway = ShellFileGateway(AccessMode.ROOT, rootExecutor)
    private val adbGateway by lazy { ShellFileGateway(AccessMode.ADB, adb.executor()) }

    private val _rootAvailable = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    fun gateway(mode: AccessMode): FileGateway = when (mode) {
        AccessMode.NORMAL -> localGateway
        AccessMode.SHIZUKU -> shizukuGateway
        AccessMode.ROOT -> rootGateway
        AccessMode.ADB -> adbGateway
    }

    suspend fun refreshRootAvailability(): Boolean = rootExecutor.isAvailable().also { _rootAvailable.value = it }

    fun initialPaths(): Pair<String, String> {
        val primary = runCatching { Environment.getExternalStorageDirectory().absolutePath }
            .getOrElse { context.filesDir.absolutePath }
        val downloads = File(primary, Environment.DIRECTORY_DOWNLOADS).takeIf { it.isDirectory }?.absolutePath
            ?: context.getExternalFilesDir(null)?.absolutePath
            ?: context.filesDir.absolutePath
        return primary to downloads
    }
}
