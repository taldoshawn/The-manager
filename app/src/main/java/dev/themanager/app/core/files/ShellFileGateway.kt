package dev.themanager.app.core.files

import android.util.Base64
import dev.themanager.app.core.model.AccessMode
import dev.themanager.app.core.model.FileItem
import dev.themanager.app.core.shell.CommandExecutor
import dev.themanager.app.core.shell.CommandResult
import dev.themanager.app.core.shell.ShellEscaper.quote
import java.io.File
import java.nio.file.Paths

class ShellFileGateway(
    override val mode: AccessMode,
    private val executor: CommandExecutor,
) : FileGateway {
    init {
        require(mode == AccessMode.ROOT || mode == AccessMode.ADB)
    }

    override suspend fun list(path: String): List<FileItem> {
        val target = normalized(path)
        val script = """
            target=${quote(target)}
            [ -d "${'$'}target" ] || exit 20
            find "${'$'}target" -mindepth 1 -maxdepth 1 -print0 | while IFS= read -r -d '' item; do
              if [ -L "${'$'}item" ]; then kind=l; elif [ -d "${'$'}item" ]; then kind=d; else kind=f; fi
              size=${'$'}(stat -c %s "${'$'}item" 2>/dev/null || printf 0)
              modified=${'$'}(stat -c %Y "${'$'}item" 2>/dev/null || printf 0)
              perms=${'$'}(stat -c %a "${'$'}item" 2>/dev/null || printf '')
              [ -r "${'$'}item" ] && readable=1 || readable=0
              [ -w "${'$'}item" ] && writable=1 || writable=0
              encoded=${'$'}(printf %s "${'$'}item" | base64 | tr -d '\n\r')
              printf '%s|%s|%s|%s|%s|%s|%s\n' "${'$'}kind" "${'$'}size" "${'$'}modified" "${'$'}perms" "${'$'}readable" "${'$'}writable" "${'$'}encoded"
            done
        """.trimIndent()
        val result = executor.execute(script, timeoutMillis = 60_000)
        result.requireSuccess("Não foi possível listar a pasta")
        return result.stdoutText.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::parseListLine)
            .sortedWith(compareBy<FileItem>({ !it.isDirectory }, { it.name.lowercase() }, { it.name }))
            .toList()
    }

    override suspend fun stat(path: String): FileItem {
        val normalized = normalized(path)
        return list(File(normalized).parent ?: "/").firstOrNull { it.path == normalized }
            ?: throw FileOperationException("O item não existe ou não está acessível.")
    }

    override suspend fun createDirectory(parent: String, name: String): FileItem {
        val target = child(parent, name)
        executor.execute("mkdir -- ${quote(target)}").requireSuccess("Não foi possível criar a pasta")
        return stat(target)
    }

    override suspend fun createFile(parent: String, name: String): FileItem {
        val target = child(parent, name)
        val script = "[ ! -e ${quote(target)} ] || exit 17; : > ${quote(target)}"
        executor.execute(script).requireSuccess("Não foi possível criar o arquivo")
        return stat(target)
    }

    override suspend fun rename(source: String, newName: String): FileItem {
        val safeSource = DestructivePathPolicy.requireSafe(source)
        val target = child(File(safeSource).parent ?: "/", newName)
        val script = "[ ! -e ${quote(target)} ] || exit 17; mv -- ${quote(safeSource)} ${quote(target)}"
        executor.execute(script, timeoutMillis = 60_000).requireSuccess("Não foi possível renomear o item")
        return stat(target)
    }

    override suspend fun copy(source: String, destinationDirectory: String): FileItem {
        val safeSource = normalized(source)
        val target = DestinationPolicy.target(safeSource, destinationDirectory).toString()
        val script = """
            [ ! -e ${quote(target)} ] || exit 17
            if [ -d ${quote(safeSource)} ]; then
              cp -R -p -- ${quote(safeSource)} ${quote(target)}
            else
              cp -p -- ${quote(safeSource)} ${quote(target)}
            fi
        """.trimIndent()
        executor.execute(script, timeoutMillis = 10 * 60_000).requireSuccess("A cópia falhou")
        return stat(target)
    }

    override suspend fun move(source: String, destinationDirectory: String): FileItem {
        val safeSource = DestructivePathPolicy.requireSafe(source)
        val target = DestinationPolicy.target(safeSource, destinationDirectory).toString()
        val script = "[ ! -e ${quote(target)} ] || exit 17; mv -- ${quote(safeSource)} ${quote(target)}"
        executor.execute(script, timeoutMillis = 10 * 60_000).requireSuccess("A movimentação falhou")
        return stat(target)
    }

    override suspend fun delete(path: String) {
        val target = DestructivePathPolicy.requireSafe(path)
        executor.execute("rm -rf -- ${quote(target)}", timeoutMillis = 10 * 60_000)
            .requireSuccess("Não foi possível excluir o item")
    }

    override suspend fun readBytes(path: String, maxBytes: Int): ByteArray {
        require(maxBytes in 1..32 * 1024 * 1024) { "Limite de leitura inválido." }
        val target = normalized(path)
        val sizeResult = executor.execute("stat -c %s -- ${quote(target)}", maxOutputBytes = 128)
        sizeResult.requireSuccess("Não foi possível verificar o tamanho")
        val size = sizeResult.stdoutText.trim().toLongOrNull() ?: throw FileOperationException("Tamanho inválido.")
        if (size > maxBytes) throw FileOperationException("O arquivo excede o limite de ${maxBytes / 1024} KB.")
        val result = executor.execute("base64 -- ${quote(target)} | tr -d '\\n\\r'", maxOutputBytes = maxBytes * 2)
        result.requireSuccess("Não foi possível ler o arquivo")
        return try {
            Base64.decode(result.stdoutText.trim(), Base64.DEFAULT)
        } catch (error: IllegalArgumentException) {
            throw FileOperationException("O transporte retornou dados inválidos.", error)
        }
    }

    override suspend fun writeBytesAtomically(path: String, data: ByteArray) {
        if (data.size > 8 * 1024 * 1024) throw FileOperationException("A edição direta está limitada a 8 MB.")
        val target = normalized(path)
        val temporary = "$target.themanager.tmp"
        val encoded = Base64.encode(data, Base64.NO_WRAP)
        val script = "dd bs=1 count=${encoded.size} 2>/dev/null | base64 -d > ${quote(temporary)} && mv -f -- ${quote(temporary)} ${quote(target)}"
        val result = executor.execute(script, stdin = encoded, timeoutMillis = 120_000, maxOutputBytes = 64 * 1024)
        if (!result.isSuccess) executor.execute("rm -f -- ${quote(temporary)}", timeoutMillis = 5_000)
        result.requireSuccess("Não foi possível salvar o arquivo")
    }

    override suspend fun sha256(path: String): String {
        val result = executor.execute("sha256sum -- ${quote(normalized(path))} | cut -d ' ' -f 1", timeoutMillis = 10 * 60_000)
        result.requireSuccess("Não foi possível calcular o SHA-256")
        return result.stdoutText.trim().also {
            if (!it.matches(Regex("[a-fA-F0-9]{64}"))) throw FileOperationException("Hash inválido retornado pelo sistema.")
        }.lowercase()
    }

    private fun parseListLine(line: String): FileItem? {
        val fields = line.split('|', limit = 7)
        if (fields.size != 7) return null
        val path = runCatching { String(Base64.decode(fields[6], Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() ?: return null
        val name = File(path).name.ifEmpty { path }
        return FileItem(
            path = normalized(path),
            name = name,
            isDirectory = fields[0] == "d",
            isSymlink = fields[0] == "l",
            size = fields[1].toLongOrNull() ?: 0L,
            modifiedAt = (fields[2].toLongOrNull() ?: 0L) * 1_000L,
            permissions = fields[3],
            readable = fields[4] == "1",
            writable = fields[5] == "1",
            hidden = name.startsWith('.'),
        )
    }

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

    private fun CommandResult.requireSuccess(action: String) {
        if (!isSuccess) {
            val detail = stderrText.trim().take(240).ifBlank { "código $exitCode" }
            throw FileOperationException("$action: $detail")
        }
    }
}
