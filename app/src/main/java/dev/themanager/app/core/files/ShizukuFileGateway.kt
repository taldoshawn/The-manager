package dev.themanager.app.core.files

import android.os.ParcelFileDescriptor
import dev.themanager.app.core.model.AccessMode
import dev.themanager.app.core.model.FileItem
import dev.themanager.app.core.shizuku.ShizukuController
import dev.themanager.app.core.shell.readLimited
import dev.themanager.app.privilege.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

class ShizukuFileGateway(
    private val controller: ShizukuController,
) : FileGateway {
    override val mode: AccessMode = AccessMode.SHIZUKU

    override suspend fun list(path: String): List<FileItem> = remoteCall("Não foi possível listar a pasta") {
        val output = mutableListOf<FileItem>()
        var offset = 0
        while (true) {
            val page = list(normalized(path), offset, PAGE_SIZE)
            output += page.map { it.toItem() }
            if (page.size < PAGE_SIZE) break
            offset += page.size
            if (offset >= MAX_DIRECTORY_ENTRIES) throw FileOperationException("A pasta excede o limite de segurança.")
        }
        output
    }

    override suspend fun stat(path: String): FileItem = remoteCall("Não foi possível ler as propriedades") {
        stat(normalized(path)).toItem()
    }

    override suspend fun createDirectory(parent: String, name: String): FileItem {
        val target = child(parent, name)
        remoteCall("Não foi possível criar a pasta") { check(createDirectory(target)) }
        return stat(target)
    }

    override suspend fun createFile(parent: String, name: String): FileItem {
        val target = child(parent, name)
        remoteCall("Não foi possível criar o arquivo") { check(createFile(target)) }
        return stat(target)
    }

    override suspend fun rename(source: String, newName: String): FileItem {
        val safeSource = DestructivePathPolicy.requireSafe(source)
        val target = child(File(safeSource).parent ?: "/", newName)
        remoteCall("Não foi possível renomear o item") { check(move(safeSource, target)) }
        return stat(target)
    }

    override suspend fun copy(source: String, destinationDirectory: String): FileItem {
        val safeSource = normalized(source)
        val target = DestinationPolicy.target(safeSource, destinationDirectory).toString()
        remoteCall("A cópia falhou") { check(copy(safeSource, target)) }
        return stat(target)
    }

    override suspend fun move(source: String, destinationDirectory: String): FileItem {
        val safeSource = DestructivePathPolicy.requireSafe(source)
        val target = DestinationPolicy.target(safeSource, destinationDirectory).toString()
        remoteCall("A movimentação falhou") { check(move(safeSource, target)) }
        return stat(target)
    }

    override suspend fun delete(path: String) {
        val target = DestructivePathPolicy.requireSafe(path)
        remoteCall("Não foi possível excluir o item") { check(delete(target)) }
    }

    override suspend fun readBytes(path: String, maxBytes: Int): ByteArray {
        require(maxBytes in 1..32 * 1024 * 1024) { "Limite de leitura inválido." }
        val metadata = stat(path)
        if (metadata.size > maxBytes) throw FileOperationException("O arquivo excede o limite de ${maxBytes / 1024} KB.")
        return remoteCall("Não foi possível ler o arquivo") {
            ParcelFileDescriptor.AutoCloseInputStream(openRead(normalized(path))).use { input ->
                try {
                    input.readLimited(maxBytes)
                } catch (error: IllegalStateException) {
                    throw FileOperationException("O arquivo excedeu o limite durante a leitura.", error)
                }
            }
        }
    }

    override suspend fun writeBytesAtomically(path: String, data: ByteArray) {
        if (data.size > 32 * 1024 * 1024) throw FileOperationException("A edição direta está limitada a 32 MB.")
        remoteCall("Não foi possível salvar o arquivo") {
            val token = beginAtomicWrite(normalized(path))
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(openPendingWrite(token)).use { output ->
                    output.write(data)
                    output.flush()
                }
                check(commitAtomicWrite(token))
            } catch (error: Throwable) {
                runCatching { abortAtomicWrite(token) }
                throw error
            }
        }
    }

    override suspend fun sha256(path: String): String = remoteCall("Não foi possível calcular o SHA-256") {
        sha256(normalized(path))
    }

    private suspend fun <T> remoteCall(message: String, block: dev.themanager.app.privilege.IPrivilegedFileService.() -> T): T =
        withContext(Dispatchers.IO) {
            try {
                controller.requireService().block()
            } catch (error: FileOperationException) {
                throw error
            } catch (error: Throwable) {
                throw FileOperationException("$message: ${error.message?.take(180) ?: error.javaClass.simpleName}", error)
            }
        }

    private fun RemoteFile.toItem() = FileItem(
        path = path,
        name = name,
        isDirectory = directory,
        isSymlink = symlink,
        size = size,
        modifiedAt = modifiedAt,
        permissions = permissions,
        readable = readable,
        writable = writable,
        hidden = hidden,
    )

    private fun child(parent: String, name: String): String {
        val parentPath = Paths.get(parent).toAbsolutePath().normalize()
        val target = parentPath.resolve(FileNamePolicy.requireValid(name)).normalize()
        require(target.parent == parentPath) { "O caminho escapou da pasta atual." }
        return target.toString()
    }

    private fun normalized(path: String): String {
        require(path.isNotBlank() && '\u0000' !in path) { "Caminho inválido." }
        return Paths.get(path).toAbsolutePath().normalize().toString()
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_DIRECTORY_ENTRIES = 20_000
    }
}
