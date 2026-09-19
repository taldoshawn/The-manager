package dev.themanager.app.core.files

import dev.themanager.app.core.model.AccessMode
import dev.themanager.app.core.model.FileItem

interface FileGateway {
    val mode: AccessMode

    suspend fun list(path: String): List<FileItem>
    suspend fun stat(path: String): FileItem
    suspend fun createDirectory(parent: String, name: String): FileItem
    suspend fun createFile(parent: String, name: String): FileItem
    suspend fun rename(source: String, newName: String): FileItem
    suspend fun copy(source: String, destinationDirectory: String): FileItem
    suspend fun move(source: String, destinationDirectory: String): FileItem
    suspend fun delete(path: String)
    suspend fun readBytes(path: String, maxBytes: Int = DEFAULT_READ_LIMIT): ByteArray
    suspend fun writeBytesAtomically(path: String, data: ByteArray)
    suspend fun sha256(path: String): String

    companion object {
        const val DEFAULT_READ_LIMIT = 2 * 1024 * 1024
    }
}

class FileOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)
