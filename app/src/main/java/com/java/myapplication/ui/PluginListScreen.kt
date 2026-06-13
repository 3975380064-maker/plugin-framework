package com.java.myapplication.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.java.myapplication.Plugin
import com.java.myapplication.BackgroundPlugin
import com.java.myapplication.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginListScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<Plugin>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf(value = null as String?) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultDialogText by remember { mutableStateOf("") }
    var resultDialogTitle by remember { mutableStateOf("") }
    var executingPlugin by remember { mutableStateOf(value = null as String?) }
    var selectedTab by remember { mutableIntStateOf(0) }  // 0=手动, 1=长期运行
    var showDeleteConfirm by remember { mutableStateOf(value = null as String?) }
    var pinnedPlugins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var runningBgPlugins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cloneCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val pluginManager = remember {
        PluginManager(context).apply {
            onPluginLoaded = { scope.launch(Dispatchers.Main) { plugins = (plugins + it).distinctBy { p -> p.getName() } } }
            onPluginUnloaded = { name ->
                scope.launch(Dispatchers.Main) {
                    plugins = plugins.filter { p -> p.getName() != name }
                    pinnedPlugins = pinnedPlugins - name
                }
            }
            onError = { scope.launch(Dispatchers.Main) { errorMessage = it } }
            onBackgroundPluginStarted = { scope.launch(Dispatchers.Main) { runningBgPlugins = runningBgPlugins + it } }
            onBackgroundPluginStopped = { scope.launch(Dispatchers.Main) { runningBgPlugins = runningBgPlugins - it } }
        }
    }

    LaunchedEffect(Unit) {
        pluginManager.initialize()
        plugins = pluginManager.getLoadedPlugins()
        isLoading = false
    }

    fun refreshPlugins() {
        isLoading = true
        errorMessage = null
        plugins = pluginManager.reloadPlugins()
        isLoading = false
    }

    fun sortedPlugins(): List<Plugin> {
        val list = plugins.toMutableList()
        list.sortByDescending { it.getName() in pinnedPlugins }
        return list
    }

    fun showExecutionResult(pluginName: String, result: String) {
        resultDialogTitle = "插件执行结果 - $pluginName"
        resultDialogText = if (result.startsWith("Error:")) "执行失败\n\n$result" else "执行成功\n\n$result"
        showResultDialog = true
    }

    fun togglePin(pluginName: String) {
        pinnedPlugins = if (pluginName in pinnedPlugins)
            pinnedPlugins - pluginName
        else
            pinnedPlugins + pluginName
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("插件管理") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("手动执行", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("长期运行", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 错误消息
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(text = error, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val displayPlugins = if (selectedTab == 0) sortedPlugins() else sortedPlugins().filter { it is BackgroundPlugin }

                if (displayPlugins.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selectedTab == 0) "暂无插件" else "暂无长期运行插件", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("点击右下角 + 添加", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayPlugins, key = { it.getName() }) { plugin ->
                            val name = plugin.getName()
                            val isRunning = name in runningBgPlugins
                            val isPinned = name in pinnedPlugins
                            val isExecuting = executingPlugin == name

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isPinned) {
                                            Text("[顶] ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                                        // 置顶切换按钮
                                        TextButton(onClick = { togglePin(name) }) {
                                            Text(if (isPinned) "取消置顶" else "置顶", style = MaterialTheme.typography.labelSmall)
                                        }
                                        // 虚拟插件数量角标
                                        val count = cloneCounts[name] ?: 0
                                        if (count > 0) {
                                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                                Text("+${count}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(plugin.getDescription(), style = MaterialTheme.typography.bodyMedium)
                                    Text("v${plugin.getVersion()}", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                        if (selectedTab == 0) {
                                            // 手动执行模式
                                            OutlinedButton(onClick = { showDeleteConfirm = name }, enabled = !isExecuting) {
                                                Text("卸载")
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Button(onClick = {
                                                if (executingPlugin == null) {
                                                    executingPlugin = name
                                                    scope.launch(Dispatchers.IO) {
                                                        val result = pluginManager.executePlugin(name)
                                                        executingPlugin = null
                                                        showExecutionResult(name, result)
                                                    }
                                                }
                                            }, enabled = !isExecuting) {
                                                if (isExecuting) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) ; Spacer(Modifier.width(4.dp)) }
                                                Text(if (isExecuting) "执行中" else "执行")
                                            }
                                        } else {
                                            // 长期运行模式
                                            OutlinedButton(onClick = { showDeleteConfirm = name }, enabled = !isExecuting) {
                                                Text("卸载")
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            if (isRunning) {
                                                Button(onClick = { pluginManager.stopBackgroundPlugin(name) }) {
                                                    Text("停止")
                                                }
                                            } else {
                                                Button(onClick = { pluginManager.startBackgroundPlugin(name) }) {
                                                    Text("启动")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 弹窗：执行结果
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text(resultDialogTitle) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(resultDialogText, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showResultDialog = false }) { Text("确定") } }
        )
    }

    // 弹窗：添加插件
    if (showAddDialog) {
        AddPluginDialog(
            onDismiss = { showAddDialog = false },
            onPluginAdded = { uri ->
                showAddDialog = false
                isLoading = true
                errorMessage = null
                val ok = pluginManager.installPluginFromUri(uri)
                if (ok) {
                    pluginManager.reloadPlugins()
                    refreshPlugins()
                } else {
                    errorMessage = "安装失败"
                }
            }
        )
    }

    // 弹窗：删除确认
    showDeleteConfirm?.let { delName ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确定删除 \"$delName\" 吗？") },
            text = { Text("此操作不可撤销") },
            confirmButton = {
                TextButton(onClick = {
                    pluginManager.uninstallPlugin(delName)
                    plugins = plugins.filter { it.getName() != delName }
                    showDeleteConfirm = null
                }) { Text("确定删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPluginDialog(
    onDismiss: () -> Unit,
    onPluginAdded: (android.net.Uri) -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    
    // 文件选择器必须在顶层定义，不能在 lambda 内部
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            // 获取文件名
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            selectedFileName = c.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AddPluginDialog", "获取文件名失败", e)
            }
            if (selectedFileName == null) {
                selectedFileName = uri.lastPathSegment ?: "未知文件"
            }
            Toast.makeText(context, "已选择文件: $selectedFileName", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "未选择文件", Toast.LENGTH_SHORT).show()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加插件") },
        text = {
            Column {
                Text("请选择.jar或.dex格式的插件文件：")
                Spacer(modifier = Modifier.height(16.dp))
                
                // 文件选择按钮
                Button(
                    onClick = {
                        // 启动文件选择器，使用通配符MIME类型确保能找到文件
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("选择文件")
                }
                
                // 显示已选择的文件
                if (selectedUri != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "已选择: ${selectedFileName ?: "未知文件"}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedUri?.let { onPluginAdded(it) }
                },
                enabled = selectedUri != null
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}