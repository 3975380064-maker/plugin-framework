package com.java.myapplication.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.java.myapplication.BackgroundPlugin
import com.java.myapplication.Plugin
import com.java.myapplication.PluginHost
import com.java.myapplication.PluginUriResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginListScreen() {
    val context = LocalContext.current
    val host = remember { PluginHost.get(context) }

    val plugins by host.plugins.collectAsState()
    val runningBackground by host.runningBackground.collectAsState()
    val pinned by host.pinned.collectAsState()
    val isLoading by host.loading.collectAsState()
    val errorMessage by host.error.collectAsState()
    val executing by host.executing.collectAsState()
    val result by host.result.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }   // 0=一次性任务, 1=长期任务
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    // PluginHost 已在 Application 中启动；这里再调一次是幂等的，便于预览/复用
    LaunchedEffect(Unit) { host.start() }

    // 一次性任务只列非 BackgroundPlugin；长期任务单独一个 Tab
    val displayed = remember(plugins, pinned, selectedTab) {
        val sorted = plugins.sortedByDescending { it.getName() in pinned }
        if (selectedTab == 0) {
            sorted.filterNot { it is BackgroundPlugin }
        } else {
            sorted.filterIsInstance<BackgroundPlugin>()
        }
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
                        Text("一次性任务", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("长期任务", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Text("+") }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { host.clearError() }) { Text("关闭") }
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (selectedTab == 0) "暂无一次性任务插件" else "暂无长期任务插件",
                            style = MaterialTheme.typography.headlineMedium
                        )
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
                    items(displayed, key = { it.getName() }) { plugin ->
                        val name = plugin.getName()
                        val isPinned = name in pinned
                        val isRunning = name in runningBackground
                        val isExecuting = executing == name
                        val busy = executing != null

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isPinned) {
                                        Text(
                                            "[顶] ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { host.togglePin(name) }) {
                                        Text(
                                            if (isPinned) "取消置顶" else "置顶",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Text(plugin.getDescription(), style = MaterialTheme.typography.bodyMedium)
                                Text("v${plugin.getVersion()}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { showDeleteConfirm = name },
                                        enabled = !isExecuting
                                    ) { Text("卸载") }
                                    Spacer(Modifier.width(8.dp))

                                    if (selectedTab == 0) {
                                        Button(
                                            onClick = { host.execute(name) },
                                            enabled = !busy
                                        ) {
                                            if (isExecuting) {
                                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(if (isExecuting) "执行中" else "执行")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (isRunning) host.stopBackground(name) else host.startBackground(name)
                                            }
                                        ) { Text(if (isRunning) "停止" else "启动") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    result?.let { executed ->
        AlertDialog(
            onDismissRequest = { host.consumeResult() },
            title = { Text("插件执行结果 - ${executed.pluginName}") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (executed.output.startsWith("Error:")) {
                            "执行失败\n\n${executed.output}"
                        } else {
                            "执行成功\n\n${executed.output}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = { TextButton(onClick = { host.consumeResult() }) { Text("确定") } }
        )
    }

    if (showAddDialog) {
        AddPluginDialog(
            onDismiss = { showAddDialog = false },
            onPluginAdded = { uri ->
                showAddDialog = false
                host.install(uri)
            }
        )
    }

    showDeleteConfirm?.let { delName ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确定删除 \"$delName\" 吗？") },
            text = { Text("此操作不可撤销") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = null
                    host.uninstall(delName)
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
    onPluginAdded: (Uri) -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = PluginUriResolver.displayName(context, uri)
                ?: uri.lastPathSegment
                ?: "未知文件"
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
                Text("请选择 .jar 格式的插件文件：")
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("选择文件") }

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
                onClick = { selectedUri?.let(onPluginAdded) },
                enabled = selectedUri != null
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}