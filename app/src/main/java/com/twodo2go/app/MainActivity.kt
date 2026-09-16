// Copyright (c) 2026 Vern McGeorge. All rights reserved.
package com.twodo2go.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = twoDo2GoColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TwoDo2GoApp(
                        initialSheetUrl = preferences.getString("sheet_url", "") ?: "",
                        initialItems = readToDoItems(preferences.getString("todo_items", "[]") ?: "[]"),
                        initialLists = readStringList(preferences.getString("known_lists", "[]") ?: "[]"),
                        onSheetUrlSaved = { url -> preferences.edit().putString("sheet_url", url).apply() },
                        onItemsSaved = { items -> preferences.edit().putString("todo_items", writeToDoItems(items)).apply() },
                        onListsSaved = { lists -> preferences.edit().putString("known_lists", writeStringList(lists)).apply() }
                    )
                }
            }
        }
    }

    companion object {
        const val PREFS_NAME = "twodo2go_settings"
    }
}

private enum class Screen { OVERVIEW, SETTINGS, LIST_DETAIL, QR_SCANNER }

@Composable
fun TwoDo2GoApp(
    initialSheetUrl: String,
    initialItems: List<ToDoItem>,
    initialLists: List<String>,
    onSheetUrlSaved: (String) -> Unit,
    onItemsSaved: (List<ToDoItem>) -> Unit,
    onListsSaved: (List<String>) -> Unit
) {
    var screen by remember { mutableStateOf(if (initialSheetUrl.isBlank()) Screen.SETTINGS else Screen.OVERVIEW) }
    var sheetUrl by remember { mutableStateOf(initialSheetUrl) }
    var items by remember { mutableStateOf(initialItems) }
    var lists by remember { mutableStateOf(initialLists) }
    var currentList by remember { mutableStateOf<String?>(null) }
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var showingAddItem by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun persistItems(newItems: List<ToDoItem>) {
        items = newItems
        onItemsSaved(newItems)
    }

    fun persistLists(newLists: List<String>) {
        lists = newLists
        onListsSaved(newLists)
    }

    fun runSync() {
        if (sheetUrl.isBlank()) return
        syncing = true
        coroutineScope.launch {
            val tabs = withContext(Dispatchers.IO) { fetchSheetTabs(sheetUrl) }
            syncing = false
            if (tabs.isEmpty()) {
                syncMessage = "Couldn't read any tabs from this Sheet. Check the URL and that " +
                    "sharing is \"Anyone with the link can view\"."
                return@launch
            }
            val imported = tabs.flatMap { tab -> parseToDoCsv(tab.csv, tab.tabName) }
            persistItems(mergeImportedToDoItems(imported, items))
            persistLists(tabs.map { it.tabName })
            syncMessage = "Synced ${tabs.size} list(s)."
        }
    }

    when (screen) {
        Screen.QR_SCANNER -> QrScannerScreen(
            onResult = { scanned ->
                screen = Screen.SETTINGS
                sheetUrl = scanned
                onSheetUrlSaved(scanned)
                runSync()
            },
            onCancel = { screen = Screen.SETTINGS }
        )
        Screen.SETTINGS -> SettingsScreen(
            sheetUrl = sheetUrl,
            onSheetUrlChange = {
                sheetUrl = it
                onSheetUrlSaved(it)
            },
            onScanQr = { screen = Screen.QR_SCANNER },
            onSync = ::runSync,
            syncing = syncing,
            syncMessage = syncMessage,
            canGoBack = lists.isNotEmpty(),
            onBack = { screen = Screen.OVERVIEW }
        )
        Screen.LIST_DETAIL -> {
            val listName = currentList
            if (listName == null) {
                screen = Screen.OVERVIEW
            } else {
                ListDetailScreen(
                    listName = listName,
                    items = items.filter { it.list == listName },
                    onBack = { screen = Screen.OVERVIEW },
                    onToggleDone = { id ->
                        persistItems(items.map {
                            if (it.id == id) {
                                it.copy(done = !it.done, doneAtEpochMs = if (!it.done) System.currentTimeMillis() else null)
                            } else it
                        })
                    },
                    onEditQuadrant = { id -> editingItemId = id },
                    onDeleteItem = { id -> persistItems(items.filterNot { it.id == id }) },
                    onAddItem = { showingAddItem = true }
                )
            }
        }
        Screen.OVERVIEW -> ListsOverviewScreen(
            lists = lists,
            items = items,
            onOpenList = {
                currentList = it
                screen = Screen.LIST_DETAIL
            },
            onOpenSettings = { screen = Screen.SETTINGS }
        )
    }

    val editingItem = items.find { it.id == editingItemId }
    if (editingItem != null) {
        QuadrantDialog(
            title = "Set priority",
            initialImportant = editingItem.important,
            initialUrgent = editingItem.urgent,
            onDismiss = { editingItemId = null },
            onSelect = { important, urgent ->
                persistItems(items.map {
                    if (it.id == editingItem.id) it.copy(important = important, urgent = urgent) else it
                })
                editingItemId = null
            }
        )
    }

    if (showingAddItem && currentList != null) {
        AddItemDialog(
            onDismiss = { showingAddItem = false },
            onConfirm = { description, important, urgent ->
                val newItem = ToDoItem(
                    id = "local-${System.currentTimeMillis()}-${items.size}",
                    description = description,
                    list = currentList!!,
                    important = important,
                    urgent = urgent
                )
                persistItems(items + newItem)
                showingAddItem = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sheetUrl: String,
    onSheetUrlChange: (String) -> Unit,
    onScanQr: () -> Unit,
    onSync: () -> Unit,
    syncing: Boolean,
    syncMessage: String,
    canGoBack: Boolean,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("2do2go Settings") },
            navigationIcon = {
                if (canGoBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        )
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Point 2do2go at the same Google Sheet you already use for MicroTasking. Each " +
                    "tab becomes a to-do list here; each checked row becomes an item.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = sheetUrl,
                onValueChange = onSheetUrlChange,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                label = { Text("Google Sheet URL") }
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Text(" Scan QR")
                }
                Button(onClick = onSync, enabled = sheetUrl.isNotBlank() && !syncing, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(if (syncing) "Syncing…" else "Sync Lists")
                }
            }
            if (syncMessage.isNotBlank()) {
                Text(
                    syncMessage,
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsOverviewScreen(
    lists: List<String>,
    items: List<ToDoItem>,
    onOpenList: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("2do2go") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (lists.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No lists yet. Open Settings and sync your Google Sheet to get started.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(lists) { listName ->
                    val openCount = items.count { it.list == listName && !it.done }
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenList(listName) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(listName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$openCount open",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listName: String,
    items: List<ToDoItem>,
    onBack: () -> Unit,
    onToggleDone: (String) -> Unit,
    onEditQuadrant: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onAddItem: () -> Unit
) {
    val open = sortedForDisplay(items.filter { !it.done })
    val done = items.filter { it.done }.sortedByDescending { it.doneAtEpochMs ?: it.addedAtEpochMs }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to lists")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (open.isEmpty() && done.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Tap + to add an item.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
            items(open) { toDoItem ->
                ToDoItemRow(
                    item = toDoItem,
                    onToggleDone = { onToggleDone(toDoItem.id) },
                    onEditQuadrant = { onEditQuadrant(toDoItem.id) },
                    onDelete = { onDeleteItem(toDoItem.id) },
                    onOpenLink = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(toDoItem.link)))
                        }
                    }
                )
            }
            if (done.isNotEmpty()) {
                item {
                    Text(
                        "Done",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                items(done) { toDoItem ->
                    ToDoItemRow(
                        item = toDoItem,
                        onToggleDone = { onToggleDone(toDoItem.id) },
                        onEditQuadrant = { onEditQuadrant(toDoItem.id) },
                        onDelete = { onDeleteItem(toDoItem.id) },
                        onOpenLink = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(toDoItem.link)))
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun quadrantColor(quadrant: Quadrant, colorScheme: androidx.compose.material3.ColorScheme): Color = when (quadrant) {
    Quadrant.DO_FIRST -> colorScheme.error
    Quadrant.SCHEDULE -> colorScheme.primary
    Quadrant.DELEGATE -> colorScheme.secondary
    Quadrant.ELIMINATE -> colorScheme.onSurfaceVariant
}

@Composable
fun ToDoItemRow(
    item: ToDoItem,
    onToggleDone: () -> Unit,
    onEditQuadrant: () -> Unit,
    onDelete: () -> Unit,
    onOpenLink: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.done, onCheckedChange = { onToggleDone() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val quadrant = item.quadrant()
                    Text(
                        quadrant.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = quadrantColor(quadrant, MaterialTheme.colorScheme),
                        modifier = Modifier.clickable(onClick = onEditQuadrant).padding(end = 12.dp)
                    )
                    if (item.link.isNotBlank()) {
                        Text(
                            "Open link",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onOpenLink)
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${item.description}")
            }
        }
    }
}

/**
 * The Eisenhower-matrix priority picker: a big 2x2 grid of tappable quadrants (important x
 * urgent), laid out important-on-top / urgent-on-left. Realized as four clickable cells rather
 * than raw tap-coordinate detection on one surface - same visual result, more robust.
 */
@Composable
fun QuadrantDialog(
    title: String,
    initialImportant: Boolean,
    initialUrgent: Boolean,
    onDismiss: () -> Unit,
    onSelect: (important: Boolean, urgent: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        "Urgent", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "Not urgent", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium
                    )
                }
                QuadrantRow(
                    rowLabel = "Important",
                    leftQuadrant = Quadrant.DO_FIRST,
                    rightQuadrant = Quadrant.SCHEDULE,
                    selected = (initialImportant && initialUrgent) to (initialImportant && !initialUrgent),
                    onLeftClick = { onSelect(true, true) },
                    onRightClick = { onSelect(true, false) }
                )
                QuadrantRow(
                    rowLabel = "Not important",
                    leftQuadrant = Quadrant.DELEGATE,
                    rightQuadrant = Quadrant.ELIMINATE,
                    selected = (!initialImportant && initialUrgent) to (!initialImportant && !initialUrgent),
                    onLeftClick = { onSelect(false, true) },
                    onRightClick = { onSelect(false, false) }
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun QuadrantRow(
    rowLabel: String,
    leftQuadrant: Quadrant,
    rightQuadrant: Quadrant,
    selected: Pair<Boolean, Boolean>,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rowLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        QuadrantCell(
            label = leftQuadrant.label,
            selected = selected.first,
            color = quadrantColor(leftQuadrant, MaterialTheme.colorScheme),
            onClick = onLeftClick,
            modifier = Modifier.weight(1f)
        )
        QuadrantCell(
            label = rightQuadrant.label,
            selected = selected.second,
            color = quadrantColor(rightQuadrant, MaterialTheme.colorScheme),
            onClick = onRightClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuadrantCell(label: String, selected: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .aspectRatio(1.4f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddItemDialog(onDismiss: () -> Unit, onConfirm: (description: String, important: Boolean, urgent: Boolean) -> Unit) {
    var description by remember { mutableStateOf("") }
    var important by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add item") },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") }
                )
                Text(
                    "Priority: ${ToDoItem(id = "", description = "", list = "", important = important, urgent = urgent).quadrant().label}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                QuadrantRow(
                    rowLabel = "",
                    leftQuadrant = Quadrant.DO_FIRST,
                    rightQuadrant = Quadrant.SCHEDULE,
                    selected = (important && urgent) to (important && !urgent),
                    onLeftClick = { important = true; urgent = true },
                    onRightClick = { important = true; urgent = false }
                )
                QuadrantRow(
                    rowLabel = "",
                    leftQuadrant = Quadrant.DELEGATE,
                    rightQuadrant = Quadrant.ELIMINATE,
                    selected = (!important && urgent) to (!important && !urgent),
                    onLeftClick = { important = false; urgent = true },
                    onRightClick = { important = false; urgent = false }
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (description.isNotBlank()) onConfirm(description.trim(), important, urgent) }) {
                Text("Add")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(onResult: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Scan Sheet QR Code") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel scan")
                }
            }
        )
        if (hasCameraPermission) {
            var hasScanned by remember { mutableStateOf(false) }
            var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
            DisposableEffect(Unit) {
                onDispose { cameraProvider?.unbindAll() }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val scanner = BarcodeScanning.getClient()
                    val executor = ContextCompat.getMainExecutor(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !hasScanned) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                        if (!hasScanned && !value.isNullOrBlank()) {
                                            hasScanned = true
                                            onResult(value)
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, executor)
                    previewView
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to scan a QR code.", modifier = Modifier.padding(bottom = 12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
            }
        }
    }
}

// Same calm palette family as MicroTasking so 2do2go reads as a sibling app.
private val twoDo2GoColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D6B),
    onPrimary = Color.White,
    secondary = Color(0xFF5FA88F),
    onSecondary = Color.White,
    background = Color(0xFFEFF2F1),
    surface = Color.White,
    error = Color(0xFFE2725B),
    onError = Color.White
)
