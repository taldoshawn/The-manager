package dev.themanager.app.core.shell

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun InputStream.readLimited(limit: Int): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw IllegalStateException("A saída do processo excedeu o limite seguro.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
