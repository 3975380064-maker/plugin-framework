package com.java.myapplication

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 插件管理器
 * 负责插件的生命周期管理、状态维护和文件操作
 */
class PluginManager(private val context: Context) {
    
    private val pluginLoader = PluginLoader(context)
    private val shizukuProxy = ShizukuProxy(context)
    
    // 插件状态回调
    var onPluginLoaded: ((Plugin) -> Unit)? = null
    var onPluginUnloaded: ((String) -> Unit)? = null
    var onPluginExecuted: ((String, String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    /**
     * 初始化插件管理器
     */
    fun initialize() {
        // 检查Shizuku可用性
        if (!shizukuProxy.isShizukuAvailable()) {
            onError?.invoke("Shizuku服务不可用，部分功能可能受限")
            // 不再直接返回，继续尝试加载插件
        }
        
        // 加载现有插件
        loadPlugins()
    }
    
    /**
     * 加载所有插件
     */
    fun loadPlugins(): List<Plugin> {
        return try {
            val plugins = pluginLoader.loadAllPlugins()
            plugins.forEach { plugin ->
                onPluginLoaded?.invoke(plugin)
            }
            plugins
        } catch (e: Exception) {
            onError?.invoke("加载插件失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 执行插件
     */
    fun executePlugin(pluginName: String, args: Map<String, Any>? = null): String {
        val plugin = pluginLoader.getPlugin(pluginName)
        if (plugin == null) {
            val errorMsg = "插件 $pluginName 未找到。可能原因：\n" +
                          "1. 插件文件缺少 META-INF/plugin.properties\n" +
                          "2. mainClass 声明有误\n" +
                          "3. 插件未正确实现 Plugin 接口"
            onError?.invoke(errorMsg)
            return "Error: Plugin not found\n\n$errorMsg"
        }
        
        return try {
            if (plugin.needsShizuku() && !shizukuProxy.isShizukuAvailable()) {
                // Shizuku 不可用，但仍然尝试执行（可能不需要真实权限）
                android.util.Log.w("PluginManager", "Shizuku 不可用，插件可能无法使用高级功能")
            }
            
            val result = plugin.execute(shizukuProxy, args)
            onPluginExecuted?.invoke(pluginName, result)
            
            // 添加调试信息
            val debugInfo = "\n\n[调试信息] 插件路径: ${getPluginDirectoryPath()}"
            result + debugInfo
        } catch (e: Exception) {
            val errorMsg = "执行插件失败: ${e.message}\n" +
                          "堆栈跟踪: ${e.stackTraceToString()}"
            onError?.invoke(errorMsg)
            "Error: $errorMsg"
        }
    }
    
    /**
     * 从文件URI安装插件
     */
    fun installPluginFromUri(uri: Uri): Boolean {
        return try {
            val pluginDir = File(context.filesDir, "plugins")
            pluginDir.mkdirs()
            
            val fileName = getFileNameFromUri(uri)
            if (fileName == null) {
                onError?.invoke("无法获取文件名")
                return false
            }
            
            // 路径穿越防护
            if (fileName.contains("/") || fileName.contains("..")) {
                onError?.invoke("非法文件名")
                return false
            }
            
            // 检查文件扩展名
            if (!fileName.endsWith(".jar", ignoreCase = true) && 
                !fileName.endsWith(".dex", ignoreCase = true)) {
                onError?.invoke("不支持的文件格式，请上传.jar或.dex文件")
                return false
            }
            
            // 复制文件到插件目录
            val pluginFile = File(pluginDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(pluginFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // 重新加载插件
            loadPlugins()
            true
        } catch (e: Exception) {
            onError?.invoke("安装插件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 卸载插件
     */
    fun uninstallPlugin(pluginName: String): Boolean {
        return try {
            // 先获取源文件名，再卸载
            val sourceFile = pluginLoader.getPluginSourceFile(pluginName)
            val result = pluginLoader.unloadPlugin(pluginName)
            if (!result) {
                onError?.invoke("插件 $pluginName 未找到")
                return false
            }
            
            // 删除插件文件（用记录的文件名，而非猜测）
            if (sourceFile != null) {
                val pluginDir = File(context.filesDir, "plugins")
                val pluginFile = File(pluginDir, sourceFile)
                if (pluginFile.exists()) {
                    pluginFile.delete()
                    android.util.Log.d("PluginManager", "已删除插件文件: ${pluginFile.absolutePath}")
                }
            } else {
                // 降级：尝试常见扩展名
                val pluginDir = File(context.filesDir, "plugins")
                listOf(".jar", ".dex").forEach { ext ->
                    val f = File(pluginDir, "$pluginName$ext")
                    if (f.exists()) f.delete()
                }
            }
            
            onPluginUnloaded?.invoke(pluginName)
            true
        } catch (e: Exception) {
            onError?.invoke("卸载插件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 重新加载插件
     */
    fun reloadPlugins(): List<Plugin> {
        return pluginLoader.reloadPlugins()
    }
    
    /**
     * 获取已加载的插件列表
     */
    fun getLoadedPlugins(): List<Plugin> {
        return pluginLoader.getLoadedPlugins()
    }
    
    /**
     * 获取插件元数据
     */
    fun getPluginMeta(name: String): PluginMeta? {
        return pluginLoader.getPluginMeta(name)
    }
    
    /**
     * 检查Shizuku权限
     */
    fun checkShizukuPermission(): Boolean {
        return shizukuProxy.checkPermission()
    }
    
    /**
     * 检查Shizuku是否已授权（别名）
     */
    fun isShizukuPermissionGranted(): Boolean {
        return shizukuProxy.checkPermission()
    }
    
    /**
     * 请求Shizuku权限
     */
    fun requestShizukuPermission() {
        shizukuProxy.requestPermission()
    }
    
    /**
     * 从URI获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName = it.getString(idx)
                }
            }
        }
        
        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }
        
        return fileName
    }
    
    /**
     * 获取插件目录路径
     */
    fun getPluginDirectoryPath(): String {
        return File(context.filesDir, "plugins").absolutePath
    }
    
    /**
     * 获取插件调试信息
     */
    fun getDebugInfo(): String {
        val pluginDir = File(context.filesDir, "plugins")
        val files = if (pluginDir.exists()) {
            pluginDir.listFiles()?.map { "${it.name} (${it.length()} bytes)" } ?: emptyList()
        } else {
            emptyList()
        }
        
        return """
        |=== 插件调试信息 ===
        |插件目录: ${pluginDir.absolutePath}
        |目录存在: ${pluginDir.exists()}
        |已加载插件: ${pluginLoader.getLoadedPlugins().size} 个
        |插件文件数量: ${files.size} 个
        |插件文件列表:
        |${files.joinToString("\n") { "  - $it" }}
        |Shizuku可用: ${shizukuProxy.isShizukuAvailable()}
        |Shizuku权限: ${shizukuProxy.checkPermission()}
        """.trimMargin()
    }
    
    /**
     * 获取插件目录中的所有文件列表（不考虑是否能加载）
     */
    fun getPluginFiles(): List<String> {
        val pluginDir = File(context.filesDir, "plugins")
        return if (pluginDir.exists()) {
            pluginDir.listFiles()?.map { 
                "${it.name} (${it.length()} bytes) - ${if (it.name.endsWith(".jar") || it.name.endsWith(".dex")) "有效的插件格式" else "无效格式"}"
            } ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * 强制重新加载插件并返回加载结果
     */
    fun reloadAndCheckPlugins(): String {
        val pluginDir = File(context.filesDir, "plugins")
        val files = pluginDir.listFiles()?.filter { 
            it.name.endsWith(".jar", ignoreCase = true) || 
            it.name.endsWith(".dex", ignoreCase = true) 
        } ?: emptyList()
        
        if (files.isEmpty()) {
            return "错误：插件目录中没有找到.jar或.dex文件\n" +
                   "目录路径: ${pluginDir.absolutePath}\n" +
                   "目录存在: ${pluginDir.exists()}"
        }
        
        val loadedPlugins = pluginLoader.reloadPlugins()
        
        return buildString {
            appendLine("=== 插件加载状态 ===")
            appendLine("找到 ${files.size} 个插件文件:")
            files.forEach { file ->
                appendLine("  - ${file.name}")
            }
            appendLine("\n成功加载 ${loadedPlugins.size} 个插件:")
            if (loadedPlugins.isEmpty()) {
                appendLine("  (无)")
                appendLine("\n可能原因:")
                appendLine("1. 插件缺少 META-INF/plugin.properties")
                appendLine("2. mainClass 声明有误")
                appendLine("3. 插件未正确实现 Plugin 接口")
                appendLine("4. 插件文件损坏或格式不正确")
            } else {
                loadedPlugins.forEach { plugin ->
                    appendLine("  - ${plugin.getName()} (版本: ${plugin.getVersion()})")
                }
            }
        }
    }
    
    // ===== 实例池 (Plugin Pool) =====
    
    private data class CloneEntry(
        val plugin: Plugin,
        var busy: Boolean = false,
        var lastUsed: Long = System.currentTimeMillis()
    )
    
    private val pluginClones = ConcurrentHashMap<String, MutableList<CloneEntry>>()
    private val cloneLocks = ConcurrentHashMap<String, Mutex>()
    private val poolScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        poolScope.launch {
            while (isActive) {
                delay(30_000)
                cleanupExpiredClones()
            }
        }
    }
    
    suspend fun acquirePluginInstance(pluginName: String): Plugin {
        val lock = cloneLocks.getOrPut(pluginName) { Mutex() }
        return lock.withLock {
            val clones = pluginClones.getOrPut(pluginName) { mutableListOf() }
            val idle = clones.firstOrNull { !it.busy }
            if (idle != null) {
                idle.busy = true
                idle.lastUsed = System.currentTimeMillis()
                return@withLock idle.plugin
            }
            if (clones.size < 5) {
                val original = pluginLoader.getPlugin(pluginName) ?: throw Exception("插件 $pluginName 未加载")
                val cloned = original.javaClass.getDeclaredConstructor().newInstance() as Plugin
                val entry = CloneEntry(plugin = cloned, busy = true)
                clones.add(entry)
                return@withLock cloned
            }
            // 池满，轮询等待
            while (true) {
                delay(100)
                val nowIdle = clones.firstOrNull { !it.busy }
                if (nowIdle != null) {
                    nowIdle.busy = true
                    nowIdle.lastUsed = System.currentTimeMillis()
                    return@withLock nowIdle.plugin
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw Exception("unreachable")
        }
    }
    
    fun releasePluginInstance(pluginName: String, plugin: Plugin) {
        val lock = cloneLocks[pluginName] ?: return
        kotlinx.coroutines.runBlocking {
            lock.withLock {
                pluginClones[pluginName]?.find { it.plugin === plugin }?.busy = false
            }
        }
    }
    
    private fun cleanupExpiredClones() {
        val now = System.currentTimeMillis()
        pluginClones.forEach { (name, clones) ->
            val lock = cloneLocks[name] ?: return@forEach
            kotlinx.coroutines.runBlocking {
                lock.withLock {
                    clones.removeAll { !it.busy && (now - it.lastUsed) > 30_000L }
                }
            }
        }
    }
    
    fun getCloneCount(pluginName: String): Int {
        return pluginClones[pluginName]?.size ?: 0
    }
    
    fun destroy() {
        poolScope.cancel()
    }
    
    // ===== 子插件调度器 =====
    
    private val subPluginLocks = ConcurrentHashMap<String, Mutex>()
    
    inner class SubPluginDispatcherImpl : SubPluginDispatcher {
        override suspend fun call(subPluginId: String, args: Map<String, String>): String {
            val lock = subPluginLocks.getOrPut(subPluginId) { Mutex() }
            return lock.withLock {
                for (plugin in pluginLoader.getLoadedPlugins()) {
                    val meta = pluginLoader.getPluginMeta(plugin.getName())
                    if (meta?.subPlugins?.contains(subPluginId) == true) {
                        val instance = acquirePluginInstance(plugin.getName())
                        try {
                            return instance.execute(shizukuProxy, mapOf("subPluginId" to subPluginId))
                        } finally {
                            releasePluginInstance(plugin.getName(), instance)
                        }
                    }
                }
                "Error: 未找到子插件 $subPluginId"
            }
        }
    }
    
    // ===== BackgroundPlugin 管理 =====
    
    private val runningBackgroundPlugins = ConcurrentHashMap<String, Job>()
    var onBackgroundPluginStarted: ((String) -> Unit)? = null
    var onBackgroundPluginStopped: ((String) -> Unit)? = null
    
    fun startBackgroundPlugin(pluginName: String): Boolean {
        if (runningBackgroundPlugins.containsKey(pluginName)) return false
        val plugin = pluginLoader.getPlugin(pluginName) ?: return false
        if (plugin !is BackgroundPlugin) return false
        val job = poolScope.launch {
            val dispatcher = SubPluginDispatcherImpl()
            try {
                android.util.Log.d("PluginManager", "BackgroundPlugin started: $pluginName")
                onBackgroundPluginStarted?.invoke(pluginName)
                plugin.runInBackground(shizukuProxy, this, dispatcher)
            } catch (e: CancellationException) {
                android.util.Log.d("PluginManager", "BackgroundPlugin cancelled: $pluginName")
            } catch (e: Exception) {
                android.util.Log.e("PluginManager", "BackgroundPlugin error: $pluginName", e)
                onError?.invoke("后台插件 $pluginName 异常: ${e.message}")
            } finally {
                runningBackgroundPlugins.remove(pluginName)
                onBackgroundPluginStopped?.invoke(pluginName)
            }
        }
        runningBackgroundPlugins[pluginName] = job
        return true
    }
    
    fun stopBackgroundPlugin(pluginName: String): Boolean {
        val job = runningBackgroundPlugins[pluginName] ?: return false
        job.cancel()
        runningBackgroundPlugins.remove(pluginName)
        return true
    }
    
    fun isBackgroundPluginRunning(pluginName: String): Boolean {
        return runningBackgroundPlugins.containsKey(pluginName)
    }
    
    fun getRunningBackgroundPlugins(): Set<String> {
        return runningBackgroundPlugins.keys.toSet()
    }
}