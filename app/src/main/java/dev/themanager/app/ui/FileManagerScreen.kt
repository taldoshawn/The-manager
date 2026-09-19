@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.themanager.app.ui

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.themanager.app.core.adb.AdbState
import dev.themanager.app.core.model.AccessMode
import dev.themanager.app.core.model.FileItem
import dev.themanager.app.core.shizuku.ShizukuState
import java.util.Locale

private enum class NameAction { FILE, DIRECTORY, RENAME }

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onOpenAllFilesSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuState.collectAsStateWithLifecycle()
    val adb by viewModel.adbState.collectAsStateWithLifecycle()
    val rootAvailable by viewModel.rootAvailable.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showConnections by remember { mutableStateOf(false) }
    var nameAction by remember { mutableStateOf<NameAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var pathPane by remember { mutableStateOf<PaneId?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MainTopBar(
                mode = state.mode,
                selectionCount = state.active.selected.size,
                onConnections = { showConnections = true },
                onClearSelection = viewModel::clearSelection,
            )
        },
        bottomBar = {
            FileActionBar(
                enabled = !state.operationRunning,
                selectionCount = state.active.selected.size,
                onNewFile = { nameAction = NameAction.FILE },
                onNewFolder = { nameAction = NameAction.DIRECTORY },
                onCopy = viewModel::copySelected,
                onMove = viewModel::moveSelected,
                onDelete = { confirmDelete = true },
                onRename = { nameAction = NameAction.RENAME },
                onSelectAll = viewModel::selectAll,
                onProperties = {
                    state.active.items.firstOrNull { it.path in state.active.selected }?.let(viewModel::showProperties)
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val dualPane = maxWidth >= 720.dp
                if (dualPane) {
                    Row(Modifier.fillMaxSize()) {
                        FilePane(
                            id = PaneId.LEFT,
                            pane = state.left,
                            active = state.activePane == PaneId.LEFT,
                            modifier = Modifier.weight(1f),
                            viewModel = viewModel,
                            onEditPath = { pathPane = PaneId.LEFT },
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = .35f)))
                        FilePane(
                            id = PaneId.RIGHT,
                            pane = state.right,
                            active = state.activePane == PaneId.RIGHT,
                            modifier = Modifier.weight(1f),
                            viewModel = viewModel,
                            onEditPath = { pathPane = PaneId.RIGHT },
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        PaneTabs(state.activePane, state.left.path, state.right.path, viewModel::setActivePane)
                        FilePane(
                            id = state.activePane,
                            pane = state.active,
                            active = true,
                            modifier = Modifier.weight(1f),
                            viewModel = viewModel,
                            onEditPath = { pathPane = state.activePane },
                        )
                    }
                }
            }

            if (state.operationRunning) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(state.operationLabel ?: "Processando…")
                    }
                }
            }
        }
    }

    if (showConnections) {
        ConnectionSheet(
            mode = state.mode,
            shizuku = shizuku,
            adb = adb,
            rootAvailable = rootAvailable,
            hasAllFiles = Environment.isExternalStorageManager(),
            onDismiss = { showConnections = false },
            onMode = viewModel::switchMode,
            onRequestShizuku = viewModel::requestShizuku,
            onPairAdb = viewModel::pairAdb,
            onConnectAdb = viewModel::connectAdb,
            onDisconnectAdb = viewModel::disconnectAdb,
            onOpenAllFilesSettings = onOpenAllFilesSettings,
        )
    }

    nameAction?.let { action ->
        NameDialog(
            title = when (action) {
                NameAction.FILE -> "Novo arquivo"
                NameAction.DIRECTORY -> "Nova pasta"
                NameAction.RENAME -> "Renomear item"
            },
            initialValue = if (action == NameAction.RENAME) {
                state.active.items.firstOrNull { it.path in state.active.selected }?.name.orEmpty()
            } else "",
            onDismiss = { nameAction = null },
            onConfirm = { value ->
                when (action) {
                    NameAction.FILE -> viewModel.createFile(value)
                    NameAction.DIRECTORY -> viewModel.createDirectory(value)
                    NameAction.RENAME -> viewModel.renameSelected(value)
                }
                nameAction = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Excluir ${state.active.selected.size} item(ns)?") },
            text = { Text("A exclusão é permanente nesta versão. Pastas críticas do sistema são bloqueadas.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; viewModel.deleteSelected() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    pathPane?.let { paneId ->
        PathDialog(
            path = state.pane(paneId).path,
            onDismiss = { pathPane = null },
            onConfirm = { viewModel.navigate(paneId, it); pathPane = null },
        )
    }

    state.document?.let { document ->
        DocumentDialog(
            document = document,
            onChange = viewModel::updateDocument,
            onSave = viewModel::saveDocument,
            onClose = {
                if (!viewModel.closeDocument()) showDiscard = true
            },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Descartar alterações?") },
            text = { Text("As mudanças que ainda não foram salvas serão perdidas.") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; viewModel.closeDocument(force = true) }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Continuar editando") } },
        )
    }

    state.properties?.let { properties ->
        PropertiesDialog(properties, viewModel::calculateHash, viewModel::closeProperties)
    }
}

@Composable
private fun MainTopBar(
    mode: AccessMode,
    selectionCount: Int,
    onConnections: () -> Unit,
    onClearSelection: () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = {
            Column {
                Text(if (selectionCount > 0) "$selectionCount selecionado(s)" else "The Manager", fontWeight = FontWeight.SemiBold)
                if (selectionCount == 0) Text("Gerenciador avançado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            if (selectionCount > 0) IconButton(onClick = onClearSelection) { Icon(Icons.Default.Close, "Limpar seleção") }
        },
        actions = {
            AssistChip(
                onClick = onConnections,
                label = { Text(mode.title) },
                leadingIcon = { Icon(Icons.Default.LockOpen, null, Modifier.size(18.dp)) },
            )
            IconButton(onClick = onConnections) { Icon(Icons.Default.Settings, "Conexões e permissões") }
        },
    )
}

@Composable
private fun PaneTabs(active: PaneId, leftPath: String, rightPath: String, onSelect: (PaneId) -> Unit) {
    TabRow(selectedTabIndex = if (active == PaneId.LEFT) 0 else 1) {
        Tab(
            selected = active == PaneId.LEFT,
            onClick = { onSelect(PaneId.LEFT) },
            text = { Text(FileName(leftPath), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)) },
        )
        Tab(
            selected = active == PaneId.RIGHT,
            onClick = { onSelect(PaneId.RIGHT) },
            text = { Text(FileName(rightPath), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun FilePane(
    id: PaneId,
    pane: PaneState,
    active: Boolean,
    modifier: Modifier,
    viewModel: FileManagerViewModel,
    onEditPath: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = { viewModel.setActivePane(id) }, onLongClick = { viewModel.setActivePane(id) }),
    ) {
        PathBar(
            path = pane.path,
            canBack = pane.historyIndex > 0,
            canForward = pane.historyIndex < pane.history.lastIndex,
            onBack = { viewModel.goBack(id) },
            onForward = { viewModel.goForward(id) },
            onUp = { viewModel.goUp(id) },
            onPath = onEditPath,
            onRefresh = { viewModel.refresh(id) },
        )
        if (pane.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        pane.error?.let { error ->
            ErrorState(error) { viewModel.refresh(id) }
        } ?: if (!pane.loading && pane.visibleItems.isEmpty()) {
            EmptyState(pane.showHidden)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(pane.visibleItems, key = { it.path }) { item ->
                    FileRow(
                        item = item,
                        selected = item.path in pane.selected,
                        selectionMode = pane.selected.isNotEmpty(),
                        onClick = {
                            if (pane.selected.isNotEmpty()) viewModel.toggleSelection(id, item)
                            else viewModel.open(item, id)
                        },
                        onLongClick = { viewModel.toggleSelection(id, item) },
                        onProperties = { viewModel.showProperties(item) },
                        onHex = { viewModel.open(item, id, forceHex = true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PathBar(
    path: String,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUp: () -> Unit,
    onPath: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        IconButton(onClick = onForward, enabled = canForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Avançar") }
        IconButton(onClick = onUp) { Icon(Icons.Default.ArrowUpward, "Pasta acima") }
        Surface(
            modifier = Modifier.weight(1f),
            onClick = onPath,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
        ) {
            Text(
                path,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Atualizar") }
    }
}

@Composable
private fun FileRow(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onProperties: () -> Unit,
    onHex: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(11.dp),
            color = if (item.isDirectory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (item.isDirectory) Icons.Default.Folder else iconFor(item.extension),
                    null,
                    Modifier.size(22.dp),
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!item.isDirectory) Text(formatBytes(item.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(FileManagerViewModel.formatDate(item.modifiedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.isSymlink) Text("link", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (selected) Icon(Icons.Default.CheckCircle, "Selecionado", tint = MaterialTheme.colorScheme.primary)
        Box {
            IconButton(onClick = { menu = true }, enabled = !selectionMode || selected) { Icon(Icons.Default.MoreVert, "Ações do item") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Propriedades") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { menu = false; onProperties() },
                )
                if (!item.isDirectory) DropdownMenuItem(
                    text = { Text("Abrir em hexadecimal") },
                    leadingIcon = { Icon(Icons.Default.Hexagon, null) },
                    onClick = { menu = false; onHex() },
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
}

@Composable
private fun FileActionBar(
    enabled: Boolean,
    selectionCount: Int,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onSelectAll: () -> Unit,
    onProperties: () -> Unit,
) {
    var newMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
        Box {
            NavigationBarItem(
                selected = false,
                enabled = enabled,
                onClick = { newMenu = true },
                icon = { Icon(Icons.Default.Add, null) },
                label = { Text("Novo") },
            )
            DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Arquivo") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                    onClick = { newMenu = false; onNewFile() },
                )
                DropdownMenuItem(
                    text = { Text("Pasta") },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                    onClick = { newMenu = false; onNewFolder() },
                )
            }
        }
        NavigationBarItem(
            selected = false,
            enabled = enabled && selectionCount > 0,
            onClick = onCopy,
            icon = { Icon(Icons.Default.ContentCopy, null) },
            label = { Text("Copiar") },
        )
        NavigationBarItem(
            selected = false,
            enabled = enabled && selectionCount > 0,
            onClick = onMove,
            icon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
            label = { Text("Mover") },
        )
        NavigationBarItem(
            selected = false,
            enabled = enabled && selectionCount > 0,
            onClick = onDelete,
            icon = { Icon(Icons.Default.Delete, null) },
            label = { Text("Excluir") },
        )
        Box {
            NavigationBarItem(
                selected = false,
                enabled = enabled,
                onClick = { moreMenu = true },
                icon = { Icon(Icons.Default.MoreVert, null) },
                label = { Text("Mais") },
            )
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(text = { Text("Selecionar tudo") }, onClick = { moreMenu = false; onSelectAll() })
                DropdownMenuItem(text = { Text("Renomear") }, enabled = selectionCount == 1, onClick = { moreMenu = false; onRename() })
                DropdownMenuItem(text = { Text("Propriedades") }, enabled = selectionCount == 1, onClick = { moreMenu = false; onProperties() })
            }
        }
    }
}

@Composable
private fun ConnectionSheet(
    mode: AccessMode,
    shizuku: ShizukuState,
    adb: AdbState,
    rootAvailable: Boolean,
    hasAllFiles: Boolean,
    onDismiss: () -> Unit,
    onMode: (AccessMode) -> Unit,
    onRequestShizuku: () -> Unit,
    onPairAdb: (Int, String, String?) -> Unit,
    onConnectAdb: (Int?, String?) -> Unit,
    onDisconnectAdb: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var pairingPort by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Acesso ao sistema", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Escolha somente o privilégio necessário. A troca não acontece sem uma conexão válida.", color = MaterialTheme.colorScheme.onSurfaceVariant)

            AccessCard(
                title = "Normal",
                status = if (hasAllFiles) "Acesso a todos os arquivos concedido" else "Acesso limitado pelo Android",
                selected = mode == AccessMode.NORMAL,
                available = true,
                onSelect = { onMode(AccessMode.NORMAL) },
                action = {
                    if (!hasAllFiles) OutlinedButton(onClick = onOpenAllFilesSettings) { Text("Permitir todos os arquivos") }
                },
            )
            AccessCard(
                title = "Shizuku / Sui",
                status = shizuku.message,
                selected = mode == AccessMode.SHIZUKU,
                available = shizuku.connected,
                onSelect = { onMode(AccessMode.SHIZUKU) },
                action = {
                    OutlinedButton(onClick = onRequestShizuku) { Text(if (shizuku.permissionGranted) "Reconectar" else "Conceder acesso") }
                },
            )
            AccessCard(
                title = "Root",
                status = if (rootAvailable) "su autorizado" else "Root não detectado ou não autorizado",
                selected = mode == AccessMode.ROOT,
                available = rootAvailable,
                onSelect = { onMode(AccessMode.ROOT) },
            )
            AccessCard(
                title = "Depuração Wi-Fi",
                status = adb.message,
                selected = mode == AccessMode.ADB,
                available = adb.connected,
                onSelect = { onMode(AccessMode.ADB) },
            )

            Text("Pareamento ADB", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Use as duas portas diferentes exibidas em Opções do desenvolvedor: uma para parear e outra para conectar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(host, { host = it.take(255) }, Modifier.fillMaxWidth(), label = { Text("Host (vazio = este aparelho)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    pairingPort,
                    { pairingPort = it.filter(Char::isDigit).take(5) },
                    Modifier.weight(1f),
                    label = { Text("Porta de pareamento") },
                    singleLine = true,
                )
                OutlinedTextField(
                    code,
                    { code = it.filter(Char::isDigit).take(6) },
                    Modifier.weight(1f),
                    label = { Text("Código de 6 dígitos") },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                connectPort,
                { connectPort = it.filter(Char::isDigit).take(5) },
                Modifier.fillMaxWidth(),
                label = { Text("Porta de conexão (opcional com descoberta)") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pairingPort.toIntOrNull()?.let { onPairAdb(it, code, host.ifBlank { null }) } },
                    enabled = !adb.busy && pairingPort.toIntOrNull() != null && code.length == 6,
                ) { Text("Parear") }
                Button(
                    onClick = { onConnectAdb(connectPort.toIntOrNull(), host.ifBlank { null }) },
                    enabled = !adb.busy && !adb.connected,
                ) { Text("Conectar") }
                if (adb.connected) TextButton(onClick = onDisconnectAdb) { Text("Desconectar") }
            }
        }
    }
}

@Composable
private fun AccessCard(
    title: String,
    status: String,
    selected: Boolean,
    available: Boolean,
    onSelect: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onSelect,
        enabled = available,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (selected) Icon(Icons.Default.CheckCircle, "Modo ativo", tint = MaterialTheme.colorScheme.primary)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
private fun NameDialog(title: String, initialValue: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(255) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PathDialog(path: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(path) { mutableStateOf(path) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir caminho") },
        text = {
            OutlinedTextField(
                value,
                { value = it.take(4096) },
                Modifier.fillMaxWidth(),
                label = { Text("Caminho absoluto") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(value) }, enabled = value.startsWith('/')) { Text("Abrir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DocumentDialog(
    document: DocumentState,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fechar") }
                    Column(Modifier.weight(1f)) {
                        Text(FileName(document.path), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(document.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (document is DocumentState.Text) {
                        IconButton(onClick = onSave, enabled = document.dirty && !document.saving) { Icon(Icons.Default.Save, "Salvar") }
                    }
                }
                HorizontalDivider()
                when (document) {
                    is DocumentState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is DocumentState.Text -> {
                        document.error?.let { Text(it, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(alpha = .15f)).padding(12.dp), color = MaterialTheme.colorScheme.error) }
                        BasicTextField(
                            value = document.content,
                            onValueChange = onChange,
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        )
                    }
                    is DocumentState.Hex -> HexViewer(document.bytes)
                }
            }
        }
    }
}

@Composable
private fun HexViewer(bytes: ByteArray) {
    val rows = remember(bytes) { bytes.asList().chunked(16) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080A0D)),
        contentPadding = PaddingValues(12.dp),
    ) {
        itemsIndexed(rows) { index, row ->
            val offset = "%08X".format(Locale.ROOT, index * 16)
            val hex = row.joinToString(" ") { "%02X".format(Locale.ROOT, it.toInt() and 0xff) }.padEnd(47)
            val ascii = row.joinToString("") { byte ->
                val value = byte.toInt() and 0xff
                if (value in 32..126) value.toChar().toString() else "."
            }
            Text("$offset  $hex  $ascii", color = Color(0xFFD4DAE5), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PropertiesDialog(properties: PropertiesState, onHash: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (properties.item.isDirectory) Icons.Default.Folder else Icons.Default.Description, null) },
        title = { Text(properties.item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyLine("Caminho", properties.item.path)
                PropertyLine("Tipo", if (properties.item.isDirectory) "Pasta" else properties.item.extension.ifBlank { "Arquivo" })
                if (!properties.item.isDirectory) PropertyLine("Tamanho", "${formatBytes(properties.item.size)} (${properties.item.size} bytes)")
                PropertyLine("Modificado", FileManagerViewModel.formatDate(properties.item.modifiedAt))
                if (properties.item.permissions.isNotBlank()) PropertyLine("Permissões", properties.item.permissions)
                PropertyLine("Acesso", listOfNotNull(if (properties.item.readable) "leitura" else null, if (properties.item.writable) "gravação" else null).joinToString(" + ").ifBlank { "nenhum" })
                if (!properties.item.isDirectory) {
                    properties.sha256?.let { PropertyLine("SHA-256", it) }
                    properties.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = onHash, enabled = !properties.loadingHash && properties.sha256 == null) {
                        if (properties.loadingHash) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Calcular SHA-256")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (label == "Caminho" || label == "SHA-256") FontFamily.Monospace else null)
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.ErrorOutline, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = retry) { Text("Tentar novamente") }
    }
}

@Composable
private fun EmptyState(showHidden: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text(if (showHidden) "Esta pasta está vazia" else "Nenhum item visível", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun iconFor(extension: String) = when (extension) {
    "kt", "kts", "java", "smali", "js", "ts", "py", "c", "cpp", "h", "rs", "go", "html", "css", "xml", "json" -> Icons.Default.Code
    else -> Icons.Default.Description
}

private fun FileName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifBlank { "/" }

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit++
    } while (value >= 1024 && unit < units.lastIndex)
    return String.format(Locale.getDefault(), if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}
