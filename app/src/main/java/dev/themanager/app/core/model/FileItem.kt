package dev.themanager.app.core.model

import java.io.File

data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val size: Long = 0L,
    val modifiedAt: Long = 0L,
    val permissions: String = "",
    val readable: Boolean = true,
    val writable: Boolean = false,
    val hidden: Boolean = false,
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    val parentPath: String
        get() = File(path).parent ?: "/"
}
