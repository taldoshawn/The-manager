package dev.themanager.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.themanager.app.AppContainer
import dev.themanager.app.TheManagerApplication
import dev.themanager.app.core.files.FileGateway
import dev.themanager.app.core.files.FileOperationException
import dev.themanager.app.core.model.AccessMode
import dev.themanager.app.core.model.FileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

enum class PaneId { LEFT, RIGHT }

enum class FileSort(val title: String) {
    NAME("Nome"), DATE("Data"), SIZE("Tamanho"), TYPE("Tipo")
}

data class PaneState(
    val path: String,
    val items: List<FileItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val history: List<String> = listOf(path),
    val historyIndex: Int = 0,
    val sort: FileSort = FileSort.NAME,
    val descending: Boolean = false,
    val showHidden: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val visibleItems: List<FileItem>
        get() {
            val visible = if (showHidden) items else items.filterNot { it.hidden }
            val comparator = when (sort) {
                FileSort.NAME -> compareBy<FileItem>({ !it.isDirectory }, { it.name.lowercase() }, { it.name })
                FileSort.DATE -> compareBy<FileItem>({ !it.isDirectory }, { it.modifiedAt }, { it.name.lowercase() })
                FileSort.SIZE -> compareBy<FileItem>({ !it.isDirectory }, { it.size }, { it.name.lowercase() })
                FileSort.TYPE -> compareBy<FileItem>({ !it.isDirectory }, { it.extension }, { it.name.lowercase() })
            }
            return visible.sortedWith(if (descending) comparator.reversed() else comparator)
        }
}

sealed interface DocumentState {
    val path: String

    data class Loading(override val path: String) : DocumentState
    data class Text(
        override val path: String,
        val content: String,
        val original: String,
        val saving: Boolean = false,
        val error: String? = null,
    ) : DocumentState {
        val dirty: Boolean get() = content != original
    }
    data class Hex(override val path: String, val bytes: ByteArray) : DocumentState
}

data class PropertiesState(
    val item: FileItem,
    val sha256: String? = null,
    val loadingHash: Boolean = false,
    val error: String? = null,
)

data class FileManagerUiState(
    val mode: AccessMode = AccessMode.NORMAL,
    val activePane: PaneId = PaneId.LEFT,
    val left: PaneState,
    val right: PaneState,
    val operationRunning: Boolean = false,
    val operationLabel: String? = null,
    val message: String? = null,
    val document: DocumentState? = null,
    val properties: PropertiesState? = null,
) {
    fun pane(id: PaneId): PaneState = if (id == PaneId.LEFT) left else right
    val active: PaneState get() = pane(activePane)
    val passive: PaneState get() = pane(if (activePane == PaneId.LEFT) PaneId.RIGHT else PaneId.LEFT)
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val container: AppContainer = (application as TheManagerApplication).container
    private var gateway: FileGateway = container.gateway(AccessMode.NORMAL)
    private val loadJobs = mutableMapOf<PaneId, Job>()
    private val initialPaths = container.initialPaths()

    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            left = PaneState(initialPaths.first),
            right = PaneState(initialPaths.second),
        ),
    )
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    val shizukuState = container.shizuku.state
    val adbState = container.adb.state
    val rootAvailable = container.rootAvailable

    init {
        refresh(PaneId.LEFT)
        refresh(PaneId.RIGHT)
        viewModelScope.launch { container.refreshRootAvailability() }
    }

    fun setActivePane(id: PaneId) {
        _uiState.update { it.copy(activePane = id) }
    }

    fun refresh(id: PaneId = _uiState.value.activePane) {
        val requestedPath = _uiState.value.pane(id).path
        loadJobs[id]?.cancel()
        loadJobs[id] = viewModelScope.launch {
            updatePane(id) { it.copy(loading = true, error = null) }
            try {
                val items = gateway.list(requestedPath)
                if (_uiState.value.pane(id).path == requestedPath) {
                    updatePane(id) { it.copy(items = items, selected = emptySet(), loading = false) }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (_uiState.value.pane(id).path == requestedPath) {
                    updatePane(id) { it.copy(loading = false, error = error.userMessage()) }
                }
            }
        }
    }

    fun open(item: FileItem, id: PaneId = _uiState.value.activePane, forceHex: Boolean = false) {
        setActivePane(id)
        if (item.isDirectory) navigate(id, item.path) else openDocument(item, forceHex)
    }

    fun navigate(id: PaneId, path: String, recordHistory: Boolean = true) {
        updatePane(id) { pane ->
            if (!recordHistory) return@updatePane pane.copy(path = path, selected = emptySet())
            val history = pane.history.take(pane.historyIndex + 1) + path
            pane.copy(path = path, history = history.takeLast(MAX_HISTORY), historyIndex = history.takeLast(MAX_HISTORY).lastIndex, selected = emptySet())
        }
        refresh(id)
    }

    fun goUp(id: PaneId = _uiState.value.activePane) {
        val pane = _uiState.value.pane(id)
        val parent = File(pane.path).parent ?: return
        navigate(id, parent)
    }

    fun goBack(id: PaneId = _uiState.value.activePane) {
        val pane = _uiState.value.pane(id)
        if (pane.historyIndex <= 0) return
        val index = pane.historyIndex - 1
        updatePane(id) { it.copy(path = it.history[index], historyIndex = index, selected = emptySet()) }
        refresh(id)
    }

    fun goForward(id: PaneId = _uiState.value.activePane) {
        val pane = _uiState.value.pane(id)
        if (pane.historyIndex >= pane.history.lastIndex) return
        val index = pane.historyIndex + 1
        updatePane(id) { it.copy(path = it.history[index], historyIndex = index, selected = emptySet()) }
        refresh(id)
    }

    fun toggleSelection(id: PaneId, item: FileItem) {
        setActivePane(id)
        updatePane(id) { pane ->
            val selected = pane.selected.toMutableSet()
            if (!selected.add(item.path)) selected.remove(item.path)
            pane.copy(selected = selected)
        }
    }

    fun selectAll(id: PaneId = _uiState.value.activePane) {
        updatePane(id) { it.copy(selected = it.visibleItems.mapTo(linkedSetOf()) { item -> item.path }) }
    }

    fun clearSelection(id: PaneId = _uiState.value.activePane) {
        updatePane(id) { it.copy(selected = emptySet()) }
    }

    fun setSort(id: PaneId, sort: FileSort) {
        updatePane(id) { pane ->
            if (pane.sort == sort) pane.copy(descending = !pane.descending)
            else pane.copy(sort = sort, descending = false)
        }
    }

    fun toggleHidden(id: PaneId) {
        updatePane(id) { it.copy(showHidden = !it.showHidden) }
    }

    fun switchMode(mode: AccessMode) {
        if (mode == _uiState.value.mode) return
        viewModelScope.launch {
            val available = when (mode) {
                AccessMode.NORMAL -> true
                AccessMode.ROOT -> container.refreshRootAvailability()
                AccessMode.SHIZUKU -> shizukuState.value.connected
                AccessMode.ADB -> adbState.value.connected
            }
            if (!available) {
                showMessage("Conecte o modo ${mode.title} antes de selecioná-lo.")
                return@launch
            }
            gateway = container.gateway(mode)
            _uiState.update { it.copy(mode = mode, message = "Modo ${mode.title} ativado") }
            refresh(PaneId.LEFT)
            refresh(PaneId.RIGHT)
        }
    }

    fun requestShizuku() = container.shizuku.requestAccess()

    fun pairAdb(pairingPort: Int, code: String, host: String? = null) {
        viewModelScope.launch {
            container.adb.pair(pairingPort, code, host)
            if (!adbState.value.busy) showMessage(adbState.value.message)
        }
    }

    fun connectAdb(connectPort: Int? = null, host: String? = null) {
        viewModelScope.launch {
            container.adb.connect(connectPort, host)
            showMessage(adbState.value.message)
        }
    }

    fun disconnectAdb() {
        viewModelScope.launch {
            if (_uiState.value.mode == AccessMode.ADB) switchMode(AccessMode.NORMAL)
            container.adb.disconnect()
        }
    }

    fun createDirectory(name: String) = runOperation("Criando pasta…") {
        gateway.createDirectory(_uiState.value.active.path, name)
        refresh(_uiState.value.activePane)
        "Pasta criada"
    }

    fun createFile(name: String) = runOperation("Criando arquivo…") {
        gateway.createFile(_uiState.value.active.path, name)
        refresh(_uiState.value.activePane)
        "Arquivo criado"
    }

    fun renameSelected(newName: String) = runOperation("Renomeando…") {
        val source = _uiState.value.active.selected.singleOrNull()
            ?: throw FileOperationException("Selecione exatamente um item.")
        gateway.rename(source, newName)
        refresh(_uiState.value.activePane)
        "Item renomeado"
    }

    fun copySelected() = transferSelected(move = false)

    fun moveSelected() = transferSelected(move = true)

    fun deleteSelected() = runOperation("Excluindo…") {
        val snapshot = _uiState.value
        val selected = snapshot.active.selected.toList()
        if (selected.isEmpty()) throw FileOperationException("Nenhum item selecionado.")
        for (path in selected) gateway.delete(path)
        refresh(snapshot.activePane)
        "${selected.size} item(ns) excluído(s)"
    }

    private fun transferSelected(move: Boolean) = runOperation(if (move) "Movendo…" else "Copiando…") {
        val snapshot = _uiState.value
        val selected = snapshot.active.selected.toList()
        if (selected.isEmpty()) throw FileOperationException("Nenhum item selecionado.")
        for (path in selected) {
            if (move) gateway.move(path, snapshot.passive.path) else gateway.copy(path, snapshot.passive.path)
        }
        refresh(PaneId.LEFT)
        refresh(PaneId.RIGHT)
        if (move) "${selected.size} item(ns) movido(s)" else "${selected.size} item(ns) copiado(s)"
    }

    private fun openDocument(item: FileItem, forceHex: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(document = DocumentState.Loading(item.path)) }
            try {
                val bytes = gateway.readBytes(item.path, DOCUMENT_LIMIT)
                val textLike = !forceHex && item.extension in TEXT_EXTENSIONS && bytes.take(4096).none { it == 0.toByte() }
                val document = if (textLike) {
                    val content = bytes.toString(Charsets.UTF_8)
                    DocumentState.Text(item.path, content, content)
                } else {
                    DocumentState.Hex(item.path, bytes)
                }
                _uiState.update { it.copy(document = document) }
            } catch (error: Throwable) {
                _uiState.update { it.copy(document = null, message = error.userMessage()) }
            }
        }
    }

    fun updateDocument(content: String) {
        _uiState.update { state ->
            val document = state.document as? DocumentState.Text ?: return@update state
            state.copy(document = document.copy(content = content, error = null))
        }
    }

    fun saveDocument() {
        val document = _uiState.value.document as? DocumentState.Text ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(document = document.copy(saving = true, error = null)) }
            try {
                gateway.writeBytesAtomically(document.path, document.content.toByteArray(Charsets.UTF_8))
                _uiState.update { it.copy(document = document.copy(original = document.content, saving = false), message = "Arquivo salvo") }
                refresh(PaneId.LEFT)
                refresh(PaneId.RIGHT)
            } catch (error: Throwable) {
                _uiState.update { it.copy(document = document.copy(saving = false, error = error.userMessage())) }
            }
        }
    }

    fun closeDocument(force: Boolean = false): Boolean {
        val document = _uiState.value.document
        if (!force && document is DocumentState.Text && document.dirty) return false
        _uiState.update { it.copy(document = null) }
        return true
    }

    fun showProperties(item: FileItem) {
        _uiState.update { it.copy(properties = PropertiesState(item)) }
    }

    fun calculateHash() {
        val properties = _uiState.value.properties ?: return
        if (properties.item.isDirectory) return
        viewModelScope.launch {
            _uiState.update { it.copy(properties = properties.copy(loadingHash = true, error = null)) }
            runCatching { gateway.sha256(properties.item.path) }
                .onSuccess { hash -> _uiState.update { it.copy(properties = properties.copy(sha256 = hash)) } }
                .onFailure { error -> _uiState.update { it.copy(properties = properties.copy(error = error.userMessage())) } }
        }
    }

    fun closeProperties() {
        _uiState.update { it.copy(properties = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun runOperation(label: String, block: suspend () -> String) {
        if (_uiState.value.operationRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationRunning = true, operationLabel = label, message = null) }
            try {
                val message = block()
                _uiState.update { it.copy(operationRunning = false, operationLabel = null, message = message) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(operationRunning = false, operationLabel = null, message = error.userMessage()) }
            }
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun updatePane(id: PaneId, transform: (PaneState) -> PaneState) {
        _uiState.update { state ->
            if (id == PaneId.LEFT) state.copy(left = transform(state.left))
            else state.copy(right = transform(state.right))
        }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is FileOperationException, is IllegalArgumentException, is IllegalStateException -> message ?: "Operação recusada"
        else -> "Falha inesperada: ${message?.take(180) ?: javaClass.simpleName}"
    }

    companion object {
        private const val MAX_HISTORY = 50
        private const val DOCUMENT_LIMIT = 2 * 1024 * 1024
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "yaml", "yml", "toml", "properties", "gradle", "kts",
            "kt", "java", "smali", "js", "ts", "jsx", "tsx", "html", "css", "scss", "py", "sh",
            "c", "h", "cpp", "hpp", "rs", "go", "sql", "csv", "log", "ini", "conf",
        )

        fun formatDate(timestamp: Long): String =
            if (timestamp <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    }
}
