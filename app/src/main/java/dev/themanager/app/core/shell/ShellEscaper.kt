package dev.themanager.app.core.shell

object ShellEscaper {
    fun quote(value: String): String {
        require('\u0000' !in value) { "O valor contém byte nulo." }
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
