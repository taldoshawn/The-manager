package dev.themanager.app.core.model

enum class AccessMode(val title: String, val description: String) {
    NORMAL("Normal", "Armazenamento permitido pelo Android"),
    SHIZUKU("Shizuku", "Serviço com identidade shell ou root"),
    ADB("ADB Wi-Fi", "Depuração sem fio pareada"),
    ROOT("Root", "Acesso via gerenciador su"),
}
