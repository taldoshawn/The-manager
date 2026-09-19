package dev.themanager.app.core.files

import dev.themanager.app.core.model.AccessMode
import dev.themanager.app.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class LocalFileGateway : FileGateway {
    override val mode: AccessMode = AccessMode.NORMAL

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val directory = Paths.get(path).toAbsolutePath().normalize()
        if (!Files.isDirectory(directory)) throw FileOperationException("A pasta não existe ou não está acessível.")
        try {
            Files.list(directory).use { stream ->
                stream.map(::toItem).sorted(fileComparator).collect(Collectors.toList())
            }
        } catch (error: Exception) {
            throw FileOperationException("Não foi possível abrir ${directory}.", error)
        }
    }

    override suspend fun stat(path: String): FileItem = withContext(Dispatchers.IO) {
        val target = Paths.get(path).toAbsolutePath().normalize()
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw FileOperationException("O arquivo não existe.")
        toItem(target)
    }

    override suspend fun createDirectory(parent: String, name: String): FileItem = withContext(Dispatchers.IO) {
        val target = safeChild(parent, name)
        try {
            Files.createDirectory(target)
            toItem(target)
        } catch (error: Exception) {
            throw FileOperationException("Não foi possível criar a pasta.", error)
        }
    }

    override suspend fun createFile(parent: String, name: String): FileItem = withContext(Dispatchers.IO) {
        val target = safeChild(parent, name)
        try {
            Files.createFile(target)
            toItem(target)
        } catch (error: Exception) {
            throw FileOperationException("Não foi possível criar o arquivo.", error)
        }
    }

    override suspend fun rename(source: String, newName: String): FileItem = withContext(Dispatchers.IO) {
        val sourcePath = Paths.get(source).toAbsolutePath().normalize()
        DestructivePathPolicy.requireSafe(sourcePath.toString())
        val target = sourcePath.parent.resolve(FileNamePolicy.requireValid(newName)).normalize()
        try {
            Files.move(sourcePath, target)
            toItem(target)
        } catch (error: Exception) {
            throw FileOperationException("Não foi possível renomear o item.", error)
        }
    }

    override suspend fun copy(source: String, destinationDirectory: String): FileItem = withContext(Dispatchers.IO) {
        val sourcePath = Paths.get(source).toAbsolutePath().normalize()
        val target = DestinationPolicy.target(source, destinationDirectory)
        if (target.exists(LinkOption.NOFOLLOW_LINKS)) throw FileOperationException("Já existe um item com esse nome no destino.")
        try {
            copyTree(sourcePath, target)
            toItem(target)
        } catch (error: Exception) {
            runCatching { deleteTree(target) }
            throw FileOperationException("A cópia não foi concluída.", error)
        }
    }

    override suspend fun move(source: String, destinationDirectory: String): FileItem = withContext(Dispatchers.IO) {
        val sourcePath = Paths.get(DestructivePathPolicy.requireSafe(source)).toAbsolutePath().normalize()
        val target = DestinationPolicy.target(source, destinationDirectory)
        if (target.exists(LinkOption.NOFOLLOW_LINKS)) throw FileOperationException("Já existe um item com esse nome no destino.")
        try {
            try {
                Files.move(sourcePath, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                copyTree(sourcePath, target)
                deleteTree(sourcePath)
            }
            toItem(target)
        } catch (error: Exception) {
            throw FileOperationException("A movimentação não foi concluída.", error)
        }
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val target = Paths.get(DestructivePathPolicy.requireSafe(path)).toAbsolutePath().normalize()
        try {
            deleteTree(target)
        } catch (error: Exception) {
            throw FileOperationException("Não foi possível excluir o item.", error)
        }
    }

    override suspend fun readBytes(path: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(maxBytes in 1..32 * 1024 * 1024) { "Limite de leitura inválido." }
        val target = Paths.get(path).toAbsolutePath().normalize()
        val size = Files.size(target)
        if (size > maxBytes) throw FileOperationException("O arquivo excede o limite de ${maxBytes / 1024} KB.")
        Files.readAllBytes(target)
    }

    override suspend fun writeBytesAtomically(path: String, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        val target = Paths.get(path).toAbsolutePath().normalize()
        val parent = target.parent ?: throw FileOperationException("Destino inválido.")
        val temp = Files.createTempFile(parent, ".themanager-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { output ->
                output.write(data)
                output.fd.sync()
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            Files.deleteIfExists(temp)
            throw FileOperationException("Não foi possível salvar o arquivo.", error)
        }
        Unit
    }

    override suspend fun sha256(path: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(Paths.get(path)).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeChild(parent: String, name: String): Path {
        val parentPath = Paths.get(parent).toAbsolutePath().normalize()
        val child = parentPath.resolve(FileNamePolicy.requireValid(name)).normalize()
        require(child.parent == parentPath) { "O caminho escapou da pasta atual." }
        return child
    }

    private fun toItem(path: Path): FileItem {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val file = path.toFile()
        return FileItem(
            path = path.toAbsolutePath().normalize().toString(),
            name = path.fileName?.toString() ?: path.toString(),
            isDirectory = attributes.isDirectory,
            isSymlink = attributes.isSymbolicLink,
            size = if (attributes.isDirectory) 0 else attributes.size(),
            modifiedAt = attributes.lastModifiedTime().toMillis(),
            readable = Files.isReadable(path),
            writable = Files.isWritable(path),
            hidden = runCatching { Files.isHidden(path) }.getOrDefault(file.name.startsWith('.')),
        )
    }

    private fun copyTree(source: Path, target: Path) {
        if (!source.isDirectory(LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(source, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES)
            return
        }
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectory(target.resolve(source.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.copy(
                    file,
                    target.resolve(source.relativize(file)),
                    LinkOption.NOFOLLOW_LINKS,
                    StandardCopyOption.COPY_ATTRIBUTES,
                )
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun deleteTree(target: Path) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private companion object {
        val fileComparator = compareBy<FileItem>({ !it.isDirectory }, { it.name.lowercase() }, { it.name })
    }
}
