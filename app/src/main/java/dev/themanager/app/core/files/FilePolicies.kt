package dev.themanager.app.core.files

import java.nio.file.Path
import java.nio.file.Paths

object FileNamePolicy {
    fun requireValid(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "O nome não pode ficar vazio." }
        require(normalized != "." && normalized != "..") { "Nome reservado." }
        require('/' !in normalized && '\u0000' !in normalized) { "O nome contém caracteres inválidos." }
        require(normalized.toByteArray(Charsets.UTF_8).size <= 255) { "O nome é longo demais." }
        return normalized
    }
}

object DestructivePathPolicy {
    private val protectedRoots = setOf(
        "/",
        "/system",
        "/system_ext",
        "/vendor",
        "/product",
        "/data",
        "/storage",
        "/sdcard",
        "/storage/emulated",
        "/storage/emulated/0",
    )

    fun requireSafe(path: String): String {
        require('\u0000' !in path) { "Caminho inválido." }
        val normalized = normalize(path)
        require(normalized !in protectedRoots) { "A raiz crítica não pode ser apagada ou movida." }
        require(normalized.length > 1) { "Caminho destrutivo recusado." }
        return normalized
    }

    private fun normalize(path: String): String =
        Paths.get(path).toAbsolutePath().normalize().toString().removeSuffix("/").ifEmpty { "/" }
}

object DestinationPolicy {
    fun target(source: String, destinationDirectory: String): Path {
        val sourcePath = Paths.get(source).toAbsolutePath().normalize()
        val destinationPath = Paths.get(destinationDirectory).toAbsolutePath().normalize()
        val target = destinationPath.resolve(sourcePath.fileName).normalize()
        require(target != sourcePath) { "Origem e destino são iguais." }
        require(!target.startsWith(sourcePath)) { "O destino não pode ficar dentro da origem." }
        return target
    }
}
